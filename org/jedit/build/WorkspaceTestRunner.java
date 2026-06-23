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
        if (ProjectRoots.findPubspecYaml(projectRoot) != null) {
            kinds.add(ProjectKind.FLUTTER);
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
            case MAVEN, GRADLE, ANT, NPM -> true;
            default -> false;
        };
    }

    private static String formatTitle(ProjectKind kind, TestRunOptions options) {
        String base = jEdit.getProperty("test-results.run-title",
            new Object[] {WorkspaceProjectRunner.kindLabel(kind)});
        if (options.debug) {
            base += TestDebugSupport.debugCaptionSuffix();
        }
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
            case FLUTTER -> buildFlutterTests(projectRoot, options);
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
        if (options.debug) {
            TestDebugSupport.applyDebugOptions(
                inv.environment, ProjectKind.MAVEN, settings.mavenOpts, "MAVEN_OPTS");
        }
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
        if (options.debug) {
            TestDebugSupport.applyDebugOptions(
                inv.environment, ProjectKind.GRADLE, settings.gradleOpts, "GRADLE_OPTS");
        }
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
        if (options.debug) {
            TestDebugSupport.applyDebugOptions(
                inv.environment, ProjectKind.ANT, settings.antOpts, "ANT_OPTS");
        }
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
        if (options.scope == TestRunOptions.Scope.SINGLE && options.className != null) {
            String pattern = options.methodName.isBlank()
                ? options.className
                : options.className;
            goal.append(" -t ").append(pattern);
        }
        return goal.toString();
    }

    private static TestInvocation buildFlutterTests(File projectRoot, TestRunOptions options) {
        File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
        if (dir == null) {
            return null;
        }
        String goal = "test";
        if (options.scope == TestRunOptions.Scope.SINGLE && options.className != null) {
            goal += " --plain-name \"" + options.displayName().replace("\"", "") + "\"";
        }
        FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(
            dir, FlutterProjectPreferences.load(projectRoot), goal);
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
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
