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

import java.awt.Color;
import java.awt.Font;

import org.gjt.sp.jedit.Buffer;

/**
 * Formats buffer tab titles with git status and dirty markers.
 * Git lookups use a cached snapshot only; no git commands run on the EDT.
 */
public final class GitBufferTabLabels {

    private GitBufferTabLabels() {}

    public static String formatTabTitle(Buffer buffer) {
        if (buffer == null) {
            return "";
        }
        return formatTabTitle(buffer, GitBufferTabStatus.getInstance().lookup(buffer));
    }

    public static String formatTabToolTip(Buffer buffer) {
        if (buffer == null) {
            return null;
        }
        return formatTabToolTip(buffer, GitBufferTabStatus.getInstance().lookup(buffer));
    }

    static String formatTabToolTip(Buffer buffer, GitModels.FileChange change) {
        String path = buffer.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String text = path;
        if (buffer.isBackup()) {
            text = String.format("(backup file?) %s", text);
        }
        if (change == null) {
            return text;
        }
        return text + "  " + change.statusLabel();
    }

    static String formatTabTitle(Buffer buffer, GitModels.FileChange change) {
        String name = buffer.getName();
        if (buffer.isDirty()) {
            name = "*" + name;
        }
        if (change == null) {
            return name;
        }
        Color foreground = GitColors.changeForeground(change.kind());
        if (foreground == null) {
            return change.statusLabel() + "  " + name;
        }
        String status = escapeHtml(change.statusLabel());
        String color = toCssColor(foreground);
        int fontStyle = Font.PLAIN;
        if (change.isStaged() || change.kind() == GitModels.ChangeKind.CONFLICT) {
            fontStyle = Font.BOLD;
        }
        String weight = fontStyle == Font.BOLD ? ";font-weight:bold" : "";
        return "<html><span style='color:" + color + weight + "'>"
            + status + "</span>  " + escapeHtml(name) + "</html>";
    }

    private static String toCssColor(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
