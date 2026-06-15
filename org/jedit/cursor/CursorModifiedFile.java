/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.io.File;
import java.util.Objects;

final class CursorModifiedFile {

    final String path;
    final boolean local;

    CursorModifiedFile(String path, boolean local) {
        this.path = normalize(path);
        this.local = local;
    }

    String displayName() {
        if (path == null) {
            return "";
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    File localFile(File workspaceRoot) {
        if (workspaceRoot == null || path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        File file = new File(workspaceRoot, normalized.replace('/', File.separatorChar));
        return file.isFile() ? file : null;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof CursorModifiedFile other && Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }

    @Override
    public String toString() {
        return path != null ? path : "";
    }

    private static String normalize(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim().replace('\\', '/');
        return trimmed.isEmpty() ? null : trimmed;
    }
}
