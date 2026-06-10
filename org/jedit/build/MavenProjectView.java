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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;

/**
 * Runs common Maven goals in the Build view.
 */
public final class MavenProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "maven-project";

    private static final String[] DEFAULT_GOALS = {
        "clean", "validate", "compile", "test", "package", "verify", "install", "deploy"
    };

    private final View view;
    private final JLabel caption;
    private final DefaultListModel<String> goalModel = new DefaultListModel<>();
    private final JList<String> goalList;
    private final JTextField customGoalField;
    private final JButton runButton;
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::refreshProject);
    private File pomDirectory;

    public MavenProjectView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel();
        add(caption, BorderLayout.NORTH);

        goalList = new JList<>(goalModel);
        goalList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        goalList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelectedGoal();
                }
            }
        });
        add(new JScrollPane(goalList), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        customGoalField = new JTextField();
        south.add(customGoalField, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton refresh = new JButton(jEdit.getProperty("maven-project.refresh"));
        refresh.addActionListener(e -> refreshProject());
        runButton = new JButton(jEdit.getProperty("maven-project.run"));
        runButton.addActionListener(e -> runSelectedGoal());
        buttons.add(refresh);
        buttons.add(runButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        refreshProject();
    }

    private void refreshProject() {
        pomDirectory = null;
        goalModel.clear();
        setProjectControlsEnabled(false);

        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File pom = ProjectRoots.findPomXml(root);
        if (pom == null) {
            caption.setText(jEdit.getProperty("maven-project.no-pom"));
            return;
        }
        pomDirectory = pom.getParentFile();
        caption.setText(jEdit.getProperty("maven-project.caption",
            new Object[] { pom.getAbsolutePath() }));
        for (String goal : DEFAULT_GOALS) {
            goalModel.addElement(goal);
        }
        goalList.setSelectedIndex(2);
        setProjectControlsEnabled(true);
    }

    private void setProjectControlsEnabled(boolean enabled) {
        goalList.setEnabled(enabled);
        customGoalField.setEnabled(enabled);
        runButton.setEnabled(enabled);
    }

    private void runSelectedGoal() {
        if (pomDirectory == null) {
            refreshProject();
            if (pomDirectory == null) {
                return;
            }
        }
        String goal = goalList.getSelectedValue();
        String custom = customGoalField.getText().trim();
        if (!custom.isEmpty()) {
            goal = custom;
        }
        if (goal == null || goal.isBlank()) {
            return;
        }
        List<String> args = new ArrayList<>();
        args.addAll(mavenLauncher());
        args.addAll(Arrays.asList(goal.split("\\s+")));
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(pomDirectory, args);
    }

    private static List<String> mavenLauncher() {
        if (OperatingSystem.isWindows()) {
            return Arrays.asList("cmd.exe", "/c", "mvn");
        }
        return List.of("mvn");
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        refreshProject();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        goalList.requestFocus();
    }
}
