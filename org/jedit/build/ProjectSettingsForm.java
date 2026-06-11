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
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.GenericGUIUtilities;

final class ProjectSettingsForm {

    private ProjectSettingsForm() {}

    static GridBagConstraints gridConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(2, 4, 2, 4);
        return c;
    }

    static void addPathRow(JPanel panel, GridBagConstraints c, String label,
                           JTextField field, File projectRoot, int dialogType) {
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
        browse.addActionListener(e -> browse(field, projectRoot, dialogType));
        row.add(browse, BorderLayout.EAST);
        panel.add(row, c);
    }

    static void addLabelRow(JPanel panel, GridBagConstraints c, String label,
                            Component field) {
        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    static JPanel buildButtons(Runnable onReset, Runnable onOk, Runnable onCancel) {
        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton reset = new JButton(jEdit.getProperty("ant-project.settings.reset"));
        reset.addActionListener(e -> onReset.run());
        JButton ok = new JButton(jEdit.getProperty("common.ok"));
        ok.addActionListener(e -> onOk.run());
        JButton cancel = new JButton(jEdit.getProperty("common.cancel"));
        cancel.addActionListener(e -> onCancel.run());
        GenericGUIUtilities.makeSameSize(ok, cancel, reset);
        buttons.add(reset);
        buttons.add(Box.createGlue());
        buttons.add(ok);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(cancel);
        return buttons;
    }

    static JPanel titledSection(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static void browse(JTextField field, File projectRoot, int dialogType) {
        View view = jEdit.getActiveView();
        String start = field.getText();
        if (ShellCommands.isBlank(start) && projectRoot != null) {
            start = projectRoot.getAbsolutePath();
        }
        String[] chosen = GUIUtilities.showVFSFileDialog(view, start, dialogType, false);
        if (chosen.length > 0) {
            field.setText(chosen[0]);
        }
    }
}
