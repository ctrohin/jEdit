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
import java.util.HashMap;
import java.util.Map;

import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.jEdit;

final class TerminalShell {

    private TerminalShell() {}

    static String[] defaultCommand() {
        String custom = jEdit.getProperty("terminal.shell", "");
        if (!ShellCommands.isBlank(custom)) {
            return custom.trim().split("\\s+");
        }
        if (OperatingSystem.isWindows()) {
            String comspec = System.getenv("COMSPEC");
            return new String[] {comspec != null ? comspec : "cmd.exe"};
        }
        String shell = System.getenv("SHELL");
        if (!ShellCommands.isBlank(shell)) {
            return new String[] {shell, "-l"};
        }
        return new String[] {"/bin/bash", "-l"};
    }

    static String shellName() {
        String[] command = defaultCommand();
        if (command.length == 0) {
            return "shell";
        }
        return new File(command[0]).getName();
    }

    static Map<String, String> environment() {
        return baseEnvironment();
    }

    static Map<String, String> baseEnvironment() {
        Map<String, String> env = new HashMap<>(System.getenv());
        if (!OperatingSystem.isWindows()) {
            env.putIfAbsent("TERM", "xterm-256color");
        }
        ShellCommands.appendEnvironmentLines(env, jEdit.getProperty("terminal.environment", ""));
        return env;
    }
}
