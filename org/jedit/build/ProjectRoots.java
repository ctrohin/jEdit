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
        File direct = new File(root, "pom.xml");
        return direct.isFile() ? direct : findNamedFile(root, "pom.xml");
    }

    static File findGradleBuild(File root) {
        if (root == null) {
            return null;
        }
        for (String name : new String[] {"build.gradle.kts", "build.gradle"}) {
            File file = new File(root, name);
            if (file.isFile()) {
                return file;
            }
        }
        return findNamedFile(root, "build.gradle.kts", "build.gradle");
    }

    static File findPackageJson(File root) {
        if (root == null) {
            return null;
        }
        File direct = new File(root, "package.json");
        return direct.isFile() ? direct : findNamedFile(root, "package.json");
    }

    static File findPubspecYaml(File root) {
        if (root == null) {
            return null;
        }
        File direct = new File(root, "pubspec.yaml");
        return direct.isFile() ? direct : findNamedFile(root, "pubspec.yaml");
    }

    static File findPythonMarker(File root) {
        if (root == null) {
            return null;
        }
        for (String name : new String[] {
            "requirements.txt", "pyproject.toml", "setup.py", "Pipfile"
        }) {
            File file = new File(root, name);
            if (file.isFile()) {
                return file;
            }
        }
        return findNamedFile(root, "requirements.txt", "pyproject.toml", "setup.py", "Pipfile");
    }

    private static File findNamedFile(File root, String... names) {
        if (root == null) {
            return null;
        }
        for (String name : names) {
            File direct = new File(root, name);
            if (direct.isFile()) {
                return direct;
            }
        }
        File current = root;
        while (current != null) {
            for (String name : names) {
                File candidate = new File(current, name);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
            current = current.getParentFile();
        }
        return null;
    }
}
