/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.gui;

/**
 * One invokable jEdit action shown in the command palette.
 */
public final class CommandPaletteEntry {

    private final String actionName;
    private final String label;
    private final String actionSetLabel;
    private final String shortcut;

    public CommandPaletteEntry(String actionName, String label,
                               String actionSetLabel, String shortcut) {
        this.actionName = actionName;
        this.label = label;
        this.actionSetLabel = actionSetLabel;
        this.shortcut = shortcut != null ? shortcut : "";
    }

    public String getActionName() {
        return actionName;
    }

    public String getLabel() {
        return label;
    }

    public String getActionSetLabel() {
        return actionSetLabel;
    }

    public String getShortcut() {
        return shortcut;
    }

    public String formatListText() {
        if (!shortcut.isEmpty()) {
            return label + "  (" + shortcut + ')';
        }
        return label;
    }

    public String formatListHtml() {
        String suffix;
        if (!shortcut.isEmpty()) {
            suffix = " <font color='#666666'>(" + escapeHtml(shortcut) + ")</font>";
        } else if (actionSetLabel != null && !actionSetLabel.isEmpty()) {
            suffix = " <font color='#666666'>— "
                + escapeHtml(actionSetLabel) + "</font>";
        } else {
            suffix = "";
        }
        return "<html>" + escapeHtml(label) + suffix + "</html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
