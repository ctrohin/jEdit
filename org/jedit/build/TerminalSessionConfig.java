/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.Map;

final class TerminalSessionConfig {

    final File workingDir;
    final Map<String, String> environment;
    final String sessionName;

    TerminalSessionConfig(File workingDir, Map<String, String> environment, String sessionName) {
        this.workingDir = workingDir;
        this.environment = environment;
        this.sessionName = sessionName != null ? sessionName : "";
    }

    static TerminalSessionConfig workspaceDefault(String profileName) {
        File root = ProjectRoots.workspaceRoot();
        File dir = root != null ? root : new File(System.getProperty("user.home"));
        return new TerminalSessionConfig(
            dir,
            TerminalEnvProfiles.environmentForProfile(profileName),
            "");
    }

    static TerminalSessionConfig forDirectory(File dir, String profileName, String sessionName) {
        File workingDir = dir != null && dir.isDirectory()
            ? dir
            : ProjectRoots.workspaceRoot();
        if (workingDir == null) {
            workingDir = new File(System.getProperty("user.home"));
        }
        return new TerminalSessionConfig(
            workingDir,
            TerminalEnvProfiles.environmentForProfile(profileName),
            sessionName);
    }
}
