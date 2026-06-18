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

public final class GradleProjectView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "gradle-project";

    private static final String[] DEFAULT_TASKS = {
        "tasks", "clean", "classes", "test", "check", "build", "assemble", "dependencies"
    };

    private final View view;
    private final JLabel caption;
    private final DefaultListModel<String> taskModel = new DefaultListModel<>();
    private final JList<String> taskList;
    private final JTextField customTaskField;
    private final JButton runButton;
    private final JButton settingsButton;
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::refreshProject);
    private File projectDirectory;
    private File buildFile;
    private File projectRoot;
    private GradleProjectSettings projectSettings = new GradleProjectSettings();

    public GradleProjectView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;
        caption = new JLabel();
        add(caption, BorderLayout.NORTH);
        taskList = new JList<>(taskModel);
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelectedTask();
                }
            }
        });
        add(new JScrollPane(taskList), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        customTaskField = new JTextField();
        south.add(customTaskField, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton refresh = new JButton(jEdit.getProperty("gradle-project.refresh"));
        refresh.addActionListener(e -> refreshProject());
        settingsButton = new JButton(jEdit.getProperty("gradle-project.settings"));
        settingsButton.addActionListener(e -> openSettings());
        runButton = new JButton(jEdit.getProperty("gradle-project.run"));
        runButton.addActionListener(e -> runSelectedTask());
        buttons.add(refresh);
        buttons.add(settingsButton);
        buttons.add(runButton);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        refreshProject();
    }

    private void refreshProject() {
        projectDirectory = null;
        buildFile = null;
        projectRoot = null;
        taskModel.clear();
        setProjectControlsEnabled(false);
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("build.no-workspace"));
            return;
        }
        File gradleBuild = ProjectRoots.findGradleBuild(root);
        if (gradleBuild == null) {
            caption.setText(jEdit.getProperty("gradle-project.no-build"));
            return;
        }
        projectRoot = root;
        buildFile = gradleBuild;
        projectDirectory = gradleBuild.getParentFile();
        projectSettings = GradleProjectPreferences.load(root);
        caption.setText(jEdit.getProperty("gradle-project.caption",
            new Object[] {gradleBuild.getAbsolutePath()}));
        for (String task : DEFAULT_TASKS) {
            taskModel.addElement(task);
        }
        if (!taskModel.isEmpty()) {
            taskList.setSelectedIndex(Math.min(2, taskModel.getSize() - 1));
        }
        setProjectControlsEnabled(true);
    }

    private void setProjectControlsEnabled(boolean enabled) {
        taskList.setEnabled(enabled);
        customTaskField.setEnabled(enabled);
        runButton.setEnabled(enabled);
        settingsButton.setEnabled(enabled);
    }

    private void openSettings() {
        if (projectRoot == null || buildFile == null) {
            refreshProject();
            if (projectRoot == null) {
                return;
            }
        }
        if (GradleProjectSettingsDialog.show(view, projectRoot, buildFile)) {
            projectSettings = GradleProjectPreferences.load(projectRoot);
        }
    }

    private void runSelectedTask() {
        if (projectDirectory == null) {
            refreshProject();
            if (projectDirectory == null) {
                return;
            }
        }
        String task = taskList.getSelectedValue();
        String custom = customTaskField.getText().trim();
        if (!custom.isEmpty()) {
            task = custom;
        }
        if (task == null || task.isBlank()) {
            return;
        }
        GradleCommandBuilder.Invocation invocation =
            GradleCommandBuilder.build(projectDirectory, projectSettings, task);
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(task, invocation.workingDir, invocation.command, invocation.environment);
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
        taskList.requestFocus();
    }
}
