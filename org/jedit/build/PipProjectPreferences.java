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

final class PipProjectPreferences {

    private static final String SUBDIR = "pip-projects";

    private PipProjectPreferences() {}

    static PipProjectSettings load(File projectRoot) {
        PipProjectSettings settings = new PipProjectSettings();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            return settings;
        }
        settings.pythonHome = props.getProperty("python.home", "");
        settings.pythonExecutable = props.getProperty("python.executable", "");
        settings.pipExecutable = props.getProperty("pip.executable", "");
        settings.virtualEnv = props.getProperty("virtual.env", "");
        settings.workingDirectory = props.getProperty("working.directory", "");
        settings.usePipModule = Boolean.parseBoolean(props.getProperty("use.pip.module", "true"));
        settings.additionalArgs = props.getProperty("additional.args", "");
        return settings;
    }

    static void save(File projectRoot, PipProjectSettings settings) throws IOException {
        Properties props = new Properties();
        ProjectPropertiesStorage.put(props, "python.home", settings.pythonHome);
        ProjectPropertiesStorage.put(props, "python.executable", settings.pythonExecutable);
        ProjectPropertiesStorage.put(props, "pip.executable", settings.pipExecutable);
        ProjectPropertiesStorage.put(props, "virtual.env", settings.virtualEnv);
        ProjectPropertiesStorage.put(props, "working.directory", settings.workingDirectory);
        props.setProperty("use.pip.module", Boolean.toString(settings.usePipModule));
        ProjectPropertiesStorage.put(props, "additional.args", settings.additionalArgs);
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }
}
