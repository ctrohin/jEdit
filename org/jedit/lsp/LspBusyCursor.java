/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.Cursor;
import java.util.Map;
import java.util.WeakHashMap;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

/**
 * Reference-counted wait cursor while an LSP request is in flight.
 */
final class LspBusyCursor {

    private static final Map<View, Integer> DEPTH = new WeakHashMap<>();

    private LspBusyCursor() {}

    static void show(View view) {
        if (view == null) {
            return;
        }
        LspAsync.runOnEdt(() -> {
            int depth = DEPTH.merge(view, 1, Integer::sum);
            if (depth == 1) {
                TextAreaPainter painter = view.getTextArea().getPainter();
                painter.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
        });
    }

    static void hide(View view) {
        if (view == null) {
            return;
        }
        LspAsync.runOnEdt(() -> {
            Integer depth = DEPTH.get(view);
            if (depth == null) {
                return;
            }
            if (depth <= 1) {
                DEPTH.remove(view);
                view.getTextArea().getPainter().resetCursor();
            } else {
                DEPTH.put(view, depth - 1);
            }
        });
    }
}
