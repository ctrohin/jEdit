/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.GenericGUIUtilities;

final class BuildOutputSettingsDialog extends EnhancedDialog {

    static final String MAX_LINES_PROPERTY = "build-output.max-lines";
    static final int DEFAULT_MAX_LINES = 200;

    private final JSpinner maxLinesSpinner;
    private boolean saved;

    static boolean show(View view) {
        BuildOutputSettingsDialog dialog = new BuildOutputSettingsDialog(view);
        dialog.setVisible(true);
        return dialog.saved;
    }

    private BuildOutputSettingsDialog(View view) {
        super(view, jEdit.getProperty("build-output.settings.title"), true);
        GUIUtilities.loadGeometry(this, "build-output-settings");

        maxLinesSpinner = new JSpinner(new SpinnerNumberModel(
            jEdit.getIntegerProperty(MAX_LINES_PROPERTY, DEFAULT_MAX_LINES),
            50, 10_000, 50));
        maxLinesSpinner.setPreferredSize(
            new Dimension(72, maxLinesSpinner.getPreferredSize().height));

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fields.add(new JLabel(jEdit.getProperty("build-output.max-lines")));
        fields.add(maxLinesSpinner);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton ok = new JButton(jEdit.getProperty("common.ok"));
        ok.addActionListener(e -> ok());
        JButton cancel = new JButton(jEdit.getProperty("common.cancel"));
        cancel.addActionListener(e -> cancel());
        GenericGUIUtilities.makeSameSize(ok, cancel);
        buttons.add(Box.createGlue());
        buttons.add(ok);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        pack();
        setLocationRelativeTo(view);
    }

    @Override
    public void ok() {
        int value = ((Number) maxLinesSpinner.getValue()).intValue();
        jEdit.setIntegerProperty(MAX_LINES_PROPERTY, value);
        saved = true;
        GUIUtilities.saveGeometry(this, "build-output-settings");
        dispose();
    }

    @Override
    public void cancel() {
        GUIUtilities.saveGeometry(this, "build-output-settings");
        dispose();
    }
}
