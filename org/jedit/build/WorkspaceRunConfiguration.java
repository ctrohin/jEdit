/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

public final class WorkspaceRunConfiguration {

    String id;
    String name = "";
    ProjectKind kind = ProjectKind.MAVEN;
    String runGoal = "";
    String vmOptions = "";
    String additionalProperties = "";

    WorkspaceRunConfiguration copy() {
        WorkspaceRunConfiguration copy = new WorkspaceRunConfiguration();
        copy.id = id;
        copy.name = name;
        copy.kind = kind;
        copy.runGoal = runGoal;
        copy.vmOptions = vmOptions;
        copy.additionalProperties = additionalProperties;
        return copy;
    }

    WorkspaceRunConfiguration duplicateWithId(String newId) {
        WorkspaceRunConfiguration copy = copy();
        copy.id = newId;
        if (name != null && !name.isBlank()) {
            copy.name = name.trim() + " (copy)";
        }
        return copy;
    }

    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return "";
    }
}
