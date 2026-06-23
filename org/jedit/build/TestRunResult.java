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
import java.util.Collections;
import java.util.List;

final class TestRunResult {

    final String title;
    final File projectRoot;
    final ProjectKind kind;
    final int exitCode;
    final List<TestCaseResult> cases;

    TestRunResult(String title, File projectRoot, ProjectKind kind, int exitCode,
                  List<TestCaseResult> cases) {
        this.title = title;
        this.projectRoot = projectRoot;
        this.kind = kind;
        this.exitCode = exitCode;
        this.cases = cases != null ? List.copyOf(cases) : List.of();
    }

    static TestRunResult empty(String title, File projectRoot, ProjectKind kind, int exitCode) {
        return new TestRunResult(title, projectRoot, kind, exitCode, List.of());
    }

    List<TestCaseResult> failedCases() {
        List<TestCaseResult> failed = new ArrayList<>();
        for (TestCaseResult testCase : cases) {
            if (testCase.status == TestCaseStatus.FAILED
                || testCase.status == TestCaseStatus.ERROR) {
                failed.add(testCase);
            }
        }
        return failed;
    }

    int count(TestCaseStatus status) {
        int count = 0;
        for (TestCaseResult testCase : cases) {
            if (testCase.status == status) {
                count++;
            }
        }
        return count;
    }

    int totalCount() {
        return cases.size();
    }

    double totalTimeSeconds() {
        double total = 0;
        for (TestCaseResult testCase : cases) {
            total += testCase.timeSeconds;
        }
        return total;
    }

    List<String> suiteNames() {
        List<String> names = new ArrayList<>();
        String previous = null;
        for (TestCaseResult testCase : cases) {
            String suite = testCase.suiteName();
            if (!suite.equals(previous)) {
                names.add(suite);
                previous = suite;
            }
        }
        return Collections.unmodifiableList(names);
    }

    List<TestCaseResult> casesForSuite(String suiteName) {
        List<TestCaseResult> suiteCases = new ArrayList<>();
        for (TestCaseResult testCase : cases) {
            if (suiteName.equals(testCase.suiteName())) {
                suiteCases.add(testCase);
            }
        }
        return suiteCases;
    }

    List<TestCaseResult> casesForClass(String className) {
        List<TestCaseResult> suiteCases = new ArrayList<>();
        for (TestCaseResult testCase : cases) {
            if (className.equals(testCase.className) || className.equals(testCase.suiteName())) {
                suiteCases.add(testCase);
            }
        }
        return suiteCases;
    }
}
