/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.gjt.sp.jedit.MiscUtilities;

/**
 * Produces LSP {@link CompletionItem}s for build configuration files.
 */
final class BuildConfigCompletionEngine {

    private static final String[] NPM_KEYS = {
        "name", "version", "description", "main", "module", "types", "exports", "scripts",
        "dependencies", "devDependencies", "peerDependencies", "optionalDependencies",
        "bundledDependencies", "workspaces", "engines", "private", "repository", "keywords",
        "author", "license", "bugs", "homepage", "type", "sideEffects"
    };

    private static final String[] PUBSPEC_KEYS = {
        "name", "version", "description", "homepage", "repository", "publish_to", "environment",
        "dependencies", "dev_dependencies", "dependency_overrides", "flutter", "assets",
        "uses-material-design", "fonts", "executables"
    };

    private static final String[] PYPROJECT_KEYS = {
        "build-system", "requires", "build-backend", "project", "name", "version",
        "description", "readme", "requires-python", "license", "authors", "dependencies",
        "optional-dependencies", "scripts", "urls", "tool"
    };

    private static final String[] GRADLE_KEYWORDS = {
        "plugins", "dependencies", "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "classpath", "repositories", "mavenCentral", "google",
        "group", "version", "tasks", "task", "apply", "plugin", "java", "kotlin"
    };

    private static final String[] GRADLE_TASKS = {
        "tasks", "clean", "classes", "test", "check", "build", "assemble", "dependencies",
        "dependencyInsight", "projects", "properties", "help"
    };

    private static final Pattern GRADLE_TASK_PATTERN = Pattern.compile(
        "(?:task|tasks\\.register|tasks\\.create)\\s*[(\"']([^\"'\\s)]+)");

    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"\\\\]*)\"\\s*:\\s*$");
    private static final Pattern JSON_STRING_VALUE = Pattern.compile(
        "\"([^\"\\\\]*)\"\\s*:\\s*\"([^\"]*)$");
    private static final Pattern YAML_KEY = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*:\\s*$");
    private static final Pattern YAML_DEP_VALUE = Pattern.compile(
        "^\\s{2,}([A-Za-z0-9._-]*)\\s*:\\s*.*$");

    private BuildConfigCompletionEngine() {}

    static List<CompletionItem> complete(String uri, String text, Position position,
                                         String projectRoot) {
        if (text == null || position == null) {
            return List.of();
        }
        String path = pathFromUri(uri);
        String fileName = MiscUtilities.getFileName(path);
        BuildConfigLspSupport.Kind kind = BuildConfigLspSupport.detectKindByFileName(fileName);
        if (kind == null) {
            return List.of();
        }

        int offset = offset(text, position);
        String prefix = wordPrefix(text, offset, kind);
        File projectDir = projectRoot != null ? new File(projectRoot) : null;
        if (projectDir == null) {
            File parent = new File(path).getParentFile();
            projectDir = parent;
        }

        Set<String> seen = new LinkedHashSet<>();
        List<CompletionItem> items = new ArrayList<>();

        if (kind == BuildConfigLspSupport.Kind.MAVEN || kind == BuildConfigLspSupport.Kind.ANT) {
            for (String tag : BuildConfigXmlSchemas.xmlTagCandidates(kind, text, offset)) {
                add(items, seen, tag, CompletionItemKind.Property, "XML element");
            }
        }

        switch (kind) {
            case NPM -> addJsonCompletions(text, offset, projectDir, items, seen);
            case FLUTTER -> addYamlCompletions(text, offset, projectDir, items, seen);
            case PIP -> addPipCompletions(text, offset, fileName, projectDir, items, seen);
            case GRADLE -> addGradleCompletions(text, items, seen);
            default -> { }
        }

        if (items.isEmpty() && !prefix.isEmpty()) {
            return List.of();
        }
        if (!prefix.isEmpty()) {
            items.removeIf(item -> item.getLabel() == null
                || !item.getLabel().startsWith(prefix));
        }
        return items;
    }

    private static void addJsonCompletions(String text, int offset, File projectDir,
                                           List<CompletionItem> items, Set<String> seen) {
        String line = lineAt(text, offset);
        String linePrefix = line.substring(0, Math.min(columnAt(text, offset), line.length()));

        Matcher valueMatcher = JSON_STRING_VALUE.matcher(linePrefix);
        if (valueMatcher.find()) {
            String key = valueMatcher.group(1);
            if (isDependencyKey(key)) {
                for (String pkg : BuildConfigDependencyIndex.packages(
                    BuildConfigLspSupport.Kind.NPM, projectDir, text, "package.json")) {
                    add(items, seen, pkg, CompletionItemKind.Module, "npm package");
                }
            }
            return;
        }

        if (linePrefix.matches(".*\"scripts\"\\s*:\\s*\\{[^}]*\"[^\"]*$")) {
            for (String script : BuildConfigDependencyIndex.npmScriptNames(projectDir)) {
                add(items, seen, script, CompletionItemKind.Function, "npm script");
            }
            return;
        }

        if (linePrefix.trim().endsWith("{") || linePrefix.matches(".*\"\\s*$")
            || YAML_KEY.matcher(linePrefix).matches()) {
            for (String key : NPM_KEYS) {
                add(items, seen, "\"" + key + "\"", CompletionItemKind.Property, "package.json field");
            }
        }
    }

    private static void addYamlCompletions(String text, int offset, File projectDir,
                                           List<CompletionItem> items, Set<String> seen) {
        String line = lineAt(text, offset);
        String linePrefix = line.substring(0, Math.min(columnAt(text, offset), line.length()));

        if (inYamlSection(text, offset, "dependencies", "dev_dependencies",
            "dependency_overrides")) {
            Matcher depMatcher = YAML_DEP_VALUE.matcher(linePrefix);
            if (depMatcher.matches()) {
                String partial = depMatcher.group(1);
                for (String pkg : BuildConfigDependencyIndex.packages(
                    BuildConfigLspSupport.Kind.FLUTTER, projectDir, text, "pubspec.yaml")) {
                    if (partial.isEmpty() || pkg.startsWith(partial)) {
                        add(items, seen, pkg, CompletionItemKind.Module, "pub package");
                    }
                }
                return;
            }
        }

        if (linePrefix.isBlank() || !linePrefix.startsWith(" ")) {
            for (String key : PUBSPEC_KEYS) {
                add(items, seen, key + ":", CompletionItemKind.Property, "pubspec field");
            }
        }
    }

    private static void addPipCompletions(String text, int offset, String fileName,
                                          File projectDir, List<CompletionItem> items,
                                          Set<String> seen) {
        String line = lineAt(text, offset);
        String linePrefix = line.substring(0, Math.min(columnAt(text, offset), line.length()));
        if (linePrefix.isBlank() || linePrefix.trim().startsWith("#")) {
            return;
        }

        if ("requirements.txt".equalsIgnoreCase(fileName)
            || "constraints.txt".equalsIgnoreCase(fileName)) {
            String partial = linePrefix.trim();
            int space = partial.indexOf(' ');
            if (space >= 0) {
                partial = partial.substring(0, space);
            }
            for (String pkg : BuildConfigDependencyIndex.packages(
                BuildConfigLspSupport.Kind.PIP, projectDir, text, fileName)) {
                if (partial.isEmpty() || pkg.startsWith(partial)) {
                    add(items, seen, pkg, CompletionItemKind.Module, "Python package");
                }
            }
            return;
        }

        if ("pyproject.toml".equalsIgnoreCase(fileName)) {
            if (inTomlDependencySection(text, offset)) {
                String partial = linePrefix.trim();
                int eq = partial.indexOf('=');
                if (eq >= 0) {
                    partial = partial.substring(0, eq).replace("\"", "").trim();
                }
                for (String pkg : BuildConfigDependencyIndex.packages(
                    BuildConfigLspSupport.Kind.PIP, projectDir, text, fileName)) {
                    if (partial.isEmpty() || pkg.startsWith(partial)) {
                        add(items, seen, pkg, CompletionItemKind.Module, "Python package");
                    }
                }
            } else if (linePrefix.trim().startsWith("[") || linePrefix.isBlank()) {
                for (String key : PYPROJECT_KEYS) {
                    add(items, seen, key, CompletionItemKind.Property, "pyproject field");
                }
            }
        }
    }

    private static void addGradleCompletions(String text, List<CompletionItem> items,
                                             Set<String> seen) {
        for (String keyword : GRADLE_KEYWORDS) {
            add(items, seen, keyword, CompletionItemKind.Keyword, "Gradle DSL");
        }
        for (String task : GRADLE_TASKS) {
            add(items, seen, task, CompletionItemKind.Method, "Gradle task");
        }
        Matcher matcher = GRADLE_TASK_PATTERN.matcher(text);
        while (matcher.find()) {
            add(items, seen, matcher.group(1), CompletionItemKind.Method, "Gradle task");
        }
    }

    private static boolean isDependencyKey(String key) {
        return "dependencies".equals(key) || "devDependencies".equals(key)
            || "peerDependencies".equals(key) || "optionalDependencies".equals(key);
    }

    private static boolean inYamlSection(String text, int offset, String... sectionNames) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        String before = text.substring(0, lineStart);
        for (int i = before.length() - 1; i >= 0; i = before.lastIndexOf('\n', i - 1)) {
            String line = before.substring(Math.max(0, i + 1), before.indexOf('\n', i + 1) >= 0
                ? before.indexOf('\n', i + 1) : before.length()).trim();
            for (String section : sectionNames) {
                if (line.equals(section + ":")) {
                    return true;
                }
            }
            if (!line.isEmpty() && !line.startsWith(" ") && line.endsWith(":")) {
                return false;
            }
        }
        return false;
    }

    private static boolean inTomlDependencySection(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        String before = text.substring(0, lineStart);
        for (int i = before.length() - 1; i >= 0; i = before.lastIndexOf('\n', i - 1)) {
            int end = before.indexOf('\n', i + 1);
            String line = before.substring(i + 1, end >= 0 ? end : before.length()).trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                String section = line.substring(1, line.length() - 1).toLowerCase(Locale.ROOT);
                return section.equals("project.dependencies")
                    || section.endsWith(".dependencies")
                    || section.equals("tool.poetry.dependencies");
            }
        }
        return false;
    }

    private static String pathFromUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return uri;
        }
        if (uri.startsWith("file:")) {
            try {
                return new File(java.net.URI.create(uri)).getPath();
            } catch (Exception ignored) {
                return uri;
            }
        }
        return uri;
    }

    private static String wordPrefix(String text, int offset, BuildConfigLspSupport.Kind kind) {
        int start = offset;
        String extra = switch (kind) {
            case MAVEN, ANT, GRADLE -> "-.:_";
            case NPM -> "-._@";
            case FLUTTER -> "-._";
            case PIP -> "-._";
        };
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (Character.isLetterOrDigit(ch) || extra.indexOf(ch) >= 0
                || ch == '"' || ch == '_') {
                start--;
            } else {
                break;
            }
        }
        return text.substring(start, offset).replace("\"", "");
    }

    private static int offset(String text, Position position) {
        int line = position.getLine();
        int character = position.getCharacter();
        int offset = 0;
        int currentLine = 0;
        while (currentLine < line && offset < text.length()) {
            int next = text.indexOf('\n', offset);
            if (next < 0) {
                return text.length();
            }
            offset = next + 1;
            currentLine++;
        }
        return Math.min(text.length(), offset + character);
    }

    private static String lineAt(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int lineEnd = text.indexOf('\n', offset);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        return text.substring(lineStart, lineEnd);
    }

    private static int columnAt(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        return offset - lineStart;
    }

    private static void add(List<CompletionItem> items, Set<String> seen, String label,
                            CompletionItemKind kind, String detail) {
        if (label == null || label.isBlank() || !seen.add(label)) {
            return;
        }
        CompletionItem item = new CompletionItem();
        item.setLabel(label);
        item.setInsertText(label);
        item.setKind(kind);
        item.setDetail(detail);
        item.setInsertTextFormat(InsertTextFormat.PlainText);
        items.add(item);
    }
}
