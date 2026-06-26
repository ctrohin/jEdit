/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gjt.sp.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Collects dependency / package names from the project tree for completion.
 */
final class BuildConfigDependencyIndex {

    private static final ConcurrentHashMap<String, CachedPackages> PACKAGE_CACHE =
        new ConcurrentHashMap<>();

    private static final class CachedPackages {
        final long packageJsonMtime;
        final long nodeModulesMtime;
        final List<String> names;

        CachedPackages(long packageJsonMtime, long nodeModulesMtime, List<String> names) {
            this.packageJsonMtime = packageJsonMtime;
            this.nodeModulesMtime = nodeModulesMtime;
            this.names = names;
        }
    }

    private static final Pattern REQUIREMENTS_PACKAGE = Pattern.compile(
        "^\\s*([A-Za-z0-9][A-Za-z0-9._-]*)");
    private static final Pattern TOML_DEPENDENCY = Pattern.compile(
        "^\\s*\"?([A-Za-z0-9][A-Za-z0-9._-]*)\"?\\s*=");
    private static final Pattern PUBSPEC_DEPENDENCY = Pattern.compile(
        "^\\s{2,}([A-Za-z0-9_]+)\\s*:");
    private static final Pattern LOCK_PACKAGE = Pattern.compile(
        "\"([^\"]+)\"\\s*:");

    private BuildConfigDependencyIndex() {}

    static List<String> npmScriptNames(File projectDir) {
        if (projectDir == null) {
            return List.of();
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.isFile()) {
            return List.of();
        }
        try (Reader reader = new FileReader(packageJson)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return List.of();
            }
            JsonElement scripts = root.getAsJsonObject().get("scripts");
            if (scripts == null || !scripts.isJsonObject()) {
                return List.of();
            }
            Map<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : scripts.getAsJsonObject().entrySet()) {
                sorted.put(entry.getKey(), entry.getValue().getAsString());
            }
            return new ArrayList<>(sorted.keySet());
        } catch (Exception ex) {
            Log.log(Log.WARNING, BuildConfigDependencyIndex.class,
                "Could not parse scripts in " + packageJson, ex);
            return List.of();
        }
    }

    static List<String> packages(BuildConfigLspSupport.Kind kind, File projectDir,
                                 String documentText, String fileName) {
        if (projectDir == null) {
            return List.of();
        }
        if (kind == BuildConfigLspSupport.Kind.NPM) {
            return cachedNpmPackages(projectDir);
        }
        Set<String> names = new LinkedHashSet<>();
        switch (kind) {
            case FLUTTER -> collectFlutter(projectDir, documentText, names);
            case PIP -> collectPip(projectDir, documentText, fileName, names);
            default -> { }
        }
        return new ArrayList<>(names);
    }

    private static List<String> cachedNpmPackages(File projectDir) {
        File packageJson = new File(projectDir, "package.json");
        File nodeModules = new File(projectDir, "node_modules");
        long packageJsonMtime = packageJson.isFile() ? packageJson.lastModified() : 0L;
        long nodeModulesMtime = nodeModules.isDirectory() ? nodeModules.lastModified() : 0L;
        String cacheKey = projectDir.getAbsolutePath();
        CachedPackages cached = PACKAGE_CACHE.get(cacheKey);
        if (cached != null
            && cached.packageJsonMtime == packageJsonMtime
            && cached.nodeModulesMtime == nodeModulesMtime) {
            return cached.names;
        }
        Set<String> names = new LinkedHashSet<>();
        collectNpm(projectDir, names);
        List<String> result = new ArrayList<>(names);
        PACKAGE_CACHE.put(cacheKey,
            new CachedPackages(packageJsonMtime, nodeModulesMtime, result));
        return result;
    }

    private static void collectNpm(File projectDir, Set<String> names) {
        File packageJson = new File(projectDir, "package.json");
        addJsonDependencyKeys(packageJson, names);
        File lock = firstExisting(projectDir, "package-lock.json", "npm-shrinkwrap.json");
        if (lock != null) {
            addJsonLockPackages(lock, names);
        }
        File yarnLock = new File(projectDir, "yarn.lock");
        if (yarnLock.isFile()) {
            addYarnLockPackages(yarnLock, names);
        }
        File nodeModules = new File(projectDir, "node_modules");
        if (nodeModules.isDirectory()) {
            File[] children = nodeModules.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && !child.getName().startsWith(".")) {
                        names.add(child.getName());
                    }
                }
            }
        }
    }

    private static void collectFlutter(File projectDir, String documentText, Set<String> names) {
        File pubspec = firstExisting(projectDir, "pubspec.yaml", "pubspec.yml");
        if (pubspec != null) {
            addYamlDependencyLines(readFile(pubspec), names, PUBSPEC_DEPENDENCY);
        }
        if (documentText != null) {
            addYamlDependencyLines(documentText, names, PUBSPEC_DEPENDENCY);
        }
        File lock = new File(projectDir, "pubspec.lock");
        if (lock.isFile()) {
            for (String line : readFile(lock).split("\n")) {
                Matcher matcher = PUBSPEC_DEPENDENCY.matcher(line);
                if (matcher.find()) {
                    names.add(matcher.group(1));
                }
            }
        }
    }

    private static void collectPip(File projectDir, String documentText, String fileName,
                                   Set<String> names) {
        for (String candidate : new String[] {
            "requirements.txt", "requirements-dev.txt", "constraints.txt"
        }) {
            File file = new File(projectDir, candidate);
            if (file.isFile()) {
                addRequirementsLines(readFile(file), names);
            }
        }
        File pyproject = new File(projectDir, "pyproject.toml");
        if (pyproject.isFile()) {
            addTomlDependencies(readFile(pyproject), names);
        }
        if (documentText != null) {
            if ("requirements.txt".equalsIgnoreCase(fileName)) {
                addRequirementsLines(documentText, names);
            } else if ("pyproject.toml".equalsIgnoreCase(fileName)) {
                addTomlDependencies(documentText, names);
            }
        }
    }

    private static void addJsonDependencyKeys(File packageJson, Set<String> names) {
        if (!packageJson.isFile()) {
            return;
        }
        try (Reader reader = new FileReader(packageJson)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject object = root.getAsJsonObject();
            for (String section : new String[] {
                "dependencies", "devDependencies", "peerDependencies", "optionalDependencies"
            }) {
                JsonElement deps = object.get(section);
                if (deps != null && deps.isJsonObject()) {
                    names.addAll(deps.getAsJsonObject().keySet());
                }
            }
        } catch (Exception ex) {
            Log.log(Log.WARNING, BuildConfigDependencyIndex.class,
                "Could not parse " + packageJson, ex);
        }
    }

    private static void addJsonLockPackages(File lockFile, Set<String> names) {
        try (Reader reader = new FileReader(lockFile)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return;
            }
            JsonObject packages = root.getAsJsonObject().getAsJsonObject("packages");
            if (packages == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : packages.entrySet()) {
                String key = entry.getKey();
                if ("".equals(key)) {
                    continue;
                }
                int slash = key.lastIndexOf("node_modules/");
                names.add(slash >= 0 ? key.substring(slash + "node_modules/".length()) : key);
            }
        } catch (Exception ex) {
            Log.log(Log.WARNING, BuildConfigDependencyIndex.class,
                "Could not parse " + lockFile, ex);
        }
    }

    private static void addYarnLockPackages(File yarnLock, Set<String> names) {
        for (String line : readFile(yarnLock).split("\n")) {
            if (line.isBlank() || line.charAt(0) == ' ' || line.charAt(0) == '#') {
                continue;
            }
            String key = line.trim();
            int at = key.indexOf('@');
            names.add(at > 0 ? key.substring(0, at) : key);
        }
    }

    private static void addRequirementsLines(String text, Set<String> names) {
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.trim().startsWith("#") || line.trim().startsWith("-")) {
                continue;
            }
            Matcher matcher = REQUIREMENTS_PACKAGE.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
    }

    private static void addTomlDependencies(String text, Set<String> names) {
        boolean inDeps = false;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                String section = trimmed.substring(1, trimmed.length() - 1).toLowerCase(Locale.ROOT);
                inDeps = section.equals("project.dependencies")
                    || section.endsWith(".dependencies")
                    || section.equals("tool.poetry.dependencies");
                continue;
            }
            if (!inDeps) {
                continue;
            }
            Matcher matcher = TOML_DEPENDENCY.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
    }

    private static void addYamlDependencyLines(String text, Set<String> names, Pattern pattern) {
        boolean inDeps = false;
        for (String line : text.split("\n")) {
            if (line.trim().equals("dependencies:") || line.trim().equals("dev_dependencies:")
                || line.trim().equals("dependency_overrides:")) {
                inDeps = true;
                continue;
            }
            if (inDeps && !line.startsWith(" ") && !line.isBlank()) {
                inDeps = false;
            }
            if (!inDeps) {
                continue;
            }
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
    }

    private static File firstExisting(File dir, String... names) {
        for (String name : names) {
            File file = new File(dir, name);
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (Exception ex) {
            return "";
        }
    }
}
