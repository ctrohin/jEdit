/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.View;

/**
 * Git helpers for the Cursor integration (review and undo local file changes).
 */
public final class GitCursorBridge {

    private GitCursorBridge() {}

    public static File repositoryFor(File workspace) {
        if (workspace == null || !workspace.isDirectory()) {
            return null;
        }
        File current = workspace;
        while (current != null) {
            if (new File(current, ".git").exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public static List<String> changedPaths(File repoRoot) {
        List<String> paths = new ArrayList<>();
        if (repoRoot == null) {
            return paths;
        }
        GitRunner runner = new GitRunner();
        GitRunner.Result result = runner.run(repoRoot, "status", "--porcelain");
        if (!result.success()) {
            return paths;
        }
        for (GitModels.FileChange change : GitModels.parseStatus(result.output)) {
            paths.add(change.path);
        }
        return paths;
    }

    public static Map<String, String> snapshotFiles(File repoRoot, Iterable<String> paths) {
        Map<String, String> snapshots = new LinkedHashMap<>();
        if (repoRoot == null || paths == null) {
            return snapshots;
        }
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            File file = new File(repoRoot, path.replace('/', File.separatorChar));
            if (!file.isFile()) {
                snapshots.put(path, null);
                continue;
            }
            try {
                snapshots.put(path, Files.readString(file.toPath(), Charset.defaultCharset()));
            } catch (Exception ignored) {
            }
        }
        return snapshots;
    }

    public static String fileAtHead(File repoRoot, String relativePath) {
        if (repoRoot == null || relativePath == null || relativePath.isBlank()) {
            return null;
        }
        GitRunner runner = new GitRunner();
        GitRunner.Result result = runner.run(repoRoot, "show", "HEAD:" + relativePath);
        if (!result.success()) {
            return null;
        }
        return result.output;
    }

    public static void reviewFile(View view, File repoRoot, String relativePath,
                                  Runnable onRepositoryChanged) {
        if (view == null || repoRoot == null || relativePath == null || relativePath.isBlank()) {
            return;
        }
        GitRunner runner = new GitRunner();
        GitRunner.Result result = runner.run(repoRoot, "status", "--porcelain");
        if (!result.success()) {
            return;
        }
        for (GitModels.FileChange change : GitModels.parseStatus(result.output)) {
            if (relativePath.equals(change.path)) {
                GitDiffDialog.show(view, repoRoot, change, runner, onRepositoryChanged);
                return;
            }
        }
        GitModels.FileChange untracked = new GitModels.FileChange('?', '?', relativePath);
        GitDiffDialog.show(view, repoRoot, untracked, runner, onRepositoryChanged);
    }

    public static void revertFiles(File repoRoot, Map<String, String> baselines,
                                   Iterable<String> paths) {
        if (repoRoot == null || paths == null) {
            return;
        }
        GitRunner runner = new GitRunner();
        List<String> checkout = new ArrayList<>();
        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (baselines != null && baselines.containsKey(path)) {
                String baseline = baselines.get(path);
                File file = new File(repoRoot, path.replace('/', File.separatorChar));
                try {
                    if (baseline == null) {
                        Files.deleteIfExists(file.toPath());
                    } else {
                        File parent = file.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }
                        Files.writeString(file.toPath(), baseline, Charset.defaultCharset());
                    }
                } catch (Exception ignored) {
                }
            } else {
                checkout.add(path);
            }
        }
        if (!checkout.isEmpty()) {
            String[] args = new String[checkout.size() + 2];
            args[0] = "checkout";
            args[1] = "--";
            for (int i = 0; i < checkout.size(); i++) {
                args[i + 2] = checkout.get(i);
            }
            runner.run(repoRoot, args);
        }
    }
}
