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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JList;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;

final class WorkspaceRunSettingsDialog extends EnhancedDialog {

    private final View view;
    private final File projectRoot;
    private final List<ProjectKind> supportedKinds;
    private final WorkspaceRunSettings workingCopy;
    private final JComboBox<ProjectKind> kindCombo;
    private final JComboBox<String> goalCombo;
    private final JTextField goalField;
    private boolean saved;

    static boolean show(View view, File projectRoot) {
        List<ProjectKind> supported = WorkspaceProjectRunner.detectSupportedKinds(projectRoot);
        if (supported.isEmpty()) {
            return false;
        }
        WorkspaceRunSettingsDialog dialog =
            new WorkspaceRunSettingsDialog(view, projectRoot, supported);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private WorkspaceRunSettingsDialog(View view, File projectRoot,
                                       List<ProjectKind> supportedKinds) {
        super(view, jEdit.getProperty("workspace-run.settings.title"), true);
        this.view = view;
        this.projectRoot = projectRoot;
        this.supportedKinds = supportedKinds;
        this.workingCopy = WorkspaceRunPreferences.load(projectRoot).copy();
        if (!supportedKinds.contains(workingCopy.kind)) {
            workingCopy.kind = supportedKinds.get(0);
        }

        kindCombo = new JComboBox<>(supportedKinds.toArray(ProjectKind[]::new));
        kindCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ProjectKind kind) {
                    setText(WorkspaceProjectRunner.kindLabel(kind));
                }
                return this;
            }
        });
        kindCombo.setSelectedItem(workingCopy.kind);
        kindCombo.addActionListener(e -> refreshGoalSuggestions());

        goalCombo = new JComboBox<>();
        goalField = new JTextField(32);
        goalCombo.addActionListener(e -> {
            Object item = goalCombo.getSelectedItem();
            if (item != null) {
                goalField.setText(item.toString());
            }
        });
        populateGoalSuggestions();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        GUIUtilities.loadGeometry(this, "workspace-run-settings");
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("workspace-run.settings.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("workspace-run.settings.run-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.kind"), kindCombo);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.goal"), goalCombo);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.goal-custom"), goalField);
        form.add(section);

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton toolSettings = new JButton(
            jEdit.getProperty("workspace-run.settings.tool-settings"));
        toolSettings.addActionListener(e -> openToolSettings());
        toolRow.add(toolSettings);
        form.add(Box.createVerticalStrut(8));
        form.add(toolRow);

        content.add(form, BorderLayout.CENTER);
        content.add(ProjectSettingsForm.buildButtons(this::resetToDefaults, this::ok, this::cancel),
            BorderLayout.SOUTH);
        setContentPane(content);
        pack();
    }

    private void refreshGoalSuggestions() {
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        if (kind == null) {
            return;
        }
        String current = goalField.getText().trim();
        if (current.isEmpty()) {
            current = workingCopy.kind == kind ? workingCopy.runGoal : "";
        }
        populateGoalSuggestions(kind, current);
    }

    private void populateGoalSuggestions() {
        populateGoalSuggestions(workingCopy.kind, workingCopy.runGoal);
    }

    private void populateGoalSuggestions(ProjectKind kind, String selected) {
        goalCombo.removeAllItems();
        for (String goal : WorkspaceProjectRunner.suggestRunGoals(projectRoot, kind)) {
            goalCombo.addItem(goal);
        }
        if (selected != null && !selected.isBlank()) {
            goalField.setText(selected.trim());
            goalCombo.setSelectedItem(selected.trim());
            if (goalCombo.getSelectedItem() == null) {
                goalCombo.addItem(selected.trim());
                goalCombo.setSelectedItem(selected.trim());
            }
        } else if (goalCombo.getItemCount() > 0) {
            goalCombo.setSelectedIndex(0);
            goalField.setText(goalCombo.getItemAt(0));
        }
    }

    private void openToolSettings() {
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        if (kind == null) {
            return;
        }
        switch (kind) {
            case MAVEN -> {
                File pom = ProjectRoots.findPomXml(projectRoot);
                if (pom != null) {
                    MavenProjectSettingsDialog.show(view, projectRoot, pom);
                }
            }
            case GRADLE -> {
                File build = ProjectRoots.findGradleBuild(projectRoot);
                if (build != null) {
                    GradleProjectSettingsDialog.show(view, projectRoot, build);
                }
            }
            case NPM -> NpmProjectSettingsDialog.show(view, projectRoot);
            case FLUTTER -> FlutterProjectSettingsDialog.show(view, projectRoot);
            case ANT -> AntProjectSettingsDialog.show(view, projectRoot);
            case PIP -> PipProjectSettingsDialog.show(view, projectRoot);
            default -> { }
        }
    }

    private void resetToDefaults() {
        if (JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("workspace-run.settings.reset-confirm"),
            jEdit.getProperty("workspace-run.settings.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        WorkspaceRunPreferences.reset(projectRoot);
        workingCopy.kind = supportedKinds.get(0);
        workingCopy.runGoal = WorkspaceProjectRunner.resolveRunGoal(projectRoot, workingCopy.kind);
        kindCombo.setSelectedItem(workingCopy.kind);
        populateGoalSuggestions();
    }

    @Override
    public void ok() {
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        if (kind == null) {
            return;
        }
        String goal = goalField.getText().trim();
        if (goal.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                jEdit.getProperty("workspace-run.settings.goal-required"),
                jEdit.getProperty("workspace-run.settings.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        workingCopy.kind = kind;
        workingCopy.runGoal = goal;
        try {
            WorkspaceRunPreferences.save(projectRoot, workingCopy);
            saved = true;
            GUIUtilities.saveGeometry(this, "workspace-run-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("workspace-run.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "workspace-run-settings");
        dispose();
    }
}
