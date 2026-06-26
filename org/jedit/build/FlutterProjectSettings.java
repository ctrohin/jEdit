/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

final class FlutterProjectSettings {

    String flutterSdk = "";
    String dartSdk = "";
    String flutterExecutable = "";
    String dartExecutable = "";
    boolean useFlutterCli = true;
    String workingDirectory = "";
    String additionalArgs = "";

    FlutterProjectSettings copy() {
        FlutterProjectSettings copy = new FlutterProjectSettings();
        copy.flutterSdk = flutterSdk;
        copy.dartSdk = dartSdk;
        copy.flutterExecutable = flutterExecutable;
        copy.dartExecutable = dartExecutable;
        copy.useFlutterCli = useFlutterCli;
        copy.workingDirectory = workingDirectory;
        copy.additionalArgs = additionalArgs;
        return copy;
    }
}
