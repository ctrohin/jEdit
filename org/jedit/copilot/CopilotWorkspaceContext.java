/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;
import org.jedit.cursor.CursorMode;

final class CopilotWorkspaceContext {

    private CopilotWorkspaceContext() {}

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
            return jEdit.getProperty("copilot.no-workspace");
        }
        return jEdit.getProperty("copilot.caption", new String[] { root.getPath() });
    }

    static String defaultCwd() {
        File root = workspaceRoot();
        if (root != null) {
            return root.getAbsolutePath();
        }
        String settings = jEdit.getSettingsDirectory();
        if (settings != null && !settings.isBlank()) {
            return settings;
        }
        return System.getProperty("user.home", ".");
    }

    static String buildPromptPrefix(View view, CursorMode mode) {
        StringBuilder sb = new StringBuilder();
        sb.append(mode == CursorMode.CHAT
            ? jEdit.getProperty("copilot.prompt.chat-intro")
            : jEdit.getProperty("copilot.prompt.agent-intro"));
        sb.append('\n').append('\n');
        sb.append(jEdit.getProperty("copilot.prompt.workspace-header")).append('\n');

        File root = workspaceRoot();
        if (root == null) {
            sb.append(jEdit.getProperty("copilot.no-workspace")).append('\n');
        } else {
            sb.append("Path: ").append(root.getPath()).append('\n');
            appendDirectoryListing(sb, root);
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
        sb.append(jEdit.getProperty("copilot.prompt.user-header")).append('\n');
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
}
