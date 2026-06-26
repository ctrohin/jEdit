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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
import org.gjt.sp.util.GenericGUIUtilities;

/**
 * Per-project Maven settings dialog (Maven home, settings.xml, profiles, etc.).
 */
final class MavenProjectSettingsDialog extends EnhancedDialog {

    private final File projectRoot;
    private final MavenProjectSettings workingCopy;
    private final JTextField mavenHomeField = new JTextField(32);
    private final JTextField settingsFileField = new JTextField(32);
    private final JTextField localRepositoryField = new JTextField(32);
    private final JTextField jdkHomeField = new JTextField(32);
    private final JTextField mavenExecutableField = new JTextField(32);
    private final JTextField mavenOptsField = new JTextField(32);
    private final JTextField additionalArgsField = new JTextField(32);
    private final JTextField manualProfilesField = new JTextField(32);
    private final JCheckBox useWrapperCheckBox;
    private final JCheckBox offlineCheckBox;
    private final JCheckBox skipTestsCheckBox;
    private final List<JCheckBox> profileCheckBoxes = new ArrayList<>();
    private boolean saved;

    static boolean show(View view, File projectRoot, File pom) {
        MavenProjectSettings settings = MavenProjectPreferences.load(projectRoot);
        MavenProjectSettingsDialog dialog =
            new MavenProjectSettingsDialog(view, projectRoot, pom, settings);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private MavenProjectSettingsDialog(View view, File projectRoot, File pom,
                                       MavenProjectSettings settings) {
        super(view, jEdit.getProperty("maven-project.settings.title"), true);
        this.projectRoot = projectRoot;
        this.workingCopy = settings.copy();
        GUIUtilities.loadGeometry(this, "maven-project-settings");

        boolean wrapperPresent = MavenCommandBuilder.hasWrapper(
            pom != null ? pom.getParentFile() : null);
        useWrapperCheckBox = new JCheckBox(
            jEdit.getProperty("maven-project.settings.use-wrapper"), workingCopy.useWrapper);
        useWrapperCheckBox.setEnabled(wrapperPresent);
        if (!wrapperPresent) {
            useWrapperCheckBox.setToolTipText(
                jEdit.getProperty("maven-project.settings.use-wrapper.missing"));
        }
        offlineCheckBox = new JCheckBox(
            jEdit.getProperty("maven-project.settings.offline"), workingCopy.offline);
        skipTestsCheckBox = new JCheckBox(
            jEdit.getProperty("maven-project.settings.skip-tests"), workingCopy.skipTests);

        populateFields();
        buildUi(view, projectRoot, pom);
        pack();
        setLocationRelativeTo(view);
    }

    private void populateFields() {
        mavenHomeField.setText(workingCopy.mavenHome);
        settingsFileField.setText(workingCopy.settingsFile);
        localRepositoryField.setText(workingCopy.localRepository);
        jdkHomeField.setText(workingCopy.jdkHome);
        mavenExecutableField.setText(workingCopy.mavenExecutable);
        mavenOptsField.setText(workingCopy.mavenOpts);
        additionalArgsField.setText(workingCopy.additionalArgs);
        manualProfilesField.setText(workingCopy.activeProfiles);
    }

    private void buildUi(View view, File projectRoot, File pom) {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel intro = new JLabel("<html>"
            + jEdit.getProperty("maven-project.settings.intro",
                new Object[] { projectRoot.getAbsolutePath() })
            + "</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(buildMavenSection());
        form.add(Box.createVerticalStrut(8));
        form.add(buildRunnerSection());
        form.add(Box.createVerticalStrut(8));
        form.add(buildProfilesSection(pom));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);
    }

    private JPanel buildMavenSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("maven-project.settings.maven-section")));
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 4, 2, 4);
        addPathRow(panel, c, jEdit.getProperty("maven-project.settings.maven-home"),
            mavenHomeField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("maven-project.settings.settings-file"),
            settingsFileField, VFSBrowser.OPEN_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("maven-project.settings.local-repository"),
            localRepositoryField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        return panel;
    }

    private JPanel buildRunnerSection() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("maven-project.settings.runner-section")));
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 4, 2, 4);
        addPathRow(panel, c, jEdit.getProperty("maven-project.settings.jdk-home"),
            jdkHomeField, VFSBrowser.CHOOSE_DIRECTORY_DIALOG);
        addPathRow(panel, c, jEdit.getProperty("maven-project.settings.maven-executable"),
            mavenExecutableField, VFSBrowser.OPEN_DIALOG);
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(useWrapperCheckBox, c);
        panel.add(offlineCheckBox, c);
        panel.add(skipTestsCheckBox, c);
        addLabelRow(panel, c, jEdit.getProperty("maven-project.settings.maven-opts"),
            mavenOptsField);
        addLabelRow(panel, c, jEdit.getProperty("maven-project.settings.additional-args"),
            additionalArgsField);
        return panel;
    }

    private JPanel buildProfilesSection(File pom) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("maven-project.settings.profiles-section")));

        Set<String> selected = parseProfiles(workingCopy.activeProfiles);
        Set<String> knownProfiles = new LinkedHashSet<>();
        if (pom != null) {
            MavenPomFile pomFile = MavenPomFile.parse(pom);
            if (pomFile != null) {
                knownProfiles.addAll(pomFile.profileIds());
            }
        }
        knownProfiles.addAll(selected);

        if (knownProfiles.isEmpty()) {
            panel.add(new JLabel(jEdit.getProperty("maven-project.settings.no-profiles")));
        } else {
            JPanel checks = new JPanel();
            checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
            for (String profileId : knownProfiles) {
                JCheckBox check = new JCheckBox(profileId, selected.contains(profileId));
                profileCheckBoxes.add(check);
                checks.add(check);
            }
            panel.add(checks);
        }

        JPanel manual = new JPanel(new BorderLayout(4, 4));
        manual.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        manual.add(new JLabel(jEdit.getProperty("maven-project.settings.manual-profiles")),
            BorderLayout.NORTH);
        manual.add(manualProfilesField, BorderLayout.CENTER);
        panel.add(manual);
        return panel;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));

        JButton reset = new JButton(jEdit.getProperty("maven-project.settings.reset"));
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

    private void addLabelRow(JPanel panel, GridBagConstraints c, String label, JTextField field) {
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
            jEdit.getProperty("maven-project.settings.reset-confirm"),
            jEdit.getProperty("maven-project.settings.title"),
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        MavenProjectPreferences.reset(projectRoot);
        MavenProjectSettings defaults = new MavenProjectSettings();
        workingCopy.mavenHome = defaults.mavenHome;
        workingCopy.settingsFile = defaults.settingsFile;
        workingCopy.localRepository = defaults.localRepository;
        workingCopy.jdkHome = defaults.jdkHome;
        workingCopy.mavenExecutable = defaults.mavenExecutable;
        workingCopy.useWrapper = defaults.useWrapper;
        workingCopy.activeProfiles = defaults.activeProfiles;
        workingCopy.offline = defaults.offline;
        workingCopy.skipTests = defaults.skipTests;
        workingCopy.mavenOpts = defaults.mavenOpts;
        workingCopy.additionalArgs = defaults.additionalArgs;
        populateFields();
        useWrapperCheckBox.setSelected(defaults.useWrapper);
        offlineCheckBox.setSelected(defaults.offline);
        skipTestsCheckBox.setSelected(defaults.skipTests);
        for (JCheckBox check : profileCheckBoxes) {
            check.setSelected(false);
        }
        manualProfilesField.setText("");
    }

    @Override
    public void ok() {
        MavenProjectSettings updated = readSettings();
        try {
            MavenProjectPreferences.save(projectRoot, updated);
            saved = true;
            GUIUtilities.saveGeometry(this, "maven-project-settings");
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("maven-project.settings.title"),
                JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "maven-project-settings");
        dispose();
    }

    private MavenProjectSettings readSettings() {
        MavenProjectSettings updated = workingCopy.copy();
        updated.mavenHome = mavenHomeField.getText().trim();
        updated.settingsFile = settingsFileField.getText().trim();
        updated.localRepository = localRepositoryField.getText().trim();
        updated.jdkHome = jdkHomeField.getText().trim();
        updated.mavenExecutable = mavenExecutableField.getText().trim();
        updated.useWrapper = useWrapperCheckBox.isSelected();
        updated.offline = offlineCheckBox.isSelected();
        updated.skipTests = skipTestsCheckBox.isSelected();
        updated.mavenOpts = mavenOptsField.getText().trim();
        updated.additionalArgs = additionalArgsField.getText().trim();
        updated.activeProfiles = mergeProfiles();
        return updated;
    }

    private String mergeProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        for (JCheckBox check : profileCheckBoxes) {
            if (check.isSelected()) {
                profiles.add(check.getText());
            }
        }
        profiles.addAll(parseProfiles(manualProfilesField.getText().trim()));
        return profiles.stream().collect(Collectors.joining(","));
    }

    private static Set<String> parseProfiles(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split("[,\\s]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
