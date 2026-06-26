/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.ThreadUtilities;

/**
 * Runs language-server I/O off the EDT. CompletableFuture stages that are
 * already complete run their callbacks on the calling thread; never chain LSP
 * work from the EDT without {@code *Async(..., EXECUTOR)}.
 */
final class LspAsync {

    static final Executor EXECUTOR = runnable -> ThreadUtilities.runInBackground(runnable);

    private LspAsync() {}

    static void runOffEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            ThreadUtilities.runInBackground(task);
        } else {
            task.run();
        }
    }

    static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    static CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, EXECUTOR);
    }

    static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR);
    }
}
