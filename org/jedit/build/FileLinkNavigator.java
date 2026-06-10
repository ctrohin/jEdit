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

import java.io.File;
import java.util.Hashtable;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

final class FileLinkNavigator {

    private FileLinkNavigator() {}

    static void openLink(View view, File projectRoot, FileLink link) {
        if (view == null || link == null) {
            return;
        }
        File file = resolveFile(projectRoot, link.path);
        if (file == null || !file.isFile()) {
            return;
        }

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Buffer.CARET, Integer.valueOf(0));
        props.put(Buffer.CARET_POSITIONED, Boolean.TRUE);

        Buffer buffer = jEdit.openFile(view, null, file.getAbsolutePath(), false, props);
        if (buffer == null) {
            return;
        }

        int line = Math.max(0, link.line - 1);
        int column = Math.max(0, link.column - 1);
        if (line >= buffer.getLineCount()) {
            line = Math.max(0, buffer.getLineCount() - 1);
        }
        int lineStart = buffer.getLineStartOffset(line);
        int lineEnd = buffer.getLineEndOffset(line);
        int offset = Math.min(lineStart + column, lineEnd);

        buffer.setIntegerProperty(Buffer.CARET, offset);
        buffer.setBooleanProperty(Buffer.CARET_POSITIONED, true);
        buffer.unsetProperty(Buffer.SCROLL_VERT);

        if (view.getBuffer() == buffer) {
            view.getEditPane().loadCaretInfo();
            view.getTextArea().requestFocus();
        }
        view.toFront();
        view.requestFocus();
    }

    private static File resolveFile(File projectRoot, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(normalizePath(path));
        if (file.isFile()) {
            return file;
        }
        if (projectRoot != null) {
            File relative = new File(projectRoot, path);
            if (relative.isFile()) {
                return relative;
            }
        }
        return file.isAbsolute() ? file : null;
    }

    private static String normalizePath(String path) {
        path = path.trim();
        if (path.length() >= 3 && path.charAt(0) == '/'
                && Character.isLetter(path.charAt(1)) && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return MiscUtilities.resolveSymlinks(path);
    }
}
