/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

final class AiInlineCompletionGhostRenderer {

    private final GhostExtension extension = new GhostExtension();
    private TextArea attachedTextArea;

    void show(TextArea textArea, Buffer buffer, AiInlineCompletionSuggestion suggestion,
            int caret) {
        if (textArea == null || buffer == null || suggestion == null
            || suggestion.insertText.isBlank()) {
            hide();
            return;
        }
        if (attachedTextArea != textArea) {
            hide();
            attachedTextArea = textArea;
            textArea.getPainter().addExtension(TextAreaPainter.TEXT_LAYER + 50, extension);
        }
        extension.update(textArea, buffer, suggestion, caret);
        textArea.getPainter().repaint();
    }

    void hide() {
        if (attachedTextArea != null) {
            attachedTextArea.getPainter().removeExtension(extension);
            attachedTextArea.getPainter().repaint();
            attachedTextArea = null;
        }
        extension.clear();
    }

    void dispose() {
        hide();
    }

    private static final class GhostExtension extends TextAreaExtension {

        private TextArea textArea;
        private Buffer buffer;
        private int caretOffset;
        private String[] ghostLines = new String[0];

        void update(TextArea textArea, Buffer buffer, AiInlineCompletionSuggestion suggestion,
                int caret) {
            this.textArea = textArea;
            this.buffer = buffer;
            this.caretOffset = caret;
            this.ghostLines = suggestion.ghostLines(buffer);
        }

        void clear() {
            textArea = null;
            buffer = null;
            caretOffset = 0;
            ghostLines = new String[0];
        }

        @Override
        public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,
                int start, int end, int y) {
            if (textArea == null || buffer == null || ghostLines.length == 0) {
                return;
            }
            int caretPhysLine = buffer.getLineOfOffset(caretOffset);
            int ghostIndex = physicalLine - caretPhysLine;
            if (ghostIndex < 0 || ghostIndex >= ghostLines.length) {
                return;
            }
            String line = ghostLines[ghostIndex];
            if (line.isEmpty()) {
                return;
            }
            TextAreaPainter painter = textArea.getPainter();
            float baseline = baselineY(painter, y);
            if (ghostIndex == 0) {
                if (start <= caretOffset && caretOffset <= end) {
                    Point caret = textArea.offsetToXY(caretOffset);
                    if (caret != null) {
                        paintGhost(gfx, painter, line, caret.x, baseline);
                    }
                }
            } else {
                Point lineStart = textArea.offsetToXY(physicalLine, 0);
                int x = lineStart != null ? lineStart.x : textArea.getHorizontalOffset();
                paintGhost(gfx, painter, line, x, baseline);
            }
        }

        @Override
        public void paintInvalidLine(Graphics2D gfx, int screenLine, int y) {
            if (textArea == null || buffer == null || ghostLines.length <= 1) {
                return;
            }
            int caretPhysLine = buffer.getLineOfOffset(caretOffset);
            int caretScreenLine = textArea.getScreenLineOfOffset(caretOffset);
            if (caretScreenLine < 0) {
                return;
            }
            int lastScreenOfCaretPhys = lastScreenLineForPhysical(caretPhysLine, caretScreenLine);
            int ghostIndex = screenLine - lastScreenOfCaretPhys;
            if (ghostIndex <= 0 || ghostIndex >= ghostLines.length) {
                return;
            }
            int targetPhys = caretPhysLine + ghostIndex;
            if (targetPhys < buffer.getLineCount()) {
                return;
            }
            String line = ghostLines[ghostIndex];
            if (line.isEmpty()) {
                return;
            }
            TextAreaPainter painter = textArea.getPainter();
            float baseline = baselineY(painter, y);
            paintGhost(gfx, painter, line, textArea.getHorizontalOffset(), baseline);
        }

        private int lastScreenLineForPhysical(int physicalLine, int fromScreenLine) {
            int last = fromScreenLine;
            int visible = textArea.getVisibleLines();
            for (int screenLine = fromScreenLine + 1; screenLine < visible; screenLine++) {
                if (textArea.getPhysicalLineOfScreenLine(screenLine) == physicalLine) {
                    last = screenLine;
                } else {
                    break;
                }
            }
            return last;
        }

        private static float baselineY(TextAreaPainter painter, int y) {
            FontMetrics fm = painter.getFontMetrics();
            return y + painter.getLineHeight() - (fm.getLeading() + 1) - fm.getDescent();
        }

        private static void paintGhost(Graphics2D gfx, TextAreaPainter painter, String text,
                int x, float baseline) {
            Color foreground = painter.getForeground();
            Color background = painter.getBackground();
            Color ghost = new Color(
                (foreground.getRed() * 2 + background.getRed()) / 3,
                (foreground.getGreen() * 2 + background.getGreen()) / 3,
                (foreground.getBlue() * 2 + background.getBlue()) / 3);
            gfx.setColor(ghost);
            gfx.setFont(painter.getFont());
            paintWithTabs(gfx, painter, text, x, baseline);
        }

        private static void paintWithTabs(Graphics2D gfx, TextAreaPainter painter, String text,
                int x, float baseline) {
            FontMetrics fm = painter.getFontMetrics();
            int index = 0;
            while (index < text.length()) {
                int codePoint = text.codePointAt(index);
                int charCount = Character.charCount(codePoint);
                if (codePoint == '\t') {
                    x = Math.round(painter.nextTabStop(x, 0));
                } else {
                    String chunk = text.substring(index, index + charCount);
                    gfx.drawString(chunk, x, baseline);
                    x += fm.stringWidth(chunk);
                }
                index += charCount;
            }
        }
    }
}
