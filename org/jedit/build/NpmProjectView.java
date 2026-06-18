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
import java.util.List;
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

public final class NpmProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "npm-project";

    private static final String[] DEFAULT_GOALS = {
        "install", "ci", "test", "run build", "run start", "outdated", "audit"
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
    private File packageDirectory;
    private File packageJson;
    private File projectRoot;
    private NpmProjectSettings projectSettings = new NpmProjectSettings();

    public NpmProjectView(View view) {
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
        JButton refresh = new JButton(jEdit.getProperty("npm-project.refresh"));
        refresh.addActionListener(e -> refreshProject());
        settingsButton = new JButton(jEdit.getProperty("npm-project.settings"));
        settingsButton.addActionListener(e -> openSettings());
        runButton = new JButton(jEdit.getProperty("npm-project.run"));
        runButton.addActionListener(e -> runSelectedGoal());
        buttons.add(refresh);
        buttons.add(settingsButton);
        buttons.add(runButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        refreshProject();
    }

    private void refreshProject() {
        packageDirectory = null;
        packageJson = null;
        projectRoot = null;
        goalModel.clear();
        setProjectControlsEnabled(false);
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File json = ProjectRoots.findPackageJson(root);
        if (json == null) {
            caption.setText(jEdit.getProperty("npm-project.no-package-json"));
            return;
        }
        projectRoot = root;
        packageJson = json;
        packageDirectory = json.getParentFile();
        projectSettings = NpmProjectPreferences.load(root);
        caption.setText(jEdit.getProperty("npm-project.caption",
            new Object[] {json.getAbsolutePath()}));
        populateGoals(json);
        if (!goalModel.isEmpty()) {
            goalList.setSelectedIndex(0);
        }
        setProjectControlsEnabled(true);
    }

    private void populateGoals(File json) {
        Set<String> goals = new LinkedHashSet<>(Arrays.asList(DEFAULT_GOALS));
        List<String> scripts = PackageJsonFile.scriptNames(json);
        for (String script : scripts) {
            goals.add("run " + script);
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
        if (NpmProjectSettingsDialog.show(view, projectRoot)) {
            projectSettings = NpmProjectPreferences.load(projectRoot);
        }
    }

    private void runSelectedGoal() {
        if (packageDirectory == null) {
            refreshProject();
            if (packageDirectory == null) {
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
        NpmCommandBuilder.Invocation invocation =
            NpmCommandBuilder.build(packageDirectory, projectSettings, goal);
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
