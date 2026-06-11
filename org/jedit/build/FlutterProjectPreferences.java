/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

final class FlutterProjectPreferences {

    private static final String SUBDIR = "flutter-projects";

    private FlutterProjectPreferences() {}

    static FlutterProjectSettings load(File projectRoot) {
        FlutterProjectSettings settings = new FlutterProjectSettings();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            return settings;
        }
        settings.flutterSdk = props.getProperty("flutter.sdk", "");
        settings.dartSdk = props.getProperty("dart.sdk", "");
        settings.flutterExecutable = props.getProperty("flutter.executable", "");
        settings.dartExecutable = props.getProperty("dart.executable", "");
        settings.useFlutterCli = Boolean.parseBoolean(props.getProperty("use.flutter.cli", "true"));
        settings.workingDirectory = props.getProperty("working.directory", "");
        settings.additionalArgs = props.getProperty("additional.args", "");
        return settings;
    }

    static void save(File projectRoot, FlutterProjectSettings settings) throws IOException {
        Properties props = new Properties();
        ProjectPropertiesStorage.put(props, "flutter.sdk", settings.flutterSdk);
        ProjectPropertiesStorage.put(props, "dart.sdk", settings.dartSdk);
        ProjectPropertiesStorage.put(props, "flutter.executable", settings.flutterExecutable);
        ProjectPropertiesStorage.put(props, "dart.executable", settings.dartExecutable);
        props.setProperty("use.flutter.cli", Boolean.toString(settings.useFlutterCli));
        ProjectPropertiesStorage.put(props, "working.directory", settings.workingDirectory);
        ProjectPropertiesStorage.put(props, "additional.args", settings.additionalArgs);
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }
}
