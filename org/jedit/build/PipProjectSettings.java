/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

final class PipProjectSettings {

    String pythonHome = "";
    String pythonExecutable = "";
    String pipExecutable = "";
    String virtualEnv = "";
    String workingDirectory = "";
    boolean usePipModule = true;
    String additionalArgs = "";

    PipProjectSettings copy() {
        PipProjectSettings copy = new PipProjectSettings();
        copy.pythonHome = pythonHome;
        copy.pythonExecutable = pythonExecutable;
        copy.pipExecutable = pipExecutable;
        copy.virtualEnv = virtualEnv;
        copy.workingDirectory = workingDirectory;
        copy.usePipModule = usePipModule;
        copy.additionalArgs = additionalArgs;
        return copy;
    }
}
