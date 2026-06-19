/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.textarea.TextArea;

final class AiInlineCompletionPopup {

    private static final int MAX_SUGGESTION_WIDTH = 480;

    private final JWindow window;
    private final JTextArea suggestionArea;
    private final JLabel hintLabel;
    private final Font hintFont;

    AiInlineCompletionPopup() {
        window = new JWindow();
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(true);
        panel.setBackground(new Color(255, 255, 225));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 120)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        suggestionArea = new JTextArea();
        suggestionArea.setEditable(false);
        suggestionArea.setFocusable(false);
        suggestionArea.setOpaque(false);
        suggestionArea.setBorder(null);
        suggestionArea.setLineWrap(true);
        suggestionArea.setWrapStyleWord(false);
        suggestionArea.setForeground(new Color(60, 60, 60));

        hintLabel = new JLabel();
        hintLabel.setOpaque(false);
        hintLabel.setForeground(new Color(136, 136, 136));
        hintFont = hintLabel.getFont().deriveFont(Font.PLAIN, 11f);
        hintLabel.setFont(hintFont);

        panel.add(suggestionArea);
        panel.add(hintLabel);
        window.getContentPane().add(panel, BorderLayout.CENTER);
        window.setFocusableWindowState(false);
    }

    void show(TextArea textArea, String suggestion) {
        if (textArea == null || suggestion == null || suggestion.isBlank()) {
            AiAssistLog.debug("popup not shown: empty suggestion or text area");
            hide();
            return;
        }
        String display = suggestion.length() > 240
            ? suggestion.substring(0, 237) + "..."
            : suggestion;
        Font editorFont = textArea.getPainter().getFont();
        suggestionArea.setFont(editorFont);
        suggestionArea.setText(display);
        sizeSuggestionArea(display, editorFont);

        hintLabel.setFont(hintFont);
        hintLabel.setText(jEdit.getProperty("ai-assist.inline.accept-hint"));
        window.pack();

        int caretOffset = textArea.getCaretPosition();
        Point caret = textArea.offsetToXY(caretOffset);
        int lineHeight = textArea.getPainter().getFontMetrics().getHeight();
        Point screen = textArea.getPainter().getLocationOnScreen();
        int popupHeight = window.getHeight();
        int popupWidth = window.getWidth();

        int x = screen.x + caret.x + 8;
        int y = screen.y + caret.y - popupHeight - 4;

        Rectangle screenBounds = textArea.getGraphicsConfiguration().getBounds();
        if (y < screenBounds.y + 4) {
            y = screen.y + caret.y + lineHeight + 4;
        }
        if (x + popupWidth > screenBounds.x + screenBounds.width - 4) {
            x = screenBounds.x + screenBounds.width - popupWidth - 4;
        }

        window.setLocation(x, y);
        window.setVisible(true);
    }

    void hide() {
        window.setVisible(false);
    }

    private void sizeSuggestionArea(String display, Font editorFont) {
        FontMetrics fm = suggestionArea.getFontMetrics(editorFont);
        String[] lines = display.split("\n", -1);
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, fm.stringWidth(line));
        }
        int width = Math.min(MAX_SUGGESTION_WIDTH, widest + 4);
        int height = fm.getHeight() * lines.length;
        Dimension size = new Dimension(width, height);
        suggestionArea.setPreferredSize(size);
        suggestionArea.setMaximumSize(new Dimension(MAX_SUGGESTION_WIDTH, height));
    }

    void dispose() {
        SwingUtilities.invokeLater(window::dispose);
    }
}
