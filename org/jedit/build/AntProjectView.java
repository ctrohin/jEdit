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
import java.awt.FlowLayout;
import java.io.File;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;

/**
 * Lists Ant targets from {@code build.xml} and runs them in the Build view.
 */
public final class AntProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "ant-project";

    private final View view;
    private final JLabel caption;
    private final DefaultListModel<String> targetModel = new DefaultListModel<>();
    private final JList<String> targetList;
    private final JButton runButton;
    private final JButton settingsButton;
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::refreshTargets);
    private File projectRoot;
    private AntBuildFile buildFile;
    private AntProjectSettings projectSettings = new AntProjectSettings();

    public AntProjectView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel();
        add(caption, BorderLayout.NORTH);

        targetList = new JList<>(targetModel);
        targetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        targetList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelectedTarget();
                }
            }
        });
        add(new JScrollPane(targetList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton refresh = new JButton(jEdit.getProperty("ant-project.refresh"));
        refresh.addActionListener(e -> refreshTargets());
        settingsButton = new JButton(jEdit.getProperty("ant-project.settings"));
        settingsButton.addActionListener(e -> openSettings());
        runButton = new JButton(jEdit.getProperty("ant-project.run"));
        runButton.addActionListener(e -> runSelectedTarget());
        buttons.add(refresh);
        buttons.add(settingsButton);
        buttons.add(runButton);
        add(buttons, BorderLayout.SOUTH);

        refreshTargets();
    }

    private void refreshTargets() {
        targetModel.clear();
        buildFile = null;
        projectRoot = null;
        setProjectControlsEnabled(false);

        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        projectRoot = root;
        projectSettings = AntProjectPreferences.load(root);
        File buildXml = AntCommandBuilder.resolveConfiguredBuildFile(root, projectSettings);
        if (buildXml == null) {
            caption.setText(jEdit.getProperty("ant-project.no-build-xml"));
            return;
        }
        buildFile = AntBuildFile.parse(buildXml);
        if (buildFile == null || buildFile.targets.isEmpty()) {
            caption.setText(jEdit.getProperty("ant-project.parse-error",
                new Object[] { buildXml.getName() }));
            return;
        }
        caption.setText(jEdit.getProperty("ant-project.caption",
            new Object[] { buildXml.getAbsolutePath() }));
        for (String target : buildFile.targets) {
            targetModel.addElement(target);
        }
        if (buildFile.defaultTarget != null) {
            targetList.setSelectedValue(buildFile.defaultTarget, true);
        } else if (!buildFile.targets.isEmpty()) {
            targetList.setSelectedIndex(0);
        }
        setProjectControlsEnabled(true);
    }

    private void setProjectControlsEnabled(boolean enabled) {
        targetList.setEnabled(enabled);
        runButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
    }

    private void openSettings() {
        if (projectRoot == null) {
            refreshTargets();
            if (projectRoot == null) {
                return;
            }
        }
        if (AntProjectSettingsDialog.show(view, projectRoot)) {
            projectSettings = AntProjectPreferences.load(projectRoot);
            refreshTargets();
        }
    }

    private void runSelectedTarget() {
        if (buildFile == null) {
            refreshTargets();
            if (buildFile == null) {
                return;
            }
        }
        String target = targetList.getSelectedValue();
        if (target == null || target.isBlank()) {
            return;
        }
        AntCommandBuilder.Invocation invocation = AntCommandBuilder.build(
            buildFile.file, projectSettings, target);
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(target, invocation.workingDir, invocation.command, invocation.environment);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        refreshTargets();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        targetList.requestFocus();
    }
}
