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

package org.jedit.git;

import java.io.File;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

final class GitRepository {

    private static final String EXECUTABLE_PROPERTY = "git.executable";

    private GitRepository() {}

    static String executable() {
        String configured = jEdit.getProperty(EXECUTABLE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "git";
    }

    static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder));
        return dir.isDirectory() ? dir : null;
    }

    static File findRoot(File start) {
        if (start == null) {
            return null;
        }
        File current = start.isDirectory() ? start : start.getParentFile();
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    static File resolveRoot(Buffer buffer) {
        File workspace = workspaceRoot();
        if (workspace != null) {
            File fromWorkspace = findRoot(workspace);
            if (fromWorkspace != null) {
                return fromWorkspace;
            }
        }
        if (buffer != null) {
            String path = buffer.getSymlinkPath();
            if (path == null || path.isBlank()) {
                path = buffer.getPath();
            }
            if (path != null && !path.isBlank() && !buffer.isUntitled()) {
                return findRoot(new File(MiscUtilities.resolveSymlinks(path)));
            }
        }
        return workspace != null ? findRoot(workspace) : null;
    }

    static File resolveFile(File repoRoot, String relativePath) {
        if (repoRoot == null || relativePath == null || relativePath.isBlank()) {
            return null;
        }
        File file = new File(repoRoot, relativePath.replace('/', File.separatorChar));
        return file.isFile() ? file : null;
    }
}
