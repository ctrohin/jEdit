/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.gui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.EditorExiting;
import org.gjt.sp.jedit.msg.EditorStarted;
import org.gjt.sp.util.Log;

import javax.swing.SwingUtilities;

/**
 * Persists and restores the set of open files for each workspace folder.
 */
final class WorkspaceOpenFiles implements EBComponent {

    private static final String SUBDIR = "workspace-open-files";
    private static final String KEY_FOLDER = "workspace.folder";
    private static final String KEY_FILES = "open.files";
    private static final String KEY_ACTIVE = "active.file";
    private static final String PATH_SEPARATOR = ">";

    private static volatile boolean registered;
    private static String activeRestoreFolder;

    private WorkspaceOpenFiles() {}

    static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;
        EditBus.addToBus(new WorkspaceOpenFiles());
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof EditorExiting) {
            String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
            if (folder != null && !folder.isBlank()) {
                save(folder);
            }
            return;
        }
        if (message instanceof EditorStarted) {
            SwingUtilities.invokeLater(() -> {
                String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
                if (folder != null && !folder.isBlank()) {
                    restore(jEdit.getActiveView(), folder);
                }
            });
        }
    }

    static void save(String workspaceFolder) {
        String canonical = canonicalFolder(workspaceFolder);
        if (canonical == null) {
            return;
        }
        if (canonical.equals(activeRestoreFolder)) {
            activeRestoreFolder = null;
        }
        List<String> paths = collectOpenPaths();
        String activePath = collectActivePath();
        Properties props = new Properties();
        props.setProperty(KEY_FOLDER, canonical);
        props.setProperty(KEY_FILES, String.join(PATH_SEPARATOR, paths));
        if (activePath != null) {
            props.setProperty(KEY_ACTIVE, activePath);
        }
        try {
            writeSettings(canonical, props);
        } catch (IOException ex) {
            Log.log(Log.WARNING, WorkspaceOpenFiles.class,
                "Could not save open files for " + canonical, ex);
        }
    }

    static void restore(View view, String workspaceFolder) {
        String canonical = canonicalFolder(workspaceFolder);
        if (canonical == null) {
            return;
        }
        if (canonical.equals(activeRestoreFolder)) {
            return;
        }
        SavedOpenFiles saved = load(canonical);
        if (saved == null) {
            saved = new SavedOpenFiles(List.of(), null);
        }
        if (saved.paths.isEmpty()) {
            restoreEmpty(view);
            activeRestoreFolder = canonical;
            return;
        }
        if (view == null) {
            view = jEdit.getActiveView();
        }
        if (view == null) {
            return;
        }

        Set<String> keepPaths = new LinkedHashSet<>(saved.paths);
        for (String path : saved.paths) {
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            jEdit.openFile(view, path);
        }

        List<Buffer> toClose = new ArrayList<>();
        for (Buffer buffer : jEdit.getBufferManager().getBuffers()) {
            if (buffer.isTemporary()) {
                toClose.add(buffer);
                continue;
            }
            if (buffer.isUntitled()) {
                toClose.add(buffer);
                continue;
            }
            String path = normalizePath(buffer.getPath());
            if (path == null || !keepPaths.contains(path)) {
                toClose.add(buffer);
            }
        }
        View closeView = view;
        for (Buffer buffer : toClose) {
            if (buffer.isTemporary()) {
                jEdit._closeBuffer(closeView, buffer);
            } else if (!jEdit.closeBuffer(closeView, buffer)) {
                break;
            }
        }

        if (saved.activePath != null) {
            jEdit.getBufferManager().getBuffer(saved.activePath)
                .ifPresent(view::setBuffer);
        } else if (!saved.paths.isEmpty()) {
            jEdit.getBufferManager().getBuffer(saved.paths.get(0))
                .ifPresent(view::setBuffer);
        }
        activeRestoreFolder = canonical;
    }

    private static void restoreEmpty(View view) {
        if (view == null) {
            view = jEdit.getActiveView();
        }
        if (view == null) {
            return;
        }
        List<Buffer> toClose = new ArrayList<>();
        for (Buffer buffer : jEdit.getBufferManager().getBuffers()) {
            toClose.add(buffer);
        }
        for (Buffer buffer : toClose) {
            if (buffer.isTemporary()) {
                jEdit._closeBuffer(view, buffer);
            } else if (!jEdit.closeBuffer(view, buffer)) {
                break;
            }
        }
        for (EditPane editPane : view.getEditPanes()) {
            if (editPane.getBufferSet().size() == 0) {
                editPane.clearBuffer();
            }
        }
        view.updateTitle();
        view.getStatus().updateCaretStatus();
        view.getStatus().updateBufferStatus();
        view.getStatus().updateMiscStatus();
    }

    private static List<String> collectOpenPaths() {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        View view = jEdit.getActiveView();
        if (view != null) {
            for (Buffer buffer : view.getBuffers()) {
                addBufferPath(paths, buffer);
            }
        }
        for (Buffer buffer : jEdit.getBufferManager().getBuffers()) {
            addBufferPath(paths, buffer);
        }
        return new ArrayList<>(paths);
    }

    private static void addBufferPath(Set<String> paths, Buffer buffer) {
        if (buffer == null || buffer.isTemporary() || buffer.isUntitled()) {
            return;
        }
        String path = normalizePath(buffer.getPath());
        if (path != null) {
            paths.add(path);
        }
    }

    private static String collectActivePath() {
        View view = jEdit.getActiveView();
        if (view == null) {
            return null;
        }
        Buffer buffer = view.getBuffer();
        if (buffer == null || buffer.isTemporary() || buffer.isUntitled()) {
            return null;
        }
        return normalizePath(buffer.getPath());
    }

    private static SavedOpenFiles load(String canonicalFolder) {
        File file = settingsFile(canonicalFolder);
        if (!file.isFile()) {
            return null;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            Log.log(Log.WARNING, WorkspaceOpenFiles.class,
                "Could not read " + file, ex);
            return null;
        }
        if (!canonicalFolder.equals(props.getProperty(KEY_FOLDER, "").trim())) {
            return null;
        }
        String filesValue = props.getProperty(KEY_FILES, "");
        List<String> paths = new ArrayList<>();
        if (!filesValue.isBlank()) {
            for (String path : filesValue.split(PATH_SEPARATOR)) {
                String normalized = normalizePath(path);
                if (normalized != null) {
                    paths.add(normalized);
                }
            }
        }
        String activePath = normalizePath(props.getProperty(KEY_ACTIVE));
        return new SavedOpenFiles(paths, activePath);
    }

    private static void writeSettings(String canonicalFolder, Properties props)
        throws IOException {
        File file = settingsFile(canonicalFolder);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "jEdit workspace open files");
        }
    }

    private static String canonicalFolder(String workspaceFolder) {
        if (workspaceFolder == null || workspaceFolder.isBlank()) {
            return null;
        }
        return MiscUtilities.resolveSymlinks(workspaceFolder.trim());
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return MiscUtilities.resolveSymlinks(path.trim());
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

    private static final class SavedOpenFiles {
        final List<String> paths;
        final String activePath;

        SavedOpenFiles(List<String> paths, String activePath) {
            this.paths = List.copyOf(paths);
            this.activePath = activePath;
        }
    }
}
