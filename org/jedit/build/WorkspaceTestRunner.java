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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

/**
 * Runs project tests through existing build tool integrations and collects
 * structured reports into {@link TestResultsHub}.
 */
public final class WorkspaceTestRunner {

    private WorkspaceTestRunner() {}

    public static boolean canRunTests(File projectRoot) {
        return !detectTestKinds(projectRoot).isEmpty();
    }

    public static List<ProjectKind> detectTestKinds(File projectRoot) {
        List<ProjectKind> kinds = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return kinds;
        }
        if (ProjectRoots.findPomXml(projectRoot) != null) {
            kinds.add(ProjectKind.MAVEN);
        }
        if (ProjectRoots.findGradleBuild(projectRoot) != null) {
            kinds.add(ProjectKind.GRADLE);
        }
        if (hasAntTestTarget(projectRoot)) {
            kinds.add(ProjectKind.ANT);
        }
        if (ProjectRoots.findPackageJson(projectRoot) != null) {
            kinds.add(ProjectKind.NPM);
        }
        ProjectKind dartKind = DartProjectSupport.resolvePubspecKind(projectRoot);
        if (dartKind != null) {
            kinds.add(dartKind);
        }
        if (ProjectRoots.findPythonMarker(projectRoot) != null && hasPythonTests(projectRoot)) {
            kinds.add(ProjectKind.PIP);
        }
        return kinds;
    }

    public static ProjectKind resolveTestKind(File projectRoot) {
        ProjectKind active = WorkspaceProjectRunner.resolveActiveKind(projectRoot);
        if (active != null && supportsStructuredResults(active)) {
            return active;
        }
        List<ProjectKind> kinds = detectTestKinds(projectRoot);
        return kinds.isEmpty() ? null : kinds.get(0);
    }

    public static void runTests(View view, File projectRoot) {
        runTests(view, projectRoot, null, TestRunOptions.all());
    }

    public static void runTests(View view, File projectRoot, ProjectKind kind) {
        runTests(view, projectRoot, kind, TestRunOptions.all());
    }

    public static void runTests(View view, File projectRoot, TestRunOptions options) {
        runTests(view, projectRoot, null, options);
    }

    public static void rerunFailed(View view, File projectRoot) {
        TestRunResult current = TestResultsHub.getInstance().getResult();
        List<TestCaseResult> failed = current.failedCases();
        if (failed.isEmpty()) {
            return;
        }
        runTests(view, projectRoot, current.kind, TestRunOptions.failedOnly(failed));
    }

    public static void discoverTests(View view, File projectRoot) {
        if (view == null || projectRoot == null) {
            return;
        }
        ProjectKind kind = resolveTestKind(projectRoot);
        TestResultsView.show(view);
        TestRunResult result = TestDiscovery.discoverProject(projectRoot, kind);
        TestResultsHub.getInstance().setResult(result);
    }

    public static void runTests(View view, File projectRoot, ProjectKind kind,
                                TestRunOptions options) {
        if (view == null || projectRoot == null) {
            return;
        }
        TestRunOptions effectiveOptions = options != null ? options : TestRunOptions.all();
        ProjectKind effectiveKind = kind != null ? kind : resolveTestKind(projectRoot);
        if (effectiveKind == null) {
            return;
        }
        if (effectiveOptions.scope == TestRunOptions.Scope.FAILED_ONLY
            && effectiveOptions.failedCases.isEmpty()) {
            return;
        }
        TestInvocation invocation = buildTestInvocation(
            projectRoot, effectiveKind, effectiveOptions);
        if (invocation == null) {
            return;
        }
        String title = formatTitle(effectiveKind, effectiveOptions);
        if (effectiveOptions.scope != TestRunOptions.Scope.FAILED_ONLY) {
            TestResultsHub.getInstance().clear();
        }
        TestResultsView.show(view);
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(title, invocation.workingDir, invocation.command, invocation.environment,
            exitCode -> {
                TestRunResult result = TestReportCollector.collect(
                    projectRoot, effectiveKind, title, exitCode);
                TestResultsHub.getInstance().setResult(result);
            });
    }

    static boolean supportsStructuredResults(ProjectKind kind) {
        return switch (kind) {
            case MAVEN, GRADLE, ANT, NPM, DART, FLUTTER, PIP -> true;
            default -> false;
        };
    }

    private static String formatTitle(ProjectKind kind, TestRunOptions options) {
        String base = jEdit.getProperty("test-results.run-title",
            new Object[] {WorkspaceProjectRunner.kindLabel(kind)});
        return switch (options.scope) {
            case SINGLE -> base + " - " + options.displayName();
            case SUITE -> base + " - " + options.className;
            case FAILED_ONLY -> jEdit.getProperty("test-results.rerun-failed-title", new Object[] {base});
            default -> base;
        };
    }

    private static TestInvocation buildTestInvocation(File projectRoot, ProjectKind kind,
                                                      TestRunOptions options) {
        return switch (kind) {
            case MAVEN -> buildMavenTests(projectRoot, options);
            case GRADLE -> buildGradleTests(projectRoot, options);
            case ANT -> buildAntTests(projectRoot, options);
            case NPM -> buildNpmTests(projectRoot, options);
            case DART -> buildDartTests(projectRoot, options);
            case FLUTTER -> buildFlutterTests(projectRoot, options);
            case PIP -> buildPipTests(projectRoot, options);
            default -> null;
        };
    }

    private static TestInvocation buildMavenTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findPomXml(projectRoot));
        if (dir == null) {
            return null;
        }
        MavenProjectSettings settings = MavenProjectPreferences.load(projectRoot).copy();
        settings.skipTests = false;
        String goal = buildMavenGoal(options);
        MavenCommandBuilder.Invocation inv = MavenCommandBuilder.build(dir, settings, goal);
        return new TestInvocation(dir, inv.command, inv.environment);
    }

    private static String buildMavenGoal(TestRunOptions options) {
        if (!options.hasSelectors()) {
            return "test";
        }
        String joined = String.join(",", options.mavenTestSelectors());
        return "-Dtest=" + joined + " test";
    }

    private static TestInvocation buildGradleTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findGradleBuild(projectRoot));
        if (dir == null) {
            return null;
        }
        GradleProjectSettings settings = GradleProjectPreferences.load(projectRoot);
        String task = buildGradleTask(options);
        GradleCommandBuilder.Invocation inv = GradleCommandBuilder.build(dir, settings, task);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static String buildGradleTask(TestRunOptions options) {
        if (!options.hasSelectors()) {
            return "test";
        }
        StringBuilder task = new StringBuilder("test");
        for (String selector : options.gradleTestSelectors()) {
            task.append(" --tests ").append(selector);
        }
        return task.toString();
    }

    private static TestInvocation buildAntTests(File projectRoot, TestRunOptions options) {
        AntProjectSettings settings = AntProjectPreferences.load(projectRoot);
        File buildXml = AntCommandBuilder.resolveConfiguredBuildFile(projectRoot, settings);
        if (buildXml == null) {
            return null;
        }
        String target = resolveAntTestTarget(buildXml);
        if (target == null) {
            return null;
        }
        AntCommandBuilder.Invocation inv = AntCommandBuilder.build(buildXml, settings, target);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static TestInvocation buildNpmTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findPackageJson(projectRoot));
        if (dir == null) {
            return null;
        }
        File reportsDir = TestReportCollector.jeditTestReportsDir(projectRoot);
        reportsDir.mkdirs();
        String goal = buildNpmTestGoal(projectRoot, dir, options, reportsDir);
        NpmCommandBuilder.Invocation inv = NpmCommandBuilder.build(
            dir, NpmProjectPreferences.load(projectRoot), goal);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static String buildNpmTestGoal(File projectRoot, File dir,
                                           TestRunOptions options, File reportsDir) {
        File packageJson = new File(dir, "package.json");
        StringBuilder goal = new StringBuilder("test -- ");
        if (NpmTestFramework.usesVitest(packageJson)) {
            goal.append("--reporter=junit --outputFile=")
                .append(reportsDir.getAbsolutePath())
                .append(File.separator)
                .append("junit.xml");
        } else {
            goal.append("--json --outputFile=")
                .append(reportsDir.getAbsolutePath())
                .append(File.separator)
                .append("jest-results.json");
        }
        appendNpmSelectors(projectRoot, goal, options);
        return goal.toString();
    }

    private static void appendNpmSelectors(File projectRoot, StringBuilder goal,
                                         TestRunOptions options) {
        switch (options.scope) {
            case SINGLE -> {
                if (!options.methodName.isBlank()) {
                    goal.append(" -t ").append(shellQuote(options.methodName));
                } else if (!options.className.isBlank()) {
                    goal.append(" -t ").append(shellQuote(options.className));
                }
            }
            case SUITE -> {
                File suiteFile = resolveJsSuiteFile(projectRoot, options.className);
                if (suiteFile != null) {
                    goal.append(' ').append(relativePath(projectRoot, suiteFile));
                }
            }
            case FAILED_ONLY -> {
                for (TestCaseResult testCase : options.failedCases) {
                    String pattern = testCase.methodName != null && !testCase.methodName.isBlank()
                        ? testCase.methodName
                        : testCase.className;
                    if (pattern != null && !pattern.isBlank()) {
                        goal.append(" -t ").append(shellQuote(pattern));
                    }
                }
            }
            default -> { }
        }
    }

    private static TestInvocation buildDartTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
        if (dir == null) {
            return null;
        }
        File reportsDir = TestReportCollector.jeditTestReportsDir(projectRoot);
        reportsDir.mkdirs();
        String goal = buildDartTestGoal(projectRoot, options, reportsDir);
        FlutterProjectSettings settings = FlutterProjectPreferences.load(projectRoot).copy();
        settings.useFlutterCli = false;
        FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(dir, settings, goal);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static TestInvocation buildFlutterTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
        if (dir == null) {
            return null;
        }
        File reportsDir = TestReportCollector.jeditTestReportsDir(projectRoot);
        reportsDir.mkdirs();
        String goal = buildDartTestGoal(projectRoot, options, reportsDir);
        FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(
            dir, FlutterProjectPreferences.load(projectRoot), goal);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static String buildDartTestGoal(File projectRoot, TestRunOptions options,
                                            File reportsDir) {
        String reportPath = new File(reportsDir, "dart-results.json").getAbsolutePath();
        StringBuilder goal = new StringBuilder("test --file-reporter=json:");
        goal.append(reportPath);
        switch (options.scope) {
            case SINGLE -> {
                if (!options.methodName.isBlank()) {
                    goal.append(" --plain-name ").append(shellQuote(options.methodName));
                }
            }
            case SUITE -> {
                File suiteFile = resolveDartTestFile(projectRoot, options.className);
                if (suiteFile != null) {
                    goal.append(' ').append(relativePath(projectRoot, suiteFile));
                }
            }
            case FAILED_ONLY -> {
                for (String plainName : options.dartPlainNames()) {
                    goal.append(" --plain-name ").append(shellQuote(plainName));
                }
            }
            default -> { }
        }
        return goal.toString();
    }

    private static TestInvocation buildPipTests(File projectRoot, TestRunOptions options) {
        File marker = ProjectRoots.findPythonMarker(projectRoot);
        if (marker == null) {
            return null;
        }
        File dir = projectDirectory(marker);
        File reportsDir = TestReportCollector.jeditTestReportsDir(projectRoot);
        reportsDir.mkdirs();
        String junitPath = new File(reportsDir, "pytest-junit.xml").getAbsolutePath();
        StringBuilder goal = new StringBuilder("-m pytest --junitxml=");
        goal.append(junitPath);
        List<String> nodeIds = options.pytestNodeIds();
        for (String nodeId : nodeIds) {
            if (nodeId != null && !nodeId.isBlank()) {
                goal.append(' ').append(nodeId);
            }
        }
        PipProjectSettings settings = PipProjectPreferences.load(projectRoot);
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(settings.pythonHome)) {
            environment.put("PYTHONHOME", settings.pythonHome.trim());
        }
        if (!ShellCommands.isBlank(settings.virtualEnv)) {
            environment.put("VIRTUAL_ENV", settings.virtualEnv.trim());
        }
        String python = ShellCommands.isBlank(settings.pythonExecutable)
            ? "python"
            : settings.pythonExecutable.trim();
        List<String> args = new ArrayList<>();
        ShellCommands.appendTokens(args, goal.toString());
        ShellCommands.appendTokens(args, settings.additionalArgs);
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            dir = new File(settings.workingDirectory.trim());
        }
        return new TestInvocation(dir, ShellCommands.wrapLauncher(python, args), environment);
    }

    private static boolean hasPythonTests(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return false;
        }
        if (new File(projectRoot, "pytest.ini").isFile()
            || new File(projectRoot, "conftest.py").isFile()) {
            return true;
        }
        return findPythonTestFile(projectRoot, 0) != null;
    }

    private static File findPythonTestFile(File dir, int depth) {
        if (depth > 8 || dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isFile()) {
                String name = child.getName();
                if (name.startsWith("test_") && name.endsWith(".py")) {
                    return child;
                }
            } else if (child.isDirectory() && !shouldSkip(child)) {
                if (child.getName().equals("tests")) {
                    return child;
                }
                File found = findPythonTestFile(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static File resolveDartTestFile(File projectRoot, String className) {
        if (projectRoot == null || className == null || className.isBlank()) {
            return null;
        }
        String base = className.replace('.', File.separatorChar);
        File candidate = TestSourceLocator.resolve(projectRoot, className, "");
        if (candidate != null) {
            return candidate;
        }
        File named = new File(projectRoot, base + "_test.dart");
        if (named.isFile()) {
            return named;
        }
        File testDir = new File(projectRoot, "test");
        File inTest = new File(testDir, className + "_test.dart");
        return inTest.isFile() ? inTest : null;
    }

    private static File resolveJsSuiteFile(File projectRoot, String className) {
        if (projectRoot == null || className == null || className.isBlank()) {
            return null;
        }
        for (String suffix : new String[] {
            ".test.js", ".test.ts", ".test.jsx", ".test.tsx",
            ".spec.js", ".spec.ts", ".spec.jsx", ".spec.tsx"
        }) {
            File candidate = findNamedTestFile(projectRoot, className + suffix, 0);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static File findNamedTestFile(File dir, String name, int depth) {
        if (depth > 8 || dir == null || !dir.isDirectory()) {
            return null;
        }
        File direct = new File(dir, name);
        if (direct.isFile()) {
            return direct;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            File found = findNamedTestFile(child, name, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String relativePath(File projectRoot, File file) {
        if (projectRoot == null || file == null) {
            return file != null ? file.getName() : "";
        }
        try {
            String root = projectRoot.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.startsWith(root)) {
                String relative = path.substring(root.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.replace('\\', '/');
            }
        } catch (Exception ignored) {
        }
        return file.getAbsolutePath().replace('\\', '/');
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "") + "\"";
    }

    private static boolean hasAntTestTarget(File projectRoot) {
        AntProjectSettings settings = AntProjectPreferences.load(projectRoot);
        File buildXml = AntCommandBuilder.resolveConfiguredBuildFile(projectRoot, settings);
        return buildXml != null && resolveAntTestTarget(buildXml) != null;
    }

    private static String resolveAntTestTarget(File buildXml) {
        AntBuildFile parsed = AntBuildFile.parse(buildXml);
        if (parsed == null) {
            return null;
        }
        for (String candidate : List.of("test", "junit", "unittest", "run-tests")) {
            if (parsed.targets.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static File projectDirectory(File markerFile) {
        return markerFile != null ? markerFile.getParentFile() : null;
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

    static final class TestInvocation {
        final File workingDir;
        final List<String> command;
        final Map<String, String> environment;

        TestInvocation(File workingDir, List<String> command, Map<String, String> environment) {
            this.workingDir = workingDir;
            this.command = command;
            this.environment = environment;
        }
    }
}
