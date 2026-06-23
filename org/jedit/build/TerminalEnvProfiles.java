/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.jEdit;

final class TerminalEnvProfiles {

    private TerminalEnvProfiles() {}

    static List<String> profileNames() {
        String raw = jEdit.getProperty("terminal.profiles", "Default");
        List<String> names = new ArrayList<>();
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        if (names.isEmpty()) {
            names.add("Default");
        }
        return names;
    }

    static Map<String, String> environmentForProfile(String profileName) {
        Map<String, String> env = new LinkedHashMap<>(TerminalShell.baseEnvironment());
        if (profileName == null || profileName.isBlank()) {
            return env;
        }
        String key = "terminal.profile." + profileName.trim() + ".environment";
        ShellCommands.appendEnvironmentLines(env, jEdit.getProperty(key, ""));
        return env;
    }
}
