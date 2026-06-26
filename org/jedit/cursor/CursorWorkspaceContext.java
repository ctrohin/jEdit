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

package org.jedit.cursor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

final class CursorWorkspaceContext {

    static final class GitHubRepo {
        final String url;
        final String startingRef;

        GitHubRepo(String url, String startingRef) {
            this.url = url;
            this.startingRef = startingRef;
        }
    }

    private CursorWorkspaceContext() {}

    static File workspaceRoot() {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(folder));
        return dir.isDirectory() ? dir : null;
    }

    static String captionText() {
        File root = workspaceRoot();
        if (root == null) {
            return jEdit.getProperty("cursor.no-workspace");
        }
        return jEdit.getProperty("cursor.caption", new String[] { root.getPath() });
    }

    static GitHubRepo findGitHubRepo() {
        File root = workspaceRoot();
        if (root == null) {
            return null;
        }
        File gitRoot = findGitRoot(root);
        if (gitRoot == null) {
            return null;
        }
        String remote = gitCommand(gitRoot, "remote", "get-url", "origin");
        if (remote == null) {
            return null;
        }
        String url = normalizeGitHubUrl(remote);
        if (url == null) {
            return null;
        }
        String branch = gitCommand(gitRoot, "rev-parse", "--abbrev-ref", "HEAD");
        if (branch == null || "HEAD".equals(branch)) {
            branch = "main";
        }
        return new GitHubRepo(url, branch);
    }

    static String buildPromptPrefix(View view, CursorMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(mode == CursorMode.CHAT
            ? jEdit.getProperty("cursor.prompt.chat-intro")
            : jEdit.getProperty("cursor.prompt.agent-intro"));
        sb.append('\n').append('\n');
        sb.append(jEdit.getProperty("cursor.prompt.workspace-header")).append('\n');

        File root = workspaceRoot();
        if (root == null) {
            sb.append(jEdit.getProperty("cursor.no-workspace")).append('\n');
        } else {
            sb.append("Path: ").append(root.getPath()).append('\n');
            appendDirectoryListing(sb, root);
            GitHubRepo repo = findGitHubRepo();
            if (repo != null) {
                sb.append("GitHub: ").append(repo.url);
                if (repo.startingRef != null) {
                    sb.append(" (branch ").append(repo.startingRef).append(')');
                }
                sb.append('\n');
            }
        }

        if (view != null) {
            Buffer buffer = view.getBuffer();
            if (buffer != null && !buffer.isClosed()) {
                String path = buffer.getPath();
                if (path != null && !path.isBlank()) {
                    sb.append("Current file: ").append(path).append('\n');
                }
                String modeName = buffer.getMode() != null ? buffer.getMode().getName() : null;
                if (modeName != null) {
                    sb.append("Editor mode: ").append(modeName).append('\n');
                }
                String selection = view.getTextArea().getSelectedText();
                if (selection != null && !selection.isBlank()) {
                    String trimmed = selection.length() > 4000
                        ? selection.substring(0, 4000) + "\n…"
                        : selection;
                    sb.append("Selected text:\n```\n").append(trimmed).append("\n```\n");
                }
            }
        }

        sb.append('\n');
        sb.append(jEdit.getProperty("cursor.prompt.user-header")).append('\n');
        return sb.toString();
    }

    private static void appendDirectoryListing(StringBuilder sb, File root) {
        String listing = formatDirectoryListing(root);
        if (listing != null && !listing.isEmpty()) {
            sb.append(listing);
        }
    }

    private static volatile String cachedListingRoot;
    private static volatile long cachedListingMtime;
    private static volatile String cachedListing;

    private static String formatDirectoryListing(File root) {
        String path = root.getAbsolutePath();
        long mtime = root.lastModified();
        if (path.equals(cachedListingRoot) && mtime == cachedListingMtime
            && cachedListing != null) {
            return cachedListing;
        }
        File[] children = root.listFiles();
        if (children == null || children.length == 0) {
            cachedListingRoot = path;
            cachedListingMtime = mtime;
            cachedListing = "";
            return "";
        }
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<String> names = new ArrayList<>();
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            names.add(child.isDirectory() ? child.getName() + "/" : child.getName());
            if (names.size() >= 40) {
                names.add("…");
                break;
            }
        }
        String listing = names.isEmpty()
            ? ""
            : "Top-level entries: " + String.join(", ", names) + '\n';
        cachedListingRoot = path;
        cachedListingMtime = mtime;
        cachedListing = listing;
        return listing;
    }

    private static boolean shouldSkip(File file) {
        String name = file.getName();
        return name.startsWith(".")
            || "node_modules".equals(name)
            || "build".equals(name)
            || "out".equals(name)
            || "target".equals(name);
    }

    private static File findGitRoot(File start) {
        File current = start.isDirectory() ? start : start.getParentFile();
        while (current != null) {
            if (new File(current, ".git").exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    private static String gitExecutable() {
        String configured = jEdit.getProperty("git.executable");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "git";
    }

    private static String gitCommand(File gitRoot, String... args) {
        List<String> command = new ArrayList<>();
        command.add(gitExecutable());
        command.addAll(Arrays.asList(args));
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(gitRoot);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            if (process.waitFor() != 0) {
                return null;
            }
            String text = output.toString().trim();
            return text.isEmpty() ? null : text;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeGitHubUrl(String remote) {
        String trimmed = remote.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("git@github.com:")) {
            String path = trimmed.substring("git@github.com:".length());
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            return "https://github.com/" + path;
        }
        if (trimmed.startsWith("https://github.com/")
            || trimmed.startsWith("http://github.com/")) {
            String url = trimmed;
            if (url.endsWith(".git")) {
                url = url.substring(0, url.length() - 4);
            }
            return url;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("github.com")) {
            if (trimmed.endsWith(".git")) {
                trimmed = trimmed.substring(0, trimmed.length() - 4);
            }
            return trimmed.startsWith("http") ? trimmed : "https://" + trimmed;
        }
        return null;
    }
}
