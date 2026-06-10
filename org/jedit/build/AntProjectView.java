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
import java.util.Arrays;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.OperatingSystem;
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
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::refreshTargets);
    private AntBuildFile buildFile;

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
        JButton run = new JButton(jEdit.getProperty("ant-project.run"));
        run.addActionListener(e -> runSelectedTarget());
        buttons.add(refresh);
        buttons.add(run);
        add(buttons, BorderLayout.SOUTH);

        refreshTargets();
    }

    private void refreshTargets() {
        targetModel.clear();
        buildFile = null;
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File buildXml = ProjectRoots.findBuildXml(root);
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
        File root = buildFile.file.getParentFile();
        List<String> command = antCommand(target);
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(root, command);
    }

    private static List<String> antCommand(String target) {
        if (OperatingSystem.isWindows()) {
            return Arrays.asList("cmd.exe", "/c", "ant", target);
        }
        return Arrays.asList("ant", target);
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
