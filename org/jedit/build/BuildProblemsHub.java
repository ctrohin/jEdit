/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

/**
 * Compiler and tool errors extracted from build output lines.
 */
public final class BuildProblemsHub {

    private static final BuildProblemsHub INSTANCE = new BuildProblemsHub();

    private final List<BuildProblem> problems = new ArrayList<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private BuildProblemsHub() {}

    public static BuildProblemsHub getInstance() {
        return INSTANCE;
    }

    public void clear() {
        synchronized (problems) {
            if (problems.isEmpty()) {
                return;
            }
            problems.clear();
        }
        notifyListeners();
    }

    public void collectFromOutput(File projectRoot, String output) {
        List<BuildProblem> collected = parseOutput(projectRoot, output);
        synchronized (problems) {
            problems.clear();
            problems.addAll(collected);
        }
        notifyListeners();
    }

    public List<BuildProblem> getProblems() {
        synchronized (problems) {
            return List.copyOf(problems);
        }
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        SwingUtilities.invokeLater(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    private static List<BuildProblem> parseOutput(File projectRoot, String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<BuildProblem> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!looksLikeProblemLine(line)) {
                continue;
            }
            List<FileLink> links = FileLinkParser.parseLine(line, 0);
            for (FileLink link : links) {
                String key = link.path + ":" + link.line + ":" + line.trim();
                if (!seen.add(key)) {
                    continue;
                }
                File file = resolveFile(projectRoot, link.path);
                result.add(new BuildProblem(
                    file != null ? file.getAbsolutePath() : link.path,
                    Math.max(1, link.line),
                    Math.max(1, link.column),
                    summarize(line),
                    isErrorLine(line)));
            }
        }
        return result;
    }

    private static boolean looksLikeProblemLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("error")
            || lower.contains("failed")
            || lower.contains("failure")
            || lower.contains("exception")
            || lower.contains("warning:");
    }

    private static boolean isErrorLine(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("warning")) {
            return false;
        }
        return lower.contains("error")
            || lower.contains("failed")
            || lower.contains("failure")
            || lower.contains("exception");
    }

    private static String summarize(String line) {
        String trimmed = line.trim();
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
    }

    private static File resolveFile(File projectRoot, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path.trim());
        if (file.isFile()) {
            return file;
        }
        if (projectRoot != null) {
            File relative = new File(projectRoot, path.trim());
            if (relative.isFile()) {
                return relative;
            }
        }
        return null;
    }

    public static final class BuildProblem {
        public final String path;
        public final int line;
        public final int column;
        public final String message;
        public final boolean error;

        BuildProblem(String path, int line, int column, String message, boolean error) {
            this.path = path;
            this.line = line;
            this.column = column;
            this.message = message;
            this.error = error;
        }
    }
}
