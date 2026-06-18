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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

/**
 * Read-only output area with Ctrl+Click (Cmd+Click on macOS) navigation to file links.
 */
final class LinkAwareTextArea extends JTextArea {

    private final View view;
    private final List<FileLink> links = new ArrayList<>();
    private File projectRoot;
    private FileLink hoveredLink;
    private int maxLines = 200;
    private int lineCount;

    LinkAwareTextArea(View view) {
        super();
        this.view = view;
        setDocument(new DefaultStyledDocument());
        setEditable(false);
        setLineWrap(false);
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN,
            jEdit.getIntegerProperty("view.fontsize", 12));
        setFont(mono);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e);
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isNavigateModifier(e)) {
                    return;
                }
                FileLink link = linkAt(e.getPoint().x, e.getPoint().y);
                if (link != null) {
                    FileLinkNavigator.openLink(view, projectRoot, link);
                }
            }
        });
    }

    void setProjectRoot(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    void setMaxLines(int maxLines) {
        this.maxLines = Math.max(1, maxLines);
        trimExcessLines();
    }

    int getMaxLines() {
        return maxLines;
    }

    void clearOutput() {
        setText("");
        links.clear();
        hoveredLink = null;
        lineCount = 0;
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
    }

    static Color errorColor() {
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        return error != null ? error : new Color(0xc62828);
    }

    void appendLine(String line) {
        appendLine(line, null);
    }

    void appendLine(String line, Color color) {
        if (line == null) {
            line = "";
        }
        try {
            int lineStart = getDocument().getLength();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            if (color != null) {
                StyleConstants.setForeground(attrs, color);
            }
            ((StyledDocument) getDocument()).insertString(lineStart, line + "\n", attrs);
            links.addAll(FileLinkParser.parseLine(line, lineStart));
            lineCount++;
            trimExcessLines();
        } catch (BadLocationException ignored) {
        }
    }

    private void trimExcessLines() {
        while (lineCount > maxLines && lineCount > 0) {
            trimFirstLine();
        }
    }

    private void trimFirstLine() {
        if (lineCount <= 0) {
            return;
        }
        try {
            int removeEnd = getLineEndOffset(0);
            if (removeEnd < getDocument().getLength()) {
                removeEnd++;
            }
            replaceRange("", 0, removeEnd);
            List<FileLink> adjusted = new ArrayList<>();
            for (FileLink link : links) {
                if (link.end <= removeEnd) {
                    continue;
                }
                int newStart = Math.max(0, link.start - removeEnd);
                adjusted.add(new FileLink(newStart, link.end - removeEnd,
                    link.path, link.line, link.column));
            }
            links.clear();
            links.addAll(adjusted);
            lineCount--;
        } catch (Exception ignored) {
            lineCount = 0;
            links.clear();
        }
    }

    private void updateHover(MouseEvent e) {
        if (!isNavigateModifier(e)) {
            if (hoveredLink != null) {
                hoveredLink = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            }
            return;
        }
        FileLink link = linkAt(e.getX(), e.getY());
        if (link != hoveredLink) {
            hoveredLink = link;
            setCursor(link != null
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        }
    }

    private FileLink linkAt(int x, int y) {
        int offset = viewToModel2D(new Point2D.Float(x, y));
        if (offset < 0) {
            return null;
        }
        for (FileLink link : links) {
            if (link.contains(offset)) {
                return link;
            }
        }
        return null;
    }

    private static boolean isNavigateModifier(MouseEvent e) {
        return OperatingSystem.isMacOS() ? e.isMetaDown() : e.isControlDown();
    }
}
