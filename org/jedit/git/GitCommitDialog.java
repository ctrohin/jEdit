/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;

final class GitCommitDialog extends EnhancedDialog {

    static void show(View view, GitModels.Commit commit, String text) {
        GitCommitDialog dialog = new GitCommitDialog(view, commit, text);
        dialog.setVisible(true);
    }

    private GitCommitDialog(View view, GitModels.Commit commit, String text) {
        super(view, jEdit.getProperty("git.commit-viewer.title",
            new String[] {commit.shortHash}), false);
        setEnterEnabled(false);

        JTextArea body = new JTextArea(text == null ? "" : text);
        body.setEditable(false);
        body.setLineWrap(false);
        body.setCaretPosition(0);
        body.setFont(new Font(Font.MONOSPACED, Font.PLAIN, body.getFont().getSize()));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setPreferredSize(new Dimension(720, 480));

        JButton closeButton = new JButton(jEdit.getProperty("common.close"));
        closeButton.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(closeButton);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().setDefaultButton(closeButton);
        pack();
        setLocationRelativeTo(view);
    }

    @Override
    public void ok() {
        dispose();
    }

    @Override
    public void cancel() {
        dispose();
    }
}
