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

final class MavenCommandBuilder {

    static final class Invocation {
        final List<String> command;
        final Map<String, String> environment;

        Invocation(List<String> command, Map<String, String> environment) {
            this.command = command;
            this.environment = environment;
        }
    }

    private MavenCommandBuilder() {}

    static Invocation build(File pomDirectory, MavenProjectSettings settings, String goal) {
        return build(pomDirectory, settings, goal, RunConfigurationOverrides.NONE);
    }

    static Invocation build(File pomDirectory, MavenProjectSettings settings, String goal,
                            RunConfigurationOverrides overrides) {
        MavenProjectSettings effective = settings != null
            ? settings
            : new MavenProjectSettings();
        RunConfigurationOverrides runOverrides = overrides != null
            ? overrides
            : RunConfigurationOverrides.NONE;
        Map<String, String> environment = new HashMap<>();
        applyEnvironment(effective, environment);
        runOverrides.mergeVmOptions(environment, "MAVEN_OPTS", effective.mavenOpts);

        List<String> mavenArgs = new ArrayList<>();
        appendMavenOptions(mavenArgs, effective);
        runOverrides.appendSystemProperties(mavenArgs);
        appendTokens(mavenArgs, goal);

        String launcher = resolveLauncher(pomDirectory, effective);
        List<String> command = wrapLauncher(launcher, mavenArgs);
        return new Invocation(command, environment);
    }

    static boolean hasWrapper(File pomDirectory) {
        if (pomDirectory == null) {
            return false;
        }
        return wrapperScript(pomDirectory).isFile();
    }

    private static void applyEnvironment(MavenProjectSettings settings, Map<String, String> env) {
        if (!isBlank(settings.mavenHome)) {
            env.put("MAVEN_HOME", settings.mavenHome.trim());
            env.put("M2_HOME", settings.mavenHome.trim());
        }
        if (!isBlank(settings.jdkHome)) {
            env.put("JAVA_HOME", settings.jdkHome.trim());
        }
    }

    private static void appendMavenOptions(List<String> args, MavenProjectSettings settings) {
        if (!isBlank(settings.settingsFile)) {
            args.add("-s");
            args.add(settings.settingsFile.trim());
        }
        if (!isBlank(settings.localRepository)) {
            args.add("-Dmaven.repo.local=" + settings.localRepository.trim());
        }
        if (settings.offline) {
            args.add("-o");
        }
        if (!isBlank(settings.activeProfiles)) {
            args.add("-P");
            args.add(settings.activeProfiles.trim());
        }
        if (settings.skipTests) {
            args.add("-DskipTests");
        }
        appendTokens(args, settings.additionalArgs);
    }

    private static String resolveLauncher(File pomDirectory, MavenProjectSettings settings) {
        if (!isBlank(settings.mavenExecutable)) {
            return settings.mavenExecutable.trim();
        }
        if (settings.useWrapper && pomDirectory != null) {
            File wrapper = wrapperScript(pomDirectory);
            if (wrapper.isFile()) {
                return wrapper.getAbsolutePath();
            }
        }
        return "mvn";
    }

    private static File wrapperScript(File pomDirectory) {
        String name = OperatingSystem.isWindows() ? "mvnw.cmd" : "mvnw";
        return new File(pomDirectory, name);
    }

    private static List<String> wrapLauncher(String launcher, List<String> mavenArgs) {
        List<String> command = new ArrayList<>();
        if (OperatingSystem.isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(launcher);
            command.addAll(mavenArgs);
            return command;
        }
        if (launcher.contains(" ")) {
            StringBuilder script = new StringBuilder();
            script.append('"').append(launcher).append('"');
            for (String arg : mavenArgs) {
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
        command.addAll(mavenArgs);
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
