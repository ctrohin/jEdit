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

final class FlutterProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final FlutterProjectSettings workingCopy;
    private final JTextField flutterSdkField = new JTextField(32);
    private final JTextField dartSdkField = new JTextField(32);
    private final JTextField flutterExecutableField = new JTextField(32);
    private final JTextField dartExecutableField = new JTextField(32);
    private final JTextField workingDirectoryField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JCheckBox useFlutterCliCheckBox;
    private boolean saved;

    static boolean show(View view, File projectRoot) {
        FlutterProjectSettingsDialog dialog = new FlutterProjectSettingsDialog(
            view, projectRoot, FlutterProjectPreferences.load(projectRoot));
        dialog.setVisible(true);
        return dialog.saved;
    }

    private FlutterProjectSettingsDialog(View view, File projectRoot,
                                         FlutterProjectSettings settings) {
        super(view, jEdit.getProperty("flutter-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "flutter-project-settings");
        useFlutterCliCheckBox = new JCheckBox(
            jEdit.getProperty("flutter-project.settings.use-flutter"),
            workingCopy.useFlutterCli);
        populateFields();
        buildUi();
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        flutterSdkField.setText(workingCopy.flutterSdk);
        dartSdkField.setText(workingCopy.dartSdk);
        flutterExecutableField.setText(workingCopy.flutterExecutable);
        dartExecutableField.setText(workingCopy.dartExecutable);
        workingDirectoryField.setText(workingCopy.workingDirectory);
        additionalArgsField.setText(workingCopy.additionalArgs);
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("flutter-project.settings.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("flutter-project.settings.sdk-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("flutter-project.settings.flutter-sdk"),
            flutterSdkField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("flutter-project.settings.dart-sdk"),
            dartSdkField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("flutter-project.settings.flutter-executable"),
            flutterExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("flutter-project.settings.dart-executable"),
            dartExecutableField, projectRoot, VFSBrowser.OPEN_DIALOG);
        ProjectSettingsForm.addPathRow(section, c,
            jEdit.getProperty("flutter-project.settings.working-directory"),
            workingDirectoryField, projectRoot, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("flutter-project.settings.additional-args"), additionalArgsField);
        ProjectSettingsForm.addLabelRow(section, c, "", useFlutterCliCheckBox);
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
            jEdit.getProperty("flutter-project.settings.reset-confirm"),
            jEdit.getProperty("flutter-project.settings.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        FlutterProjectPreferences.reset(projectRoot);
        FlutterProjectSettings defaults = new FlutterProjectSettings();
        workingCopy.flutterSdk = defaults.flutterSdk;
        workingCopy.dartSdk = defaults.dartSdk;
        workingCopy.flutterExecutable = defaults.flutterExecutable;
        workingCopy.dartExecutable = defaults.dartExecutable;
        workingCopy.useFlutterCli = defaults.useFlutterCli;
        workingCopy.workingDirectory = defaults.workingDirectory;
        workingCopy.additionalArgs = defaults.additionalArgs;
        populateFields();
        useFlutterCliCheckBox.setSelected(defaults.useFlutterCli);
    }

    @Override
    public void ok() {
        try {
            FlutterProjectPreferences.save(projectRoot, readSettings());
            saved = true;
            GUIUtilities.saveGeometry(this, "flutter-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("flutter-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "flutter-project-settings");
        dispose();
    }

    private FlutterProjectSettings readSettings() {
        FlutterProjectSettings updated = workingCopy.copy();
        updated.flutterSdk = flutterSdkField.getText().trim();
        updated.dartSdk = dartSdkField.getText().trim();
        updated.flutterExecutable = flutterExecutableField.getText().trim();
        updated.dartExecutable = dartExecutableField.getText().trim();
        updated.useFlutterCli = useFlutterCliCheckBox.isSelected();
        updated.workingDirectory = workingDirectoryField.getText().trim();
        updated.additionalArgs = additionalArgsField.getText().trim();
        return updated;
    }
}
