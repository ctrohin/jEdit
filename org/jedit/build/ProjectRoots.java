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

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

final class ProjectRoots {

    private ProjectRoots() {}

    static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder));
        return dir.isDirectory() ? dir : null;
    }

    static File findBuildXml(File root) {
        return findNamedFile(root, "build.xml");
    }

    static File findPomXml(File root) {
        if (root == null) {
            return null;
        }
        File pom = new File(root, "pom.xml");
        return pom.isFile() ? pom : null;
    }

    private static File findNamedFile(File root, String name) {
        if (root == null) {
            return null;
        }
        File direct = new File(root, name);
        if (direct.isFile()) {
            return direct;
        }
        File current = root;
        while (current != null) {
            File candidate = new File(current, name);
            if (candidate.isFile()) {
                return candidate;
            }
            current = current.getParentFile();
        }
        return null;
    }
}
