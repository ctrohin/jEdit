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

final class WorkspaceRunPreferences {

    private static final String SUBDIR = "workspace-run";
    private static final String KEY_KIND = "project.kind";
    private static final String KEY_RUN_GOAL = "run.goal";

    private WorkspaceRunPreferences() {}

    static WorkspaceRunSettings load(File projectRoot) {
        WorkspaceRunSettings settings = new WorkspaceRunSettings();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            return settings;
        }
        ProjectKind kind = ProjectKind.fromId(props.getProperty(KEY_KIND));
        if (kind != null) {
            settings.kind = kind;
        }
        settings.runGoal = props.getProperty(KEY_RUN_GOAL, "");
        return settings;
    }

    static void save(File projectRoot, WorkspaceRunSettings settings) throws IOException {
        if (settings == null) {
            return;
        }
        Properties props = new Properties();
        props.setProperty(KEY_KIND, settings.kind.id());
        ProjectPropertiesStorage.put(props, KEY_RUN_GOAL, settings.runGoal);
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }
}
