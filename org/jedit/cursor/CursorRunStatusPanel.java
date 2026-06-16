/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.jEdit;

/**
 * Live run activity shown at the bottom of the chat. Each row updates in place
 * instead of appending lines to the conversation.
 */
final class CursorRunStatusPanel extends JPanel {

    private final JLabel statusLabel;
    private final JLabel thinkingLabel;
    private final JLabel toolLabel;

    CursorRunStatusPanel() {
        super(new GridBagLayout());
        setOpaque(true);
        setBackground(CursorMarkdown.textBackground());
        Color border = UIManager.getColor("Component.borderColor");
        if (border == null) {
            border = CursorMarkdown.metaForeground(CursorMarkdown.textForeground());
        }
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, border),
            new EmptyBorder(6, 12, 6, 12)));
        setVisible(false);

        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 11);
        Color meta = CursorMarkdown.metaForeground(CursorMarkdown.textForeground());

        statusLabel = createRowLabel(mono, meta);
        thinkingLabel = createRowLabel(mono, meta);
        toolLabel = createRowLabel(mono, meta);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(1, 0, 1, 0);
        add(statusLabel, gbc);
        gbc.gridy = 1;
        add(thinkingLabel, gbc);
        gbc.gridy = 2;
        add(toolLabel, gbc);
    }

    void reset() {
        hideRow(statusLabel);
        hideRow(thinkingLabel);
        hideRow(toolLabel);
        setVisible(false);
        revalidate();
        repaint();
    }

    void setStatus(String status) {
        if (status == null || status.isBlank()) {
            hideRow(statusLabel);
            updateVisibility();
            return;
        }
        showRow(statusLabel, jEdit.getProperty("cursor.status-prefix") + status.trim());
    }

    void setThinking(String text) {
        if (text == null || text.isBlank()) {
            hideRow(thinkingLabel);
            updateVisibility();
            return;
        }
        String display = truncate(text.trim(), 240);
        showRow(thinkingLabel, jEdit.getProperty("cursor.thinking-prefix") + display);
    }

    void setTool(String name, String status) {
        if (name == null || name.isBlank()) {
            hideRow(toolLabel);
            updateVisibility();
            return;
        }
        String detail = name.trim();
        if (status != null && !status.isBlank()) {
            detail += " (" + status.trim() + ")";
        }
        showRow(toolLabel, jEdit.getProperty("cursor.tool-prefix") + detail);
    }

    private static JLabel createRowLabel(Font font, Color color) {
        JLabel label = new JLabel();
        label.setFont(font);
        label.setForeground(color);
        label.setVisible(false);
        return label;
    }

    private void showRow(JLabel label, String text) {
        label.setText(text);
        label.setVisible(true);
        setVisible(true);
        revalidate();
        repaint();
    }

    private void hideRow(JLabel label) {
        label.setText("");
        label.setVisible(false);
    }

    private void updateVisibility() {
        boolean any = statusLabel.isVisible() || thinkingLabel.isVisible() || toolLabel.isVisible();
        setVisible(any);
        revalidate();
        repaint();
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
