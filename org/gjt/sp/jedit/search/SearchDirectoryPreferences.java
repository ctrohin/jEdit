/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.search;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

/**
 * Remembers the last search directory for each workspace project folder.
 * When no project folder is open, a global settings property is used instead.
 */
final class SearchDirectoryPreferences {

    private static final String GLOBAL_PROPERTY = "search.directory.last";
    private static final String SUBDIR = "workspace-search-directory";
    private static final String KEY_FOLDER = "workspace.folder";
    private static final String KEY_DIRECTORY = "search.directory";

    private SearchDirectoryPreferences() {}

    static String defaultDirectory(View view) {
        File workspace = workspaceRoot();
        if (workspace != null) {
            String saved = loadProjectDirectory(workspace);
            if (isDirectory(saved)) {
                return saved;
            }
            return workspace.getAbsolutePath();
        }
        String global = jEdit.getProperty(GLOBAL_PROPERTY);
        if (isDirectory(global)) {
            return global.trim();
        }
        if (view != null && view.getBuffer() != null) {
            return view.getBuffer().getDirectory();
        }
        return "";
    }

    static void saveDirectory(String directory) {
        String resolved = resolveDirectory(directory);
        if (!isDirectory(resolved)) {
            return;
        }
        File workspace = workspaceRoot();
        if (workspace != null) {
            saveProjectDirectory(workspace, resolved);
        } else {
            jEdit.setProperty(GLOBAL_PROPERTY, resolved);
        }
    }

    private static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder.trim()));
        return dir.isDirectory() ? dir : null;
    }

    private static String loadProjectDirectory(File workspace) {
        String canonical = canonicalFolder(workspace.getAbsolutePath());
        if (canonical == null) {
            return null;
        }
        File file = settingsFile(canonical);
        if (!file.isFile()) {
            return null;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            Log.log(Log.WARNING, SearchDirectoryPreferences.class,
                "Could not read " + file, ex);
            return null;
        }
        if (!canonical.equals(props.getProperty(KEY_FOLDER, "").trim())) {
            return null;
        }
        return props.getProperty(KEY_DIRECTORY, "").trim();
    }

    private static void saveProjectDirectory(File workspace, String directory) {
        String canonical = canonicalFolder(workspace.getAbsolutePath());
        if (canonical == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY_FOLDER, canonical);
        props.setProperty(KEY_DIRECTORY, directory);
        try {
            File file = settingsFile(canonical);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "jEdit workspace search directory");
            }
        } catch (IOException ex) {
            Log.log(Log.WARNING, SearchDirectoryPreferences.class,
                "Could not save search directory for " + canonical, ex);
        }
    }

    private static String canonicalFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }
        return MiscUtilities.resolveSymlinks(folder.trim());
    }

    private static String resolveDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "";
        }
        return MiscUtilities.resolveSymlinks(directory.trim());
    }

    private static boolean isDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return new File(path.trim()).isDirectory();
    }

    private static File settingsFile(String canonicalFolder) {
        String name = new File(canonicalFolder).getName()
            .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isEmpty()) {
            name = "workspace";
        }
        String id = Integer.toHexString(canonicalFolder.hashCode());
        return new File(new File(jEdit.getSettingsDirectory(), SUBDIR),
            name + "-" + id + ".properties");
    }
}
