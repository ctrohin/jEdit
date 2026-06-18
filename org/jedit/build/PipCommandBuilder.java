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

final class PipCommandBuilder {

    static final class Invocation {
        final File workingDir;
        final List<String> command;
        final Map<String, String> environment;

        Invocation(File workingDir, List<String> command, Map<String, String> environment) {
            this.workingDir = workingDir;
            this.command = command;
            this.environment = environment;
        }
    }

    private PipCommandBuilder() {}

    static Invocation build(File projectDirectory, PipProjectSettings settings, String goal) {
        return build(projectDirectory, settings, goal, RunConfigurationOverrides.NONE);
    }

    static Invocation build(File projectDirectory, PipProjectSettings settings, String goal,
                            RunConfigurationOverrides overrides) {
        PipProjectSettings effective = settings != null ? settings : new PipProjectSettings();
        RunConfigurationOverrides runOverrides = overrides != null
            ? overrides
            : RunConfigurationOverrides.NONE;
        File workingDir = resolveWorkingDir(projectDirectory, effective);
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(effective.pythonHome)) {
            environment.put("PYTHONHOME", effective.pythonHome.trim());
        }
        if (!ShellCommands.isBlank(effective.virtualEnv)) {
            environment.put("VIRTUAL_ENV", effective.virtualEnv.trim());
        }
        runOverrides.appendEnvironmentVariables(environment);

        List<String> args = buildPipArgs(effective, goal);
        runOverrides.appendVmOptionTokens(args);
        ShellCommands.appendTokens(args, effective.additionalArgs);
        String launcher = resolveLauncher(effective);
        return new Invocation(workingDir,
            ShellCommands.wrapLauncher(launcher, args), environment);
    }

    private static List<String> buildPipArgs(PipProjectSettings settings, String goal) {
        List<String> args = new ArrayList<>();
        if (settings.usePipModule) {
            args.add("-m");
            args.add("pip");
        }
        if (!ShellCommands.isBlank(goal)) {
            ShellCommands.appendTokens(args, goal.trim());
        }
        return args;
    }

    private static String resolveLauncher(PipProjectSettings settings) {
        if (settings.usePipModule) {
            if (!ShellCommands.isBlank(settings.pythonExecutable)) {
                return settings.pythonExecutable.trim();
            }
            return "python";
        }
        if (!ShellCommands.isBlank(settings.pipExecutable)) {
            return settings.pipExecutable.trim();
        }
        return "pip";
    }

    private static File resolveWorkingDir(File projectDirectory, PipProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            return new File(settings.workingDirectory.trim());
        }
        return projectDirectory;
    }
}
