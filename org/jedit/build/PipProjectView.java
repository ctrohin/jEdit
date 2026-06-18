/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

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

public final class PipProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "pip-project";

    private static final String[] DEFAULT_GOALS = {
        "install", "freeze", "list", "check", "download"
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
    private File markerFile;
    private File projectRoot;
    private PipProjectSettings projectSettings = new PipProjectSettings();

    public PipProjectView(View view) {
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
        JButton refresh = new JButton(jEdit.getProperty("pip-project.refresh"));
        refresh.addActionListener(e -> refreshProject());
        settingsButton = new JButton(jEdit.getProperty("pip-project.settings"));
        settingsButton.addActionListener(e -> openSettings());
        runButton = new JButton(jEdit.getProperty("pip-project.run"));
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
        markerFile = null;
        projectRoot = null;
        goalModel.clear();
        setProjectControlsEnabled(false);
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File marker = ProjectRoots.findPythonMarker(root);
        if (marker == null) {
            caption.setText(jEdit.getProperty("pip-project.no-marker"));
            return;
        }
        projectRoot = root;
        markerFile = marker;
        projectDirectory = marker.getParentFile();
        projectSettings = PipProjectPreferences.load(root);
        caption.setText(jEdit.getProperty("pip-project.caption",
            new Object[] {marker.getAbsolutePath()}));
        populateGoals(marker);
        if (!goalModel.isEmpty()) {
            goalList.setSelectedIndex(0);
        }
        setProjectControlsEnabled(true);
    }

    private void populateGoals(File marker) {
        Set<String> goals = new LinkedHashSet<>(Arrays.asList(DEFAULT_GOALS));
        File dir = marker.getParentFile();
        if (new File(dir, "requirements.txt").isFile()) {
            goals.add("install -r requirements.txt");
        }
        if (new File(dir, "pyproject.toml").isFile() || new File(dir, "setup.py").isFile()) {
            goals.add("install -e .");
        }
        for (String goal : goals) {
            goalModel.addElement(goal);
        }
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
        if (PipProjectSettingsDialog.show(view, projectRoot)) {
            projectSettings = PipProjectPreferences.load(projectRoot);
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
        PipCommandBuilder.Invocation invocation =
            PipCommandBuilder.build(projectDirectory, projectSettings, goal);
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
