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

final class NpmCommandBuilder {

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

    private NpmCommandBuilder() {}

    static Invocation build(File packageDirectory, NpmProjectSettings settings, String goal) {
        NpmProjectSettings effective = settings != null ? settings : new NpmProjectSettings();
        File workingDir = resolveWorkingDir(packageDirectory, effective);
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(effective.nodeHome)) {
            environment.put("NODE_HOME", effective.nodeHome.trim());
        }
        if (!ShellCommands.isBlank(effective.nodeOptions)) {
            environment.put("NODE_OPTIONS", effective.nodeOptions.trim());
        }

        List<String> args = buildToolArgs(effective, goal);
        ShellCommands.appendTokens(args, effective.additionalArgs);
        String launcher = resolveLauncher(effective);
        return new Invocation(workingDir,
            ShellCommands.wrapLauncher(launcher, args), environment);
    }

    private static List<String> buildToolArgs(NpmProjectSettings settings, String goal) {
        List<String> args = new ArrayList<>();
        if (ShellCommands.isBlank(goal)) {
            return args;
        }
        String trimmed = goal.trim();
        if (settings.useYarn()) {
            if (trimmed.startsWith("run ")) {
                args.add("run");
                ShellCommands.appendTokens(args, trimmed.substring(4));
            } else {
                args.add(trimmed);
            }
            return args;
        }
        if (trimmed.startsWith("run ")) {
            args.add("run");
            ShellCommands.appendTokens(args, trimmed.substring(4));
        } else {
            ShellCommands.appendTokens(args, trimmed);
        }
        return args;
    }

    private static File resolveWorkingDir(File packageDirectory, NpmProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            return new File(settings.workingDirectory.trim());
        }
        return packageDirectory;
    }

    private static String resolveLauncher(NpmProjectSettings settings) {
        if (settings.useYarn()) {
            if (!ShellCommands.isBlank(settings.yarnExecutable)) {
                return settings.yarnExecutable.trim();
            }
            return "yarn";
        }
        if (!ShellCommands.isBlank(settings.npmExecutable)) {
            return settings.npmExecutable.trim();
        }
        return "npm";
    }
}
