/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.gjt.sp.jedit.jEdit;

final class WorkspaceRunConfigurationPreferences {

    private static final String SUBDIR = "workspace-run";
    private static final String KEY_CONFIG_IDS = "config.ids";
    private static final String KEY_DEFAULT_ID = "default.id";
    private static final String KEY_NEXT_ID = "next.id";
    private static final String KEY_CONFIG_NAME = "name";
    private static final String KEY_CONFIG_KIND = "kind";
    private static final String KEY_CONFIG_GOAL = "goal";
    private static final String KEY_CONFIG_VM_OPTIONS = "vm.options";
    private static final String KEY_CONFIG_PROPERTIES = "additional.properties";

    private static final String LEGACY_KEY_KIND = "project.kind";
    private static final String LEGACY_KEY_RUN_GOAL = "run.goal";

    private WorkspaceRunConfigurationPreferences() {}

    static WorkspaceRunConfigurationSet load(File projectRoot) {
        WorkspaceRunConfigurationSet set = new WorkspaceRunConfigurationSet();
        Properties props = ProjectPropertiesStorage.load(projectRoot, SUBDIR);
        if (props == null) {
            ensureDefaultConfiguration(projectRoot, set);
            return set;
        }

        String ids = props.getProperty(KEY_CONFIG_IDS, "").trim();
        if (ids.isEmpty()) {
            migrateLegacyConfiguration(set, props, projectRoot);
            if (set.isEmpty()) {
                ensureDefaultConfiguration(projectRoot, set);
            } else {
                try {
                    save(projectRoot, set);
                } catch (IOException ex) {
                    // keep in-memory defaults
                }
            }
            return set;
        }

        String defaultId = props.getProperty(KEY_DEFAULT_ID, "").trim();
        int nextId = parsePositiveInt(props.getProperty(KEY_NEXT_ID), 1);
        set.setNextId(nextId);
        for (String id : ids.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            WorkspaceRunConfiguration cfg = readConfiguration(props, trimmed);
            if (cfg != null) {
                set.add(cfg);
            }
        }
        if (!defaultId.isEmpty()) {
            set.setDefault(defaultId);
        }
        if (set.isEmpty()) {
            ensureDefaultConfiguration(projectRoot, set);
        }
        return set;
    }

    static void save(File projectRoot, WorkspaceRunConfigurationSet set) throws IOException {
        if (set == null || set.isEmpty()) {
            return;
        }
        Properties props = new Properties();
        List<String> ids = new ArrayList<>();
        for (WorkspaceRunConfiguration cfg : set.getConfigurations()) {
            ids.add(cfg.id);
            String prefix = "config." + cfg.id + ".";
            ProjectPropertiesStorage.put(props, prefix + KEY_CONFIG_NAME, cfg.name);
            props.setProperty(prefix + KEY_CONFIG_KIND, cfg.kind.id());
            ProjectPropertiesStorage.put(props, prefix + KEY_CONFIG_GOAL, cfg.runGoal);
            ProjectPropertiesStorage.put(props, prefix + KEY_CONFIG_VM_OPTIONS, cfg.vmOptions);
            ProjectPropertiesStorage.put(props, prefix + KEY_CONFIG_PROPERTIES,
                cfg.additionalProperties);
        }
        props.setProperty(KEY_CONFIG_IDS, String.join(",", ids));
        props.setProperty(KEY_DEFAULT_ID, set.getDefaultId());
        props.setProperty(KEY_NEXT_ID, String.valueOf(set.getNextId()));
        ProjectPropertiesStorage.save(projectRoot, SUBDIR, props);
    }

    static void reset(File projectRoot) {
        ProjectPropertiesStorage.reset(projectRoot, SUBDIR);
    }

    private static WorkspaceRunConfiguration readConfiguration(Properties props, String id) {
        String prefix = "config." + id + ".";
        ProjectKind kind = ProjectKind.fromId(props.getProperty(prefix + KEY_CONFIG_KIND));
        if (kind == null) {
            return null;
        }
        WorkspaceRunConfiguration cfg = new WorkspaceRunConfiguration();
        cfg.id = id;
        cfg.name = props.getProperty(prefix + KEY_CONFIG_NAME, "");
        cfg.kind = kind;
        cfg.runGoal = props.getProperty(prefix + KEY_CONFIG_GOAL, "");
        cfg.vmOptions = props.getProperty(prefix + KEY_CONFIG_VM_OPTIONS, "");
        cfg.additionalProperties = props.getProperty(prefix + KEY_CONFIG_PROPERTIES, "");
        return cfg;
    }

    private static void migrateLegacyConfiguration(WorkspaceRunConfigurationSet set,
                                                    Properties props, File projectRoot) {
        ProjectKind kind = ProjectKind.fromId(props.getProperty(LEGACY_KEY_KIND));
        String goal = props.getProperty(LEGACY_KEY_RUN_GOAL, "");
        if (kind == null && (goal == null || goal.isBlank())) {
            return;
        }
        List<ProjectKind> supported = WorkspaceProjectRunner.detectSupportedKinds(projectRoot);
        if (supported.isEmpty()) {
            return;
        }
        if (kind == null || !supported.contains(kind)) {
            kind = supported.get(0);
        }
        WorkspaceRunConfiguration cfg = set.createNew(kind, goal);
        cfg.name = jEdit.getProperty("workspace-run.config.default-name");
        if (cfg.runGoal == null || cfg.runGoal.isBlank()) {
            cfg.runGoal = WorkspaceProjectRunner.defaultRunGoalForKind(projectRoot, kind);
        }
        set.add(cfg);
        set.setDefault(cfg.id);
    }

    private static void ensureDefaultConfiguration(File projectRoot,
                                                   WorkspaceRunConfigurationSet set) {
        List<ProjectKind> supported = WorkspaceProjectRunner.detectSupportedKinds(projectRoot);
        if (supported.isEmpty()) {
            return;
        }
        ProjectKind kind = supported.get(0);
        String goal = WorkspaceProjectRunner.defaultRunGoalForKind(projectRoot, kind);
        WorkspaceRunConfiguration cfg = set.createNew(kind, goal);
        cfg.name = jEdit.getProperty("workspace-run.config.default-name");
        set.add(cfg);
        set.setDefault(cfg.id);
        try {
            save(projectRoot, set);
        } catch (IOException ex) {
            // keep in-memory default
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
