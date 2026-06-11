/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.io.File;

import javax.swing.BorderFactory;
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

final class PipProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final PipProjectSettings workingCopy;
    private final JTextField pythonHomeField = new JTextField(32);
    private final JTextField pythonExecutableField = new JTextField(32);
    private final JTextField pipExecutableField = new JTextField(32);
    private final JTextField virtualEnvField = new JTextField(32);
    private final JTextField workingDirectoryField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JCheckBox usePipModuleCheckBox;
    private boolean saved;

    static boolean show(View view, File projectRoot) {
        PipProjectSettingsDialog dialog = new PipProjectSettingsDialog(
            view, projectRoot, PipProjectPreferences.load(projectRoot));
        dialog.setVisible(true);
        return dialog.saved;
    }

    private PipProjectSettingsDialog(View view, File projectRoot, PipProjectSettings settings) {
        super(view, jEdit.getProperty("pip-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "pip-project-settings");
        usePipModuleCheckBox = new JCheckBox(
            jEdit.getProperty("pip-project.settings.use-pip-module"),
            workingCopy.usePipModule);
        populateFields();
        buildUi();
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        pythonHomeField.setText(workingCopy.pythonHome);
        pythonExecutableField.setText(workingCopy.pythonExecutable);
        pipExecutableField.setText(workingCopy.pipExecutable);
        virtualEnvField.setText(workingCopy.virtualEnv);
        workingDirectoryField.setText(workingCopy.workingDirectory);
        additionalArgsField.setText(workingCopy.additionalArgs);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("pip-project.settings.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("pip-project.settings.python-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("pip-project.settings.python-home"),
            pythonHomeField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("pip-project.settings.python-executable"),
            pythonExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("pip-project.settings.pip-executable"),
            pipExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("pip-project.settings.virtual-env"),
            virtualEnvField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("pip-project.settings.working-directory"),
            workingDirectoryField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("pip-project.settings.additional-args"), additionalArgsField);
        ProjectSettingsForm.addLabelRow(section, c, "", usePipModuleCheckBox);
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
            jEdit.getProperty("pip-project.settings.reset-confirm"),
            jEdit.getProperty("pip-project.settings.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        PipProjectPreferences.reset(projectRoot);
        PipProjectSettings defaults = new PipProjectSettings();
        workingCopy.pythonHome = defaults.pythonHome;
        workingCopy.pythonExecutable = defaults.pythonExecutable;
        workingCopy.pipExecutable = defaults.pipExecutable;
        workingCopy.virtualEnv = defaults.virtualEnv;
        workingCopy.workingDirectory = defaults.workingDirectory;
        workingCopy.usePipModule = defaults.usePipModule;
        workingCopy.additionalArgs = defaults.additionalArgs;
        populateFields();
        usePipModuleCheckBox.setSelected(defaults.usePipModule);
    }

    @Override
    public void ok() {
        try {
            PipProjectPreferences.save(projectRoot, readSettings());
            saved = true;
            GUIUtilities.saveGeometry(this, "pip-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("pip-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "pip-project-settings");
        dispose();
    }

    private PipProjectSettings readSettings() {
        PipProjectSettings updated = workingCopy.copy();
        updated.pythonHome = pythonHomeField.getText().trim();
        updated.pythonExecutable = pythonExecutableField.getText().trim();
        updated.pipExecutable = pipExecutableField.getText().trim();
        updated.virtualEnv = virtualEnvField.getText().trim();
        updated.workingDirectory = workingDirectoryField.getText().trim();
        updated.usePipModule = usePipModuleCheckBox.isSelected();
        updated.additionalArgs = additionalArgsField.getText().trim();
        return updated;
    }
}
