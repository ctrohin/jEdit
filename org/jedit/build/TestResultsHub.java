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
import java.util.concurrent.CopyOnWriteArrayList;

final class TestResultsHub {

    private static final TestResultsHub INSTANCE = new TestResultsHub();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile TestRunResult current = TestRunResult.empty("", null, null, -1);

    static TestResultsHub getInstance() {
        return INSTANCE;
    }

    TestRunResult getResult() {
        return current;
    }

    void setResult(TestRunResult result) {
        current = result != null
            ? result
            : TestRunResult.empty("", null, null, -1);
        notifyListeners();
    }

    void clear() {
        current = TestRunResult.empty("", null, null, -1);
        notifyListeners();
    }

    void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}
