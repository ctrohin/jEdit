/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;

final class GradleProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final GradleProjectSettings workingCopy;
    private final JTextField gradleHomeField = new JTextField(32);
    private final JTextField jdkHomeField = new JTextField(32);
    private final JTextField gradleExecutableField = new JTextField(32);
    private final JTextField gradleUserHomeField = new JTextField(32);
    private final JTextField workingDirectoryField = new JTextField(32);
    private final JTextField gradleOptsField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JCheckBox useWrapperCheckBox;
    private boolean saved;

    static boolean show(View view, File projectRoot, File buildFile) {
        GradleProjectSettingsDialog dialog = new GradleProjectSettingsDialog(
            view, projectRoot, buildFile, GradleProjectPreferences.load(projectRoot));
        dialog.setVisible(true);
        return dialog.saved;
    }

    private GradleProjectSettingsDialog(View view, File projectRoot, File buildFile,
                                        GradleProjectSettings settings) {
        super(view, jEdit.getProperty("gradle-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "gradle-project-settings");
        boolean wrapperPresent = GradleCommandBuilder.hasWrapper(
            buildFile != null ? buildFile.getParentFile() : null);
        useWrapperCheckBox = new JCheckBox(
            jEdit.getProperty("gradle-project.settings.use-wrapper"), workingCopy.useWrapper);
        useWrapperCheckBox.setEnabled(wrapperPresent);
        populateFields();
        buildUi();
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        gradleHomeField.setText(workingCopy.gradleHome);
        jdkHomeField.setText(workingCopy.jdkHome);
        gradleExecutableField.setText(workingCopy.gradleExecutable);
        gradleUserHomeField.setText(workingCopy.gradleUserHome);
        workingDirectoryField.setText(workingCopy.workingDirectory);
        gradleOptsField.setText(workingCopy.gradleOpts);
        additionalArgsField.setText(workingCopy.additionalArgs);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("gradle-project.settings.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("gradle-project.settings.gradle-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("gradle-project.settings.gradle-home"),
            gradleHomeField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("gradle-project.settings.jdk-home"),
            jdkHomeField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("gradle-project.settings.gradle-executable"),
            gradleExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("gradle-project.settings.gradle-user-home"),
            gradleUserHomeField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("gradle-project.settings.working-directory"),
            workingDirectoryField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("gradle-project.settings.gradle-opts"), gradleOptsField);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("gradle-project.settings.additional-args"), additionalArgsField);
        ProjectSettingsForm.addLabelRow(section, c, "", useWrapperCheckBox);
        form.add(section);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        content.add(scroll, BorderLayout.CENTER);
        content.add(ProjectSettingsForm.buildButtons(this::resetToDefaults, this::ok, this::cancel),
            BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void resetToDefaults() {
        if (JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("gradle-project.settings.reset-confirm"),
            jEdit.getProperty("gradle-project.settings.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        GradleProjectPreferences.reset(projectRoot);
        GradleProjectSettings defaults = new GradleProjectSettings();
        workingCopy.gradleHome = defaults.gradleHome;
        workingCopy.jdkHome = defaults.jdkHome;
        workingCopy.gradleExecutable = defaults.gradleExecutable;
        workingCopy.gradleUserHome = defaults.gradleUserHome;
        workingCopy.workingDirectory = defaults.workingDirectory;
        workingCopy.useWrapper = defaults.useWrapper;
        workingCopy.gradleOpts = defaults.gradleOpts;
        workingCopy.additionalArgs = defaults.additionalArgs;
        populateFields();
        useWrapperCheckBox.setSelected(defaults.useWrapper);
    }

    @Override
    public void ok() {
        GradleProjectSettings updated = readSettings();
        try {
            GradleProjectPreferences.save(projectRoot, updated);
            saved = true;
            GUIUtilities.saveGeometry(this, "gradle-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("gradle-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "gradle-project-settings");
        dispose();
    }

    private GradleProjectSettings readSettings() {
        GradleProjectSettings updated = workingCopy.copy();
        updated.gradleHome = gradleHomeField.getText().trim();
        updated.jdkHome = jdkHomeField.getText().trim();
        updated.gradleExecutable = gradleExecutableField.getText().trim();
        updated.gradleUserHome = gradleUserHomeField.getText().trim();
        updated.workingDirectory = workingDirectoryField.getText().trim();
        updated.useWrapper = useWrapperCheckBox.isSelected();
        updated.gradleOpts = gradleOptsField.getText().trim();
        updated.additionalArgs = additionalArgsField.getText().trim();
        return updated;
    }
}
