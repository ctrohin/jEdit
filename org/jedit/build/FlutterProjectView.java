/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;

public final class FlutterProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "flutter-project";

    private static final String[] DEFAULT_GOALS = {
        "pub get", "pub upgrade", "pub outdated", "pub deps",
        "analyze", "test", "build", "clean"
    };

    private final View view;
    private final JLabel caption;
    private final DefaultListModel<String> goalModel = new DefaultListModel<>();
    private final JList<String> goalList;
    private final JTextField customGoalField;
    private final JButton runButton;
    private final JButton settingsButton;
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::refreshProject);
    private File projectDirectory;
    private File pubspecFile;
    private File projectRoot;
    private FlutterProjectSettings projectSettings = new FlutterProjectSettings();

    public FlutterProjectView(View view) {
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
        JButton refresh = new JButton(jEdit.getProperty("flutter-project.refresh"));
        refresh.addActionListener(e -> refreshProject());
        settingsButton = new JButton(jEdit.getProperty("flutter-project.settings"));
        settingsButton.addActionListener(e -> openSettings());
        runButton = new JButton(jEdit.getProperty("flutter-project.run"));
        runButton.addActionListener(e -> runSelectedGoal());
        buttons.add(refresh);
        buttons.add(settingsButton);
        buttons.add(runButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        refreshProject();
    }

    private void refreshProject() {
        projectDirectory = null;
        pubspecFile = null;
        projectRoot = null;
        goalModel.clear();
        setProjectControlsEnabled(false);
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File pubspec = ProjectRoots.findPubspecYaml(root);
        if (pubspec == null) {
            caption.setText(jEdit.getProperty("flutter-project.no-pubspec"));
            return;
        }
        projectRoot = root;
        pubspecFile = pubspec;
        projectDirectory = pubspec.getParentFile();
        projectSettings = FlutterProjectPreferences.load(root);
        caption.setText(jEdit.getProperty("flutter-project.caption",
            new Object[] {pubspec.getAbsolutePath()}));
        for (String goal : DEFAULT_GOALS) {
            goalModel.addElement(goal);
        }
        if (!goalModel.isEmpty()) {
            goalList.setSelectedIndex(0);
        }
        setProjectControlsEnabled(true);
    }

    private void setProjectControlsEnabled(boolean enabled) {
        goalList.setEnabled(enabled);
        customGoalField.setEnabled(enabled);
        runButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
    }

    private void openSettings() {
        if (projectRoot == null) {
            refreshProject();
            if (projectRoot == null) {
                return;
            }
        }
        if (FlutterProjectSettingsDialog.show(view, projectRoot)) {
            projectSettings = FlutterProjectPreferences.load(projectRoot);
        }
    }

    private void runSelectedGoal() {
        if (projectDirectory == null) {
            refreshProject();
            if (projectDirectory == null) {
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
        FlutterCommandBuilder.Invocation invocation =
            FlutterCommandBuilder.build(projectDirectory, projectSettings, goal);
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(goal, invocation.workingDir, invocation.command, invocation.environment);
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
