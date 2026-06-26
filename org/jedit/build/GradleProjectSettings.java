/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

final class GradleProjectSettings {

    String gradleHome = "";
    String jdkHome = "";
    String gradleExecutable = "";
    String gradleUserHome = "";
    String workingDirectory = "";
    boolean useWrapper = true;
    String gradleOpts = "";
    String additionalArgs = "";

    GradleProjectSettings copy() {
        GradleProjectSettings copy = new GradleProjectSettings();
        copy.gradleHome = gradleHome;
        copy.jdkHome = jdkHome;
        copy.gradleExecutable = gradleExecutable;
        copy.gradleUserHome = gradleUserHome;
        copy.workingDirectory = workingDirectory;
        copy.useWrapper = useWrapper;
        copy.gradleOpts = gradleOpts;
        copy.additionalArgs = additionalArgs;
        return copy;
    }
}
