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
import javax.swing.JComboBox;
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

final class NpmProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final NpmProjectSettings workingCopy;
    private final JTextField nodeHomeField = new JTextField(32);
    private final JTextField npmExecutableField = new JTextField(32);
    private final JTextField yarnExecutableField = new JTextField(32);
    private final JTextField workingDirectoryField = new JTextField(32);
    private final JTextField nodeOptionsField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JComboBox<String> packageManagerCombo;
    private boolean saved;

    static boolean show(View view, File projectRoot) {
        NpmProjectSettingsDialog dialog = new NpmProjectSettingsDialog(
            view, projectRoot, NpmProjectPreferences.load(projectRoot));
        dialog.setVisible(true);
        return dialog.saved;
    }

    private NpmProjectSettingsDialog(View view, File projectRoot, NpmProjectSettings settings) {
        super(view, jEdit.getProperty("npm-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "npm-project-settings");
        packageManagerCombo = new JComboBox<>(new String[] {
            NpmProjectSettings.MANAGER_NPM, NpmProjectSettings.MANAGER_YARN
        });
        packageManagerCombo.setSelectedItem(workingCopy.packageManager);
        populateFields();
        buildUi();
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        nodeHomeField.setText(workingCopy.nodeHome);
        npmExecutableField.setText(workingCopy.npmExecutable);
        yarnExecutableField.setText(workingCopy.yarnExecutable);
        workingDirectoryField.setText(workingCopy.workingDirectory);
        nodeOptionsField.setText(workingCopy.nodeOptions);
        additionalArgsField.setText(workingCopy.additionalArgs);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("npm-project.settings.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("npm-project.settings.node-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("npm-project.settings.node-home"),
            nodeHomeField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("npm-project.settings.package-manager"), packageManagerCombo);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("npm-project.settings.npm-executable"),
            npmExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("npm-project.settings.yarn-executable"),
            yarnExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("npm-project.settings.working-directory"),
            workingDirectoryField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("npm-project.settings.node-options"), nodeOptionsField);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("npm-project.settings.additional-args"), additionalArgsField);
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
            jEdit.getProperty("npm-project.settings.reset-confirm"),
            jEdit.getProperty("npm-project.settings.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        NpmProjectPreferences.reset(projectRoot);
        NpmProjectSettings defaults = new NpmProjectSettings();
        workingCopy.nodeHome = defaults.nodeHome;
        workingCopy.npmExecutable = defaults.npmExecutable;
        workingCopy.yarnExecutable = defaults.yarnExecutable;
        workingCopy.packageManager = defaults.packageManager;
        workingCopy.workingDirectory = defaults.workingDirectory;
        workingCopy.nodeOptions = defaults.nodeOptions;
        workingCopy.additionalArgs = defaults.additionalArgs;
        populateFields();
        packageManagerCombo.setSelectedItem(defaults.packageManager);
    }

    @Override
    public void ok() {
        try {
            NpmProjectPreferences.save(projectRoot, readSettings());
            saved = true;
            GUIUtilities.saveGeometry(this, "npm-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("npm-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "npm-project-settings");
        dispose();
    }

    private NpmProjectSettings readSettings() {
        NpmProjectSettings updated = workingCopy.copy();
        updated.nodeHome = nodeHomeField.getText().trim();
        updated.npmExecutable = npmExecutableField.getText().trim();
        updated.yarnExecutable = yarnExecutableField.getText().trim();
        updated.packageManager = (String) packageManagerCombo.getSelectedItem();
        updated.workingDirectory = workingDirectoryField.getText().trim();
        updated.nodeOptions = nodeOptionsField.getText().trim();
        updated.additionalArgs = additionalArgsField.getText().trim();
        return updated;
    }
}
