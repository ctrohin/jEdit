/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.jedit.copilot.CopilotPlugin;

public class AiAssistOptionPane extends AbstractOptionPane {

    private JComboBox<AiAssistProvider> providerCombo;
    private JCheckBox inlineAutomaticCheck;
    private JSpinner idleSpinner;
    private JLabel idleDelayLabel;
    private JLabel copilotStatusLabel;
    private JButton copilotLoginButton;
    private JButton copilotLogoutButton;

    public AiAssistOptionPane() {
        super("ai-assist");
    }

    @Override
    protected void _init() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 8, 0);

        add(new JLabel("<html>" + jEdit.getProperty("options.ai-assist.intro") + "</html>"), c);

        providerCombo = new JComboBox<>(AiAssistProvider.values());
        providerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                javax.swing.JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
                java.awt.Component component = super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
                if (value instanceof AiAssistProvider provider && component instanceof JLabel label) {
                    label.setText(provider.label());
                }
                return component;
            }
        });
        addField(jEdit.getProperty("options.ai-assist.provider"), providerCombo, c);

        inlineAutomaticCheck = new JCheckBox(
            jEdit.getProperty("options.ai-assist.inline-automatic"));
        add(inlineAutomaticCheck, c);

        JPanel idleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        idleDelayLabel = new JLabel(jEdit.getProperty("options.ai-assist.idle-delay"));
        idleSpinner = new JSpinner(new SpinnerNumberModel(
            AiAssistConfig.idleDelayMs(), 300, 10000, 100));
        idleRow.add(idleDelayLabel);
        idleRow.add(idleSpinner);
        add(idleRow, c);

        ChangeListener automaticListener = e -> updateAutomaticControls();
        inlineAutomaticCheck.addChangeListener(automaticListener);

        add(createCopilotPanel(), c);

        providerCombo.setSelectedItem(AiAssistConfig.provider());
        inlineAutomaticCheck.setSelected(AiAssistConfig.inlineAutomatic());
        idleSpinner.setValue(AiAssistConfig.idleDelayMs());
        updateAutomaticControls();
        refreshAuthLabels();
    }

    private JPanel createCopilotPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder(
            jEdit.getProperty("options.ai-assist.copilot.title")));
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        c.insets = new Insets(0, 0, 4, 0);

        copilotStatusLabel = new JLabel();
        panel.add(copilotStatusLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        copilotLoginButton = new JButton(jEdit.getProperty("options.ai-assist.login"));
        copilotLogoutButton = new JButton(jEdit.getProperty("options.ai-assist.logout"));
        buttons.add(copilotLoginButton);
        buttons.add(copilotLogoutButton);
        panel.add(buttons, c);

        copilotLoginButton.addActionListener(this::copilotLogin);
        copilotLogoutButton.addActionListener(this::copilotLogout);
        return panel;
    }

    private void addField(String label, java.awt.Component field, GridBagConstraints c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel(label));
        row.add(field);
        add(row, c);
    }

    private void updateAutomaticControls() {
        boolean automatic = inlineAutomaticCheck.isSelected();
        idleSpinner.setEnabled(automatic);
        idleDelayLabel.setEnabled(automatic);
    }

    @Override
    protected void _save() {
        AiAssistProvider provider = (AiAssistProvider) providerCombo.getSelectedItem();
        AiAssistConfig.setProvider(provider);
        AiAssistConfig.setInlineAutomatic(inlineAutomaticCheck.isSelected());
        AiAssistConfig.setIdleDelayMs((Integer) idleSpinner.getValue());
        AiAssistPlugin.restartHub();
    }

    private void refreshAuthLabels() {
        boolean copilotSignedIn = CopilotPlugin.isSignedIn();
        copilotStatusLabel.setText(jEdit.getProperty(copilotSignedIn
            ? "options.ai-assist.copilot.signed-in"
            : "options.ai-assist.copilot.signed-out"));
        copilotLoginButton.setEnabled(!copilotSignedIn);
        copilotLogoutButton.setEnabled(copilotSignedIn);
    }

    private void copilotLogin(ActionEvent event) {
        View view = jEdit.getActiveView();
        if (view != null) {
            CopilotPlugin.login(view);
        }
        SwingUtilities.invokeLater(this::refreshAuthLabels);
    }

    private void copilotLogout(ActionEvent event) {
        View view = jEdit.getActiveView();
        if (view != null) {
            CopilotPlugin.logout(view);
        }
        refreshAuthLabels();
    }
}
