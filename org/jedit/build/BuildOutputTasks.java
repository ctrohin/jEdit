/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.List;

final class BuildOutputTasks {

    private BuildOutputTasks() {}

    static String taskKey(File workingDir, List<String> command) {
        String dir = workingDir != null ? workingDir.getAbsolutePath() : "";
        return dir + "|" + String.join("\u0000", command);
    }

    static String defaultTitle(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "Output";
        }
        String display = String.join(" ", command);
        if (display.length() > 48) {
            return display.substring(0, 45) + "...";
        }
        return display;
    }
}
