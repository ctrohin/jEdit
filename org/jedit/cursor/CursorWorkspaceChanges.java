/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.gjt.sp.jedit.View;
import org.jedit.git.GitCursorBridge;

public final class CursorWorkspaceChanges {

    private CursorWorkspaceChanges() {}

    public static void beginRun(CursorConversation conversation, File workspace) {
        conversation.modifiedFiles.clear();
        conversation.undoBaselines.clear();
        conversation.runStartDirtyPaths.clear();
        conversation.runStartSnapshots.clear();

        if (conversation.mode != CursorMode.AGENT) {
            return;
        }
        File repoRoot = GitCursorBridge.repositoryFor(workspace);
        if (repoRoot == null) {
            return;
        }
        List<String> dirtyPaths = GitCursorBridge.changedPaths(repoRoot);
        conversation.runStartDirtyPaths.addAll(dirtyPaths);
        Map<String, String> snapshots = GitCursorBridge.snapshotFiles(repoRoot, dirtyPaths);
        conversation.runStartSnapshots.putAll(snapshots);
        conversation.undoBaselines.putAll(snapshots);
    }

    public static void noteToolPath(CursorConversation conversation, String path, File workspace) {
        if (path == null || path.isBlank()) {
            return;
        }
        String relative = toWorkspaceRelative(path, workspace);
        boolean local = relative != null && new File(workspace, relative).isFile();
        conversation.addModifiedFile(new CursorModifiedFile(
            relative != null ? relative : path, local));
        File repoRoot = GitCursorBridge.repositoryFor(workspace);
        if (repoRoot != null) {
            ensureUndoBaseline(conversation, repoRoot, relative != null ? relative : path);
        }
    }

    public static void syncRunChanges(CursorConversation conversation, File workspace) {
        File repoRoot = GitCursorBridge.repositoryFor(workspace);
        if (repoRoot == null) {
            return;
        }
        for (String path : GitCursorBridge.changedPaths(repoRoot)) {
            if (changedDuringRun(conversation, repoRoot, path)) {
                conversation.addModifiedFile(new CursorModifiedFile(path, true));
                ensureUndoBaseline(conversation, repoRoot, path);
            }
        }
    }

    static void undoAll(View view, CursorConversation conversation, File workspace) {
        File repoRoot = GitCursorBridge.repositoryFor(workspace);
        Set<String> paths = new LinkedHashSet<>();
        for (CursorModifiedFile file : conversation.modifiedFiles) {
            if (file.path != null) {
                paths.add(file.path);
            }
        }
        if (repoRoot != null && !paths.isEmpty()) {
            GitCursorBridge.revertFiles(repoRoot, conversation.undoBaselines, paths);
        }
        conversation.modifiedFiles.clear();
        conversation.undoBaselines.clear();
        conversation.runStartDirtyPaths.clear();
        conversation.runStartSnapshots.clear();
    }

    static void reviewFile(View view, CursorConversation conversation, File workspace,
                           CursorModifiedFile file, Runnable onChanged) {
        if (file == null) {
            return;
        }
        File repoRoot = GitCursorBridge.repositoryFor(workspace);
        String path = file.path;
        if (repoRoot != null) {
            GitCursorBridge.reviewFile(view, repoRoot, path, onChanged);
            return;
        }
        File local = file.localFile(workspace);
        if (local != null) {
            org.gjt.sp.jedit.jEdit.openFile(view, local.getAbsolutePath());
        }
    }

    private static boolean changedDuringRun(CursorConversation conversation, File repoRoot,
                                          String path) {
        if (!conversation.runStartDirtyPaths.contains(path)) {
            return true;
        }
        String atRunStart = conversation.runStartSnapshots.get(path);
        String current = currentContent(repoRoot, path);
        return !Objects.equals(atRunStart, current);
    }

    private static String currentContent(File repoRoot, String path) {
        Map<String, String> snapshot = GitCursorBridge.snapshotFiles(repoRoot, List.of(path));
        return snapshot.get(path);
    }

    private static void ensureUndoBaseline(CursorConversation conversation, File repoRoot,
                                           String path) {
        if (path == null || path.isBlank() || conversation.undoBaselines.containsKey(path)) {
            return;
        }
        if (conversation.runStartSnapshots.containsKey(path)) {
            conversation.undoBaselines.put(path, conversation.runStartSnapshots.get(path));
            return;
        }
        conversation.undoBaselines.put(path, GitCursorBridge.fileAtHead(repoRoot, path));
    }

    private static String toWorkspaceRelative(String path, File workspace) {
        if (path == null || workspace == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        String root = workspace.getAbsolutePath().replace('\\', '/');
        if (normalized.startsWith(root)) {
            String relative = normalized.substring(root.length());
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return relative.isEmpty() ? null : relative;
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        File candidate = new File(workspace, normalized.replace('/', File.separatorChar));
        return candidate.isFile() ? normalized : null;
    }
}
