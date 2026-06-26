/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Exposes test failures to the unified Problems view.
 */
public final class TestProblemsExporter {

    private TestProblemsExporter() {}

    public static final class ExportedProblem {
        public final String path;
        public final int line;
        public final String message;

        ExportedProblem(String path, int line, String message) {
            this.path = path;
            this.line = line;
            this.message = message;
        }
    }

    public static List<ExportedProblem> failedTests() {
        TestRunResult result = TestResultsHub.getInstance().getResult();
        List<ExportedProblem> problems = new ArrayList<>();
        for (TestCaseResult testCase : result.cases) {
            if (testCase.status != TestCaseStatus.FAILED
                && testCase.status != TestCaseStatus.ERROR) {
                continue;
            }
            String path = testCase.sourceFile != null
                ? testCase.sourceFile.getAbsolutePath()
                : testCase.className;
            int line = testCase.navigationLine();
            String message = testCase.message != null && !testCase.message.isBlank()
                ? testCase.message
                : testCase.displayName();
            problems.add(new ExportedProblem(path, Math.max(1, line), message));
        }
        return problems;
    }

    public static void addListener(Runnable listener) {
        TestResultsHub.getInstance().addListener(listener);
    }

    public static void removeListener(Runnable listener) {
        TestResultsHub.getInstance().removeListener(listener);
    }
}
