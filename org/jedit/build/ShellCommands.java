/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.OperatingSystem;

final class ShellCommands {

    private ShellCommands() {}

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static void appendTokens(List<String> args, String text) {
        if (isBlank(text)) {
            return;
        }
        Collections.addAll(args, text.trim().split("\\s+"));
    }

    static String mergeSpaceSeparated(String base, String extra) {
        if (isBlank(base)) {
            return isBlank(extra) ? "" : extra.trim();
        }
        if (isBlank(extra)) {
            return base.trim();
        }
        return base.trim() + " " + extra.trim();
    }

    static void appendPropertyLines(List<String> args, String properties) {
        if (isBlank(properties)) {
            return;
        }
        for (String line : properties.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (!key.isEmpty()) {
                args.add("-D" + key + "=" + value);
            }
        }
    }

    static void appendEnvironmentLines(Map<String, String> environment, String variables) {
        if (isBlank(variables)) {
            return;
        }
        for (String line : variables.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (!key.isEmpty()) {
                environment.put(key, value);
            }
        }
    }

    static List<String> wrapLauncher(String launcher, List<String> toolArgs) {
        List<String> command = new ArrayList<>();
        if (OperatingSystem.isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add(launcher);
            command.addAll(toolArgs);
            return command;
        }
        if (launcher.contains(" ")) {
            StringBuilder script = new StringBuilder();
            script.append('"').append(launcher).append('"');
            for (String arg : toolArgs) {
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
        command.addAll(toolArgs);
        return command;
    }
}
