/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

final class GradleProjectPreferences {

    private static final String SUBDIR = "gradle-projects";

    private GradleProjectPreferences() {}

    static GradleProjectSettings load(File projectRoot) {
        GradleProjectSettings settings = new GradleProjectSettings();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            return settings;
        }
        settings.gradleHome = props.getProperty("gradle.home", "");
        settings.jdkHome = props.getProperty("jdk.home", "");
        settings.gradleExecutable = props.getProperty("gradle.executable", "");
        settings.gradleUserHome = props.getProperty("gradle.user.home", "");
        settings.workingDirectory = props.getProperty("working.directory", "");
        settings.useWrapper = Boolean.parseBoolean(props.getProperty("use.wrapper", "true"));
        settings.gradleOpts = props.getProperty("gradle.opts", "");
        settings.additionalArgs = props.getProperty("additional.args", "");
        return settings;
    }

    static void save(File projectRoot, GradleProjectSettings settings) throws IOException {
        Properties props = new Properties();
        ProjectPropertiesStorage.put(props, "gradle.home", settings.gradleHome);
        ProjectPropertiesStorage.put(props, "jdk.home", settings.jdkHome);
        ProjectPropertiesStorage.put(props, "gradle.executable", settings.gradleExecutable);
        ProjectPropertiesStorage.put(props, "gradle.user.home", settings.gradleUserHome);
        ProjectPropertiesStorage.put(props, "working.directory", settings.workingDirectory);
        props.setProperty("use.wrapper", Boolean.toString(settings.useWrapper));
        ProjectPropertiesStorage.put(props, "gradle.opts", settings.gradleOpts);
        ProjectPropertiesStorage.put(props, "additional.args", settings.additionalArgs);
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }
}
