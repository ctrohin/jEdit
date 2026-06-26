/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

/**
 * Detects runnable project types in the workspace folder and executes the
 * configured run goal in the Build output view.
 */
public final class WorkspaceProjectRunner {

    private WorkspaceProjectRunner() {}

    public static boolean canRun(File projectRoot) {
        return !detectSupportedKinds(projectRoot).isEmpty();
    }

    public static List<ProjectKind> detectSupportedKinds(File projectRoot) {
        List<ProjectKind> kinds = new ArrayList<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return kinds;
        }
        if (ProjectRoots.findPubspecYaml(projectRoot) != null) {
            ProjectKind dartKind = DartProjectSupport.resolvePubspecKind(projectRoot);
            if (dartKind != null) {
                kinds.add(dartKind);
            }
        }
        if (ProjectRoots.findPomXml(projectRoot) != null) {
            kinds.add(ProjectKind.MAVEN);
        }
        if (ProjectRoots.findGradleBuild(projectRoot) != null) {
            kinds.add(ProjectKind.GRADLE);
        }
        if (ProjectRoots.findPackageJson(projectRoot) != null) {
            kinds.add(ProjectKind.NPM);
        }
        if (ProjectRoots.findBuildXml(projectRoot) != null) {
            kinds.add(ProjectKind.ANT);
        }
        if (ProjectRoots.findPythonMarker(projectRoot) != null) {
            kinds.add(ProjectKind.PIP);
        }
        return kinds;
    }

    public static WorkspaceRunConfiguration resolveDefaultConfiguration(File projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        return WorkspaceRunConfigurationPreferences.load(projectRoot).getDefault();
    }

    public static ProjectKind resolveActiveKind(File projectRoot) {
        WorkspaceRunConfiguration cfg = resolveDefaultConfiguration(projectRoot);
        return cfg != null ? cfg.kind : null;
    }

    public static String resolveRunGoal(File projectRoot, ProjectKind kind) {
        if (projectRoot == null || kind == null) {
            return "";
        }
        WorkspaceRunConfiguration cfg = resolveDefaultConfiguration(projectRoot);
        if (cfg != null && kind == cfg.kind && cfg.runGoal != null && !cfg.runGoal.isBlank()) {
            return cfg.runGoal.trim();
        }
        return defaultRunGoal(projectRoot, kind);
    }

    public static List<String> suggestRunGoals(File projectRoot, ProjectKind kind) {
        return suggestRunGoals(projectRoot, kind, null);
    }

    public static List<String> suggestRunGoals(File projectRoot, ProjectKind kind,
                                               String currentGoal) {
        Set<String> goals = new LinkedHashSet<>();
        if (projectRoot == null || kind == null) {
            return List.of();
        }
        if (currentGoal != null && !currentGoal.isBlank()) {
            goals.add(currentGoal.trim());
        }
        goals.addAll(defaultRunGoalCandidates(projectRoot, kind));
        return new ArrayList<>(goals);
    }

    public static String defaultRunGoalForKind(File projectRoot, ProjectKind kind) {
        return defaultRunGoal(projectRoot, kind);
    }

    public static void runProject(View view, File projectRoot) {
        runConfiguration(view, projectRoot, null);
    }

    public static void runConfiguration(View view, File projectRoot,
                                        WorkspaceRunConfiguration configuration) {
        if (view == null || projectRoot == null) {
            return;
        }
        WorkspaceRunConfiguration cfg = configuration;
        if (cfg == null) {
            cfg = resolveDefaultConfiguration(projectRoot);
        }
        if (cfg == null || cfg.runGoal == null || cfg.runGoal.isBlank()) {
            return;
        }
        RunInvocation invocation = buildInvocation(projectRoot, cfg);
        if (invocation == null) {
            return;
        }
        BuildOutputView output = BuildOutputView.show(view);
        output.runBuild(formatRunTitle(cfg), invocation.workingDir,
            invocation.command, invocation.environment);
    }

    public static boolean configureRun(View view, File projectRoot) {
        if (view == null || projectRoot == null) {
            return false;
        }
        return WorkspaceRunConfigurationsDialog.showManager(view, projectRoot)
            != WorkspaceRunConfigurationsDialog.Result.NONE;
    }

    public static String configurationLabel(WorkspaceRunConfiguration cfg) {
        if (cfg == null) {
            return "";
        }
        String label = cfg.displayName();
        if (!label.isEmpty()) {
            return label;
        }
        return kindLabel(cfg.kind) + ": " + cfg.runGoal;
    }

    public static void showRunConfigurationMenu(java.awt.event.ActionEvent event, View view,
                                              File projectRoot, Runnable onChanged) {
        WorkspaceRunConfigurationPopup.show(event, view, projectRoot, onChanged);
    }

    public static boolean createRunConfiguration(View view, File projectRoot) {
        if (view == null || projectRoot == null) {
            return false;
        }
        return WorkspaceRunConfigurationsDialog.showNew(view, projectRoot)
            != WorkspaceRunConfigurationsDialog.Result.NONE;
    }

    private static String formatRunTitle(WorkspaceRunConfiguration cfg) {
        if (cfg.name != null && !cfg.name.isBlank()) {
            return cfg.name.trim();
        }
        return kindLabel(cfg.kind) + ": " + cfg.runGoal;
    }

    static RunInvocation buildInvocation(File projectRoot, WorkspaceRunConfiguration cfg) {
        if (projectRoot == null || cfg == null || cfg.kind == null
            || cfg.runGoal == null || cfg.runGoal.isBlank()) {
            return null;
        }
        RunConfigurationOverrides overrides = RunConfigurationOverrides.from(cfg);
        ProjectKind kind = cfg.kind;
        String goal = cfg.runGoal;
        return switch (kind) {
            case MAVEN -> {
                File dir = projectDirectory(ProjectRoots.findPomXml(projectRoot));
                if (dir == null) {
                    yield null;
                }
                MavenCommandBuilder.Invocation inv = MavenCommandBuilder.build(
                    dir, MavenProjectPreferences.load(projectRoot), goal, overrides);
                yield new RunInvocation(dir, inv.command, inv.environment);
            }
            case GRADLE -> {
                File dir = projectDirectory(ProjectRoots.findGradleBuild(projectRoot));
                if (dir == null) {
                    yield null;
                }
                GradleCommandBuilder.Invocation inv = GradleCommandBuilder.build(
                    dir, GradleProjectPreferences.load(projectRoot), goal, overrides);
                yield new RunInvocation(inv.workingDir, inv.command, inv.environment);
            }
            case NPM -> {
                File dir = projectDirectory(ProjectRoots.findPackageJson(projectRoot));
                if (dir == null) {
                    yield null;
                }
                NpmCommandBuilder.Invocation inv = NpmCommandBuilder.build(
                    dir, NpmProjectPreferences.load(projectRoot), goal, overrides);
                yield new RunInvocation(inv.workingDir, inv.command, inv.environment);
            }
            case FLUTTER -> {
                File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
                if (dir == null) {
                    yield null;
                }
                FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(
                    dir, FlutterProjectPreferences.load(projectRoot), goal, overrides);
                yield new RunInvocation(inv.workingDir, inv.command, inv.environment);
            }
            case DART -> {
                File dir = projectDirectory(ProjectRoots.findPubspecYaml(projectRoot));
                if (dir == null) {
                    yield null;
                }
                FlutterProjectSettings settings = FlutterProjectPreferences.load(projectRoot).copy();
                settings.useFlutterCli = false;
                FlutterCommandBuilder.Invocation inv = FlutterCommandBuilder.build(
                    dir, settings, goal, overrides);
                yield new RunInvocation(inv.workingDir, inv.command, inv.environment);
            }
            case ANT -> {
                AntProjectSettings settings = AntProjectPreferences.load(projectRoot);
                File buildXml = AntCommandBuilder.resolveConfiguredBuildFile(projectRoot, settings);
                if (buildXml == null) {
                    yield null;
                }
                AntCommandBuilder.Invocation inv =
                    AntCommandBuilder.build(buildXml, settings, goal, overrides);
                yield new RunInvocation(inv.workingDir, inv.command, inv.environment);
            }
            case PIP -> buildPythonInvocation(projectRoot, goal, overrides);
        };
    }

    private static RunInvocation buildPythonInvocation(File projectRoot, String goal,
                                                       RunConfigurationOverrides overrides) {
        File marker = ProjectRoots.findPythonMarker(projectRoot);
        if (marker == null) {
            return null;
        }
        PipProjectSettings settings = PipProjectPreferences.load(projectRoot);
        File workingDir = projectDirectory(marker);
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            workingDir = new File(settings.workingDirectory.trim());
        }
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(settings.pythonHome)) {
            environment.put("PYTHONHOME", settings.pythonHome.trim());
        }
        if (!ShellCommands.isBlank(settings.virtualEnv)) {
            environment.put("VIRTUAL_ENV", settings.virtualEnv.trim());
        }
        if (overrides != null) {
            overrides.appendEnvironmentVariables(environment);
        }
        String python = ShellCommands.isBlank(settings.pythonExecutable)
            ? "python"
            : settings.pythonExecutable.trim();
        List<String> args = new ArrayList<>();
        if (overrides != null) {
            overrides.appendVmOptionTokens(args);
        }
        ShellCommands.appendTokens(args, goal);
        ShellCommands.appendTokens(args, settings.additionalArgs);
        return new RunInvocation(workingDir,
            ShellCommands.wrapLauncher(python, args), environment);
    }

    private static File projectDirectory(File markerFile) {
        return markerFile != null ? markerFile.getParentFile() : null;
    }

    private static String defaultRunGoal(File projectRoot, ProjectKind kind) {
        List<String> candidates = defaultRunGoalCandidates(projectRoot, kind);
        return candidates.isEmpty() ? "" : candidates.get(0);
    }

    private static List<String> defaultRunGoalCandidates(File projectRoot, ProjectKind kind) {
        List<String> goals = new ArrayList<>();
        switch (kind) {
            case MAVEN -> {
                File pom = ProjectRoots.findPomXml(projectRoot);
                if (pom != null) {
                    MavenPomFile parsed = MavenPomFile.parse(pom);
                    if (parsed != null) {
                        for (String custom : parsed.customGoals()) {
                            if (custom.contains("spring-boot:run")) {
                                goals.add("spring-boot:run");
                            }
                        }
                        if (parsed.customGoals().contains("exec:java")) {
                            goals.add("exec:java");
                        }
                    }
                }
                goals.add("spring-boot:run");
                goals.add("exec:java");
                goals.add("package");
            }
            case GRADLE -> {
                goals.add("run");
                goals.add("bootRun");
                goals.add("application:run");
                goals.add("build");
            }
            case NPM -> {
                File json = ProjectRoots.findPackageJson(projectRoot);
                if (json != null) {
                    for (String script : PackageJsonFile.scriptNames(json)) {
                        goals.add("run " + script);
                    }
                }
                goals.add("run start");
                goals.add("run dev");
                goals.add("start");
            }
            case FLUTTER -> {
                goals.add("run");
                goals.add("run -d chrome");
            }
            case DART -> {
                goals.add("run");
                goals.add("test");
            }
            case ANT -> {
                AntProjectSettings settings = AntProjectPreferences.load(projectRoot);
                File buildXml = AntCommandBuilder.resolveConfiguredBuildFile(projectRoot, settings);
                if (buildXml != null) {
                    AntBuildFile parsed = AntBuildFile.parse(buildXml);
                    if (parsed != null) {
                        if (parsed.defaultTarget != null && !parsed.defaultTarget.isBlank()) {
                            goals.add(parsed.defaultTarget);
                        }
                        for (String target : List.of("run", "start", "main")) {
                            if (parsed.targets.contains(target)) {
                                goals.add(target);
                            }
                        }
                        goals.addAll(parsed.targets);
                    }
                }
            }
            case PIP -> {
                File marker = ProjectRoots.findPythonMarker(projectRoot);
                File dir = marker != null ? marker.getParentFile() : projectRoot;
                if (dir != null) {
                    if (new File(dir, "main.py").isFile()) {
                        goals.add("main.py");
                    }
                    if (new File(dir, "app.py").isFile()) {
                        goals.add("app.py");
                    }
                    if (new File(dir, "manage.py").isFile()) {
                        goals.add("manage.py runserver");
                    }
                }
                goals.add("-m main");
            }
            default -> { }
        }
        return goals;
    }

    public static String kindLabel(ProjectKind kind) {
        return jEdit.getProperty("workspace-run.kind." + kind.id());
    }

    static final class RunInvocation {
        final File workingDir;
        final List<String> command;
        final Map<String, String> environment;

        RunInvocation(File workingDir, List<String> command, Map<String, String> environment) {
            this.workingDir = workingDir;
            this.command = command;
            this.environment = environment;
        }
    }
}
