/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

final class NpmProjectSettings {

    static final String MANAGER_NPM = "npm";
    static final String MANAGER_YARN = "yarn";

    String nodeHome = "";
    String npmExecutable = "";
    String yarnExecutable = "";
    String packageManager = MANAGER_NPM;
    String workingDirectory = "";
    String nodeOptions = "";
    String additionalArgs = "";

    NpmProjectSettings copy() {
        NpmProjectSettings copy = new NpmProjectSettings();
        copy.nodeHome = nodeHome;
        copy.npmExecutable = npmExecutable;
        copy.yarnExecutable = yarnExecutable;
        copy.packageManager = packageManager;
        copy.workingDirectory = workingDirectory;
        copy.nodeOptions = nodeOptions;
        copy.additionalArgs = additionalArgs;
        return copy;
    }

    boolean useYarn() {
        return MANAGER_YARN.equalsIgnoreCase(packageManager);
    }
}
