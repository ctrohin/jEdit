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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
    private final Map<String, TerminalTab> tabsByKey = new LinkedHashMap<>();
    private final AtomicInteger tabCounter = new AtomicInteger();
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateCaption);

    public TerminalView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel();
        updateCaption();

        tabbedPane = new FlatTabbedPane();
        tabbedPane.setTabsClosable(true);
        tabbedPane.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
        tabbedPane.setTabCloseToolTipText(jEdit.getProperty("terminal.close-tab"));
        tabbedPane.setTabCloseCallback((pane, tabIndex) -> closeTabAt(tabIndex));

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        JLabel emptyLabel = new JLabel(jEdit.getProperty("terminal.empty"));
        emptyPanel.add(emptyLabel, new GridBagConstraints());

        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.add(emptyPanel, EMPTY_CARD);
        contentPanel.add(tabbedPane, TABS_CARD);

        JPanel north = new JPanel(new BorderLayout(4, 0));
        north.add(caption, BorderLayout.CENTER);
        JPanel northButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton newTerminal = new JButton(jEdit.getProperty("terminal.new"));
        newTerminal.addActionListener(e -> openNewTab());
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

    private File workingDirectory() {
        File root = ProjectRoots.workspaceRoot();
        return root != null ? root : new File(System.getProperty("user.home"));
    }

    private void openNewTab() {
        String tabKey = "terminal-" + tabCounter.incrementAndGet();
        try {
            final TerminalTab[] holder = new TerminalTab[1];
            holder[0] = new TerminalTab(tabKey, workingDirectory(), () -> updateTabHeader(holder[0]));
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

    private void closeTabAt(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabbedPane.getTabCount()) {
            return;
        }
        JComponent component = (JComponent) tabbedPane.getComponentAt(tabIndex);
        for (TerminalTab tab : tabsByKey.values()) {
            if (tab.panel == component) {
                closeTab(tab);
                return;
            }
        }
    }

    private void closeTab(TerminalTab tab) {
        tab.close();
        tabsByKey.entrySet().removeIf(entry -> entry.getValue() == tab);
        tabbedPane.remove(tab.panel);
        updateEmptyState();
    }

    private void updateEmptyState() {
        contentLayout.show(contentPanel,
            tabbedPane.getTabCount() == 0 ? EMPTY_CARD : TABS_CARD);
    }

    private TerminalTab selectedTab() {
        var selected = tabbedPane.getSelectedComponent();
        if (selected == null) {
            return null;
        }
        for (TerminalTab tab : tabsByKey.values()) {
            if (tab.panel == selected) {
                return tab;
            }
        }
        return null;
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
            closeTab(tab);
        }
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
