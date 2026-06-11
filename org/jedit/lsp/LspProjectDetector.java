/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
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

package org.jedit.lsp;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects which LSP language servers are relevant for a project folder.
 */
final class LspProjectDetector {

    private static final int MAX_SCAN_DEPTH = 4;

    private static final Set<String> SKIP_DIRECTORIES = Set.of(
        ".git", ".hg", ".svn", ".idea", ".vscode", ".cursor",
        "node_modules", "target", "build", "dist", "out",
        ".dart_tool", "__pycache__", ".venv", "venv", ".gradle",
        "vendor", "bin", "obj");

    private LspProjectDetector() {}

    static List<LspServerDefinition> detectApplicableServers(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return List.of();
        }

        Set<String> modeNames = new LinkedHashSet<>();
        detectRootMarkers(projectRoot, modeNames);
        scanForSourceFiles(projectRoot, 0, modeNames);

        List<LspServerDefinition> definitions = new ArrayList<>();
        for (String modeName : modeNames) {
            LspServerDefinition definition = LspConfig.getDefinition(modeName);
            if (definition != null) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    static List<LspServerDefinition> detectMissingServers(File projectRoot) {
        List<LspServerDefinition> applicable = detectApplicableServers(projectRoot);
        List<LspServerDefinition> missing = new ArrayList<>();
        for (LspServerDefinition definition : dedupeByExecutable(applicable)) {
            if (!LspConfig.isServerEnabled(definition.getModeName())) {
                continue;
            }
            if (!LspServerInstaller.isServerInstalled(definition)) {
                missing.add(definition);
            }
        }
        return missing;
    }

    static List<LspServerDefinition> dedupeByExecutable(List<LspServerDefinition> definitions) {
        Map<String, LspServerDefinition> byExecutable = new LinkedHashMap<>();
        for (LspServerDefinition definition : definitions) {
            String key = definition.getExecutable().toLowerCase(Locale.ROOT);
            byExecutable.putIfAbsent(key, definition);
        }
        return new ArrayList<>(byExecutable.values());
    }

    private static void detectRootMarkers(File root, Set<String> modeNames) {
        if (exists(root, "pubspec.yaml") || exists(root, "analysis_options.yaml")) {
            modeNames.add("dart");
        }
        if (exists(root, "Cargo.toml")) {
            modeNames.add("rust");
        }
        if (exists(root, "pom.xml")) {
            modeNames.add("maven");
        }
        if (exists(root, "build.xml")) {
            modeNames.add("ant");
        }
        if (exists(root, "pom.xml") || exists(root, "build.gradle")
            || exists(root, "build.gradle.kts") || exists(root, "settings.gradle")
            || exists(root, "settings.gradle.kts") || exists(root, "mvnw")
            || exists(root, "mvnw.cmd")) {
            modeNames.add("java");
            if (exists(root, "build.gradle") || exists(root, "build.gradle.kts")
                || exists(root, "settings.gradle") || exists(root, "settings.gradle.kts")) {
                modeNames.add("gradle");
            }
        }
        if (exists(root, "package.json") || exists(root, "pnpm-lock.yaml")
            || exists(root, "yarn.lock") || exists(root, "bun.lockb")
            || exists(root, "tsconfig.json")) {
            modeNames.add("javascript");
            if (exists(root, "package.json")) {
                modeNames.add("json");
            }
        }
        if (exists(root, "pubspec.yaml") || exists(root, "pubspec.yml")) {
            modeNames.add("yaml");
        }
        if (exists(root, "pyproject.toml") || exists(root, "requirements.txt")
            || exists(root, "setup.py") || exists(root, "setup.cfg")
            || exists(root, "Pipfile") || exists(root, "poetry.lock")) {
            modeNames.add("python");
            modeNames.add("pip");
        }
        if (exists(root, "CMakeLists.txt") || exists(root, "Makefile")
            || exists(root, "compile_commands.json") || exists(root, "meson.build")
            || exists(root, "conanfile.txt") || exists(root, "conanfile.py")) {
            modeNames.add("c");
            modeNames.add("c++");
        }
    }

    private static void scanForSourceFiles(File directory, int depth, Set<String> modeNames) {
        if (depth > MAX_SCAN_DEPTH) {
            return;
        }
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName();
                if (name.startsWith(".") || SKIP_DIRECTORIES.contains(name)) {
                    continue;
                }
                scanForSourceFiles(child, depth + 1, modeNames);
            } else {
                addModeForExtension(child.getName(), modeNames);
            }
        }
    }

    private static void addModeForExtension(String fileName, Set<String> modeNames) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String extension = lower.substring(dot);
        switch (extension) {
            case ".py", ".pyw", ".pyi" -> modeNames.add("python");
            case ".java" -> modeNames.add("java");
            case ".c" -> modeNames.add("c");
            case ".cpp", ".cc", ".cxx", ".hpp", ".hh", ".hxx", ".mm" -> modeNames.add("c++");
            case ".h" -> {
                modeNames.add("c");
                modeNames.add("c++");
            }
            case ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx", ".vue", ".svelte" ->
                modeNames.add("javascript");
            case ".dart" -> modeNames.add("dart");
            case ".rs" -> modeNames.add("rust");
            default -> { }
        }
    }

    private static boolean exists(File root, String name) {
        return new File(root, name).isFile();
    }
}
