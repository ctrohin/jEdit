/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Color;

import javax.swing.UIManager;

final class GitColors {

    private GitColors() {}

    static boolean isDarkTheme() {
        Color background = UIManager.getColor("List.background");
        if (background == null) {
            background = UIManager.getColor("Panel.background");
        }
        return isDark(background);
    }

    static Color changeForeground(GitModels.ChangeKind kind) {
        return isDarkTheme() ? darkForeground(kind) : lightForeground(kind);
    }

    static Color changeBackground(GitModels.ChangeKind kind) {
        return isDarkTheme() ? darkBackground(kind) : lightBackground(kind);
    }

    static Color diffInserted() {
        return isDarkTheme() ? new Color(86, 211, 100) : new Color(26, 127, 55);
    }

    static Color diffDeleted() {
        return isDarkTheme() ? new Color(255, 123, 114) : new Color(207, 34, 46);
    }

    static Color currentBranchForeground() {
        return isDarkTheme() ? new Color(86, 211, 100) : new Color(26, 127, 55);
    }

    private static Color lightForeground(GitModels.ChangeKind kind) {
        return switch (kind) {
            case UNTRACKED -> new Color(9, 105, 218);
            case ADDED -> new Color(26, 127, 55);
            case MODIFIED -> new Color(188, 76, 0);
            case DELETED -> new Color(207, 34, 46);
            case RENAMED -> new Color(130, 80, 223);
            case COPIED -> new Color(14, 138, 127);
            case CONFLICT -> new Color(185, 28, 28);
            case OTHER -> null;
        };
    }

    private static Color darkForeground(GitModels.ChangeKind kind) {
        return switch (kind) {
            case UNTRACKED -> new Color(121, 192, 255);
            case ADDED -> new Color(86, 211, 100);
            case MODIFIED -> new Color(255, 166, 87);
            case DELETED -> new Color(255, 123, 114);
            case RENAMED -> new Color(210, 168, 255);
            case COPIED -> new Color(86, 212, 221);
            case CONFLICT -> new Color(255, 148, 146);
            case OTHER -> null;
        };
    }

    private static Color lightBackground(GitModels.ChangeKind kind) {
        return switch (kind) {
            case UNTRACKED -> new Color(221, 235, 255);
            case ADDED -> new Color(218, 243, 226);
            case MODIFIED -> new Color(255, 240, 214);
            case DELETED -> new Color(255, 226, 228);
            case RENAMED -> new Color(240, 230, 255);
            case COPIED -> new Color(214, 245, 242);
            case CONFLICT -> new Color(255, 220, 220);
            case OTHER -> null;
        };
    }

    private static Color darkBackground(GitModels.ChangeKind kind) {
        return switch (kind) {
            case UNTRACKED -> new Color(28, 42, 64);
            case ADDED -> new Color(24, 48, 32);
            case MODIFIED -> new Color(58, 42, 24);
            case DELETED -> new Color(58, 28, 28);
            case RENAMED -> new Color(46, 32, 58);
            case COPIED -> new Color(22, 48, 50);
            case CONFLICT -> new Color(62, 28, 28);
            case OTHER -> null;
        };
    }

    private static boolean isDark(Color color) {
        if (color == null) {
            return false;
        }
        double luminance = (0.299 * color.getRed())
            + (0.587 * color.getGreen())
            + (0.114 * color.getBlue());
        return luminance < 128;
    }
}
