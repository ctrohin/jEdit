/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.formdev.flatlaf.extras.components.FlatTabbedPane;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;
import org.jedit.cursor.CursorConversation;
import org.jedit.cursor.CursorFolderListener;
import org.jedit.cursor.CursorMode;

/**
 * GitHub Copilot dockable with tabbed conversations, reusing the Cursor chat UI.
 */
public final class CopilotView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "copilot";

    private final View view;
    private final JLabel workspaceCaption;
    private final JLabel accountLabel;
    private final JButton loginButton;
    private final JButton logoutButton;
    private final JButton newConversationButton;
    private final FlatTabbedPane conversationTabs;
    private final JComboBox<CursorMode> modeSelector;
    private final JComboBox<CopilotModelInfo> modelSelector;
    private final DefaultComboBoxModel<CopilotModelInfo> modelSelectorModel;
    private final JTextField input;
    private final JButton sendButton;
    private final JButton stopButton;
    private final CursorFolderListener folderListener = new CursorFolderListener(this::refreshWorkspaceCaption);

    private boolean syncingModeSelector;

    public CopilotView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        workspaceCaption = new JLabel(" ");
        accountLabel = new JLabel(" ");

        loginButton = new JButton(jEdit.getProperty("copilot.login"));
        loginButton.addActionListener(e -> login());

        logoutButton = new JButton(jEdit.getProperty("copilot.logout"));
        logoutButton.addActionListener(e -> logout());
        logoutButton.setEnabled(false);

        JPanel header = new JPanel(new BorderLayout(4, 0));
        header.add(workspaceCaption, BorderLayout.CENTER);

        JPanel accountBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        accountBar.add(accountLabel);
        accountBar.add(loginButton);
        accountBar.add(logoutButton);
        header.add(accountBar, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        newConversationButton = new JButton(jEdit.getProperty("copilot.new-conversation"));
        newConversationButton.addActionListener(e -> openNewConversationTab());

        conversationTabs = new FlatTabbedPane();
        conversationTabs.setTabsClosable(true);
        conversationTabs.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
        conversationTabs.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
        conversationTabs.setTabCloseToolTipText(jEdit.getProperty("copilot.tab.close.tooltip"));
        conversationTabs.setTabCloseCallback((pane, tabIndex) -> closeConversationTab(tabIndex));
        conversationTabs.addChangeListener(new TabChangeListener());

        JPanel conversationArea = new JPanel(new BorderLayout(0, 4));
        JPanel tabToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabToolbar.add(newConversationButton);
        conversationArea.add(tabToolbar, BorderLayout.NORTH);
        conversationArea.add(conversationTabs, BorderLayout.CENTER);
        add(conversationArea, BorderLayout.CENTER);

        modeSelector = new JComboBox<>(CursorMode.values());
        modeSelector.setRenderer(new ModeCellRenderer());

        modelSelectorModel = new DefaultComboBoxModel<>();
        modelSelectorModel.addElement(CopilotModelInfo.accountDefault());
        modelSelector = new JComboBox<>(modelSelectorModel);
        modelSelector.setRenderer(new ModelCellRenderer());
        modelSelector.setPrototypeDisplayValue(new CopilotModelInfo(
            "gpt-4.1", "GPT-4.1", ""));

        input = new JTextField();
        input.addActionListener(e -> sendPrompt());

        sendButton = new JButton(jEdit.getProperty("copilot.send"));
        sendButton.addActionListener(e -> sendPrompt());

        stopButton = new JButton(jEdit.getProperty("copilot.stop"));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> activePanel().stopActiveRun());

        JPanel composer = new JPanel(new BorderLayout(4, 0));
        JPanel composerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        composerLeft.add(modeSelector);
        composerLeft.add(modelSelector);
        composer.add(composerLeft, BorderLayout.WEST);
        composer.add(input, BorderLayout.CENTER);
        JPanel composerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        composerRight.add(sendButton);
        composerRight.add(stopButton);
        composer.add(composerRight, BorderLayout.EAST);
        add(composer, BorderLayout.SOUTH);

        modeSelector.addActionListener(e -> {
            if (!syncingModeSelector && modeSelector.getSelectedItem() instanceof CursorMode mode) {
                CopilotConversationPanel panel = activePanel();
                if (panel != null) {
                    panel.setMode(mode);
                }
                jEdit.setProperty(CopilotConfig.MODE_PROPERTY, mode.name());
                updateModeTooltip();
            }
        });
        modelSelector.addActionListener(e -> {
            Object item = modelSelector.getSelectedItem();
            if (item instanceof CopilotModelInfo selected) {
                CopilotConfig.setModelId(selected.isAccountDefault() ? null : selected.id());
                updateModelTooltip();
            }
        });

        restoreConversationsAsync();
        updateModeTooltip();
        updateModelTooltip();
        refreshWorkspaceCaption();
        refreshAuthState();
        if (CopilotAuth.isSignedIn()) {
            refreshModelsAsync();
        }
    }

    public static CopilotView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (CopilotView) dwm.getDockableWindow(NAME);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        refreshWorkspaceCaption();
        refreshAuthState();
        if (CopilotAuth.isSignedIn() && modelSelectorModel.getSize() <= 1) {
            refreshModelsAsync();
        }
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CopilotConversationPanel panel) {
                panel.stopActiveRun();
            }
        }
        saveHistory();
        CopilotLocalBridgePool.releaseAll();
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        input.requestFocusInWindow();
    }

    void promptLogin() {
        login();
    }

    void promptLogout() {
        logout();
    }

    private void restoreConversationsAsync() {
        ThreadUtilities.runInBackground(() -> {
            List<CursorConversation> saved = CopilotHistoryStore.load();
            SwingUtilities.invokeLater(() -> applyRestoredConversations(saved));
        });
    }

    private void applyRestoredConversations(List<CursorConversation> saved) {
        if (conversationTabs.getTabCount() > 0) {
            return;
        }
        if (saved.isEmpty()) {
            openConversationTab(createNewConversation(loadSavedMode()), true);
        } else {
            for (CursorConversation conversation : saved) {
                openConversationTab(conversation, false);
            }
            conversationTabs.setSelectedIndex(conversationTabs.getTabCount() - 1);
        }
        syncModeSelectorFromActiveTab();
    }

    private static CursorConversation createNewConversation(CursorMode mode) {
        return new CursorConversation(
            UUID.randomUUID().toString(),
            jEdit.getProperty("copilot.tab.new"),
            mode,
            null,
            null);
    }

    private void openNewConversationTab() {
        if (activePanel() != null && activePanel().isRunning()) {
            return;
        }
        openConversationTab(createNewConversation(selectedMode()), true);
    }

    private void openConversationTab(CursorConversation conversation, boolean select) {
        Runnable loginRequired = this::login;
        CopilotConversationPanel panel = new CopilotConversationPanel(
            view, conversation, loginRequired, this::updateComposerState,
            this::onConversationUpdated);
        conversationTabs.addTab(conversation.title, panel);
        if (select) {
            conversationTabs.setSelectedComponent(panel);
        }
        updateComposerState();
    }

    private void closeConversationTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= conversationTabs.getTabCount()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(view,
            jEdit.getProperty("copilot.tab.close.confirm"),
            jEdit.getProperty("copilot.tab.close.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Component component = conversationTabs.getComponentAt(tabIndex);
        if (component instanceof CopilotConversationPanel panel) {
            panel.stopActiveRun();
            panel.disposeBridge();
        }
        conversationTabs.removeTabAt(tabIndex);
        saveHistory();
        if (conversationTabs.getTabCount() == 0) {
            openConversationTab(createNewConversation(selectedMode()), true);
        } else {
            syncModeSelectorFromActiveTab();
            updateComposerState();
        }
    }

    private void onConversationUpdated(CursorConversation conversation) {
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CopilotConversationPanel panel
                && panel.conversation() == conversation) {
                conversationTabs.setTitleAt(i, conversation.title);
                break;
            }
        }
        saveHistory();
    }

    private void saveHistory() {
        List<CursorConversation> conversations = new ArrayList<>();
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CopilotConversationPanel panel) {
                conversations.add(panel.conversation());
            }
        }
        List<CursorConversation> snapshot = List.copyOf(conversations);
        ThreadUtilities.runInBackground(() -> CopilotHistoryStore.save(snapshot));
    }

    private CopilotConversationPanel activePanel() {
        Component selected = conversationTabs.getSelectedComponent();
        return selected instanceof CopilotConversationPanel panel ? panel : null;
    }

    private void syncModeSelectorFromActiveTab() {
        CopilotConversationPanel panel = activePanel();
        if (panel == null) {
            return;
        }
        syncingModeSelector = true;
        try {
            modeSelector.setSelectedItem(panel.mode());
            updateModeTooltip();
        } finally {
            syncingModeSelector = false;
        }
    }

    private void updateModeTooltip() {
        Object item = modeSelector.getSelectedItem();
        if (item instanceof CursorMode mode) {
            modeSelector.setToolTipText(jEdit.getProperty(
                "copilot.mode." + mode.name().toLowerCase() + ".description"));
        }
    }

    private void updateModelTooltip() {
        Object item = modelSelector.getSelectedItem();
        if (item instanceof CopilotModelInfo selected
            && selected.description() != null && !selected.description().isBlank()) {
            modelSelector.setToolTipText(selected.description());
        } else {
            modelSelector.setToolTipText(jEdit.getProperty("copilot.model.tooltip"));
        }
    }

    private CursorMode selectedMode() {
        Object item = modeSelector.getSelectedItem();
        return item instanceof CursorMode mode ? mode : loadSavedMode();
    }

    private String selectedModelId() {
        Object item = modelSelector.getSelectedItem();
        if (!(item instanceof CopilotModelInfo selected) || selected.isAccountDefault()) {
            return null;
        }
        return selected.id();
    }

    private CursorMode loadSavedMode() {
        String saved = jEdit.getProperty(CopilotConfig.MODE_PROPERTY);
        if (saved != null) {
            try {
                return CursorMode.valueOf(saved);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return CursorMode.AGENT;
    }

    private void sendPrompt() {
        CopilotConversationPanel panel = activePanel();
        if (panel == null || panel.isRunning()) {
            return;
        }
        String userText = input.getText().trim();
        if (userText.isEmpty()) {
            return;
        }
        input.setText("");
        panel.sendMessage(userText, selectedModelId());
        updateComposerState();
    }

    private void updateComposerState() {
        boolean signedIn = CopilotAuth.isSignedIn();
        CopilotConversationPanel panel = activePanel();
        boolean running = panel != null && panel.isRunning();
        input.setEnabled(signedIn && !running);
        sendButton.setEnabled(signedIn && !running);
        stopButton.setEnabled(signedIn && running);
        modeSelector.setEnabled(signedIn && !running);
        modelSelector.setEnabled(signedIn && !running);
        newConversationButton.setEnabled(signedIn && !running);
        if (panel != null) {
            panel.refreshAuthState(signedIn);
        }
    }

    private void refreshWorkspaceCaption() {
        workspaceCaption.setText(CopilotWorkspaceContext.captionText());
    }

    private void refreshAuthState() {
        boolean signedIn = CopilotAuth.isSignedIn();
        loginButton.setEnabled(!signedIn);
        logoutButton.setEnabled(signedIn);
        accountLabel.setText(signedIn
            ? jEdit.getProperty("copilot.logged-in")
            : jEdit.getProperty("copilot.logged-out"));
        if (!signedIn) {
            resetModelSelector();
        }
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CopilotConversationPanel panel) {
                panel.refreshAuthState(signedIn);
            }
        }
        updateComposerState();
    }

    private void refreshModelsAsync() {
        if (!CopilotAuth.isSignedIn()) {
            return;
        }
        modelSelector.setEnabled(false);
        String token = CopilotConfig.gitHubToken();
        String cwd = CopilotWorkspaceContext.defaultCwd();
        ThreadUtilities.runInBackground(() -> {
            try {
                CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__models__");
                List<CopilotModelInfo> models = bridge.listModels(token, cwd);
                SwingUtilities.invokeLater(() -> applyModels(models));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    resetModelSelector();
                    updateComposerState();
                });
            }
        });
    }

    private void applyModels(List<CopilotModelInfo> models) {
        String savedId = CopilotConfig.modelId();
        modelSelectorModel.removeAllElements();
        modelSelectorModel.addElement(CopilotModelInfo.accountDefault());
        CopilotModelInfo selected = null;
        for (CopilotModelInfo model : models) {
            if (CopilotModelInfo.isDuplicateAuto(model)) {
                continue;
            }
            modelSelectorModel.addElement(model);
            if (selected == null && savedId != null && savedId.equals(model.id())) {
                selected = model;
            }
        }
        if (selected == null) {
            selected = modelSelectorModel.getElementAt(0);
        }
        modelSelector.setSelectedItem(selected);
        updateModelTooltip();
        updateComposerState();
    }

    private void resetModelSelector() {
        modelSelectorModel.removeAllElements();
        modelSelectorModel.addElement(CopilotModelInfo.accountDefault());
        modelSelector.setSelectedIndex(0);
        updateModelTooltip();
    }

    private void login() {
        JPasswordField tokenField = new JPasswordField(32);
        Object[] message = {
            jEdit.getProperty("copilot.login.message"),
            tokenField
        };
        int choice = JOptionPane.showConfirmDialog(view, message,
            jEdit.getProperty("copilot.login.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        char[] tokenChars = tokenField.getPassword();
        String token = tokenChars == null ? "" : new String(tokenChars).trim();
        if (tokenChars != null) {
            java.util.Arrays.fill(tokenChars, '\0');
        }

        String tokenError = CopilotTokenValidator.validateTokenOrNull(token);
        if (tokenError != null) {
            JOptionPane.showMessageDialog(view, tokenError,
                jEdit.getProperty("copilot.login.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        accountLabel.setText(jEdit.getProperty("copilot.logged-in-pending"));
        String cwd = CopilotWorkspaceContext.defaultCwd();
        ThreadUtilities.runInBackground(() -> {
            try {
                CopilotLocalBridgePool.releaseAll();
                CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__auth__");
                bridge.validate(token.isEmpty() ? null : token, cwd);
                if (token.isEmpty()) {
                    CopilotConfig.setGitHubToken(null);
                } else {
                    CopilotConfig.setGitHubToken(token);
                }
                CopilotAuth.setSignedIn(true);
                CopilotPlugin.authenticateGhostLsp();
                SwingUtilities.invokeLater(() -> {
                    refreshAuthState();
                    refreshModelsAsync();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    String detail = CopilotTokenValidator.formatAuthError(e.getMessage());
                    JOptionPane.showMessageDialog(view,
                        jEdit.getProperty("copilot.login.failed", new String[] { detail }),
                        jEdit.getProperty("copilot.login.title"),
                        JOptionPane.ERROR_MESSAGE);
                    refreshAuthState();
                });
            }
        });
    }

    private void logout() {
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CopilotConversationPanel panel) {
                panel.stopActiveRun();
            }
        }
        saveHistory();
        CopilotLocalBridgePool.releaseAll();
        CopilotAuth.clear();
        refreshAuthState();
    }

    private final class TabChangeListener implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            syncModeSelectorFromActiveTab();
            updateComposerState();
        }
    }

    private static final class ModeCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            if (value instanceof CursorMode mode && component instanceof JLabel label) {
                label.setText(jEdit.getProperty("copilot.mode." + mode.name().toLowerCase()));
            }
            return component;
        }
    }

    private static final class ModelCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            if (value instanceof CopilotModelInfo model && component instanceof JLabel label) {
                label.setText(model.displayName());
            }
            return component;
        }
    }
}
