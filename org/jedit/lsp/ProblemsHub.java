/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.jedit.build.BuildProblemsHub;
import org.jedit.build.TestProblemsExporter;
import org.gjt.sp.jedit.jEdit;

/**
 * Aggregates LSP diagnostics, build output problems, and test failures.
 */
public final class ProblemsHub {

    public enum Source {
        LSP("lsp-problems.source.lsp"),
        BUILD("lsp-problems.source.build"),
        TEST("lsp-problems.source.test");

        private final String labelProperty;

        Source(String labelProperty) {
            this.labelProperty = labelProperty;
        }

        String label() {
            return jEdit.getProperty(labelProperty);
        }
    }

    public enum SeverityFilter {
        ALL,
        ERRORS,
        WARNINGS
    }

    private static final ProblemsHub INSTANCE = new ProblemsHub();

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile SeverityFilter severityFilter = SeverityFilter.ALL;
    private volatile boolean includeLsp = true;
    private volatile boolean includeBuild = true;
    private volatile boolean includeTests = true;

    private ProblemsHub() {}

    public static ProblemsHub getInstance() {
        return INSTANCE;
    }

    public void setSeverityFilter(SeverityFilter filter) {
        severityFilter = filter != null ? filter : SeverityFilter.ALL;
        notifyListeners();
    }

    public SeverityFilter getSeverityFilter() {
        return severityFilter;
    }

    public void setSourceFilter(boolean lsp, boolean build, boolean tests) {
        includeLsp = lsp;
        includeBuild = build;
        includeTests = tests;
        notifyListeners();
    }

    public List<FileProblems> getFileProblems() {
        Map<String, List<UnifiedProblem>> grouped = new LinkedHashMap<>();
        if (includeLsp) {
            for (LspDiagnosticsHub.FileProblems group
                : LspDiagnosticsHub.getInstance().getFileProblems()) {
                for (LspDiagnosticProblem problem : group.getProblems()) {
                    if (!matchesSeverity(problem.getSeverity())) {
                        continue;
                    }
                    add(grouped, problem.getUri(), UnifiedProblem.fromLsp(problem));
                }
            }
        }
        if (includeBuild) {
            for (BuildProblemsHub.BuildProblem problem
                : BuildProblemsHub.getInstance().getProblems()) {
                if (!matchesSeverity(problem.error)) {
                    continue;
                }
                String uri = LspDocumentUri.pathToUri(problem.path);
                add(grouped, uri, UnifiedProblem.fromBuild(problem));
            }
        }
        if (includeTests) {
            for (TestProblemsExporter.ExportedProblem testCase : TestProblemsExporter.failedTests()) {
                if (!matchesSeverity(true)) {
                    continue;
                }
                String uri = LspDocumentUri.pathToUri(testCase.path);
                add(grouped, uri, UnifiedProblem.fromTest(testCase));
            }
        }

        List<FileProblems> files = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<UnifiedProblem>> entry : grouped.entrySet()) {
            List<UnifiedProblem> problems = new ArrayList<>(entry.getValue());
            problems.sort(Comparator.naturalOrder());
            files.add(new FileProblems(entry.getKey(), problems));
        }
        return files;
    }

    public int countProblems() {
        int count = 0;
        for (FileProblems group : getFileProblems()) {
            count += group.getProblems().size();
        }
        return count;
    }

    public int countErrors() {
        int count = 0;
        for (FileProblems group : getFileProblems()) {
            for (UnifiedProblem problem : group.getProblems()) {
                if (problem.severity == UnifiedProblem.Severity.ERROR) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countWarnings() {
        int count = 0;
        for (FileProblems group : getFileProblems()) {
            for (UnifiedProblem problem : group.getProblems()) {
                if (problem.severity == UnifiedProblem.Severity.WARNING) {
                    count++;
                }
            }
        }
        return count;
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    void notifyListeners() {
        SwingUtilities.invokeLater(() -> {
            for (Runnable listener : listeners) {
                listener.run();
            }
        });
    }

    private static void add(Map<String, List<UnifiedProblem>> grouped,
                            String uri, UnifiedProblem problem) {
        grouped.computeIfAbsent(uri, key -> new ArrayList<>()).add(problem);
    }

    private boolean matchesSeverity(LspDiagnosticProblem.Severity severity) {
        return switch (severityFilter) {
            case ALL -> true;
            case ERRORS -> severity == LspDiagnosticProblem.Severity.ERROR;
            case WARNINGS -> severity == LspDiagnosticProblem.Severity.WARNING;
        };
    }

    private boolean matchesSeverity(boolean error) {
        return switch (severityFilter) {
            case ALL -> true;
            case ERRORS -> error;
            case WARNINGS -> !error;
        };
    }

    public static final class FileProblems {
        private final String uri;
        private final List<UnifiedProblem> problems;

        FileProblems(String uri, List<UnifiedProblem> problems) {
            this.uri = uri;
            this.problems = List.copyOf(problems);
        }

        public String getUri() {
            return uri;
        }

        public List<UnifiedProblem> getProblems() {
            return problems;
        }
    }
}
