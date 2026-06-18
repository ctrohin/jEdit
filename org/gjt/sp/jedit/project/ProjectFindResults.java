/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.jedit.lsp.LspPlugin;
import org.jedit.lsp.LspSymbolHit;

/**
 * Publishes project search matches to the Find results dockable.
 */
public final class ProjectFindResults {

    private ProjectFindResults() {}

    public static void publish(View view, String query, List<ProjectSearchMatch> matches) {
        if (view == null || matches == null || matches.isEmpty()) {
            return;
        }
        List<LspSymbolHit> hits = new ArrayList<>(matches.size());
        for (ProjectSearchMatch match : matches) {
            hits.add(match.toLspSymbolHit());
        }
        LspPlugin.publishProjectSearchResults(view, query, hits);
    }

    public static void openMatch(View view, ProjectSearchMatch match) {
        if (view == null || match == null) {
            return;
        }
        LspPlugin.openSymbolHit(view, match.toLspSymbolHit());
    }

    public static void openFile(View view, String path) {
        if (view == null || path == null) {
            return;
        }
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Buffer.CARET, Integer.valueOf(0));
        props.put(Buffer.CARET_POSITIONED, Boolean.TRUE);
        Buffer buffer = jEdit.openFile(view, null, path, false, props);
        if (buffer == null) {
            return;
        }
        view.toFront();
        view.requestFocus();
    }
}
