/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class CursorToolCallFiles {

    private static final String[] PATH_KEYS = {
        "path",
        "file_path",
        "target_file",
        "relative_workspace_path",
        "file",
        "filepath"
    };

    private CursorToolCallFiles() {}

    public static boolean isMutatingTool(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        String lower = toolName.toLowerCase();
        if (isReadOnlyTool(lower)) {
            return false;
        }
        return lower.contains("write")
            || lower.contains("edit")
            || lower.contains("replace")
            || lower.contains("patch")
            || lower.contains("create")
            || lower.contains("delete")
            || lower.contains("remove")
            || lower.contains("apply")
            || lower.contains("strreplace")
            || lower.contains("insert")
            || lower.contains("touch");
    }

    public static boolean isDirectoryPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.trim().replace('\\', '/');
        return normalized.endsWith("/");
    }

    public static String extractPath(String toolName, JsonObject args) {
        if (args == null) {
            return null;
        }
        for (String key : PATH_KEYS) {
            if (args.has(key) && args.get(key).isJsonPrimitive()) {
                String path = args.get(key).getAsString();
                if (path != null && !path.isBlank()) {
                    return path;
                }
            }
        }
        if (toolName != null) {
            String lower = toolName.toLowerCase();
            if (lower.contains("file") || lower.contains("write") || lower.contains("edit")
                || lower.contains("replace") || lower.contains("patch")) {
                for (String key : args.keySet()) {
                    JsonElement value = args.get(key);
                    if (value != null && value.isJsonPrimitive()) {
                        String text = value.getAsString();
                        if (looksLikePath(text)) {
                            return text;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isReadOnlyTool(String lowerToolName) {
        return lowerToolName.contains("read")
            || lowerToolName.contains("grep")
            || lowerToolName.contains("search")
            || lowerToolName.contains("list")
            || lowerToolName.contains("glob")
            || lowerToolName.contains("find")
            || lowerToolName.contains("fetch")
            || lowerToolName.contains("http")
            || lowerToolName.contains("web")
            || lowerToolName.contains("view")
            || lowerToolName.contains("cat")
            || lowerToolName.contains("head")
            || lowerToolName.contains("tail")
            || lowerToolName.contains("stat")
            || lowerToolName.contains("diff");
    }

    private static boolean looksLikePath(String text) {
        if (text == null || text.isBlank() || text.length() > 260) {
            return false;
        }
        return text.contains("/") || text.contains("\\")
            || text.endsWith(".java") || text.endsWith(".xml") || text.endsWith(".props")
            || text.endsWith(".md") || text.endsWith(".txt") || text.endsWith(".gradle")
            || text.endsWith(".kt") || text.endsWith(".ts") || text.endsWith(".js");
    }
}
