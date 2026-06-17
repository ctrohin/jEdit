/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.util.Log;

/**
 * Fetches and caches LSP document symbols for the Structure view.
 */
public final class LspStructureHub {

    private static final LspStructureHub INSTANCE = new LspStructureHub();
    private static final int REFRESH_DEBOUNCE_MS = 750;
    private static final int NOTIFY_DEBOUNCE_MS = 100;

    private final Map<String, StructureSnapshot> byUri = new TreeMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Timer refreshTimer;
    private Timer notifyTimer;
    private Buffer pendingBuffer;
    private int fetchGeneration;

    private LspStructureHub() {}

    public static LspStructureHub getInstance() {
        return INSTANCE;
    }

    public void clearAll() {
        synchronized (this) {
            byUri.clear();
            pendingBuffer = null;
            fetchGeneration++;
        }
        cancelRefreshTimer();
        scheduleNotifyListeners();
    }

    public void clear(String uri) {
        if (uri == null) {
            return;
        }
        synchronized (this) {
            if (byUri.remove(uri) == null) {
                return;
            }
        }
        scheduleNotifyListeners();
    }

    public void requestRefresh(Buffer buffer) {
        if (buffer == null || listeners.isEmpty()) {
            return;
        }
        synchronized (this) {
            pendingBuffer = buffer;
        }
        scheduleRefresh();
    }

    public synchronized StructureSnapshot getSnapshot(Buffer buffer) {
        if (buffer == null) {
            return StructureSnapshot.empty(null);
        }
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        StructureSnapshot snapshot = byUri.get(uri);
        return snapshot != null ? snapshot : StructureSnapshot.empty(uri);
    }

    public void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void scheduleRefresh() {
        SwingUtilities.invokeLater(() -> {
            if (refreshTimer == null) {
                refreshTimer = new Timer(REFRESH_DEBOUNCE_MS, e -> startFetch());
                refreshTimer.setRepeats(false);
            }
            refreshTimer.restart();
        });
    }

    private void cancelRefreshTimer() {
        SwingUtilities.invokeLater(() -> {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
        });
    }

    private void startFetch() {
        Buffer buffer;
        synchronized (this) {
            buffer = pendingBuffer;
            pendingBuffer = null;
        }
        if (buffer == null) {
            return;
        }

        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        int generation = ++fetchGeneration;
        StructureSnapshot existing;
        synchronized (this) {
            existing = byUri.get(uri);
        }
        boolean keepStaleTree = existing != null
            && existing.getState() == StructureSnapshot.State.READY;
        if (!keepStaleTree) {
            setSnapshot(uri, StructureSnapshot.loading(uri));
        }

        GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
        if (client == null || !client.hasActiveSession() || !client.isAlive()
            || client.isShuttingDown()) {
            setSnapshot(uri, StructureSnapshot.unavailable(uri,
                "lsp-structure.no-server"));
            return;
        }

        LspPlugin.flushBufferChangesAsync(buffer)
            .thenCompose(ignored -> client.whenReady())
            .thenCompose(ignored -> client.getServer().getTextDocumentService()
                .documentSymbol(documentSymbolParams(uri)))
            .thenAccept(symbols -> {
                if (generation != fetchGeneration) {
                    return;
                }
                List<LspSymbolHit> hits = LspLocations.fromDocumentSymbols(uri, symbols);
                setSnapshot(uri, StructureSnapshot.ready(uri, hits));
            })
            .exceptionally(ex -> {
                if (generation != fetchGeneration) {
                    return null;
                }
                if (isUnsupportedMethod(ex)) {
                    setSnapshot(uri, StructureSnapshot.unavailable(uri,
                        "lsp-structure.unsupported"));
                } else {
                    Log.log(Log.ERROR, LspStructureHub.class,
                        "Error fetching document symbols for " + buffer.getPath(), ex);
                    setSnapshot(uri, StructureSnapshot.unavailable(uri,
                        "lsp-structure.error"));
                }
                return null;
            });
    }

    private void setSnapshot(String uri, StructureSnapshot snapshot) {
        synchronized (this) {
            StructureSnapshot existing = byUri.get(uri);
            if (snapshot.equals(existing)) {
                return;
            }
            byUri.put(uri, snapshot);
        }
        scheduleNotifyListeners();
    }

    private void scheduleNotifyListeners() {
        SwingUtilities.invokeLater(() -> {
            if (notifyTimer == null) {
                notifyTimer = new Timer(NOTIFY_DEBOUNCE_MS, e -> fireListeners());
                notifyTimer.setRepeats(false);
            }
            notifyTimer.restart();
        });
    }

    private void fireListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    private static DocumentSymbolParams documentSymbolParams(String uri) {
        DocumentSymbolParams params = new DocumentSymbolParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        return params;
    }

    private static boolean isUnsupportedMethod(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ResponseErrorException responseError) {
                return responseError.getResponseError().getCode() == -32601;
            }
            current = current.getCause();
        }
        return false;
    }

    public static final class StructureSnapshot {
        public enum State {
            EMPTY,
            LOADING,
            READY,
            UNAVAILABLE
        }

        private final String uri;
        private final State state;
        private final List<LspSymbolHit> symbols;
        private final String messageKey;

        private StructureSnapshot(String uri, State state, List<LspSymbolHit> symbols,
                                  String messageKey) {
            this.uri = uri;
            this.state = state;
            this.symbols = symbols == null ? List.of() : List.copyOf(symbols);
            this.messageKey = messageKey;
        }

        static StructureSnapshot empty(String uri) {
            return new StructureSnapshot(uri, State.EMPTY, List.of(), "lsp-structure.empty");
        }

        static StructureSnapshot loading(String uri) {
            return new StructureSnapshot(uri, State.LOADING, List.of(),
                "lsp-structure.loading");
        }

        static StructureSnapshot ready(String uri, List<LspSymbolHit> symbols) {
            if (symbols == null || symbols.isEmpty()) {
                return new StructureSnapshot(uri, State.EMPTY, List.of(),
                    "lsp-structure.empty");
            }
            return new StructureSnapshot(uri, State.READY, symbols, null);
        }

        static StructureSnapshot unavailable(String uri, String messageKey) {
            return new StructureSnapshot(uri, State.UNAVAILABLE, List.of(), messageKey);
        }

        public String getUri() {
            return uri;
        }

        public State getState() {
            return state;
        }

        public List<LspSymbolHit> getSymbols() {
            return symbols;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public int getSymbolCount() {
            return countSymbols(symbols);
        }

        private static int countSymbols(List<LspSymbolHit> hits) {
            int count = 0;
            for (LspSymbolHit hit : hits) {
                count++;
                count += countSymbols(hit.getChildren());
            }
            return count;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StructureSnapshot other)) {
                return false;
            }
            return state == other.state
                && Objects.equals(uri, other.uri)
                && Objects.equals(symbols, other.symbols)
                && Objects.equals(messageKey, other.messageKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, state, symbols, messageKey);
        }
    }
}
