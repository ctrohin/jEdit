/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
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
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;

/**
 * Prompts to install missing LSP servers when a project folder is opened.
 */
final class LspProjectInstallDialog {

    private LspProjectInstallDialog() {}

    static void maybePromptForProject(String folderPath) {
        if (folderPath == null || folderPath.isBlank()
            || LspProjectPreferences.isInstallPromptSuppressed(folderPath)) {
            return;
        }

        ThreadUtilities.runInBackground(() -> {
            File root = new File(folderPath);
            List<LspServerDefinition> missing = LspProjectDetector.detectMissingServers(root);
            if (missing.isEmpty()) {
                return;
            }
            SwingUtilities.invokeLater(() -> showDialog(folderPath, missing));
        });
    }

    private static void showDialog(String folderPath, List<LspServerDefinition> missing) {
        View view = jEdit.getActiveView();
        Component parent = view != null ? view : null;

        JDialog dialog = new JDialog(
            view != null ? view : JOptionPane.getRootFrame(),
            jEdit.getProperty("lsp.project.install.title"),
            true);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        String languages = missing.stream()
            .map(LspServerDefinition::getDisplayName)
            .collect(Collectors.joining(", "));
        JLabel intro = new JLabel("<html>"
            + jEdit.getProperty("lsp.project.install.intro",
                new String[] {languages, folderPath})
            + "</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel serverPanel = new JPanel();
        serverPanel.setLayout(new BoxLayout(serverPanel, BoxLayout.Y_AXIS));
        List<ServerRow> rows = new ArrayList<>();
        for (LspServerDefinition definition : missing) {
            ServerRow row = new ServerRow(definition);
            rows.add(row);
            serverPanel.add(row.panel);
            serverPanel.add(Box.createVerticalStrut(4));
        }

        JScrollPane serverScroll = new JScrollPane(serverPanel);
        serverScroll.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("lsp.project.install.missing-title")));

        JTextArea outputArea = new JTextArea(6, 50);
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        JScrollPane outputScroll = new JScrollPane(outputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder(
            jEdit.getProperty("options.lsp-servers.output-title")));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(serverScroll, BorderLayout.CENTER);
        center.add(outputScroll, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);

        JCheckBox suppressCheckBox = new JCheckBox(
            jEdit.getProperty("lsp.project.install.suppress"));
        JPanel south = new JPanel(new BorderLayout());
        south.add(suppressCheckBox, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton installButton = new JButton(
            jEdit.getProperty("lsp.project.install.install-selected"));
        JButton skipButton = new JButton(jEdit.getProperty("lsp.project.install.skip"));
        buttons.add(installButton);
        buttons.add(skipButton);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        installButton.addActionListener(e -> {
            List<LspServerDefinition> selected = rows.stream()
                .filter(row -> row.checkBox.isSelected())
                .map(row -> row.definition)
                .toList();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                    jEdit.getProperty("lsp.project.install.none-selected"),
                    jEdit.getProperty("lsp.project.install.title"),
                    JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (suppressCheckBox.isSelected()) {
                LspProjectPreferences.suppressInstallPrompt(folderPath);
            }
            installButton.setEnabled(false);
            skipButton.setEnabled(false);
            outputArea.setText(jEdit.getProperty("lsp.project.install.installing"));
            ThreadUtilities.runInBackground(() -> installSelected(selected, outputArea, () ->
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    skipButton.setEnabled(true);
                    LspPlugin plugin = LspPlugin.getInstance();
                    if (plugin != null) {
                        plugin.reloadConfiguration();
                    }
                })));
        });

        skipButton.addActionListener(e -> {
            if (suppressCheckBox.isSelected()) {
                LspProjectPreferences.suppressInstallPrompt(folderPath);
            }
            dialog.dispose();
        });

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void installSelected(List<LspServerDefinition> selected,
                                        JTextArea outputArea,
                                        Runnable onComplete) {
        StringBuilder log = new StringBuilder();
        for (LspServerDefinition definition : selected) {
            appendLine(log, jEdit.getProperty("lsp.project.install.server-header",
                new String[] {definition.getDisplayName()}));

            if (LspServerInstaller.getInstallCommand(definition) == null) {
                appendLine(log, jEdit.getProperty(definition.getInstallHelpProperty()));
                appendLine(log, "");
                updateOutput(outputArea, log.toString());
                continue;
            }

            String error = LspServerInstaller.installServer(definition, line -> {
                synchronized (log) {
                    appendLine(log, line);
                    updateOutput(outputArea, log.toString());
                }
            });
            if (error != null) {
                appendLine(log, error);
            } else if (LspServerInstaller.isServerInstalled(definition)) {
                appendLine(log, jEdit.getProperty("options.lsp-servers.install-success",
                    new String[] {definition.getDisplayName()}));
            }
            appendLine(log, "");
            updateOutput(outputArea, log.toString());
        }
        onComplete.run();
    }

    private static void appendLine(StringBuilder log, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (log.length() > 0) {
            log.append('\n');
        }
        log.append(line);
    }

    private static void updateOutput(JTextArea outputArea, String text) {
        SwingUtilities.invokeLater(() -> outputArea.setText(text));
    }

    private static final class ServerRow {
        private final LspServerDefinition definition;
        private final JCheckBox checkBox;
        private final JPanel panel;

        private ServerRow(LspServerDefinition definition) {
            this.definition = definition;
            this.checkBox = new JCheckBox(jEdit.getProperty(
                "lsp.project.install.server-line",
                new String[] {definition.getDisplayName(), definition.getExecutable()}),
                true);
            this.panel = new JPanel(new BorderLayout());
            this.panel.add(checkBox, BorderLayout.CENTER);
            boolean canAutoInstall = LspServerInstaller.getInstallCommand(definition) != null;
            if (!canAutoInstall) {
                JLabel manual = new JLabel(jEdit.getProperty("lsp.project.install.manual-hint"));
                manual.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
                panel.add(manual, BorderLayout.SOUTH);
            }
        }
    }
}
