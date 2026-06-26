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

package org.jedit.lsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.gjt.sp.jedit.OperatingSystem;
import org.jedit.lsp.buildconfig.BuildConfigLspSupport;
import org.gjt.sp.util.Log;

/**
 * Detects, tests, and installs LSP server executables on the system PATH.
 */
final class LspServerInstaller {

    private static final int COMMAND_TIMEOUT_SECONDS = 120;
    private static final int TEST_TIMEOUT_SECONDS = 15;

    private LspServerInstaller() {}

    static boolean isOnPath(String executable) {
        return findExecutable(executable) != null;
    }

    /**
     * Resolves the first token of a command to an absolute executable path when possible.
     */
    static String[] resolveCommand(String[] command) {
        if (command == null || command.length == 0) {
            return command;
        }
        String resolved = findExecutable(command[0]);
        if (resolved == null || resolved.equals(command[0])) {
            return command;
        }
        String[] resolvedCommand = command.clone();
        resolvedCommand[0] = resolved;
        return resolvedCommand;
    }

    static String findExecutable(String executable) {
        if (executable == null || executable.isBlank()) {
            return null;
        }

        String baseName = stripExtension(new File(executable).getName());
        if (isAbsoluteExecutable(executable)) {
            return normalizeExecutablePath(executable, baseName);
        }

        if ("dart".equalsIgnoreCase(baseName)) {
            String dart = findDartExecutable();
            if (dart != null) {
                return dart;
            }
        }

        String fromPath = findInPathDirectories(baseName);
        if (fromPath != null) {
            return fromPath;
        }

        String fromWhere = queryPathCommand(baseName);
        if (fromWhere != null) {
            return normalizeExecutablePath(fromWhere, baseName);
        }

        if ("dart".equalsIgnoreCase(baseName)) {
            return findDartExecutable();
        }
        return null;
    }

    static boolean isServerInstalled(LspServerDefinition definition) {
        if (definition == null) {
            return false;
        }
        if (BuildConfigLspSupport.isBuiltinMode(definition.getModeName())) {
            return true;
        }
        String[] command = LspConfig.getServerCommand(definition.getModeName());
        if (command != null && command.length > 0) {
            return findExecutable(command[0]) != null;
        }
        return isOnPath(definition.getExecutable());
    }

    /**
     * Returns installation status for every configured language server.
     */
    static Map<String, Boolean> detectAllServers() {
        Map<String, Boolean> installed = new LinkedHashMap<>();
        for (LspServerDefinition definition : LspConfig.getServerDefinitions()) {
            installed.put(definition.getModeName(), isServerInstalled(definition));
        }
        return installed;
    }

    static String formatResolvedCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "";
        }
        String[] parsed = parseCommand(commandLine);
        if (parsed.length == 0) {
            return commandLine;
        }
        String[] resolved = resolveCommand(parsed);
        return LspConfig.commandToString(resolved);
    }

    static String testServer(String[] command) {
        if (command == null || command.length == 0) {
            return "No command configured.";
        }
        String[] resolved = resolveCommand(command);
        String executable = resolved[0];
        String[] testCommand = new String[] {executable, "--version"};

        Process process = null;
        try {
            ProcessBuilder builder = createProcessBuilder(testCommand);
            builder.redirectErrorStream(true);
            process = builder.start();

            boolean finished = process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = readAvailableOutput(process, 8192);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                if (output.isBlank()) {
                    return "Timed out while testing " + executable + ".";
                }
                return output.trim() + "\n\n(timed out after " + TEST_TIMEOUT_SECONDS + "s)";
            }

            int exit = process.exitValue();
            if (output.isBlank()) {
                return exit == 0
                    ? "OK: " + executable
                    : executable + " exited with code " + exit + ".";
            }
            return output.trim();
        } catch (Exception ex) {
            return "Failed to run " + executable + ": " + ex.getMessage();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    static String installServer(LspServerDefinition definition, Consumer<String> log) {
        String[] installCommand = getInstallCommand(definition);
        if (installCommand == null || installCommand.length == 0) {
            return null;
        }

        log.accept("Running: " + LspConfig.commandToString(installCommand));
        try {
            ProcessBuilder builder = createProcessBuilder(installCommand);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.accept(line);
                }
            }
            boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Install timed out.";
            }
            if (process.exitValue() != 0) {
                return "Install exited with code " + process.exitValue() + ".";
            }
            return null;
        } catch (Exception ex) {
            return "Install failed: " + ex.getMessage();
        }
    }

    static String[] getInstallCommand(LspServerDefinition definition) {
        if (definition == null) {
            return null;
        }
        if (OperatingSystem.isWindows()) {
            return definition.getWindowsInstall();
        }
        if (OperatingSystem.isMacOS()) {
            return definition.getMacInstall();
        }
        return definition.getUnixInstall();
    }

    static ProcessBuilder createProcessBuilder(String[] command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Empty command");
        }
        String[] resolved = resolveCommand(command);
        ProcessBuilder builder = wrapWindowsBatchIfNeeded(resolved);
        augmentProcessEnvironment(builder);
        return builder;
    }

    private static ProcessBuilder wrapWindowsBatchIfNeeded(String[] command) {
        if (!OperatingSystem.isWindows() || command.length == 0) {
            return new ProcessBuilder(command);
        }
        String executable = command[0].toLowerCase();
        if (executable.endsWith(".bat") || executable.endsWith(".cmd")) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add("cmd.exe");
            wrapped.add("/c");
            wrapped.addAll(Arrays.asList(command));
            return new ProcessBuilder(wrapped);
        }
        return new ProcessBuilder(command);
    }

    private static void augmentProcessEnvironment(ProcessBuilder builder) {
        Set<String> pathEntries = new LinkedHashSet<>();
        addPathEntries(pathEntries, builder.environment().get("PATH"));
        addPathEntries(pathEntries, builder.environment().get("Path"));
        addPathEntries(pathEntries, System.getenv("PATH"));
        addPathEntries(pathEntries, System.getenv("Path"));
        addWellKnownSdkDirectories(pathEntries);
        if (!pathEntries.isEmpty()) {
            String merged = String.join(File.pathSeparator, pathEntries);
            builder.environment().put("PATH", merged);
            builder.environment().put("Path", merged);
        }
    }

    private static void addPathEntries(Set<String> entries, String pathValue) {
        if (pathValue == null || pathValue.isBlank()) {
            return;
        }
        for (String entry : pathValue.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                entries.add(entry.trim());
            }
        }
    }

    private static void addWellKnownSdkDirectories(Set<String> entries) {
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String userProfile = System.getenv("USERPROFILE");

        if (programFiles != null) {
            entries.add(programFiles + "\\Dart\\dart-sdk\\bin");
        }
        if (localAppData != null) {
            entries.add(localAppData + "\\Dart\\dart-sdk\\bin");
            entries.add(localAppData + "\\flutter\\bin\\cache\\dart-sdk\\bin");
        }
        if (userProfile != null) {
            entries.add(userProfile + "\\flutter\\bin\\cache\\dart-sdk\\bin");
            entries.add(userProfile + "\\scoop\\apps\\dart\\current\\bin");
            entries.add(userProfile + "\\scoop\\shims");
            entries.add(userProfile + "\\.cargo\\bin");
        }
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            entries.add(appData + "\\npm");
        }
        String home = System.getenv("HOME");
        if (home != null) {
            entries.add(home + "/.cargo/bin");
            entries.add(home + "/.local/bin");
        }
    }

    private static String findInPathDirectories(String executable) {
        Set<String> pathEntries = new LinkedHashSet<>();
        addPathEntries(pathEntries, System.getenv("PATH"));
        addPathEntries(pathEntries, System.getenv("Path"));
        addWellKnownSdkDirectories(pathEntries);

        List<String> candidates = new ArrayList<>();
        if (OperatingSystem.isWindows()) {
            if (!executable.toLowerCase().endsWith(".exe")) {
                candidates.add(executable + ".exe");
            }
            candidates.add(executable + ".bat");
            candidates.add(executable + ".cmd");
            candidates.add(executable);
        } else {
            candidates.add(executable);
        }

        for (String directory : pathEntries) {
            for (String candidate : candidates) {
                File file = new File(directory, candidate);
                if (!file.isFile()) {
                    continue;
                }
                String resolved = normalizeExecutablePath(file.getAbsolutePath(), executable);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static String queryPathCommand(String executable) {
        try {
            ProcessBuilder builder;
            if (OperatingSystem.isWindows()) {
                builder = new ProcessBuilder("cmd.exe", "/c", "where", executable);
            } else {
                builder = new ProcessBuilder("sh", "-c", "command -v " + shellQuote(executable));
            }
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = finished ? readAvailableOutput(process, 4096) : "";
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0 || output.isBlank()) {
                return null;
            }
            String firstLine = output.lines().findFirst().orElse("").trim();
            if (firstLine.isEmpty()) {
                return null;
            }
            return normalizeExecutablePath(firstLine, executable);
        } catch (Exception ex) {
            Log.log(Log.DEBUG, LspServerInstaller.class,
                "PATH lookup failed for " + executable, ex);
            return null;
        }
    }

    private static String findDartExecutable() {
        for (String candidate : dartExecutableCandidates()) {
            File file = new File(candidate);
            if (file.isFile()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private static List<String> dartExecutableCandidates() {
        List<String> candidates = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        String programFiles = System.getenv("ProgramFiles");
        String userProfile = System.getenv("USERPROFILE");
        String flutterRoot = System.getenv("FLUTTER_ROOT");

        if (flutterRoot != null && !flutterRoot.isBlank()) {
            candidates.add(flutterRoot + "\\bin\\cache\\dart-sdk\\bin\\dart.exe");
        }
        if (programFiles != null) {
            candidates.add(programFiles + "\\Dart\\dart-sdk\\bin\\dart.exe");
        }
        if (localAppData != null) {
            candidates.add(localAppData + "\\Dart\\dart-sdk\\bin\\dart.exe");
            candidates.add(localAppData + "\\flutter\\bin\\cache\\dart-sdk\\bin\\dart.exe");
        }
        if (userProfile != null) {
            candidates.add(userProfile + "\\flutter\\bin\\cache\\dart-sdk\\bin\\dart.exe");
            candidates.add(userProfile + "\\scoop\\apps\\dart\\current\\bin\\dart.exe");
        }

        Set<String> pathEntries = new LinkedHashSet<>();
        addPathEntries(pathEntries, System.getenv("PATH"));
        addPathEntries(pathEntries, System.getenv("Path"));
        for (String directory : pathEntries) {
            String normalized = directory.replace('\\', '/').toLowerCase();
            if (normalized.endsWith("/flutter/bin")) {
                candidates.add(directory + "\\cache\\dart-sdk\\bin\\dart.exe");
            }
        }
        return candidates;
    }

    private static String normalizeExecutablePath(String path, String baseName) {
        if (path == null || path.isBlank()) {
            return null;
        }

        File file = new File(path);
        if (!file.isFile() && OperatingSystem.isWindows()
            && !path.toLowerCase().endsWith(".exe")) {
            File exeCandidate = new File(path + ".exe");
            if (exeCandidate.isFile()) {
                file = exeCandidate;
            }
        }
        if (!file.isFile()) {
            return null;
        }

        if ("dart".equalsIgnoreCase(baseName)) {
            String flutterDart = resolveFlutterBundledDart(file);
            if (flutterDart != null) {
                return flutterDart;
            }
        }

        if (OperatingSystem.isWindows()) {
            String lowerName = file.getName().toLowerCase();
            if (lowerName.endsWith(".bat") || lowerName.endsWith(".cmd")) {
                return file.getAbsolutePath();
            }
            if (isWindowsNativeExecutable(file)) {
                return file.getAbsolutePath();
            }
            return null;
        }
        return file.getAbsolutePath();
    }

    private static String resolveFlutterBundledDart(File dartFile) {
        File binDir = dartFile.getParentFile();
        if (binDir == null || !"bin".equalsIgnoreCase(binDir.getName())) {
            return null;
        }
        File flutterRoot = binDir.getParentFile();
        if (flutterRoot == null) {
            return null;
        }
        File cachedDart = new File(flutterRoot, "bin/cache/dart-sdk/bin/dart.exe");
        if (cachedDart.isFile()) {
            return cachedDart.getAbsolutePath();
        }
        return null;
    }

    private static boolean isWindowsNativeExecutable(File file) {
        if (!file.isFile()) {
            return false;
        }
        if (file.getName().toLowerCase().endsWith(".exe")) {
            return true;
        }
        try (var input = new java.io.FileInputStream(file)) {
            byte[] header = new byte[2];
            if (input.read(header) != 2) {
                return false;
            }
            return header[0] == 'M' && header[1] == 'Z';
        } catch (IOException ex) {
            return false;
        }
    }

    private static boolean isAbsoluteExecutable(String executable) {
        File file = new File(executable);
        return file.isAbsolute();
    }

    private static String stripExtension(String executable) {
        int index = executable.lastIndexOf('.');
        if (index > 0 && executable.indexOf(File.separatorChar) < index) {
            return executable.substring(0, index);
        }
        return executable;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static String[] parseCommand(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return new String[0];
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            if (inQuotes) {
                if (ch == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                inQuotes = true;
                quoteChar = ch;
            } else if (Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens.toArray(String[]::new);
    }

    private static String readAvailableOutput(Process process, int maxChars)
        throws IOException, InterruptedException {
        InputStream stream = process.getInputStream();
        StringBuilder output = new StringBuilder();
        byte[] buffer = new byte[1024];
        long deadline = System.currentTimeMillis() + 2000;

        while (output.length() < maxChars && System.currentTimeMillis() < deadline) {
            int available = stream.available();
            if (available > 0) {
                int n = stream.read(buffer, 0, Math.min(buffer.length,
                    Math.min(available, maxChars - output.length())));
                if (n <= 0) {
                    break;
                }
                output.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                continue;
            }
            if (!process.isAlive()) {
                int n = stream.read(buffer, 0, Math.min(buffer.length, maxChars - output.length()));
                if (n <= 0) {
                    break;
                }
                output.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
                continue;
            }
            Thread.sleep(50);
        }

        if (output.length() > maxChars) {
            return output.substring(0, maxChars) + "...";
        }
        return output.toString();
    }
}
