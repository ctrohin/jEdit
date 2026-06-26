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

package org.jedit.lsp;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.gjt.sp.jedit.AbstractOptionPane;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;

/**
 * Global options pane for configuring and installing LSP language servers.
 */
public class LspServersOptionPane extends AbstractOptionPane {

    private final List<LspServerDefinition> definitions = LspConfig.getServerDefinitions();
    private final Map<String, ServerRow> rows = new LinkedHashMap<>();

    private DefaultListModel<LspServerDefinition> listModel;
    private JList<LspServerDefinition> serverList;
    private JLabel statusLabel;
    private JCheckBox enabledCheckBox;
    private JTextField commandField;
    private JTextArea installHelpArea;
    private JTextArea outputArea;
    private JButton installButton;
    private JButton testButton;
    private JButton refreshButton;

    public LspServersOptionPane() {
        super("lsp-servers");
    }

    @Override
    protected void _init() {
        setLayout(new BorderLayout(6, 6));

        JLabel intro = new JLabel("<html>"
            + jEdit.getProperty("options.lsp-servers.intro")
            + "</html>");
        intro.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        add(intro, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        for (LspServerDefinition definition : definitions) {
            listModel.addElement(definition);
            rows.put(definition.getModeName(), new ServerRow(definition));
        }

        serverList = new JList<>(listModel);
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        serverList.setCellRenderer(new ServerListRenderer());

        JPanel details = new JPanel(new BorderLayout(6, 6));
        details.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

        JPanel fields = new JPanel();
        fields.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;

        enabledCheckBox = new JCheckBox(jEdit.getProperty("options.lsp-servers.enabled"));
        fields.add(enabledCheckBox, c);

        c.gridy++;
        fields.add(new JLabel(jEdit.getProperty("options.lsp-servers.command")), c);

        c.gridy++;
        c.weightx = 1;
        commandField = new JTextField();
        fields.add(commandField, c);

        c.gridy++;
        c.weightx = 0;
        statusLabel = new JLabel();
        fields.add(statusLabel, c);

        c.gridy++;
        c.weightx = 1;
        c.fill = java.awt.GridBagConstraints.BOTH;
        c.weighty = 0.2;
        installHelpArea = new JTextArea(4, 40);
        installHelpArea.setEditable(false);
        installHelpArea.setLineWrap(true);
        installHelpArea.setWrapStyleWord(true);
        installHelpArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JScrollPane helpScroll = new JScrollPane(installHelpArea);
        helpScroll.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("options.lsp-servers.install-help-title")));
        fields.add(helpScroll, c);

        c.gridy++;
        c.weighty = 0.5;
        outputArea = new JTextArea(8, 40);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("options.lsp-servers.output-title")));
        fields.add(outputScroll, c);

        details.add(fields, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton(jEdit.getProperty("options.lsp-servers.refresh"));
        testButton = new JButton(jEdit.getProperty("options.lsp-servers.test"));
        installButton = new JButton(jEdit.getProperty("options.lsp-servers.install"));
        refreshButton.addActionListener(e -> refreshSelectedStatus());
        testButton.addActionListener(e -> testSelectedServer());
        installButton.addActionListener(e -> installSelectedServer());
        buttons.add(refreshButton);
        buttons.add(testButton);
        buttons.add(installButton);
        details.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(serverList), details);
        split.setResizeWeight(0.28);
        split.setPreferredSize(new Dimension(760, 420));
        split.setMinimumSize(new Dimension(500, 300));
        add(split, BorderLayout.CENTER);

        enabledCheckBox.addActionListener(e -> storeCurrentRowFromUi());
        commandField.addActionListener(e -> storeCurrentRowFromUi());
        serverList.addListSelectionListener(new SelectionHandler());

        if (!definitions.isEmpty()) {
            serverList.setSelectedIndex(0);
        } else {
            showSelectedDefinition();
        }

        refreshAllStatuses();
    }

    @Override
    protected void _save() {
        storeCurrentRowFromUi();
        for (ServerRow row : rows.values()) {
            LspConfig.setServerEnabled(row.definition.getModeName(), row.enabled);
            LspConfig.setServerCommand(row.definition.getModeName(), row.command);
        }
        LspPlugin plugin = LspPlugin.getInstance();
        if (plugin != null) {
            plugin.reloadConfiguration();
        }
    }

    private void showSelectedDefinition() {
        if (enabledCheckBox == null) {
            return;
        }
        LspServerDefinition definition = serverList.getSelectedValue();
        if (definition == null) {
            enabledCheckBox.setEnabled(false);
            commandField.setEnabled(false);
            testButton.setEnabled(false);
            installButton.setEnabled(false);
            refreshButton.setEnabled(false);
            statusLabel.setText("");
            commandField.setText("");
            installHelpArea.setText("");
            outputArea.setText("");
            return;
        }

        ServerRow row = rows.get(definition.getModeName());
        enabledCheckBox.setEnabled(true);
        commandField.setEnabled(true);
        testButton.setEnabled(true);
        refreshButton.setEnabled(true);
        enabledCheckBox.setSelected(row.enabled);
        commandField.setText(LspServerInstaller.formatResolvedCommand(row.command));
        installHelpArea.setText(jEdit.getProperty(definition.getInstallHelpProperty()));
        outputArea.setText(row.lastOutput == null ? "" : row.lastOutput);
        updateStatusLabel(row);
        installButton.setEnabled(LspServerInstaller.getInstallCommand(definition) != null);
    }

    private void storeCurrentRowFromUi() {
        LspServerDefinition definition = serverList.getSelectedValue();
        if (definition == null) {
            return;
        }
        ServerRow row = rows.get(definition.getModeName());
        row.enabled = enabledCheckBox.isSelected();
        row.command = commandField.getText().trim();
    }

    private void refreshAllStatuses() {
        ThreadUtilities.runInBackground(() -> {
            Map<String, Boolean> installed = new HashMap<>(
                LspServerInstaller.detectAllServers());
            SwingUtilities.invokeLater(() -> {
                for (Map.Entry<String, Boolean> entry : installed.entrySet()) {
                    rows.get(entry.getKey()).installed = entry.getValue();
                }
                serverList.repaint();
                LspServerDefinition selected = serverList.getSelectedValue();
                if (selected != null) {
                    updateStatusLabel(rows.get(selected.getModeName()));
                }
            });
        });
    }

    private void refreshSelectedStatus() {
        LspServerDefinition definition = serverList.getSelectedValue();
        if (definition == null) {
            return;
        }
        refreshButton.setEnabled(false);
        ThreadUtilities.runInBackground(() -> {
            boolean installed = LspServerInstaller.isServerInstalled(definition);
            SwingUtilities.invokeLater(() -> {
                ServerRow row = rows.get(definition.getModeName());
                row.installed = installed;
                if (installed) {
                    String resolved = LspServerInstaller.formatResolvedCommand(row.command);
                    row.command = resolved;
                    if (serverList.getSelectedValue() == definition) {
                        commandField.setText(resolved);
                    }
                }
                updateStatusLabel(row);
                serverList.repaint();
                refreshButton.setEnabled(true);
            });
        });
    }

    private void testSelectedServer() {
        storeCurrentRowFromUi();
        LspServerDefinition definition = serverList.getSelectedValue();
        if (definition == null) {
            return;
        }
        ServerRow row = rows.get(definition.getModeName());
        String[] command = LspServerInstaller.parseCommand(row.command);
        testButton.setEnabled(false);
        outputArea.setText(jEdit.getProperty("options.lsp-servers.testing"));
        ThreadUtilities.runInBackground(() -> {
            String testResult;
            try {
                testResult = LspServerInstaller.testServer(command);
            } catch (Exception ex) {
                testResult = "Failed to test command: " + ex.getMessage();
            }
            final String result = testResult;
            final String resolvedCommand = LspServerInstaller.formatResolvedCommand(row.command);
            SwingUtilities.invokeLater(() -> {
                row.command = resolvedCommand;
                commandField.setText(resolvedCommand);
                row.lastOutput = result;
                outputArea.setText(result);
                testButton.setEnabled(true);
            });
        });
    }

    private void installSelectedServer() {
        LspServerDefinition definition = serverList.getSelectedValue();
        if (definition == null) {
            return;
        }
        if (LspServerInstaller.getInstallCommand(definition) == null) {
            JOptionPane.showMessageDialog(this,
                jEdit.getProperty(definition.getInstallHelpProperty()),
                jEdit.getProperty("options.lsp-servers.install-manual-title"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("options.lsp-servers.install-confirm",
                new String[] {definition.getDisplayName()}),
            jEdit.getProperty("options.lsp-servers.install"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        installButton.setEnabled(false);
        testButton.setEnabled(false);
        outputArea.setText("");
        ThreadUtilities.runInBackground(() -> {
            StringBuilder log = new StringBuilder();
            String error = LspServerInstaller.installServer(definition, line -> {
                synchronized (log) {
                    if (log.length() > 0) {
                        log.append('\n');
                    }
                    log.append(line);
                }
                String text = log.toString();
                SwingUtilities.invokeLater(() -> outputArea.setText(text));
            });
            boolean installed = LspServerInstaller.isServerInstalled(definition);
            SwingUtilities.invokeLater(() -> {
                ServerRow row = rows.get(definition.getModeName());
                row.installed = installed;
                row.lastOutput = log.toString();
                if (error != null) {
                    if (row.lastOutput.isBlank()) {
                        row.lastOutput = error;
                    } else {
                        row.lastOutput = row.lastOutput + "\n\n" + error;
                    }
                    outputArea.setText(row.lastOutput);
                    JOptionPane.showMessageDialog(LspServersOptionPane.this,
                        error,
                        jEdit.getProperty("options.lsp-servers.install-error-title"),
                        JOptionPane.ERROR_MESSAGE);
                } else if (installed) {
                    JOptionPane.showMessageDialog(LspServersOptionPane.this,
                        jEdit.getProperty("options.lsp-servers.install-success",
                            new String[] {definition.getDisplayName()}),
                        jEdit.getProperty("options.lsp-servers.install"),
                        JOptionPane.INFORMATION_MESSAGE);
                }
                updateStatusLabel(row);
                serverList.repaint();
                installButton.setEnabled(true);
                testButton.setEnabled(true);
            });
        });
    }

    private void updateStatusLabel(ServerRow row) {
        String statusKey;
        if (!row.enabled) {
            statusKey = "options.lsp-servers.status.disabled";
        } else if (row.installed) {
            statusKey = "options.lsp-servers.status.installed";
        } else {
            statusKey = "options.lsp-servers.status.missing";
        }
        statusLabel.setText(jEdit.getProperty(statusKey,
            new String[] {row.definition.getExecutable()}));
    }

    private final class SelectionHandler implements ListSelectionListener {
        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }
            showSelectedDefinition();
        }
    }

    private final class ServerListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            if (value instanceof LspServerDefinition definition) {
                ServerRow row = rows.get(definition.getModeName());
                String status = row.enabled
                    ? (row.installed ? "✓" : "✗")
                    : "−";
                label.setText(status + "  " + definition.getDisplayName()
                    + " (" + definition.getModeName() + ")");
            }
            return label;
        }
    }

    private static final class ServerRow {
        private final LspServerDefinition definition;
        private boolean enabled;
        private String command;
        private boolean installed;
        private String lastOutput;

        private ServerRow(LspServerDefinition definition) {
            this.definition = definition;
            this.enabled = LspConfig.isServerEnabled(definition.getModeName());
            this.command = LspConfig.getServerCommandProperty(definition.getModeName());
        }
    }
}
