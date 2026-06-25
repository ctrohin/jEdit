/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

/**
 * Resolves workspace project folders (primary + additional roots).
 */
public final class ProjectRoots {

    public static final String EXTRA_FOLDERS_KEY = "workspace.extra-folders";

    private ProjectRoots() {}

    public static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder.trim()));
        return dir.isDirectory() ? dir : null;
    }

    public static List<File> workspaceRoots() {
        Set<String> seen = new LinkedHashSet<>();
        List<File> roots = new ArrayList<>();
        File primary = workspaceRoot();
        if (primary != null && seen.add(primary.getAbsolutePath())) {
            roots.add(primary);
        }
        String extra = jEdit.getProperty(EXTRA_FOLDERS_KEY, "");
        if (!extra.isBlank()) {
            for (String part : extra.split(">")) {
                if (part.isBlank()) {
                    continue;
                }
                File dir = new File(MiscUtilities.resolveSymlinks(part.trim()));
                if (dir.isDirectory() && seen.add(dir.getAbsolutePath())) {
                    roots.add(dir);
                }
            }
        }
        return roots;
    }

    public static void addExtraRoot(File folder) {
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        File primary = workspaceRoot();
        if (primary != null && primary.equals(folder)) {
            return;
        }
        List<String> extras = new ArrayList<>();
        String stored = jEdit.getProperty(EXTRA_FOLDERS_KEY, "");
        if (!stored.isBlank()) {
            for (String part : stored.split(">")) {
                if (!part.isBlank()) {
                    extras.add(part.trim());
                }
            }
        }
        String path = folder.getAbsolutePath();
        if (!extras.contains(path)) {
            extras.add(path);
            jEdit.setProperty(EXTRA_FOLDERS_KEY, String.join(">", extras));
        }
    }

    public static void removeExtraRoot(File folder) {
        if (folder == null) {
            return;
        }
        String path = folder.getAbsolutePath();
        List<String> extras = new ArrayList<>();
        String stored = jEdit.getProperty(EXTRA_FOLDERS_KEY, "");
        if (!stored.isBlank()) {
            for (String part : stored.split(">")) {
                if (!part.isBlank() && !path.equals(part.trim())) {
                    extras.add(part.trim());
                }
            }
        }
        if (extras.isEmpty()) {
            jEdit.unsetProperty(EXTRA_FOLDERS_KEY);
        } else {
            jEdit.setProperty(EXTRA_FOLDERS_KEY, String.join(">", extras));
        }
    }
}
