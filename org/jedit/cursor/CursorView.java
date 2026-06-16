/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

/**
 * Cursor Cloud Agents dockable with tabbed conversations and persisted history.
 */
public final class CursorView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "cursor";

    private final View view;
    private final JLabel workspaceCaption;
    private final JLabel accountLabel;
    private final JButton loginButton;
    private final JButton logoutButton;
    private final JButton newConversationButton;
    private final FlatTabbedPane conversationTabs;
    private final JComboBox<CursorMode> modeSelector;
    private final JComboBox<CursorRuntime> runtimeSelector;
    private final JComboBox<CursorModelInfo> modelSelector;
    private final DefaultComboBoxModel<CursorModelInfo> modelSelectorModel;
    private final JTextField input;
    private final JButton sendButton;
    private final JButton stopButton;
    private final CursorFolderListener folderListener = new CursorFolderListener(this::refreshWorkspaceCaption);

    private CursorApiClient.AccountInfo accountInfo;
    private boolean syncingModeSelector;

    public CursorView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        workspaceCaption = new JLabel(" ");
        accountLabel = new JLabel(" ");

        loginButton = new JButton(jEdit.getProperty("cursor.login"));
        loginButton.addActionListener(e -> login());

        logoutButton = new JButton(jEdit.getProperty("cursor.logout"));
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

        newConversationButton = new JButton(jEdit.getProperty("cursor.new-conversation"));
        newConversationButton.addActionListener(e -> openNewConversationTab());

        conversationTabs = new FlatTabbedPane();
        conversationTabs.setTabsClosable(true);
        conversationTabs.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
        conversationTabs.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
        conversationTabs.setTabCloseToolTipText(jEdit.getProperty("cursor.tab.close.tooltip"));
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

        runtimeSelector = new JComboBox<>(CursorRuntime.values());
        runtimeSelector.setRenderer(new RuntimeCellRenderer());
        runtimeSelector.setSelectedItem(CursorConfig.runtime());

        modelSelectorModel = new DefaultComboBoxModel<>();
        modelSelectorModel.addElement(CursorModelInfo.accountDefault());
        modelSelector = new JComboBox<>(modelSelectorModel);
        modelSelector.setRenderer(new ModelCellRenderer());
        modelSelector.setPrototypeDisplayValue(new CursorModelInfo(
            "composer-2", "Composer 2", ""));

        input = new JTextField();
        input.addActionListener(e -> sendPrompt());

        sendButton = new JButton(jEdit.getProperty("cursor.send"));
        sendButton.addActionListener(e -> sendPrompt());

        stopButton = new JButton(jEdit.getProperty("cursor.stop"));
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> activePanel().stopActiveRun());

        JPanel composer = new JPanel(new BorderLayout(4, 0));
        JPanel composerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        composerLeft.add(modeSelector);
        composerLeft.add(runtimeSelector);
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
                CursorConversationPanel panel = activePanel();
                if (panel != null) {
                    panel.setMode(mode);
                }
                jEdit.setProperty(CursorConfig.MODE_PROPERTY, mode.name());
                updateModeTooltip();
            }
        });
        runtimeSelector.addActionListener(e -> {
            if (runtimeSelector.getSelectedItem() instanceof CursorRuntime runtime) {
                CursorConfig.setRuntime(runtime);
                updateRuntimeTooltip();
            }
        });
        modelSelector.addActionListener(e -> {
            Object item = modelSelector.getSelectedItem();
            if (item instanceof CursorModelInfo selected) {
                CursorConfig.setModelId(selected.isAccountDefault() ? null : selected.id());
                updateModelTooltip();
            }
        });

        restoreConversationsAsync();
        updateModeTooltip();
        updateRuntimeTooltip();
        updateModelTooltip();
        refreshWorkspaceCaption();
        refreshAuthState();
        if (CursorConfig.apiKey() != null) {
            refreshModelsAsync();
        }
    }

    public static CursorView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (CursorView) dwm.getDockableWindow(NAME);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        refreshWorkspaceCaption();
        refreshAuthState();
        if (CursorConfig.apiKey() != null && modelSelectorModel.getSize() <= 1) {
            refreshModelsAsync();
        }
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CursorConversationPanel panel) {
                panel.stopActiveRun();
            }
        }
        saveHistory();
        CursorLocalBridgePool.releaseAll();
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        input.requestFocusInWindow();
    }

    private void restoreConversationsAsync() {
        ThreadUtilities.runInBackground(() -> {
            List<CursorConversation> saved = CursorHistoryStore.load();
            SwingUtilities.invokeLater(() -> applyRestoredConversations(saved));
        });
    }

    private void applyRestoredConversations(List<CursorConversation> saved) {
        if (conversationTabs.getTabCount() > 0) {
            return;
        }
        if (saved.isEmpty()) {
            openConversationTab(CursorConversation.createNew(loadSavedMode()), true);
        } else {
            for (CursorConversation conversation : saved) {
                openConversationTab(conversation, false);
            }
            conversationTabs.setSelectedIndex(conversationTabs.getTabCount() - 1);
        }
        syncModeSelectorFromActiveTab();
    }

    private void openNewConversationTab() {
        if (activePanel() != null && activePanel().isRunning()) {
            return;
        }
        openConversationTab(CursorConversation.createNew(selectedMode()), true);
    }

    private void openConversationTab(CursorConversation conversation, boolean select) {
        Runnable loginRequired = this::login;
        CursorConversationPanel panel = new CursorConversationPanel(
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
            jEdit.getProperty("cursor.tab.close.confirm"),
            jEdit.getProperty("cursor.tab.close.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        Component component = conversationTabs.getComponentAt(tabIndex);
        if (component instanceof CursorConversationPanel panel) {
            panel.stopActiveRun();
            panel.disposeBridge();
        }
        conversationTabs.removeTabAt(tabIndex);
        saveHistory();
        if (conversationTabs.getTabCount() == 0) {
            openConversationTab(CursorConversation.createNew(selectedMode()), true);
        } else {
            syncModeSelectorFromActiveTab();
            updateComposerState();
        }
    }

    private void onConversationUpdated(CursorConversation conversation) {
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CursorConversationPanel panel
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
            if (component instanceof CursorConversationPanel panel) {
                conversations.add(panel.conversation());
            }
        }
        List<CursorConversation> snapshot = List.copyOf(conversations);
        ThreadUtilities.runInBackground(() -> CursorHistoryStore.save(snapshot));
    }

    private CursorConversationPanel activePanel() {
        Component selected = conversationTabs.getSelectedComponent();
        return selected instanceof CursorConversationPanel panel ? panel : null;
    }

    private void syncModeSelectorFromActiveTab() {
        CursorConversationPanel panel = activePanel();
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
                "cursor.mode." + mode.name().toLowerCase() + ".description"));
        }
    }

    private void updateRuntimeTooltip() {
        Object item = runtimeSelector.getSelectedItem();
        if (item instanceof CursorRuntime runtime) {
            runtimeSelector.setToolTipText(jEdit.getProperty(
                "cursor.runtime." + runtime.name().toLowerCase() + ".description"));
        }
    }

    private void updateModelTooltip() {
        Object item = modelSelector.getSelectedItem();
        if (item instanceof CursorModelInfo selected
            && selected.description() != null && !selected.description().isBlank()) {
            modelSelector.setToolTipText(selected.description());
        } else {
            modelSelector.setToolTipText(jEdit.getProperty("cursor.model.tooltip"));
        }
    }

    private CursorMode selectedMode() {
        Object item = modeSelector.getSelectedItem();
        return item instanceof CursorMode mode ? mode : loadSavedMode();
    }

    private String selectedModelId() {
        Object item = modelSelector.getSelectedItem();
        if (!(item instanceof CursorModelInfo selected) || selected.isAccountDefault()) {
            return null;
        }
        return selected.id();
    }

    private CursorMode loadSavedMode() {
        String saved = jEdit.getProperty(CursorConfig.MODE_PROPERTY);
        if (saved != null) {
            try {
                return CursorMode.valueOf(saved);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return CursorMode.AGENT;
    }

    private CursorRuntime selectedRuntime() {
        Object item = runtimeSelector.getSelectedItem();
        return item instanceof CursorRuntime runtime ? runtime : CursorConfig.runtime();
    }

    private void sendPrompt() {
        CursorConversationPanel panel = activePanel();
        if (panel == null || panel.isRunning()) {
            return;
        }
        String userText = input.getText().trim();
        if (userText.isEmpty()) {
            return;
        }
        input.setText("");
        panel.sendMessage(userText, selectedModelId(), selectedRuntime());
        updateComposerState();
    }

    private void updateComposerState() {
        boolean loggedIn = CursorConfig.apiKey() != null;
        CursorConversationPanel panel = activePanel();
        boolean running = panel != null && panel.isRunning();
        input.setEnabled(loggedIn && !running);
        sendButton.setEnabled(loggedIn && !running);
        stopButton.setEnabled(loggedIn && running);
        modeSelector.setEnabled(loggedIn && !running);
        runtimeSelector.setEnabled(loggedIn && !running);
        modelSelector.setEnabled(loggedIn && !running);
        newConversationButton.setEnabled(loggedIn && !running);
        if (panel != null) {
            panel.refreshAuthState(loggedIn);
        }
    }

    private void refreshWorkspaceCaption() {
        workspaceCaption.setText(CursorWorkspaceContext.captionText());
    }

    private void refreshAuthState() {
        boolean loggedIn = CursorConfig.apiKey() != null;
        loginButton.setEnabled(!loggedIn);
        logoutButton.setEnabled(loggedIn);
        if (loggedIn) {
            updateAccountLabel();
            if (accountInfo == null) {
                refreshAccountAsync();
            }
        } else {
            accountInfo = null;
            accountLabel.setText(jEdit.getProperty("cursor.logged-out"));
            resetModelSelector();
        }
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CursorConversationPanel panel) {
                panel.refreshAuthState(loggedIn);
            }
        }
        updateComposerState();
    }

    private void updateAccountLabel() {
        if (accountInfo != null) {
            String text = accountInfo.displayName;
            if (accountInfo.userEmail != null && !accountInfo.userEmail.isBlank()
                && !accountInfo.userEmail.equals(accountInfo.displayName)) {
                text = accountInfo.displayName + " <" + accountInfo.userEmail + ">";
            }
            accountLabel.setText(jEdit.getProperty("cursor.logged-in", new String[] { text }));
        } else {
            accountLabel.setText(jEdit.getProperty("cursor.logged-in-pending"));
        }
    }

    private void refreshAccountAsync() {
        String apiKey = CursorConfig.apiKey();
        if (apiKey == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            try {
                CursorApiClient client = new CursorApiClient(apiKey);
                CursorApiClient.AccountInfo info = client.fetchAccount();
                SwingUtilities.invokeLater(() -> {
                    accountInfo = info;
                    updateAccountLabel();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() ->
                    accountLabel.setText(jEdit.getProperty("cursor.logged-in-error")));
            }
        });
    }

    private void refreshModelsAsync() {
        String apiKey = CursorConfig.apiKey();
        if (apiKey == null) {
            return;
        }
        modelSelector.setEnabled(false);
        ThreadUtilities.runInBackground(() -> {
            try {
                List<CursorModelInfo> models = new CursorApiClient(apiKey).fetchModels();
                SwingUtilities.invokeLater(() -> applyModels(models));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    resetModelSelector();
                    updateComposerState();
                });
            }
        });
    }

    private void applyModels(List<CursorModelInfo> models) {
        String savedId = CursorConfig.modelId();
        modelSelectorModel.removeAllElements();
        modelSelectorModel.addElement(CursorModelInfo.accountDefault());
        CursorModelInfo selected = null;
        for (CursorModelInfo model : models) {
            if (CursorModelInfo.isDuplicateAuto(model)) {
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
        modelSelectorModel.addElement(CursorModelInfo.accountDefault());
        modelSelector.setSelectedIndex(0);
        updateModelTooltip();
    }

    private void login() {
        JPasswordField apiKeyField = new JPasswordField(32);
        Object[] message = {
            jEdit.getProperty("cursor.login.message"),
            apiKeyField
        };
        int choice = JOptionPane.showConfirmDialog(view, message,
            jEdit.getProperty("cursor.login.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        char[] keyChars = apiKeyField.getPassword();
        String apiKey = keyChars == null ? "" : new String(keyChars).trim();
        if (keyChars != null) {
            java.util.Arrays.fill(keyChars, '\0');
        }
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                jEdit.getProperty("cursor.login.empty-key"),
                jEdit.getProperty("cursor.login.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        loginButton.setEnabled(false);
        accountLabel.setText(jEdit.getProperty("cursor.logged-in-pending"));
        ThreadUtilities.runInBackground(() -> {
            try {
                CursorApiClient client = new CursorApiClient(apiKey);
                CursorApiClient.AccountInfo info = client.fetchAccount();
                CursorConfig.setApiKey(apiKey);
                SwingUtilities.invokeLater(() -> {
                    accountInfo = info;
                    refreshAuthState();
                    refreshModelsAsync();
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    accountInfo = null;
                    JOptionPane.showMessageDialog(view,
                        jEdit.getProperty("cursor.login.failed", new String[] { e.getMessage() }),
                        jEdit.getProperty("cursor.login.title"),
                        JOptionPane.ERROR_MESSAGE);
                    refreshAuthState();
                });
            }
        });
    }

    private void logout() {
        for (int i = 0; i < conversationTabs.getTabCount(); i++) {
            Component component = conversationTabs.getComponentAt(i);
            if (component instanceof CursorConversationPanel panel) {
                panel.stopActiveRun();
            }
        }
        saveHistory();
        CursorLocalBridgePool.releaseAll();
        CursorConfig.clearSession();
        accountInfo = null;
        refreshAuthState();
    }

    void promptLogin() {
        login();
    }

    void promptLogout() {
        logout();
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
                label.setText(mode.label());
            }
            return component;
        }
    }

    private static final class RuntimeCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            if (value instanceof CursorRuntime runtime && component instanceof JLabel label) {
                label.setText(runtime.label());
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
            if (value instanceof CursorModelInfo model && component instanceof JLabel label) {
                label.setText(model.displayName());
            }
            return component;
        }
    }
}
