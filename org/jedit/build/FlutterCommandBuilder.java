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

final class FlutterCommandBuilder {

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

    private FlutterCommandBuilder() {}

    static Invocation build(File projectDirectory, FlutterProjectSettings settings, String goal) {
        FlutterProjectSettings effective = settings != null
            ? settings
            : new FlutterProjectSettings();
        File workingDir = resolveWorkingDir(projectDirectory, effective);
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(effective.flutterSdk)) {
            environment.put("FLUTTER_ROOT", effective.flutterSdk.trim());
        }
        if (!ShellCommands.isBlank(effective.dartSdk)) {
            environment.put("DART_SDK", effective.dartSdk.trim());
        }

        List<String> args = buildArgs(effective, goal);
        ShellCommands.appendTokens(args, effective.additionalArgs);
        String launcher = resolveLauncher(effective, goal);
        return new Invocation(workingDir,
            ShellCommands.wrapLauncher(launcher, args), environment);
    }

    private static List<String> buildArgs(FlutterProjectSettings settings, String goal) {
        List<String> args = new ArrayList<>();
        if (ShellCommands.isBlank(goal)) {
            return args;
        }
        String trimmed = goal.trim();
        if (settings.useFlutterCli && trimmed.startsWith("pub ")) {
            args.add("pub");
            ShellCommands.appendTokens(args, trimmed.substring(4));
            return args;
        }
        if (settings.useFlutterCli && !trimmed.startsWith("dart ")) {
            ShellCommands.appendTokens(args, trimmed);
            return args;
        }
        if (trimmed.startsWith("dart ")) {
            ShellCommands.appendTokens(args, trimmed.substring(5));
        } else {
            ShellCommands.appendTokens(args, trimmed);
        }
        return args;
    }

    private static String resolveLauncher(FlutterProjectSettings settings, String goal) {
        boolean pubGoal = goal != null && goal.trim().startsWith("pub ");
        if (!settings.useFlutterCli || pubGoal) {
            if (!ShellCommands.isBlank(settings.dartExecutable)) {
                return settings.dartExecutable.trim();
            }
            return pubGoal && settings.useFlutterCli ? resolveFlutter(settings) : "dart";
        }
        return resolveFlutter(settings);
    }

    private static String resolveFlutter(FlutterProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.flutterExecutable)) {
            return settings.flutterExecutable.trim();
        }
        return "flutter";
    }

    private static File resolveWorkingDir(File projectDirectory, FlutterProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            return new File(settings.workingDirectory.trim());
        }
        return projectDirectory;
    }
}
