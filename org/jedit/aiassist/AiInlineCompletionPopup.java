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
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.textarea.TextArea;

final class AiInlineCompletionPopup {

    private final JWindow window;
    private final JLabel label;

    AiInlineCompletionPopup() {
        window = new JWindow();
        label = new JLabel();
        label.setOpaque(true);
        label.setBackground(new Color(255, 255, 225));
        label.setForeground(new Color(60, 60, 60));
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 120)),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        window.getContentPane().add(label, BorderLayout.CENTER);
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
        label.setText("<html>" + escapeHtml(display).replace("\n", "<br>") + "</html>");
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

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    void dispose() {
        SwingUtilities.invokeLater(window::dispose);
    }
}
