/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.GenericGUIUtilities;

final class WorkspaceRunConfigurationsDialog extends EnhancedDialog {

    enum Result {
        NONE,
        SAVED,
        SAVED_AND_RAN
    }

    private final View view;
    private final File projectRoot;
    private final List<ProjectKind> supportedKinds;
    private final WorkspaceRunConfigurationSet configurationSet;
    private final DefaultListModel<WorkspaceRunConfiguration> listModel;
    private final JList<WorkspaceRunConfiguration> configList;
    private final JTextField nameField;
    private final JComboBox<ProjectKind> kindCombo;
    private final JComboBox<String> goalCombo;
    private final JTextField goalField;
    private final JTextField vmOptionsField;
    private final JTextArea propertiesArea;
    private final JLabel vmOptionsHintLabel;
    private final JLabel propertiesHintLabel;
    private WorkspaceRunConfiguration selectedConfig;
    private boolean suppressListEvents;
    private Result result = Result.NONE;

    static Result showManager(View view, File projectRoot) {
        return show(view, projectRoot, false, null);
    }

    static Result showNew(View view, File projectRoot) {
        return show(view, projectRoot, true, null);
    }

    private static Result show(View view, File projectRoot, boolean createNew, String selectId) {
        List<ProjectKind> supported = WorkspaceProjectRunner.detectSupportedKinds(projectRoot);
        if (supported.isEmpty()) {
            return Result.NONE;
        }
        WorkspaceRunConfigurationsDialog dialog =
            new WorkspaceRunConfigurationsDialog(view, projectRoot, supported);
        if (createNew) {
            dialog.addConfiguration();
            dialog.nameField.setText("");
        } else if (selectId != null) {
            WorkspaceRunConfiguration cfg = dialog.configurationSet.findById(selectId);
            if (cfg != null) {
                dialog.selectConfiguration(cfg);
            }
        }
        dialog.setVisible(true);
        return dialog.result;
    }

    private WorkspaceRunConfigurationsDialog(View view, File projectRoot,
                                             List<ProjectKind> supportedKinds) {
        super(view, jEdit.getProperty("workspace-run.config.title"), true);
        this.view = view;
        this.projectRoot = projectRoot;
        this.supportedKinds = supportedKinds;
        this.configurationSet = WorkspaceRunConfigurationPreferences.load(projectRoot).copy();

        listModel = new DefaultListModel<>();
        for (WorkspaceRunConfiguration cfg : configurationSet.getConfigurations()) {
            listModel.addElement(cfg);
        }

        configList = new JList<>(listModel);
        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(new ConfigurationListRenderer());
        configList.addListSelectionListener(new ConfigurationSelectionListener());

        nameField = new JTextField(24);
        kindCombo = new JComboBox<>(supportedKinds.toArray(ProjectKind[]::new));
        kindCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ProjectKind kind) {
                    setText(WorkspaceProjectRunner.kindLabel(kind));
                }
                return this;
            }
        });
        kindCombo.addActionListener(e -> {
            refreshGoalSuggestions();
            refreshOverrideHints();
        });

        goalCombo = new JComboBox<>();
        goalField = new JTextField(32);
        vmOptionsField = new JTextField(32);
        propertiesArea = new JTextArea(4, 32);
        propertiesArea.setLineWrap(false);
        vmOptionsHintLabel = new JLabel();
        propertiesHintLabel = new JLabel();
        goalCombo.addActionListener(e -> {
            Object item = goalCombo.getSelectedItem();
            if (item != null) {
                goalField.setText(item.toString());
            }
        });

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        GUIUtilities.loadGeometry(this, "workspace-run-configurations");

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(new JLabel("<html>"
            + jEdit.getProperty("workspace-run.config.intro",
                new Object[] {projectRoot.getAbsolutePath()})
            + "</html>"), BorderLayout.NORTH);
        content.add(buildMainPanel(), BorderLayout.CENTER);
        content.add(buildButtonRow(), BorderLayout.SOUTH);
        setContentPane(content);
        pack();

        if (!listModel.isEmpty()) {
            selectConfiguration(listModel.getElementAt(0));
        }
    }

    private JSplitPane buildMainPanel() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(buildListToolbar(), BorderLayout.NORTH);
        left.add(new JScrollPane(configList), BorderLayout.CENTER);
        left.setPreferredSize(new java.awt.Dimension(220, 320));

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(buildEditorPanel(), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.35);
        return split;
    }

    private JPanel buildListToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton add = new JButton(jEdit.getProperty("workspace-run.config.add"));
        add.addActionListener(e -> addConfiguration());
        JButton remove = new JButton(jEdit.getProperty("workspace-run.config.remove"));
        remove.addActionListener(e -> removeConfiguration());
        JButton duplicate = new JButton(jEdit.getProperty("workspace-run.config.duplicate"));
        duplicate.addActionListener(e -> duplicateConfiguration());
        JButton setDefault = new JButton(jEdit.getProperty("workspace-run.config.set-default"));
        setDefault.addActionListener(e -> setDefaultConfiguration());
        toolbar.add(add);
        toolbar.add(remove);
        toolbar.add(duplicate);
        toolbar.add(setDefault);
        return toolbar;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nameRow.add(new JLabel(jEdit.getProperty("workspace-run.config.name")));
        nameRow.add(nameField);
        panel.add(nameRow);
        panel.add(Box.createVerticalStrut(8));

        JPanel section = ProjectSettingsForm.titledSection(
            jEdit.getProperty("workspace-run.settings.run-section"));
        GridBagConstraints c = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.kind"), kindCombo);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.goal"), goalCombo);
        ProjectSettingsForm.addLabelRow(section, c,
            jEdit.getProperty("workspace-run.settings.goal-custom"), goalField);
        panel.add(section);
        panel.add(Box.createVerticalStrut(8));

        JPanel overridesSection = ProjectSettingsForm.titledSection(
            jEdit.getProperty("workspace-run.config.overrides-section"));
        GridBagConstraints overrideConstraints = ProjectSettingsForm.gridConstraints();
        ProjectSettingsForm.addLabelRow(overridesSection, overrideConstraints,
            jEdit.getProperty("workspace-run.config.vm-options"), vmOptionsField);
        overrideConstraints.gridy++;
        overrideConstraints.gridx = 0;
        overrideConstraints.gridwidth = GridBagConstraints.REMAINDER;
        overridesSection.add(vmOptionsHintLabel, overrideConstraints);
        overrideConstraints.gridy++;
        overrideConstraints.insets.top = 8;
        ProjectSettingsForm.addLabelRow(overridesSection, overrideConstraints,
            jEdit.getProperty("workspace-run.config.additional-properties"),
            new JScrollPane(propertiesArea));
        overrideConstraints.gridy++;
        overrideConstraints.insets.top = 2;
        overridesSection.add(propertiesHintLabel, overrideConstraints);
        panel.add(overridesSection);
        refreshOverrideHints();

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton toolSettings = new JButton(
            jEdit.getProperty("workspace-run.settings.tool-settings"));
        toolSettings.addActionListener(e -> openToolSettings());
        toolRow.add(toolSettings);
        panel.add(Box.createVerticalStrut(8));
        panel.add(toolRow);
        return panel;
    }

    private JPanel buildButtonRow() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton save = new JButton(jEdit.getProperty("workspace-run.config.save"));
        save.addActionListener(e -> saveConfigurations());
        JButton run = new JButton(jEdit.getProperty("workspace-run.config.run"));
        run.addActionListener(e -> saveAndRun());
        JButton cancel = new JButton(jEdit.getProperty("common.cancel"));
        cancel.addActionListener(e -> cancel());
        GenericGUIUtilities.makeSameSize(save, run, cancel);
        buttons.add(save);
        buttons.add(run);
        buttons.add(cancel);
        return buttons;
    }

    private void addConfiguration() {
        commitEditorToConfig();
        ProjectKind kind = supportedKinds.get(0);
        String goal = WorkspaceProjectRunner.defaultRunGoalForKind(projectRoot, kind);
        WorkspaceRunConfiguration cfg = configurationSet.createNew(kind, goal);
        configurationSet.add(cfg);
        listModel.addElement(cfg);
        selectConfiguration(cfg);
    }

    private void removeConfiguration() {
        WorkspaceRunConfiguration cfg = configList.getSelectedValue();
        if (cfg == null) {
            return;
        }
        if (!configurationSet.remove(cfg)) {
            JOptionPane.showMessageDialog(this,
                jEdit.getProperty("workspace-run.config.remove-last"),
                jEdit.getProperty("workspace-run.config.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        int index = listModel.indexOf(cfg);
        listModel.removeElement(cfg);
        if (selectedConfig == cfg) {
            selectedConfig = null;
        }
        if (!listModel.isEmpty()) {
            int next = Math.min(index, listModel.getSize() - 1);
            selectConfiguration(listModel.getElementAt(next));
        }
    }

    private void duplicateConfiguration() {
        commitEditorToConfig();
        WorkspaceRunConfiguration source = configList.getSelectedValue();
        if (source == null) {
            return;
        }
        WorkspaceRunConfiguration copy = configurationSet.duplicate(source);
        if (copy == null) {
            return;
        }
        listModel.addElement(copy);
        selectConfiguration(copy);
    }

    private void setDefaultConfiguration() {
        commitEditorToConfig();
        WorkspaceRunConfiguration cfg = configList.getSelectedValue();
        if (cfg == null) {
            return;
        }
        configurationSet.setDefault(cfg.id);
        configList.repaint();
    }

    private void selectConfiguration(WorkspaceRunConfiguration cfg) {
        suppressListEvents = true;
        configList.setSelectedValue(cfg, true);
        suppressListEvents = false;
        loadEditorFromConfig(cfg);
    }

    private void commitEditorToConfig() {
        if (selectedConfig == null) {
            return;
        }
        selectedConfig.name = nameField.getText().trim();
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        if (kind != null) {
            selectedConfig.kind = kind;
        }
        selectedConfig.runGoal = goalField.getText().trim();
        selectedConfig.vmOptions = vmOptionsField.getText().trim();
        selectedConfig.additionalProperties = propertiesArea.getText().trim();
        configList.repaint();
    }

    private void loadEditorFromConfig(WorkspaceRunConfiguration cfg) {
        selectedConfig = cfg;
        nameField.setText(cfg.name != null ? cfg.name : "");
        if (!supportedKinds.contains(cfg.kind)) {
            cfg.kind = supportedKinds.get(0);
        }
        kindCombo.setSelectedItem(cfg.kind);
        populateGoalSuggestions(cfg.kind, cfg.runGoal);
        vmOptionsField.setText(cfg.vmOptions != null ? cfg.vmOptions : "");
        propertiesArea.setText(cfg.additionalProperties != null ? cfg.additionalProperties : "");
        refreshOverrideHints();
    }

    private void refreshOverrideHints() {
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        String kindId = kind != null ? kind.id() : "maven";
        vmOptionsHintLabel.setText(jEdit.getProperty(
            "workspace-run.config.vm-options-hint." + kindId));
        propertiesHintLabel.setText(jEdit.getProperty(
            "workspace-run.config.additional-properties-hint." + kindId));
    }

    private void refreshGoalSuggestions() {
        ProjectKind kind = (ProjectKind) kindCombo.getSelectedItem();
        if (kind == null) {
            return;
        }
        String current = goalField.getText().trim();
        if (current.isEmpty() && selectedConfig != null && selectedConfig.kind == kind) {
            current = selectedConfig.runGoal;
        }
        populateGoalSuggestions(kind, current);
    }

    private void populateGoalSuggestions(ProjectKind kind, String selected) {
        goalCombo.removeAllItems();
        for (String goal : WorkspaceProjectRunner.suggestRunGoals(
            projectRoot, kind, selected)) {
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
            case FLUTTER, DART -> FlutterProjectSettingsDialog.show(view, projectRoot);
            case ANT -> AntProjectSettingsDialog.show(view, projectRoot);
            case PIP -> PipProjectSettingsDialog.show(view, projectRoot);
            default -> { }
        }
    }

    private boolean validateCurrentConfiguration() {
        commitEditorToConfig();
        String goal = goalField.getText().trim();
        if (goal.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                jEdit.getProperty("workspace-run.settings.goal-required"),
                jEdit.getProperty("workspace-run.config.title"),
                JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private boolean persistConfigurations() {
        if (!validateCurrentConfiguration()) {
            return false;
        }
        try {
            WorkspaceRunConfigurationPreferences.save(projectRoot, configurationSet);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                jEdit.getProperty("workspace-run.config.title"),
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void saveConfigurations() {
        if (persistConfigurations()) {
            result = Result.SAVED;
            GUIUtilities.saveGeometry(this, "workspace-run-configurations");
            dispose();
        }
    }

    private void saveAndRun() {
        if (!validateCurrentConfiguration()) {
            return;
        }
        if (selectedConfig != null
            && (selectedConfig.name == null || selectedConfig.name.isBlank())) {
            selectedConfig.name = configurationSet.suggestUntitledName();
            nameField.setText(selectedConfig.name);
        }
        if (!persistConfigurations()) {
            return;
        }
        WorkspaceProjectRunner.runConfiguration(view, projectRoot, selectedConfig.copy());
        result = Result.SAVED_AND_RAN;
        GUIUtilities.saveGeometry(this, "workspace-run-configurations");
        dispose();
    }

    @Override
    public void ok() {
        saveConfigurations();
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "workspace-run-configurations");
        dispose();
    }

    private final class ConfigurationSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() || suppressListEvents) {
                return;
            }
            WorkspaceRunConfiguration cfg = configList.getSelectedValue();
            if (cfg == null || cfg == selectedConfig) {
                return;
            }
            commitEditorToConfig();
            loadEditorFromConfig(cfg);
        }
    }

    private final class ConfigurationListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof WorkspaceRunConfiguration cfg) {
                String label = cfg.displayName();
                if (label.isEmpty()) {
                    label = WorkspaceProjectRunner.kindLabel(cfg.kind) + ": " + cfg.runGoal;
                }
                if (cfg.id.equals(configurationSet.getDefaultId())) {
                    label += " ("
                        + jEdit.getProperty("workspace-run.config.default-marker") + ")";
                }
                setText(label);
            }
            return this;
        }
    }
}
