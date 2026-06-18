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
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import com.formdev.flatlaf.extras.components.FlatTabbedPane;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.jEdit;

/**
 * Output console with tabbed build/task runs.
 */
public final class BuildOutputView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "build-output";
    private static final String EMPTY_CARD = "empty";
    private static final String TABS_CARD = "tabs";

    private final View view;
    private final FlatTabbedPane tabbedPane;
    private final CardLayout contentLayout;
    private final JPanel contentPanel;
    private final Map<String, BuildOutputTab> tabsByKey = new LinkedHashMap<>();
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateProjectRoots);

    public BuildOutputView(View view) {
        super(new BorderLayout());
        this.view = view;

        tabbedPane = new FlatTabbedPane();
        tabbedPane.setTabsClosable(true);
        tabbedPane.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
        tabbedPane.setTabCloseToolTipText(jEdit.getProperty("build-output.close-tab"));
        tabbedPane.setTabCloseCallback((pane, tabIndex) -> closeTabAt(tabIndex));

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        JLabel emptyLabel = new JLabel(jEdit.getProperty("build-output.empty"));
        emptyPanel.add(emptyLabel, new GridBagConstraints());

        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.add(emptyPanel, EMPTY_CARD);
        contentPanel.add(tabbedPane, TABS_CARD);
        add(contentPanel, BorderLayout.CENTER);
        updateEmptyState();
    }

    public static BuildOutputView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (BuildOutputView) dwm.getDockableWindow(NAME);
    }

    void runBuild(File workingDir, List<String> command) {
        runBuild(null, workingDir, command, null);
    }

    void runBuild(File workingDir, List<String> command, Map<String, String> environment) {
        runBuild(null, workingDir, command, environment);
    }

    void runBuild(String taskTitle, File workingDir, List<String> command,
                  Map<String, String> environment) {
        String taskKey = BuildOutputTasks.taskKey(workingDir, command);
        BuildOutputTab existing = tabsByKey.get(taskKey);
        if (existing != null && existing.isRunning() && !confirmRestart()) {
            return;
        }
        String title = taskTitle != null && !taskTitle.isBlank()
            ? taskTitle.trim()
            : BuildOutputTasks.defaultTitle(command);
        BuildOutputTab tab = findOrCreateTab(taskKey, title);
        tab.runner.stop();
        tab.output.clearOutput();
        tab.output.appendLine("$ " + String.join(" ", command));
        setTabStatus(tab, jEdit.getProperty("build-output.running"));
        tab.runner.run(workingDir, command, environment,
            (line, error) -> tab.output.appendLine(line, error ? LinkAwareTextArea.errorColor() : null),
            () -> setTabStatus(tab, jEdit.getProperty("build-output.finished")));
        updateTabHeader(tab);
        tabbedPane.setSelectedComponent(tab.panel);
    }

    private boolean confirmRestart() {
        String cancel = jEdit.getProperty("common.cancel");
        String restart = jEdit.getProperty("build-output.already-running.restart");
        int choice = JOptionPane.showOptionDialog(
            view,
            jEdit.getProperty("build-output.already-running.message"),
            jEdit.getProperty("build-output.already-running.title"),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[] {cancel, restart},
            cancel);
        return choice == 1;
    }

    private BuildOutputTab findOrCreateTab(String taskKey, String title) {
        BuildOutputTab existing = tabsByKey.get(taskKey);
        if (existing != null) {
            existing.setTitle(title);
            tabbedPane.setSelectedComponent(existing.panel);
            updateTabHeader(existing);
            return existing;
        }

        BuildOutputTab tab = new BuildOutputTab(
            view, taskKey, title, currentMaxLines(), this::openSettings);
        tabsByKey.put(taskKey, tab);
        tabbedPane.addTab(title, tab.panel);
        updateProjectRoot(tab);
        updateEmptyState();
        return tab;
    }

    private void openSettings() {
        if (BuildOutputSettingsDialog.show(view)) {
            applyMaxLines(currentMaxLines());
        }
    }

    private void updateTabHeader(BuildOutputTab tab) {
        int index = tabbedPane.indexOfComponent(tab.panel);
        if (index < 0) {
            return;
        }
        tabbedPane.setTitleAt(index, tab.getTitle());
    }

    private void closeTabAt(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabbedPane.getTabCount()) {
            return;
        }
        Component component = tabbedPane.getComponentAt(tabIndex);
        for (BuildOutputTab tab : tabsByKey.values()) {
            if (tab.panel == component) {
                closeTab(tab);
                return;
            }
        }
    }

    private void closeTab(BuildOutputTab tab) {
        tab.runner.stop();
        tabsByKey.remove(tab.taskKey);
        tabbedPane.remove(tab.panel);
        updateEmptyState();
    }

    private void updateEmptyState() {
        contentLayout.show(contentPanel,
            tabbedPane.getTabCount() == 0 ? EMPTY_CARD : TABS_CARD);
    }

    private BuildOutputTab selectedTab() {
        var selected = tabbedPane.getSelectedComponent();
        if (selected == null) {
            return null;
        }
        for (BuildOutputTab tab : tabsByKey.values()) {
            if (tab.panel == selected) {
                return tab;
            }
        }
        return null;
    }

    private void setTabStatus(BuildOutputTab tab, String status) {
        tab.setStatus(status);
        updateTabHeader(tab);
    }

    private void applyMaxLines(int value) {
        for (BuildOutputTab tab : tabsByKey.values()) {
            tab.output.setMaxLines(value);
        }
    }

    private int currentMaxLines() {
        return jEdit.getIntegerProperty(
            BuildOutputSettingsDialog.MAX_LINES_PROPERTY,
            BuildOutputSettingsDialog.DEFAULT_MAX_LINES);
    }

    private void updateProjectRoots() {
        File root = ProjectRoots.workspaceRoot();
        for (BuildOutputTab tab : tabsByKey.values()) {
            tab.output.setProjectRoot(root);
        }
    }

    private void updateProjectRoot(BuildOutputTab tab) {
        tab.output.setProjectRoot(ProjectRoots.workspaceRoot());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        updateProjectRoots();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        BuildOutputTab tab = selectedTab();
        if (tab != null) {
            tab.output.requestFocus();
        } else if (tabbedPane.getTabCount() > 0) {
            tabbedPane.requestFocus();
        }
    }
}
