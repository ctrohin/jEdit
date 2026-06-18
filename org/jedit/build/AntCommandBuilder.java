/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.OperatingSystem;

final class AntCommandBuilder {

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

    private AntCommandBuilder() {}

    static Invocation build(File defaultBuildXml, AntProjectSettings settings, String target) {
        return build(defaultBuildXml, settings, target, RunConfigurationOverrides.NONE);
    }

    static Invocation build(File defaultBuildXml, AntProjectSettings settings, String target,
                            RunConfigurationOverrides overrides) {
        AntProjectSettings effective = settings != null
            ? settings
            : new AntProjectSettings();
        RunConfigurationOverrides runOverrides = overrides != null
            ? overrides
            : RunConfigurationOverrides.NONE;

        File buildXml = resolveBuildFile(defaultBuildXml, effective);
        File workingDir = resolveWorkingDirectory(buildXml, effective);

        Map<String, String> environment = new HashMap<>();
        applyEnvironment(effective, environment, runOverrides);

        List<String> antArgs = new ArrayList<>();
        appendAntOptions(antArgs, buildXml, effective);
        appendProperties(antArgs, effective.properties);
        runOverrides.appendSystemProperties(antArgs);
        appendTokens(antArgs, effective.additionalArgs);
        if (target != null && !target.isBlank()) {
            antArgs.add(target.trim());
        }

        String launcher = resolveLauncher(effective);
        List<String> command = wrapLauncher(launcher, antArgs);
        return new Invocation(workingDir, command, environment);
    }

    static File resolveConfiguredBuildFile(File projectRoot, AntProjectSettings settings) {
        if (settings != null && !isBlank(settings.buildFile)) {
            File configured = new File(settings.buildFile.trim());
            if (configured.isFile()) {
                return configured;
            }
        }
        if (projectRoot != null) {
            return ProjectRoots.findBuildXml(projectRoot);
        }
        return null;
    }

    private static File resolveBuildFile(File defaultBuildXml, AntProjectSettings settings) {
        if (!isBlank(settings.buildFile)) {
            File configured = new File(settings.buildFile.trim());
            if (configured.isFile()) {
                return configured;
            }
        }
        return defaultBuildXml;
    }

    private static File resolveWorkingDirectory(File buildXml, AntProjectSettings settings) {
        if (!isBlank(settings.workingDirectory)) {
            File dir = new File(settings.workingDirectory.trim());
            if (dir.isDirectory()) {
                return dir;
            }
        }
        if (buildXml != null) {
            File parent = buildXml.getParentFile();
            if (parent != null && parent.isDirectory()) {
                return parent;
            }
        }
        return null;
    }

    private static void applyEnvironment(AntProjectSettings settings, Map<String, String> env,
                                         RunConfigurationOverrides overrides) {
        if (!isBlank(settings.antHome)) {
            env.put("ANT_HOME", settings.antHome.trim());
        }
        if (!isBlank(settings.jdkHome)) {
            env.put("JAVA_HOME", settings.jdkHome.trim());
        }
        overrides.mergeVmOptions(env, "ANT_OPTS", settings.antOpts);
    }

    private static void appendAntOptions(List<String> args, File buildXml,
                                         AntProjectSettings settings) {
        if (buildXml != null && buildXml.isFile()) {
            args.add("-buildfile");
            args.add(buildXml.getAbsolutePath());
        }
        if (!isBlank(settings.propertyFile)) {
            args.add("-propertyfile");
            args.add(settings.propertyFile.trim());
        }
        switch (settings.logLevel != null ? settings.logLevel : "default") {
            case "quiet" -> args.add("-quiet");
            case "verbose" -> args.add("-verbose");
            case "debug" -> args.add("-debug");
            default -> { }
        }
    }

    private static void appendProperties(List<String> args, String properties) {
        ShellCommands.appendPropertyLines(args, properties);
    }

    private static String resolveLauncher(AntProjectSettings settings) {
        if (!isBlank(settings.antExecutable)) {
            return settings.antExecutable.trim();
        }
        return "ant";
    }

    private static List<String> wrapLauncher(String launcher, List<String> antArgs) {
        List<String> command = new ArrayList<>();
        if (OperatingSystem.isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(launcher);
            command.addAll(antArgs);
            return command;
        }
        if (launcher.contains(" ")) {
            StringBuilder script = new StringBuilder();
            script.append('"').append(launcher).append('"');
            for (String arg : antArgs) {
                script.append(' ');
                if (arg.indexOf(' ') >= 0) {
                    script.append('"').append(arg).append('"');
                } else {
                    script.append(arg);
                }
            }
            command.add("/bin/sh");
            command.add("-lc");
            command.add(script.toString());
            return command;
        }
        command.add(launcher);
        command.addAll(antArgs);
        return command;
    }

    private static void appendTokens(List<String> args, String text) {
        if (isBlank(text)) {
            return;
        }
        Collections.addAll(args, text.trim().split("\\s+"));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
