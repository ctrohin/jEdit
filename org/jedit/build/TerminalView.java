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

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;

/**
 * Simple project shell: each command runs in the workspace folder.
 */
public final class TerminalView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "terminal";

    private final View view;
    private final LinkAwareTextArea output;
    private final JTextField commandField;
    private final JLabel caption;
    private final BuildProcessRunner runner = new BuildProcessRunner();
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateCaption);

    public TerminalView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel();
        output = new LinkAwareTextArea(view);
        updateCaption();
        add(caption, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        commandField = new JTextField();
        commandField.addActionListener(e -> runCommand());
        south.add(commandField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton run = new JButton(jEdit.getProperty("terminal.run"));
        run.addActionListener(e -> runCommand());
        JButton clear = new JButton(jEdit.getProperty("terminal.clear"));
        clear.addActionListener(e -> output.clearOutput());
        JButton stop = new JButton(jEdit.getProperty("terminal.stop"));
        stop.addActionListener(e -> runner.stop());
        buttons.add(clear);
        buttons.add(stop);
        buttons.add(run);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);
    }

    private void updateCaption() {
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            caption.setText(jEdit.getProperty("terminal.no-workspace"));
        } else {
            caption.setText(jEdit.getProperty("terminal.caption",
                new Object[] { root.getAbsolutePath() }));
        }
        output.setProjectRoot(root);
    }

    private void runCommand() {
        String commandLine = commandField.getText().trim();
        if (commandLine.isEmpty()) {
            return;
        }
        updateCaption();
        File root = ProjectRoots.workspaceRoot();
        output.appendLine("$ " + commandLine);
        List<String> command = shellCommand(commandLine);
        runner.run(root, command, output::appendLine, null);
        commandField.setText("");
    }

    private static List<String> shellCommand(String commandLine) {
        if (OperatingSystem.isWindows()) {
            return Arrays.asList("cmd.exe", "/c", commandLine);
        }
        return Arrays.asList("/bin/sh", "-lc", commandLine);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        updateCaption();
        SwingUtilities.invokeLater(commandField::requestFocus);
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        commandField.requestFocus();
    }
}
