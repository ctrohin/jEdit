/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.io.File;
import java.util.Locale;
import java.util.Set;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;

/**
 * Identifies build / package-manager configuration buffers and their LSP mode.
 */
public final class BuildConfigLspSupport {

    public static final String BUILTIN_EXECUTABLE = "jedit-builtin";

    private static final Set<String> BUILTIN_MODES = Set.of(
        "maven", "ant", "json", "yaml", "gradle", "pip");

    private BuildConfigLspSupport() {}

    public static boolean isBuiltinMode(String modeName) {
        return modeName != null && BUILTIN_MODES.contains(modeName);
    }

    /**
     * Returns the LSP mode for a build-config buffer, or {@code null} if this is not one.
     */
    public static String resolveLspMode(Buffer buffer) {
        Kind kind = detectKind(buffer);
        return kind == null ? null : kind.lspMode;
    }

    public static Kind detectKind(Buffer buffer) {
        if (buffer == null) {
            return null;
        }
        String path = buffer.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return detectKindByFileName(MiscUtilities.getFileName(path));
    }

    public static Kind detectKindByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "pom.xml" -> Kind.MAVEN;
            case "build.xml" -> Kind.ANT;
            case "package.json" -> Kind.NPM;
            case "pubspec.yaml", "pubspec.yml" -> Kind.FLUTTER;
            case "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts" ->
                Kind.GRADLE;
            case "requirements.txt", "pyproject.toml", "pipfile", "setup.py" -> Kind.PIP;
            default -> null;
        };
    }

    public static File projectDirectory(Buffer buffer) {
        String path = buffer.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        File parent = new File(path).getParentFile();
        return parent != null ? parent : null;
    }

    public enum Kind {
        MAVEN("maven"),
        ANT("ant"),
        NPM("json"),
        FLUTTER("yaml"),
        GRADLE("gradle"),
        PIP("pip");

        public final String lspMode;

        Kind(String lspMode) {
            this.lspMode = lspMode;
        }
    }
}
