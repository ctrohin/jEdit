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

import org.gjt.sp.jedit.OperatingSystem;

final class GradleCommandBuilder {

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

    private GradleCommandBuilder() {}

    static Invocation build(File projectDirectory, GradleProjectSettings settings, String task) {
        GradleProjectSettings effective = settings != null
            ? settings
            : new GradleProjectSettings();
        File workingDir = resolveWorkingDir(projectDirectory, effective);
        Map<String, String> environment = new HashMap<>();
        if (!ShellCommands.isBlank(effective.gradleHome)) {
            environment.put("GRADLE_HOME", effective.gradleHome.trim());
        }
        if (!ShellCommands.isBlank(effective.jdkHome)) {
            environment.put("JAVA_HOME", effective.jdkHome.trim());
        }
        if (!ShellCommands.isBlank(effective.gradleUserHome)) {
            environment.put("GRADLE_USER_HOME", effective.gradleUserHome.trim());
        }
        if (!ShellCommands.isBlank(effective.gradleOpts)) {
            environment.put("GRADLE_OPTS", effective.gradleOpts.trim());
        }

        List<String> args = new ArrayList<>();
        ShellCommands.appendTokens(args, effective.additionalArgs);
        ShellCommands.appendTokens(args, task);

        String launcher = resolveLauncher(workingDir, effective);
        return new Invocation(workingDir,
            ShellCommands.wrapLauncher(launcher, args), environment);
    }

    static boolean hasWrapper(File projectDirectory) {
        if (projectDirectory == null) {
            return false;
        }
        String name = OperatingSystem.isWindows() ? "gradlew.bat" : "gradlew";
        return new File(projectDirectory, name).isFile();
    }

    private static File resolveWorkingDir(File projectDirectory, GradleProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.workingDirectory)) {
            return new File(settings.workingDirectory.trim());
        }
        return projectDirectory;
    }

    private static String resolveLauncher(File workingDir, GradleProjectSettings settings) {
        if (!ShellCommands.isBlank(settings.gradleExecutable)) {
            return settings.gradleExecutable.trim();
        }
        if (settings.useWrapper && workingDir != null) {
            String name = OperatingSystem.isWindows() ? "gradlew.bat" : "gradlew";
            File wrapper = new File(workingDir, name);
            if (wrapper.isFile()) {
                return wrapper.getAbsolutePath();
            }
        }
        return "gradle";
    }
}
