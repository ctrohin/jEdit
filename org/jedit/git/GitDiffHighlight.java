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

package org.jedit.git;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Stroke;

import javax.swing.JLayer;
import javax.swing.JScrollBar;

import org.gjt.sp.jedit.textarea.JEditEmbeddedTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

final class GitDiffHighlight extends TextAreaExtension {

    private static final Color INSERTED_BORDER = new Color(34, 139, 34);
    private static final Color DELETED_BORDER = new Color(178, 34, 34);
    private static final float BORDER_WIDTH = 2f;
    private static final Stroke BORDER_STROKE = new BasicStroke(BORDER_WIDTH);

    private final JEditEmbeddedTextArea textArea;
    private final boolean leftSide;
    private volatile GitLineDiff.Result diff = new GitLineDiff.Result(0, 0);

    GitDiffHighlight(JEditEmbeddedTextArea textArea, boolean leftSide) {
        this.textArea = textArea;
        this.leftSide = leftSide;
    }

    void setDiff(GitLineDiff.Result diff) {
        this.diff = diff != null ? diff : new GitLineDiff.Result(0, 0);
        repaintAll();
    }

    void repaintAll() {
        int lineCount = textArea.getBuffer().getLineCount();
        if (lineCount > 0) {
            textArea.invalidateLineRange(0, lineCount - 1);
        }
        textArea.getPainter().repaint();
    }

    @Override
    public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,
                               int start, int end, int y) {
        GitLineDiff.Result current = diff;
        boolean changed = leftSide
            ? current.isLeftChanged(physicalLine)
            : current.isRightChanged(physicalLine);
        if (!changed) {
            return;
        }
        int width = textArea.getPainter().getWidth();
        int lineHeight = textArea.getPainter().getLineHeight();
        int inset = 1;
        int rectWidth = Math.max(0, width - (2 * inset) - 1);
        int rectHeight = Math.max(0, lineHeight - (2 * inset) - 1);

        Color previousColor = gfx.getColor();
        Stroke previousStroke = gfx.getStroke();
        gfx.setColor(leftSide ? DELETED_BORDER : INSERTED_BORDER);
        gfx.setStroke(BORDER_STROKE);
        gfx.drawRect(inset, y + inset, rectWidth, rectHeight);
        gfx.setStroke(previousStroke);
        gfx.setColor(previousColor);
    }

    static void install(JEditEmbeddedTextArea textArea, GitDiffHighlight highlight) {
        textArea.getPainter().addExtension(TextAreaPainter.BRACKET_HIGHLIGHT_LAYER, highlight);
    }

    static JScrollBar findVerticalScrollBar(JEditEmbeddedTextArea area) {
        return findScrollBar(area, JScrollBar.VERTICAL);
    }

    private static JScrollBar findScrollBar(Container container, int orientation) {
        for (Component child : container.getComponents()) {
            Component current = child;
            if (current instanceof JLayer<?> layer) {
                current = layer.getView();
            }
            if (current instanceof JScrollBar bar && bar.getOrientation() == orientation) {
                return bar;
            }
            if (current instanceof Container nested) {
                JScrollBar found = findScrollBar(nested, orientation);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
