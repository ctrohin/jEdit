/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores the current LSP symbol search result and a history of the last searches.
 */
public final class LspSymbolSearchHub {

    public static final int MAX_HISTORY = 10;

    private static final LspSymbolSearchHub INSTANCE = new LspSymbolSearchHub();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> structureListeners = new CopyOnWriteArrayList<>();
    private final List<LspSymbolSearchResult> history = new ArrayList<>();
    private LspSymbolSearchResult current;

    private LspSymbolSearchHub() {}

    public static LspSymbolSearchHub getInstance() {
        return INSTANCE;
    }

    public synchronized LspSymbolSearchResult getCurrent() {
        return current;
    }

    public synchronized List<LspSymbolSearchResult> getHistory() {
        return List.copyOf(history);
    }

    public synchronized void publish(LspSymbolSearchResult result) {
        Objects.requireNonNull(result, "result");
        current = result;
        history.removeIf(entry -> entry.getId().equals(result.getId()));
        history.add(0, result);
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        fireChanged();
    }

    public synchronized void select(String resultId) {
        if (resultId == null) {
            return;
        }
        for (LspSymbolSearchResult entry : history) {
            if (entry.getId().equals(resultId)) {
                current = entry;
                fireChanged();
                return;
            }
        }
    }

    public synchronized void clear() {
        current = null;
        history.clear();
        fireChanged();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public void addStructureListener(Runnable listener) {
        structureListeners.add(listener);
    }

    public void removeStructureListener(Runnable listener) {
        structureListeners.remove(listener);
    }

    public void fireStructureChanged() {
        for (Runnable listener : structureListeners) {
            listener.run();
        }
    }

    private void fireChanged() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
