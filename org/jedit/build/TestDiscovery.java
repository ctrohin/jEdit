/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;

final class TestDiscovery {

    private static final Pattern JAVA_TEST_METHOD = Pattern.compile(
        "(?:public\\s+|protected\\s+|private\\s+)?(?:static\\s+)?void\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_TEST = Pattern.compile(
        "\\b(?:it|test)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DART_TEST = Pattern.compile(
        "\\b(?:test|testWidgets)\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern DART_GROUP = Pattern.compile(
        "\\bgroup\\s*\\(\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern PYTHON_TEST = Pattern.compile(
        "\\bdef\\s+(test_\\w+)\\s*\\(");
    private static final Pattern JAVA_TEST_ANNOTATION = Pattern.compile(
        "@(?:Test|org\\.junit\\.Test|org\\.junit\\.jupiter\\.api\\.Test|ParameterizedTest)\\b");

    private TestDiscovery() {}

    static TestRunResult discoverProject(File projectRoot, ProjectKind kind) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return TestRunResult.empty(jEdit.getProperty("test-results.discover-title"),
                projectRoot, kind, -1);
        }
        List<TestCaseResult> cases = new ArrayList<>();
        Set<File> scanned = new LinkedHashSet<>();
        collectFromTree(projectRoot, kind, cases, scanned, 0);
        String title = jEdit.getProperty("test-results.discover-title");
        return new TestRunResult(title, projectRoot, kind, -1, cases);
    }

    static List<DiscoveredTest> discoverBuffer(Buffer buffer) {
        if (buffer == null) {
            return List.of();
        }
        String path = buffer.getPath();
        if (path == null || path.isBlank()) {
            return List.of();
        }
        File file = new File(path);
        if (!file.isFile()) {
            return List.of();
        }
        String name = file.getName().toLowerCase();
        try {
            String text = buffer.getText(0, buffer.getLength());
            if (name.endsWith(".java")) {
                return discoverJavaFile(file, text);
            }
            if (isJsTestFile(name)) {
                return discoverJsFile(file, text);
            }
            if (name.endsWith("_test.dart")) {
                return discoverDartFile(file, text);
            }
            if (name.startsWith("test_") && name.endsWith(".py")) {
                return discoverPythonFile(file, text);
            }
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private static void collectFromTree(File dir, ProjectKind kind, List<TestCaseResult> cases,
                                        Set<File> scanned, int depth) {
        if (depth > 12 || dir == null || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isFile()) {
                scanFile(child, cases, scanned);
            } else if (child.isDirectory() && !shouldSkip(child)) {
                collectFromTree(child, kind, cases, scanned, depth + 1);
            }
        }
    }

    private static void scanFile(File file, List<TestCaseResult> cases, Set<File> scanned) {
        if (!scanned.add(file)) {
            return;
        }
        String name = file.getName().toLowerCase();
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (name.endsWith(".java") && isJavaTestFile(file)) {
                for (DiscoveredTest test : discoverJavaFile(file, text)) {
                    cases.add(test.toResult(file.getParentFile()));
                }
            } else if (isJsTestFile(name)) {
                for (DiscoveredTest test : discoverJsFile(file, text)) {
                    cases.add(test.toResult(file.getParentFile()));
                }
            } else if (name.endsWith("_test.dart")) {
                for (DiscoveredTest test : discoverDartFile(file, text)) {
                    cases.add(test.toResult(file.getParentFile()));
                }
            } else if (name.startsWith("test_") && name.endsWith(".py")) {
                for (DiscoveredTest test : discoverPythonFile(file, text)) {
                    cases.add(test.toResult(file.getParentFile()));
                }
            } else if (name.endsWith(".py") && isPythonTestFile(file)) {
                for (DiscoveredTest test : discoverPythonFile(file, text)) {
                    cases.add(test.toResult(file.getParentFile()));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isJavaTestFile(File file) {
        String path = file.getPath().replace('\\', '/').toLowerCase();
        return path.contains("/test/")
            || path.contains("/src/test/")
            || file.getName().endsWith("Test.java")
            || file.getName().endsWith("Tests.java");
    }

    private static boolean isJsTestFile(String name) {
        return name.endsWith(".test.js") || name.endsWith(".test.ts")
            || name.endsWith(".test.jsx") || name.endsWith(".test.tsx")
            || name.endsWith(".spec.js") || name.endsWith(".spec.ts")
            || name.endsWith(".spec.jsx") || name.endsWith(".spec.tsx");
    }

    private static List<DiscoveredTest> discoverJavaFile(File file, String text) {
        List<DiscoveredTest> tests = new ArrayList<>();
        String className = javaClassName(file, text);
        String[] lines = text.split("\\R", -1);
        boolean pendingAnnotation = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (JAVA_TEST_ANNOTATION.matcher(line).find()) {
                pendingAnnotation = true;
                continue;
            }
            if (pendingAnnotation || line.contains("@Test")) {
                Matcher matcher = JAVA_TEST_METHOD.matcher(line);
                if (matcher.find()) {
                    tests.add(new DiscoveredTest(className, matcher.group(1), i + 1, file));
                    pendingAnnotation = false;
                    continue;
                }
            }
            if (line.startsWith("@")) {
                continue;
            }
            pendingAnnotation = false;
        }
        return tests;
    }

    private static List<DiscoveredTest> discoverJsFile(File file, String text) {
        List<DiscoveredTest> tests = new ArrayList<>();
        String className = jsSuiteName(file);
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = JS_TEST.matcher(lines[i]);
            if (matcher.find()) {
                tests.add(new DiscoveredTest(className, matcher.group(1), i + 1, file));
            }
        }
        return tests;
    }

    private static List<DiscoveredTest> discoverDartFile(File file, String text) {
        List<DiscoveredTest> tests = new ArrayList<>();
        String className = dartSuiteName(file);
        String[] lines = text.split("\\R", -1);
        List<String> groupStack = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            Matcher groupMatcher = DART_GROUP.matcher(line);
            if (groupMatcher.find()) {
                groupStack.add(groupMatcher.group(1));
                continue;
            }
            if (line.startsWith("});") || line.equals("},);")) {
                if (!groupStack.isEmpty()) {
                    groupStack.remove(groupStack.size() - 1);
                }
                continue;
            }
            Matcher testMatcher = DART_TEST.matcher(line);
            if (testMatcher.find()) {
                String fullName = qualifiedDartName(groupStack, testMatcher.group(1));
                tests.add(new DiscoveredTest(className, fullName, i + 1, file));
            }
        }
        return tests;
    }

    private static List<DiscoveredTest> discoverPythonFile(File file, String text) {
        List<DiscoveredTest> tests = new ArrayList<>();
        String className = pythonSuiteName(file);
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = PYTHON_TEST.matcher(lines[i]);
            if (matcher.find()) {
                tests.add(new DiscoveredTest(className, matcher.group(1), i + 1, file));
            }
        }
        return tests;
    }

    private static String qualifiedDartName(List<String> groups, String testName) {
        if (groups.isEmpty()) {
            return testName;
        }
        StringBuilder name = new StringBuilder();
        for (String group : groups) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(group);
        }
        name.append(' ').append(testName);
        return name.toString();
    }

    private static String javaClassName(File file, String text) {
        Matcher matcher = Pattern.compile("class\\s+(\\w+)").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String jsSuiteName(File file) {
        String name = file.getName();
        for (String suffix : new String[] {
            ".test.js", ".test.ts", ".test.jsx", ".test.tsx",
            ".spec.js", ".spec.ts", ".spec.jsx", ".spec.tsx"
        }) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String dartSuiteName(File file) {
        String name = file.getName();
        if (name.endsWith("_test.dart")) {
            return name.substring(0, name.length() - "_test.dart".length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String pythonSuiteName(File file) {
        String path = file.getPath().replace('\\', '/');
        int testsIndex = path.lastIndexOf("/tests/");
        if (testsIndex >= 0) {
            String module = path.substring(testsIndex + "/tests/".length());
            int dot = module.lastIndexOf('.');
            if (dot > 0) {
                module = module.substring(0, dot);
            }
            return module.replace('/', '.');
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isPythonTestFile(File file) {
        String path = file.getPath().replace('\\', '/').toLowerCase();
        return path.contains("/tests/") || file.getName().startsWith("test_");
    }

    private static boolean shouldSkip(File dir) {
        String name = dir.getName();
        return name.startsWith(".")
            || name.equals("node_modules")
            || name.equals("target")
            || name.equals("build")
            || name.equals("out")
            || name.equals(".dart_tool");
    }

    static final class DiscoveredTest {
        final String className;
        final String methodName;
        final int line;
        final File sourceFile;

        DiscoveredTest(String className, String methodName, int line, File sourceFile) {
            this.className = className;
            this.methodName = methodName;
            this.line = line;
            this.sourceFile = sourceFile;
        }

        TestCaseResult toResult(File projectRoot) {
            return new TestCaseResult(
                className,
                methodName,
                TestCaseStatus.DISCOVERED,
                0,
                "",
                sourceFile,
                line);
        }
    }
}
