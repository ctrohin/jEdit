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
 * structured Surefire/Gradle XML reports into {@link TestResultsHub}.
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
        runTests(view, projectRoot, null);
    }

    public static void runTests(View view, File projectRoot, ProjectKind kind) {
        if (view == null || projectRoot == null) {
            return;
        }
        ProjectKind effectiveKind = kind != null ? kind : resolveTestKind(projectRoot);
        if (effectiveKind == null) {
            return;
        }
        TestInvocation invocation = buildTestInvocation(projectRoot, effectiveKind);
        if (invocation == null) {
            return;
        }
        String title = jEdit.getProperty("test-results.run-title",
            new Object[] {WorkspaceProjectRunner.kindLabel(effectiveKind)});
        TestResultsHub.getInstance().clear();
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
            case MAVEN, GRADLE, ANT -> true;
            default -> false;
        };
    }

    private static TestInvocation buildTestInvocation(File projectRoot, ProjectKind kind) {
        return switch (kind) {
            case MAVEN -> buildMavenTests(projectRoot);
            case GRADLE -> buildGradleTests(projectRoot);
            case ANT -> buildAntTests(projectRoot);
            case NPM -> buildNpmTests(projectRoot);
            case FLUTTER -> buildFlutterTests(projectRoot);
            default -> null;
        };
    }

    private static TestInvocation buildMavenTests(File projectRoot) {
        File dir = projectDirectory(ProjectRoots.findPomXml(projectRoot));
        if (dir == null) {
            return null;
        }
        MavenProjectSettings settings = MavenProjectPreferences.load(projectRoot).copy();
        settings.skipTests = false;
        MavenCommandBuilder.Invocation inv = MavenCommandBuilder.build(dir, settings, "test");
        return new TestInvocation(dir, inv.command, inv.environment);
    }

    private static TestInvocation buildGradleTests(File projectRoot) {
        File dir = projectDirectory(ProjectRoots.findGradleBuild(projectRoot));
        if (dir == null) {
            return null;
        }
        GradleCommandBuilder.Invocation inv = GradleCommandBuilder.build(
            dir, GradleProjectPreferences.load(projectRoot), "test");
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static TestInvocation buildAntTests(File projectRoot) {
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

    private static TestInvocation buildNpmTests(File projectRoot) {
        File dir = projectDirectory(ProjectRoots.findPackageJson(projectRoot));
        if (dir == null) {
            return null;
        }
        NpmCommandBuilder.Invocation inv = NpmCommandBuilder.build(
            dir, NpmProjectPreferences.load(projectRoot), "test");
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
    }

    private static TestInvocation buildFlutterTests(File projectRoot) {
        File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
        if (dir == null) {
            return null;
        }
        FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(
            dir, FlutterProjectPreferences.load(projectRoot), "test");
        return new TestInvocation(inv.workingDir, inv.command, inv.environment);
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
