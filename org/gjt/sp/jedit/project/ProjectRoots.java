/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.io.File;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

/**
 * Resolves the workspace project folder.
 */
public final class ProjectRoots {

    private ProjectRoots() {}

    public static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder.trim()));
        return dir.isDirectory() ? dir : null;
    }
}
