/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

final class WorkspaceRunConfigurationPopup {

    private WorkspaceRunConfigurationPopup() {}

    static void show(ActionEvent event, View view, File projectRoot, Runnable onChanged) {
        Object source = event.getSource();
        if (!(source instanceof JComponent invoker)) {
            return;
        }
        WorkspaceRunConfigurationSet set = WorkspaceRunConfigurationPreferences.load(projectRoot);
        JPopupMenu menu = new JPopupMenu();
        for (WorkspaceRunConfiguration cfg : set.getConfigurations()) {
            String label = cfg.displayName();
            if (label.isEmpty()) {
                label = WorkspaceProjectRunner.kindLabel(cfg.kind) + ": " + cfg.runGoal;
            }
            JMenuItem item = new JMenuItem(label);
            item.setToolTipText(WorkspaceProjectRunner.kindLabel(cfg.kind)
                + ": " + cfg.runGoal);
            if (cfg.id.equals(set.getDefaultId())) {
                item.setFont(item.getFont().deriveFont(java.awt.Font.BOLD));
            }
            item.addActionListener(e ->
                WorkspaceProjectRunner.runConfiguration(view, projectRoot, cfg));
            menu.add(item);
        }
        menu.add(new JSeparator());
        JMenuItem newItem = new JMenuItem(jEdit.getProperty("workspace-run.popup.new"));
        newItem.addActionListener(e -> {
            if (WorkspaceRunConfigurationsDialog.showNew(view, projectRoot)
                != WorkspaceRunConfigurationsDialog.Result.NONE) {
                onChanged.run();
            }
        });
        menu.add(newItem);
        JMenuItem settingsItem = new JMenuItem(jEdit.getProperty("workspace-run.popup.settings"));
        settingsItem.addActionListener(e -> {
            if (WorkspaceRunConfigurationsDialog.showManager(view, projectRoot)
                != WorkspaceRunConfigurationsDialog.Result.NONE) {
                onChanged.run();
            }
        });
        menu.add(settingsItem);
        menu.show(invoker, 0, invoker.getHeight());
    }
}
