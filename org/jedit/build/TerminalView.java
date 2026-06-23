/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.extras.components.FlatTabbedPane;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.jEdit;

/**
 * Integrated terminal with persistent PTY shell sessions.
 */
public final class TerminalView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "terminal";
    private static final String EMPTY_CARD = "empty";
    private static final String TABS_CARD = "tabs";

    private final View view;
    private final JLabel caption;
    private final FlatTabbedPane tabbedPane;
    private final CardLayout contentLayout;
    private final JPanel contentPanel;
    private final JComboBox<String> profileCombo;
    private final Map<String, TerminalTab> tabsByKey = new LinkedHashMap<>();
    private final AtomicInteger tabCounter = new AtomicInteger();
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateCaption);

    public TerminalView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel();
        updateCaption();

        tabbedPane = createTabbedPane();

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        JLabel emptyLabel = new JLabel(jEdit.getProperty("terminal.empty"));
        emptyPanel.add(emptyLabel, new GridBagConstraints());

        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.add(emptyPanel, EMPTY_CARD);
        contentPanel.add(tabbedPane, TABS_CARD);

        profileCombo = new JComboBox<>(TerminalEnvProfiles.profileNames().toArray(new String[0]));
        profileCombo.setSelectedItem("Default");

        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(caption, BorderLayout.CENTER);
        JPanel northButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        northButtons.add(new JLabel(jEdit.getProperty("terminal.profile")));
        northButtons.add(profileCombo);
        JButton newTerminal = new JButton(jEdit.getProperty("terminal.new"));
        newTerminal.addActionListener(e -> showNewTerminalMenu(newTerminal));
        northButtons.add(newTerminal);
        north.add(northButtons, BorderLayout.EAST);

        add(north, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        updateEmptyState();
    }

    public static TerminalView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (TerminalView) dwm.getDockableWindow(NAME);
    }

    private FlatTabbedPane createTabbedPane() {
        FlatTabbedPane pane = new FlatTabbedPane();
        pane.setTabsClosable(true);
        pane.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
        pane.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
        pane.setTabCloseToolTipText(jEdit.getProperty("terminal.close-tab"));
        pane.setTabCloseCallback((p, tabIndex) -> closeTabAt(tabIndex));
        pane.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = pane.indexAtLocation(e.getX(), e.getY());
                    if (index >= 0) {
                        renameTabAt(index);
                    }
                }
            }
        });
        return pane;
    }

    private String selectedProfile() {
        Object selected = profileCombo.getSelectedItem();
        return selected != null ? selected.toString() : "Default";
    }

    private void showNewTerminalMenu(JComponent invoker) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("terminal.new-workspace", () ->
            openNewTab(TerminalSessionConfig.workspaceDefault(selectedProfile()))));
        File root = ProjectRoots.workspaceRoot();
        if (root != null) {
            for (File module : ProjectModuleRoots.listModuleDirectories(root)) {
                if (module.equals(root)) {
                    continue;
                }
                String label = root.toPath().relativize(module.toPath()).toString();
                menu.add(item(jEdit.getProperty("terminal.new-module",
                    new Object[] {label}), () -> openNewTab(
                    TerminalSessionConfig.forDirectory(
                        module, selectedProfile(), label))));
            }
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    private static JMenuItem item(String property, Runnable action) {
        JMenuItem menuItem = new JMenuItem(jEdit.getProperty(property));
        menuItem.addActionListener(e -> action.run());
        return menuItem;
    }

    private void openNewTab() {
        openNewTab(TerminalSessionConfig.workspaceDefault(selectedProfile()));
    }

    private void openNewTab(TerminalSessionConfig config) {
        String tabKey = "terminal-" + tabCounter.incrementAndGet();
        try {
            final TerminalTab[] holder = new TerminalTab[1];
            holder[0] = new TerminalTab(view, tabKey, config, () -> updateTabHeader(holder[0]));
            TerminalTab tab = holder[0];
            assignUniqueTitle(tab);
            tabsByKey.put(tabKey, tab);
            tabbedPane.addTab(tab.getTitle(), tab.panel);
            updateEmptyState();
            tabbedPane.setSelectedComponent(tab.panel);
            SwingUtilities.invokeLater(tab::requestFocus);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                view,
                jEdit.getProperty("terminal.start-failed", new Object[] {e.getMessage()}),
                jEdit.getProperty("terminal.start-failed.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void assignUniqueTitle(TerminalTab tab) {
        String base = tab.getTitle();
        int suffix = 1;
        String candidate = base;
        while (titleInUse(candidate, tab)) {
            suffix++;
            candidate = base + " (" + suffix + ")";
        }
        if (!candidate.equals(base)) {
            tab.setTitleSuffix(" (" + suffix + ")");
        }
    }

    private boolean titleInUse(String title, TerminalTab except) {
        for (TerminalTab other : tabsByKey.values()) {
            if (other != except && title.equals(other.getTitle())) {
                return true;
            }
        }
        return false;
    }

    private void updateTabHeader(TerminalTab tab) {
        int index = tabbedPane.indexOfComponent(tab.panel);
        if (index >= 0) {
            tabbedPane.setTitleAt(index, tab.getTitle());
        }
    }

    private void renameTabAt(int tabIndex) {
        TerminalTab tab = tabForComponent(tabbedPane.getComponentAt(tabIndex));
        if (tab == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(
            view,
            jEdit.getProperty("terminal.rename.prompt"),
            tab.getTitle());
        if (name != null && !name.isBlank()) {
            tab.setCustomName(name.trim());
            updateTabHeader(tab);
        }
    }

    private void closeTabAt(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabbedPane.getTabCount()) {
            return;
        }
        TerminalTab tab = tabForComponent(tabbedPane.getComponentAt(tabIndex));
        if (tab != null) {
            closeTab(tab);
        }
    }

    private TerminalTab tabForComponent(java.awt.Component component) {
        if (!(component instanceof JComponent jComponent)) {
            return null;
        }
        for (TerminalTab tab : tabsByKey.values()) {
            if (tab.panel == jComponent) {
                return tab;
            }
        }
        return null;
    }

    private void closeTab(TerminalTab tab) {
        tab.close();
        tabsByKey.entrySet().removeIf(entry -> entry.getValue() == tab);
        tabbedPane.remove(tab.panel);
        updateEmptyState();
    }

    private void updateEmptyState() {
        contentLayout.show(contentPanel,
            tabbedPane.getTabCount() > 0 ? TABS_CARD : EMPTY_CARD);
    }

    private TerminalTab selectedTab() {
        var selected = tabbedPane.getSelectedComponent();
        if (selected == null) {
            return null;
        }
        return tabForComponent(selected);
    }

    private void updateCaption() {
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("terminal.no-workspace"));
        } else {
            caption.setText(jEdit.getProperty("terminal.caption",
                new Object[] {root.getAbsolutePath()}));
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        updateCaption();
        if (tabbedPane.getTabCount() == 0) {
            SwingUtilities.invokeLater(this::openNewTab);
        }
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        for (TerminalTab tab : List.copyOf(tabsByKey.values())) {
            tab.close();
        }
        tabsByKey.clear();
        tabbedPane.removeAll();
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        TerminalTab tab = selectedTab();
        if (tab != null) {
            tab.requestFocus();
        } else {
            openNewTab();
        }
    }
}
