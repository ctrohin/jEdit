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
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.util.ThreadUtilities;

/**
 * Cached git status for buffer tabs. All git commands run off the EDT.
 */
public final class GitBufferTabStatus implements EBComponent {

    private static GitBufferTabStatus instance;

    private final Map<String, Map<String, GitModels.FileChange>> repoChanges =
        new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private int refreshGeneration;

    public static GitBufferTabStatus getInstance() {
        if (instance == null) {
            instance = new GitBufferTabStatus();
            EditBus.addToBus(instance);
            GitBlameSupport.install();
            GitMergeConflictSupport.install();
        }
        return instance;
    }

    private GitBufferTabStatus() {}

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Looks up git status for a buffer from the cache. Safe on the EDT.
     */
    public GitModels.FileChange lookup(Buffer buffer) {
        if (buffer == null || buffer.isUntitled()) {
            return null;
        }
        File repoRoot = GitRepository.resolveRoot(buffer);
        if (repoRoot == null) {
            return null;
        }
        String relativePath = relativeGitPath(repoRoot, bufferFile(buffer));
        if (relativePath == null) {
            return null;
        }
        Map<String, GitModels.FileChange> changes = repoChanges.get(repoKey(repoRoot));
        if (changes == null) {
            return null;
        }
        GitModels.FileChange change = changes.get(relativePath);
        if (change != null) {
            return change;
        }
        if (isWindows()) {
            for (Map.Entry<String, GitModels.FileChange> entry : changes.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(relativePath)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public void requestRefresh(Buffer... buffers) {
        if (buffers == null || buffers.length == 0) {
            return;
        }
        Set<File> repos = new HashSet<>();
        for (Buffer buffer : buffers) {
            if (buffer == null) {
                continue;
            }
            File repoRoot = GitRepository.resolveRoot(buffer);
            if (repoRoot != null) {
                repos.add(repoRoot);
            }
        }
        requestRefreshRepos(repos);
    }

    public void requestRefreshRepo(File repoRoot) {
        if (repoRoot == null) {
            return;
        }
        requestRefreshRepos(Set.of(repoRoot));
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof GitStatusChanged statusChanged) {
            File repoRoot = statusChanged.getRepoRoot();
            if (repoRoot != null) {
                requestRefreshRepo(repoRoot);
            } else {
                requestRefreshRepos(Set.copyOf(repoChanges.keySet().stream()
                    .map(File::new)
                    .toList()));
            }
            return;
        }
        if (message instanceof GitHeadChanged
            || message instanceof ProjectFolderOpened
            || message instanceof ProjectFolderClosed) {
            refreshKnownRepos();
            return;
        }
        if (message instanceof BufferUpdate bufferUpdate
            && bufferUpdate.getWhat() == BufferUpdate.SAVED) {
            Buffer buffer = bufferUpdate.getBuffer();
            if (buffer != null) {
                requestRefresh(buffer);
            }
        }
    }

    private void refreshKnownRepos() {
        Set<File> repos = new HashSet<>();
        for (String key : repoChanges.keySet()) {
            repos.add(new File(key));
        }
        File workspaceRepo = GitRepository.findRoot(GitRepository.workspaceRoot());
        if (workspaceRepo != null) {
            repos.add(workspaceRepo);
        }
        requestRefreshRepos(repos);
    }

    private void requestRefreshRepos(Set<File> repos) {
        if (repos.isEmpty()) {
            return;
        }
        int generation = ++refreshGeneration;
        Set<File> repoCopy = Set.copyOf(repos);
        ThreadUtilities.runInBackground(() -> {
            Map<String, Map<String, GitModels.FileChange>> updates = new HashMap<>();
            GitRunner runner = new GitRunner();
            for (File repo : repoCopy) {
                if (repo == null) {
                    continue;
                }
                String key = repoKey(repo);
                GitRunner.Result version = runner.run(repo, "--version");
                if (!version.success()) {
                    updates.put(key, Map.of());
                    continue;
                }
                List<GitModels.FileChange> changes = GitRepositoryLoader.loadChanges(repo, runner);
                Map<String, GitModels.FileChange> byPath = new HashMap<>();
                for (GitModels.FileChange change : changes) {
                    byPath.put(change.path, change);
                }
                updates.put(key, byPath);
            }
            SwingUtilities.invokeLater(() -> applyUpdates(generation, updates));
        });
    }

    private void applyUpdates(int generation, Map<String, Map<String, GitModels.FileChange>> updates) {
        if (generation != refreshGeneration) {
            return;
        }
        for (Map.Entry<String, Map<String, GitModels.FileChange>> entry : updates.entrySet()) {
            repoChanges.put(entry.getKey(), entry.getValue());
        }
        fireListeners();
    }

    private void fireListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static File bufferFile(Buffer buffer) {
        String path = buffer.getSymlinkPath();
        if (path == null || path.isBlank()) {
            path = buffer.getPath();
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        return new File(MiscUtilities.resolveSymlinks(path));
    }

    static String relativeGitPath(File repoRoot, File file) {
        if (repoRoot == null || file == null) {
            return null;
        }
        try {
            String repo = repoRoot.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (!path.startsWith(repo)) {
                return null;
            }
            String relative = path.substring(repo.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            if (relative.isEmpty()) {
                return null;
            }
            return relative.replace(File.separatorChar, '/');
        } catch (IOException e) {
            return null;
        }
    }

    private static String repoKey(File repoRoot) {
        return repoRoot.getAbsolutePath();
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }
}
