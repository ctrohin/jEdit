/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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
import java.awt.Stroke;
import java.util.ArrayList;
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
    private static final Stroke UNDERLINE_STROKE = new BasicStroke(UNDERLINE_WIDTH);

    private static final Color ERROR_BG =
        backgroundTint(LspDiagnosticProblem.Severity.ERROR.getColor());
    private static final Color WARNING_BG =
        backgroundTint(LspDiagnosticProblem.Severity.WARNING.getColor());
    private static final Color INFO_BG =
        backgroundTint(LspDiagnosticProblem.Severity.INFO.getColor());

    @SuppressWarnings("unchecked")
    private static final List<LspDiagnosticProblem>[] EMPTY_INDEX = new List[0];

    private final JEditTextArea textArea;
    private volatile List<LspDiagnosticProblem>[] problemsByLine = EMPTY_INDEX;

    LspDiagnosticHighlight(JEditTextArea textArea) {
        this.textArea = textArea;
    }

    void updateProblems(Buffer buffer) {
        if (buffer == null) {
            problemsByLine = EMPTY_INDEX;
            return;
        }
        updateProblems(buffer,
            LspDiagnosticsHub.getInstance().getProblemsForBuffer(buffer));
    }

    void updateProblems(Buffer buffer, List<LspDiagnosticProblem> problems) {
        if (buffer == null || problems == null || problems.isEmpty()) {
            problemsByLine = EMPTY_INDEX;
            return;
        }
        problemsByLine = indexProblemsByLine(problems, buffer.getLineCount());
    }

    @Override
    public void paintValidLine(Graphics2D gfx, int screenLine,
                               int physicalLine, int start, int end, int y) {
        List<LspDiagnosticProblem>[] index = problemsByLine;
        if (physicalLine < 0 || physicalLine >= index.length) {
            return;
        }

        List<LspDiagnosticProblem> lineProblems = index[physicalLine];
        if (lineProblems == null || lineProblems.isEmpty()) {
            return;
        }

        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return;
        }

        int lineHeight = textArea.getPainter().getLineHeight();
        int lineStartOffset = buffer.getLineStartOffset(physicalLine);
        Stroke oldStroke = gfx.getStroke();
        gfx.setStroke(UNDERLINE_STROKE);
        try {
            for (LspDiagnosticProblem problem : lineProblems) {
                paintProblem(gfx, buffer, problem, screenLine, physicalLine,
                    start, end, y, lineHeight, lineStartOffset);
            }
        } finally {
            gfx.setStroke(oldStroke);
        }
    }

    private void paintProblem(Graphics2D gfx, Buffer buffer,
                              LspDiagnosticProblem problem, int screenLine,
                              int physicalLine, int lineStart, int lineEnd,
                              int y, int lineHeight, int lineStartOffset) {
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

        int x1;
        int x2;
        int diagStartLine = problem.getLine();
        int diagEndLine = problem.getEndLine();
        if (diagStartLine == diagEndLine && physicalLine == diagStartLine) {
            Point p1 = textArea.offsetToXY(physicalLine, segStart - lineStartOffset);
            Point p2 = textArea.offsetToXY(physicalLine, segEnd - lineStartOffset);
            if (p1 == null) {
                return;
            }
            x1 = p1.x;
            x2 = p2 != null ? p2.x : textArea.getWidth();
        } else if (physicalLine == diagStartLine) {
            Point p1 = textArea.offsetToXY(physicalLine, segStart - lineStartOffset);
            if (p1 == null) {
                return;
            }
            x1 = p1.x;
            x2 = textArea.getWidth();
        } else if (physicalLine == diagEndLine) {
            x1 = 0;
            Point p2 = textArea.offsetToXY(physicalLine, segEnd - lineStartOffset);
            x2 = p2 != null ? p2.x : textArea.getWidth();
        } else {
            x1 = 0;
            x2 = textArea.getWidth();
        }

        if (x2 <= x1) {
            x2 = x1 + 2;
        }

        LspDiagnosticProblem.Severity severity = problem.getSeverity();
        gfx.setColor(backgroundFor(severity));
        gfx.fillRect(x1, y, x2 - x1, lineHeight);

        int underlineY = y + lineHeight - 2;
        gfx.setColor(severity.getColor());
        gfx.drawLine(x1, underlineY, x2, underlineY);
    }

    @SuppressWarnings("unchecked")
    private static List<LspDiagnosticProblem>[] indexProblemsByLine(
        List<LspDiagnosticProblem> problems, int lineCount)
    {
        if (lineCount <= 0 || problems.isEmpty()) {
            return EMPTY_INDEX;
        }

        List<LspDiagnosticProblem>[] byLine = new List[lineCount];
        for (LspDiagnosticProblem problem : problems) {
            int startLine = Math.max(0, problem.getLine());
            int endLine = Math.min(problem.getEndLine(), lineCount - 1);
            if (startLine >= lineCount) {
                continue;
            }
            for (int line = startLine; line <= endLine; line++) {
                List<LspDiagnosticProblem> bucket = byLine[line];
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    byLine[line] = bucket;
                }
                bucket.add(problem);
            }
        }
        return byLine;
    }

    private static Color backgroundFor(LspDiagnosticProblem.Severity severity) {
        return switch (severity) {
            case WARNING -> WARNING_BG;
            case INFO -> INFO_BG;
            default -> ERROR_BG;
        };
    }

    private static Color backgroundTint(Color color) {
        return new Color(
            color.getRed(),
            color.getGreen(),
            color.getBlue(),
            BACKGROUND_ALPHA);
    }
}
