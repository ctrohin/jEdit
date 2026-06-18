/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.ArrayList;
import java.util.List;

import org.gjt.sp.jedit.jEdit;

final class WorkspaceRunConfigurationSet {

    private final List<WorkspaceRunConfiguration> configurations = new ArrayList<>();
    private String defaultId = "";
    private int nextId = 1;

    List<WorkspaceRunConfiguration> getConfigurations() {
        return List.copyOf(configurations);
    }

    WorkspaceRunConfiguration getDefault() {
        if (defaultId != null && !defaultId.isBlank()) {
            WorkspaceRunConfiguration found = findById(defaultId);
            if (found != null) {
                return found;
            }
        }
        return configurations.isEmpty() ? null : configurations.get(0);
    }

    String getDefaultId() {
        WorkspaceRunConfiguration def = getDefault();
        return def != null ? def.id : "";
    }

    void setDefault(String id) {
        if (findById(id) != null) {
            defaultId = id;
        }
    }

    WorkspaceRunConfiguration findById(String id) {
        if (id == null) {
            return null;
        }
        for (WorkspaceRunConfiguration cfg : configurations) {
            if (id.equals(cfg.id)) {
                return cfg;
            }
        }
        return null;
    }

    String allocateId() {
        return String.valueOf(nextId++);
    }

    WorkspaceRunConfiguration createNew(ProjectKind kind, String goal) {
        WorkspaceRunConfiguration cfg = new WorkspaceRunConfiguration();
        cfg.id = allocateId();
        cfg.kind = kind;
        cfg.runGoal = goal != null ? goal : "";
        return cfg;
    }

    void add(WorkspaceRunConfiguration cfg) {
        if (cfg == null || findById(cfg.id) != null) {
            return;
        }
        configurations.add(cfg);
        if (defaultId == null || defaultId.isBlank() || findById(defaultId) == null) {
            defaultId = cfg.id;
        }
    }

    boolean remove(WorkspaceRunConfiguration cfg) {
        if (cfg == null || configurations.size() <= 1) {
            return false;
        }
        if (!configurations.remove(cfg)) {
            return false;
        }
        if (cfg.id.equals(defaultId)) {
            defaultId = configurations.get(0).id;
        }
        return true;
    }

    WorkspaceRunConfiguration duplicate(WorkspaceRunConfiguration source) {
        if (source == null) {
            return null;
        }
        WorkspaceRunConfiguration copy = source.duplicateWithId(allocateId());
        configurations.add(copy);
        return copy;
    }

    boolean isEmpty() {
        return configurations.isEmpty();
    }

    String suggestUntitledName() {
        int n = 1;
        while (true) {
            String candidate = jEdit.getProperty("workspace-run.config.untitled",
                new String[] {String.valueOf(n)});
            boolean used = false;
            for (WorkspaceRunConfiguration cfg : configurations) {
                if (candidate.equals(cfg.name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
            n++;
        }
    }

    int getNextId() {
        return nextId;
    }

    void setNextId(int nextId) {
        this.nextId = Math.max(1, nextId);
    }

    WorkspaceRunConfigurationSet copy() {
        WorkspaceRunConfigurationSet copy = new WorkspaceRunConfigurationSet();
        copy.nextId = nextId;
        copy.defaultId = defaultId;
        for (WorkspaceRunConfiguration cfg : configurations) {
            copy.configurations.add(cfg.copy());
        }
        return copy;
    }
}
