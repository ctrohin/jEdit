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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;

/**
 * Paints LSP diagnostic ranges in the text area (background tint and underline).
 */
final class LspDiagnosticHighlight extends TextAreaExtension {

    private static final int BACKGROUND_ALPHA = 48;
    private static final float UNDERLINE_WIDTH = 2f;

    private final JEditTextArea textArea;

    LspDiagnosticHighlight(JEditTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void paintValidLine(Graphics2D gfx, int screenLine,
                               int physicalLine, int start, int end, int y) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return;
        }

        List<LspDiagnosticProblem> problems =
            LspDiagnosticsHub.getInstance().getProblemsForBuffer(buffer);
        if (problems.isEmpty()) {
            return;
        }

        int lineHeight = textArea.getPainter().getLineHeight();
        for (LspDiagnosticProblem problem : problems) {
            paintProblem(gfx, buffer, problem, start, end, y, lineHeight);
        }
    }

    private void paintProblem(Graphics2D gfx, Buffer buffer,
                              LspDiagnosticProblem problem,
                              int lineStart, int lineEnd, int y, int lineHeight) {
        int diagStart = problem.getStartOffset(buffer);
        int diagEnd = problem.getEndOffset(buffer);
        if (diagEnd <= lineStart || diagStart >= lineEnd) {
            return;
        }

        int segStart = Math.max(diagStart, lineStart);
        int segEnd = Math.min(diagEnd, lineEnd);
        if (segEnd <= segStart) {
            segEnd = Math.min(segStart + 1, lineEnd);
        }

        Point p1 = textArea.offsetToXY(segStart);
        if (p1 == null) {
            return;
        }

        int x1 = p1.x;
        int x2 = x1 + textArea.getPainter().getFontMetrics().charWidth('m');
        if (segEnd > segStart) {
            int xyOffset = Math.min(segEnd, buffer.getLength());
            if (xyOffset > 0 && xyOffset == buffer.getLength()) {
                xyOffset--;
            }
            Point p2 = textArea.offsetToXY(xyOffset);
            if (p2 != null) {
                x2 = p2.x;
                if (segEnd > segStart && xyOffset >= segStart) {
                    x2 += textArea.getPainter().getFontMetrics().charWidth('m');
                }
            }
        }
        if (x2 <= x1) {
            x2 = x1 + 2;
        }

        Color severityColor = problem.getSeverity().getColor();
        gfx.setColor(new Color(
            severityColor.getRed(),
            severityColor.getGreen(),
            severityColor.getBlue(),
            BACKGROUND_ALPHA));
        gfx.fillRect(x1, y, x2 - x1, lineHeight);

        int underlineY = y + lineHeight - 2;
        gfx.setColor(severityColor);
        gfx.setStroke(new BasicStroke(UNDERLINE_WIDTH));
        gfx.drawLine(x1, underlineY, x2, underlineY);
    }
}
