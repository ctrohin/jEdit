/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.gjt.sp.jedit.ActionSet;
import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.GenericGUIUtilities;

/**
 * Collects and filters jEdit actions for the command palette.
 */
final class CommandPaletteIndex {

    private static final int MAX_RESULTS = 200;

    private CommandPaletteIndex() {}

    static List<CommandPaletteEntry> build() {
        List<CommandPaletteEntry> entries = new ArrayList<>();
        for (String name : jEdit.getActionNames()) {
            if ("command-palette".equals(name)) {
                continue;
            }
            EditAction action = jEdit.getAction(name);
            if (action == null) {
                continue;
            }
            String label = action.getLabel();
            if (label == null || label.isEmpty() || label.equals(name + ".label")) {
                continue;
            }
            label = GenericGUIUtilities.prettifyMenuLabel(label);
            ActionSet actionSet = jEdit.getActionSetForAction(name);
            String setLabel = actionSet != null ? actionSet.getLabel() : "";
            String shortcut = GUIUtilities.getShortcutLabel(name, true);
            entries.add(new CommandPaletteEntry(name, label, setLabel, shortcut));
        }
        entries.sort(Comparator.comparing(CommandPaletteEntry::getLabel,
            String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    static List<CommandPaletteEntry> filter(List<CommandPaletteEntry> entries,
                                            String query) {
        if (query == null || query.isBlank()) {
            int limit = Math.min(entries.size(), MAX_RESULTS);
            return entries.subList(0, limit);
        }
        String lower = query.toLowerCase(Locale.ROOT);
        List<CommandPaletteEntry> matches = new ArrayList<>();
        for (CommandPaletteEntry entry : entries) {
            if (matches(entry, lower)) {
                matches.add(entry);
            }
            if (matches.size() >= MAX_RESULTS) {
                break;
            }
        }
        matches.sort((a, b) -> {
            int rankA = rank(a, lower);
            int rankB = rank(b, lower);
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
        return matches;
    }

    private static boolean matches(CommandPaletteEntry entry, String lower) {
        if (entry.getLabel().toLowerCase(Locale.ROOT).contains(lower)) {
            return true;
        }
        if (entry.getActionName().toLowerCase(Locale.ROOT).contains(lower)) {
            return true;
        }
        String setLabel = entry.getActionSetLabel();
        return setLabel != null
            && setLabel.toLowerCase(Locale.ROOT).contains(lower);
    }

    private static int rank(CommandPaletteEntry entry, String lower) {
        String label = entry.getLabel().toLowerCase(Locale.ROOT);
        if (label.startsWith(lower)) {
            return 0;
        }
        if (label.contains(lower)) {
            return 1;
        }
        if (entry.getActionName().toLowerCase(Locale.ROOT).contains(lower)) {
            return 2;
        }
        return 3;
    }
}
