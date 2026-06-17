/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.Locale;

/**
 * Build / run tool kinds supported by the workspace run action.
 */
public enum ProjectKind {
    MAVEN("maven"),
    GRADLE("gradle"),
    NPM("npm"),
    FLUTTER("flutter"),
    ANT("ant"),
    PIP("pip");

    private final String id;

    ProjectKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ProjectKind fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ProjectKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
