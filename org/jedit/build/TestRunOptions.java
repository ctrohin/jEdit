/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TestRunOptions {

    enum Scope {
        ALL,
        SUITE,
        SINGLE,
        FAILED_ONLY
    }

    final Scope scope;
    final String className;
    final String methodName;
    final boolean debug;
    final List<TestCaseResult> failedCases;

    private TestRunOptions(Scope scope, String className, String methodName,
                           boolean debug, List<TestCaseResult> failedCases) {
        this.scope = scope;
        this.className = className != null ? className : "";
        this.methodName = methodName != null ? methodName : "";
        this.debug = debug;
        this.failedCases = failedCases != null ? List.copyOf(failedCases) : List.of();
    }

    static TestRunOptions all() {
        return new TestRunOptions(Scope.ALL, "", "", false, List.of());
    }

    static TestRunOptions suite(String className) {
        return new TestRunOptions(Scope.SUITE, className, "", false, List.of());
    }

    static TestRunOptions single(String className, String methodName) {
        return new TestRunOptions(Scope.SINGLE, className, methodName, false, List.of());
    }

    static TestRunOptions single(String className, String methodName, boolean debug) {
        return new TestRunOptions(Scope.SINGLE, className, methodName, debug, List.of());
    }

    static TestRunOptions failedOnly(List<TestCaseResult> failedCases) {
        return new TestRunOptions(Scope.FAILED_ONLY, "", "", false, failedCases);
    }

    static TestRunOptions debugAll() {
        return new TestRunOptions(Scope.ALL, "", "", true, List.of());
    }

    static TestRunOptions debugSuite(String className) {
        return new TestRunOptions(Scope.SUITE, className, "", true, List.of());
    }

    List<String> mavenTestSelectors() {
        return selectors('#');
    }

    List<String> gradleTestSelectors() {
        return selectors('.');
    }

    private List<String> selectors(char methodSeparator) {
        return switch (scope) {
            case ALL -> List.of();
            case SUITE -> List.of(className);
            case SINGLE -> {
                if (methodName.isBlank()) {
                    yield List.of(className);
                }
                yield List.of(className + methodSeparator + methodName);
            }
            case FAILED_ONLY -> {
                List<String> selectors = new ArrayList<>();
                for (TestCaseResult testCase : failedCases) {
                    if (testCase.className == null || testCase.className.isBlank()) {
                        continue;
                    }
                    if (testCase.methodName == null || testCase.methodName.isBlank()) {
                        selectors.add(testCase.className);
                    } else {
                        selectors.add(testCase.className + methodSeparator + testCase.methodName);
                    }
                }
                yield selectors;
            }
        };
    }

    boolean hasSelectors() {
        return !mavenTestSelectors().isEmpty();
    }

    String displayName() {
        if (methodName.isBlank()) {
            return className;
        }
        return methodName;
    }
}
