/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.ArrayList;
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
    final List<TestCaseResult> failedCases;

    private TestRunOptions(Scope scope, String className, String methodName,
                           List<TestCaseResult> failedCases) {
        this.scope = scope;
        this.className = className != null ? className : "";
        this.methodName = methodName != null ? methodName : "";
        this.failedCases = failedCases != null ? List.copyOf(failedCases) : List.of();
    }

    static TestRunOptions all() {
        return new TestRunOptions(Scope.ALL, "", "", List.of());
    }

    static TestRunOptions suite(String className) {
        return new TestRunOptions(Scope.SUITE, className, "", List.of());
    }

    static TestRunOptions single(String className, String methodName) {
        return new TestRunOptions(Scope.SINGLE, className, methodName, List.of());
    }

    static TestRunOptions failedOnly(List<TestCaseResult> failedCases) {
        return new TestRunOptions(Scope.FAILED_ONLY, "", "", failedCases);
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

    List<String> dartPlainNames() {
        return switch (scope) {
            case ALL -> List.of();
            case SUITE -> List.of();
            case SINGLE -> methodName.isBlank() ? List.of() : List.of(methodName);
            case FAILED_ONLY -> {
                List<String> names = new ArrayList<>();
                for (TestCaseResult testCase : failedCases) {
                    if (testCase.methodName != null && !testCase.methodName.isBlank()) {
                        names.add(testCase.methodName);
                    }
                }
                yield names;
            }
        };
    }

    List<String> pytestNodeIds() {
        return switch (scope) {
            case ALL -> List.of();
            case SUITE -> List.of(pytestNodeId(className, ""));
            case SINGLE -> List.of(pytestNodeId(className, methodName));
            case FAILED_ONLY -> {
                List<String> nodes = new ArrayList<>();
                for (TestCaseResult testCase : failedCases) {
                    nodes.add(pytestNodeId(testCase.className, testCase.methodName));
                }
                yield nodes;
            }
        };
    }

    private static String pytestNodeId(String className, String methodName) {
        if (className == null || className.isBlank()) {
            return methodName != null ? methodName : "";
        }
        String path = className.replace('.', '/') + ".py";
        if (methodName == null || methodName.isBlank()) {
            return path;
        }
        return path + "::" + methodName;
    }

    String displayName() {
        if (methodName.isBlank()) {
            return className;
        }
        return methodName;
    }
}
