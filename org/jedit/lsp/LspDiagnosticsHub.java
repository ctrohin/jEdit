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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.Diagnostic;

/**
 * Stores LSP diagnostics keyed by document URI and notifies Problems views.
 */
public final class LspDiagnosticsHub {

    private static final LspDiagnosticsHub INSTANCE = new LspDiagnosticsHub();

    private final Map<String, List<LspDiagnosticProblem>> byUri = new TreeMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private LspDiagnosticsHub() {}

    public static LspDiagnosticsHub getInstance() {
        return INSTANCE;
    }

    public synchronized void setDiagnostics(String uri, List<Diagnostic> diagnostics) {
        if (uri == null) {
            return;
        }
        if (diagnostics == null || diagnostics.isEmpty()) {
            byUri.remove(uri);
        } else {
            List<LspDiagnosticProblem> problems = new ArrayList<>(diagnostics.size());
            for (Diagnostic diagnostic : diagnostics) {
                if (diagnostic != null) {
                    problems.add(LspDiagnosticProblem.fromLsp(uri, diagnostic));
                }
            }
            Collections.sort(problems);
            byUri.put(uri, problems);
        }
        notifyListeners();
    }

    public synchronized List<FileProblems> getFileProblems() {
        List<FileProblems> groups = new ArrayList<>(byUri.size());
        for (Map.Entry<String, List<LspDiagnosticProblem>> entry : byUri.entrySet()) {
            groups.add(new FileProblems(entry.getKey(), entry.getValue()));
        }
        return groups;
    }

    public synchronized int getProblemCount() {
        int count = 0;
        for (List<LspDiagnosticProblem> problems : byUri.values()) {
            count += problems.size();
        }
        return count;
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            if (SwingUtilities.isEventDispatchThread()) {
                listener.run();
            } else {
                SwingUtilities.invokeLater(listener);
            }
        }
    }

    public static final class FileProblems {
        private final String uri;
        private final List<LspDiagnosticProblem> problems;

        FileProblems(String uri, List<LspDiagnosticProblem> problems) {
            this.uri = uri;
            this.problems = List.copyOf(problems);
        }

        public String getUri() {
            return uri;
        }

        public List<LspDiagnosticProblem> getProblems() {
            return problems;
        }
    }
}
