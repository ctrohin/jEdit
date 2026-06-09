/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.lsp;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import javax.swing.UIManager;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;

/**
 * In-memory back/forward navigation history for LSP go-to-definition jumps.
 */
final class LspNavigationHistory {

    private static final int MAX_ENTRIES = 100;

    private static final EBComponent VIEW_LISTENER = new EBComponent() {
        @Override
        public void handleMessage(EBMessage msg) {
            if (msg instanceof ViewUpdate update
                && update.getWhat() == ViewUpdate.CLOSED) {
                histories.remove(update.getView());
            }
        }
    };

    private static final Map<View, ViewHistory> histories = new IdentityHashMap<>();

    private LspNavigationHistory() {}

    static void install() {
        EditBus.addToBus(VIEW_LISTENER);
    }

    static void uninstall() {
        EditBus.removeFromBus(VIEW_LISTENER);
        histories.clear();
    }

    static NavigationEntry capture(View view) {
        if (view == null) {
            return null;
        }
        return NavigationEntry.from(view);
    }

    static void pushBack(View view, NavigationEntry entry) {
        if (LspGoToDefinition.isHistorySuppressed() || view == null || entry == null) {
            return;
        }

        ViewHistory history = histories.computeIfAbsent(view, ignored -> new ViewHistory());
        NavigationEntry last = history.back.peekLast();
        if (last != null && last.equals(entry)) {
            return;
        }

        history.back.addLast(entry);
        trim(history.back);
        history.forward.clear();
    }

    static void goBack(View view) {
        if (view == null) {
            return;
        }

        ViewHistory history = histories.get(view);
        if (history == null || history.back.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        NavigationEntry current = NavigationEntry.from(view);
        if (current != null) {
            history.forward.addLast(current);
            trim(history.forward);
        }

        NavigationEntry target = history.back.removeLast();
        navigate(view, target);
    }

    static void goForward(View view) {
        if (view == null) {
            return;
        }

        ViewHistory history = histories.get(view);
        if (history == null || history.forward.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        NavigationEntry current = NavigationEntry.from(view);
        if (current != null) {
            history.back.addLast(current);
            trim(history.back);
        }

        NavigationEntry target = history.forward.removeLast();
        navigate(view, target);
    }

    private static void navigate(View view, NavigationEntry entry) {
        LspGoToDefinition.setHistorySuppressed(true);
        try {
            LspGoToDefinition.navigateToEntry(view, entry.path(), entry.offset());
        } finally {
            LspGoToDefinition.setHistorySuppressed(false);
        }
    }

    private static void trim(ArrayDeque<NavigationEntry> entries) {
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
    }

    private static final class ViewHistory {
        final ArrayDeque<NavigationEntry> back = new ArrayDeque<>();
        final ArrayDeque<NavigationEntry> forward = new ArrayDeque<>();
    }

    record NavigationEntry(String path, int offset) {
        static NavigationEntry from(View view) {
            Buffer buffer = view.getBuffer();
            if (buffer == null) {
                return null;
            }
            JEditTextArea textArea = view.getTextArea();
            if (textArea == null) {
                return null;
            }
            return new NavigationEntry(buffer.getPath(), textArea.getCaretPosition());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof NavigationEntry other
                && offset == other.offset
                && Objects.equals(path, other.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, offset);
        }
    }
}
