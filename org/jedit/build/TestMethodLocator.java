/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves source line numbers for test methods when reports omit them.
 */
final class TestMethodLocator {

    private static final Pattern JAVA_METHOD = Pattern.compile(
        "(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?void\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_DART_TEST = Pattern.compile(
        "\\b(?:it|test|testWidgets)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PYTHON_DEF = Pattern.compile(
        "\\bdef\\s+(\\w+)\\s*\\(");

    private TestMethodLocator() {}

    static int resolveLine(File sourceFile, String methodName, int hintLine) {
        if (hintLine > 0) {
            return hintLine;
        }
        return findMethodLine(sourceFile, methodName);
    }

    static int findMethodLine(File sourceFile, String methodName) {
        if (sourceFile == null || !sourceFile.isFile()
            || methodName == null || methodName.isBlank()) {
            return 0;
        }
        String text;
        try {
            text = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return 0;
        }
        String name = methodName.trim();
        String shortName = shortTestName(name);
        String[] lines = text.split("\\R", -1);
        String lowerName = name.toLowerCase();
        String lowerFile = sourceFile.getName().toLowerCase();

        if (lowerFile.endsWith(".java") || lowerFile.endsWith(".kt")) {
            return findJavaMethodLine(lines, shortName);
        }
        if (lowerFile.endsWith(".py")) {
            return findPythonMethodLine(lines, shortName);
        }
        if (lowerFile.endsWith(".dart")
            || lowerFile.endsWith(".js") || lowerFile.endsWith(".ts")
            || lowerFile.endsWith(".jsx") || lowerFile.endsWith(".tsx")) {
            return findJsDartTestLine(lines, name, shortName, lowerName);
        }
        return findGenericLine(lines, shortName, lowerName);
    }

    private static int findJavaMethodLine(String[] lines, String methodName) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            Matcher matcher = JAVA_METHOD.matcher(line);
            if (matcher.find() && matcher.group(1).equals(methodName)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int findPythonMethodLine(String[] lines, String methodName) {
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = PYTHON_DEF.matcher(lines[i]);
            if (matcher.find() && matcher.group(1).equals(methodName)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int findJsDartTestLine(String[] lines, String fullName,
                                          String shortName, String lowerFullName) {
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = JS_DART_TEST.matcher(lines[i]);
            if (!matcher.find()) {
                continue;
            }
            String found = matcher.group(1);
            if (found.equals(shortName) || found.equals(fullName)) {
                return i + 1;
            }
            if (lowerFullName.endsWith(" " + found.toLowerCase())) {
                return i + 1;
            }
        }
        return 0;
    }

    private static int findGenericLine(String[] lines, String shortName, String lowerFullName) {
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.contains(shortName)) {
                Matcher matcher = JS_DART_TEST.matcher(trimmed);
                if (matcher.find()) {
                    return i + 1;
                }
                Matcher py = PYTHON_DEF.matcher(trimmed);
                if (py.find() && py.group(1).equals(shortName)) {
                    return i + 1;
                }
            }
            if (lowerFullName.contains(shortName.toLowerCase()) && trimmed.contains(shortName)) {
                return i + 1;
            }
        }
        return 0;
    }

    static String shortTestName(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return "";
        }
        int space = methodName.lastIndexOf(' ');
        return space >= 0 ? methodName.substring(space + 1) : methodName;
    }

    static TestCaseResult enrich(File projectRoot, TestCaseResult testCase) {
        File source = testCase.sourceFile;
        if (source == null || !source.isFile()) {
            source = TestSourceLocator.resolve(projectRoot, testCase.className, testCase.message);
        }
        int line = resolveLine(source, testCase.methodName, testCase.line);
        if (source == testCase.sourceFile && line == testCase.line) {
            return testCase;
        }
        return new TestCaseResult(
            testCase.className,
            testCase.methodName,
            testCase.status,
            testCase.timeSeconds,
            testCase.message,
            source,
            line);
    }

    static List<TestCaseResult> enrichAll(File projectRoot, List<TestCaseResult> cases) {
        List<TestCaseResult> enriched = new ArrayList<>(cases.size());
        for (TestCaseResult testCase : cases) {
            enriched.add(enrich(projectRoot, testCase));
        }
        return enriched;
    }
}
