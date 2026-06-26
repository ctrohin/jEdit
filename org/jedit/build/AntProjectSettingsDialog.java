/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.GenericGUIUtilities;

/**
 * Per-project Ant settings dialog (Ant home, build file, properties, etc.).
 */
final class AntProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final AntProjectSettings workingCopy;
    private final JTextField antHomeField = new JTextField(32);
    private final JTextField jdkHomeField = new JTextField(32);
    private final JTextField buildFileField = new JTextField(32);
    private final JTextField workingDirectoryField = new JTextField(32);
    private final JTextField propertyFileField = new JTextField(32);
    private final JTextField antExecutableField = new JTextField(32);
    private final JTextField antOptsField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JTextArea propertiesArea = new JTextArea(5, 32);
    private final JComboBox<String> logLevelCombo;
    private boolean saved;

    static boolean show(View view, File projectRoot) {
        AntProjectSettings settings = AntProjectPreferences.load(projectRoot);
        AntProjectSettingsDialog dialog =
            new AntProjectSettingsDialog(view, projectRoot, settings);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private AntProjectSettingsDialog(View view, File projectRoot,
                                     AntProjectSettings settings) {
        super(view, jEdit.getProperty("ant-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "ant-project-settings");

        logLevelCombo = new JComboBox<>(new String[] {
            "default", "quiet", "verbose", "debug"
        });
        logLevelCombo.setSelectedItem(workingCopy.logLevel);

        populateFields();
        buildUi();
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        antHomeField.setText(workingCopy.antHome);
        jdkHomeField.setText(workingCopy.jdkHome);
        buildFileField.setText(workingCopy.buildFile);
        workingDirectoryField.setText(workingCopy.workingDirectory);
        propertyFileField.setText(workingCopy.propertyFile);
        antExecutableField.setText(workingCopy.antExecutable);
        antOptsField.setText(workingCopy.antOpts);
        additionalArgsField.setText(workingCopy.additionalArgs);
        propertiesArea.setText(workingCopy.properties);
        if (workingCopy.buildFile.isBlank()) {
            File defaultBuild = ProjectRoots.findBuildXml(projectRoot);
            if (defaultBuild != null) {
                buildFileField.setToolTipText(defaultBuild.getAbsolutePath());
            }
        }
    }

    private void buildUi() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel intro = new JLabel("<html>"
            + jEdit.getProperty("ant-project.settings.intro",
                new Object[] { projectRoot.getAbsolutePath() })
            + "</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(buildAntSection());
        form.add(Box.createVerticalStrut(8));
        form.add(buildProjectSection());
        form.add(Box.createVerticalStrut(8));
        form.add(buildPropertiesSection());

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);
    }

    private JPanel buildAntSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("ant-project.settings.ant-section")));
        GridBagConstraints c = gridConstraints();
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.ant-home"),
            antHomeField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.jdk-home"),
            jdkHomeField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.ant-executable"),
            antExecutableField, VFSBrowser.OPEN_DIALOG);
        addLabelRow(panel, c, jEdit.getProperty("ant-project.settings.ant-opts"),
            antOptsField);
        addLabelRow(panel, c, jEdit.getProperty("ant-project.settings.log-level"),
            logLevelCombo);
        addLabelRow(panel, c, jEdit.getProperty("ant-project.settings.additional-args"),
            additionalArgsField);
        return panel;
    }

    private JPanel buildProjectSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("ant-project.settings.project-section")));
        GridBagConstraints c = gridConstraints();
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.build-file"),
            buildFileField, VFSBrowser.OPEN_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.working-directory"),
            workingDirectoryField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("ant-project.settings.property-file"),
            propertyFileField, VFSBrowser.OPEN_DIALOG);
        return panel;
    }

    private JPanel buildPropertiesSection() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("ant-project.settings.properties-section")));
        propertiesArea.setLineWrap(false);
        panel.add(new JScrollPane(propertiesArea), BorderLayout.CENTER);
        panel.add(new JLabel(jEdit.getProperty("ant-project.settings.properties-hint")),
            BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

        JButton reset = new JButton(jEdit.getProperty("ant-project.settings.reset"));
        reset.addActionListener(e -> resetToDefaults());
        JButton ok = new JButton(jEdit.getProperty("common.ok"));
        ok.addActionListener(e -> ok());
        JButton cancel = new JButton(jEdit.getProperty("common.cancel"));
        cancel.addActionListener(e -> cancel());
        getRootPane().setDefaultButton(ok);
        GenericGUIUtilities.makeSameSize(ok, cancel, reset);

        buttons.add(reset);
        buttons.add(Box.createGlue());
        buttons.add(ok);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(cancel);
        return buttons;
    }

    private static GridBagConstraints gridConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 4, 2, 4);
        return c;
    }

    private void addPathRow(JPanel panel, GridBagConstraints c, String label,
                            JTextField field, int dialogType) {
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.add(field, BorderLayout.CENTER);
        JButton browse = new JButton("...");
        browse.addActionListener(e -> browse(field, dialogType));
        row.add(browse, BorderLayout.EAST);
        panel.add(row, c);
    }

    private void addLabelRow(JPanel panel, GridBagConstraints c, String label,
                             java.awt.Component field) {
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private void browse(JTextField field, int dialogType) {
        View view = jEdit.getActiveView();
        String start = field.getText();
        if (start == null || start.isBlank()) {
            start = projectRoot.getAbsolutePath();
        }
        String[] chosen = GUIUtilities.showVFSFileDialog(view, start, dialogType, false);
        if (chosen.length > 0) {
            field.setText(chosen[0]);
        }
    }

    private void resetToDefaults() {
        int answer = JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("ant-project.settings.reset-confirm"),
            jEdit.getProperty("ant-project.settings.title"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        AntProjectPreferences.reset(projectRoot);
        AntProjectSettings defaults = new AntProjectSettings();
        workingCopy.antHome = defaults.antHome;
        workingCopy.jdkHome = defaults.jdkHome;
        workingCopy.buildFile = defaults.buildFile;
        workingCopy.workingDirectory = defaults.workingDirectory;
        workingCopy.propertyFile = defaults.propertyFile;
        workingCopy.antExecutable = defaults.antExecutable;
        workingCopy.antOpts = defaults.antOpts;
        workingCopy.properties = defaults.properties;
        workingCopy.additionalArgs = defaults.additionalArgs;
        workingCopy.logLevel = defaults.logLevel;
        populateFields();
        logLevelCombo.setSelectedItem(defaults.logLevel);
    }

    @Override
    public void ok() {
        AntProjectSettings updated = readSettings();
        try {
            AntProjectPreferences.save(projectRoot, updated);
            saved = true;
            GUIUtilities.saveGeometry(this, "ant-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("ant-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "ant-project-settings");
        dispose();
    }

    private AntProjectSettings readSettings() {
        AntProjectSettings updated = workingCopy.copy();
        updated.antHome = antHomeField.getText().trim();
        updated.jdkHome = jdkHomeField.getText().trim();
        updated.buildFile = buildFileField.getText().trim();
        updated.workingDirectory = workingDirectoryField.getText().trim();
        updated.propertyFile = propertyFileField.getText().trim();
        updated.antExecutable = antExecutableField.getText().trim();
        updated.antOpts = antOptsField.getText().trim();
        updated.additionalArgs = additionalArgsField.getText().trim();
        updated.properties = propertiesArea.getText().trim();
        updated.logLevel = (String) logLevelCombo.getSelectedItem();
        return updated;
    }
}
