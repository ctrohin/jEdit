/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

final class WorkspaceRunSettings {

    ProjectKind kind = ProjectKind.MAVEN;
    String runGoal = "";

    WorkspaceRunSettings copy() {
        WorkspaceRunSettings copy = new WorkspaceRunSettings();
        copy.kind = kind;
        copy.runGoal = runGoal;
        return copy;
    }
}
