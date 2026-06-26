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

final class NpmProjectPreferences {

    private static final String SUBDIR = "npm-projects";

    private NpmProjectPreferences() {}

    static NpmProjectSettings load(File projectRoot) {
        NpmProjectSettings settings = new NpmProjectSettings();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            return settings;
        }
        settings.nodeHome = props.getProperty("node.home", "");
        settings.npmExecutable = props.getProperty("npm.executable", "");
        settings.yarnExecutable = props.getProperty("yarn.executable", "");
        settings.packageManager = props.getProperty("package.manager", NpmProjectSettings.MANAGER_NPM);
        settings.workingDirectory = props.getProperty("working.directory", "");
        settings.nodeOptions = props.getProperty("node.options", "");
        settings.additionalArgs = props.getProperty("additional.args", "");
        return settings;
    }

    static void save(File projectRoot, NpmProjectSettings settings) throws IOException {
        Properties props = new Properties();
        ProjectPropertiesStorage.put(props, "node.home", settings.nodeHome);
        ProjectPropertiesStorage.put(props, "npm.executable", settings.npmExecutable);
        ProjectPropertiesStorage.put(props, "yarn.executable", settings.yarnExecutable);
        ProjectPropertiesStorage.put(props, "package.manager", settings.packageManager);
        ProjectPropertiesStorage.put(props, "working.directory", settings.workingDirectory);
        ProjectPropertiesStorage.put(props, "node.options", settings.nodeOptions);
        ProjectPropertiesStorage.put(props, "additional.args", settings.additionalArgs);
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }
}
