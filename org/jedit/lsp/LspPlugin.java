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

import io.vavr.control.Try;
import org.eclipse.lsp4j.services.LanguageServer;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.EditorExiting;
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

import org.eclipse.lsp4j.*;
import org.jedit.lsp.buildconfig.BuildConfigLspSupport;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Main entry point for the LSP integration in jEdit.
 * It listens for buffer events and manages LSP clients for different languages.
 */
public class LspPlugin extends EditPlugin implements EBComponent {

    // Package-protected so sub-components can access clients
    final Map<String, GenericLspClient> clients = new HashMap<>();
    private final Map<Buffer, BufferLspHandler> handlers = new HashMap<>();
    private final List<GenericLspClient> startedClients = new CopyOnWriteArrayList<>();
    private final Map<String, Timer> idleShutdownTimers = new HashMap<>();
    private final Object workspaceLock = new Object();
    private final int DEFAULT_LEVEL = Log.ERROR;
    private String currentProjectRoot;
    private static LspPlugin instance;
    private volatile boolean stopped;
    private volatile int workspaceGeneration;
    private Thread shutdownHook;

    public LspPlugin() {
        instance = this;
    }

    public static LspPlugin getInstance() {
        return instance;
    }

    static boolean isStopped() {
        LspPlugin plugin = getInstance();
        return plugin == null || plugin.stopped;
    }
    @Override
    public void start() {
        Log.log(DEFAULT_LEVEL, this, "LSP Plugin starting...");
        LspDiagnosticHighlights.install();
        LspGoToDefinition.install();
        LspNavigationHistory.install();
        LspSignatureHelp.install();
        shutdownHook = new Thread(this::shutdownHookKill, "jedit-lsp-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Stops LSP clients during application exit without blocking the UI thread.
     */
    public void stopForExit() {
        if (stopped) {
            return;
        }
        stopped = true;
        workspaceGeneration++;
        cancelAllIdleShutdownTimers();
        removeShutdownHook();
        uninstallLspUi();
        markAllClientsShuttingDown();
        markAllHandlersClosedForExit();
        shutdownAllClientsForExit();
        startedClients.clear();
        clients.clear();
        handlers.clear();
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        workspaceGeneration++;
        cancelAllIdleShutdownTimers();
        removeShutdownHook();
        uninstallLspUi();
        markAllClientsShuttingDown();
        markAllHandlersClosedForExit();
        for (GenericLspClient client : startedClients) {
            client.shutdownForExit();
        }
        startedClients.clear();
        clients.clear();
        handlers.clear();
        EditBus.removeFromBus(this);
    }

    private void cancelAllIdleShutdownTimers() {
        synchronized (this) {
            for (Timer timer : idleShutdownTimers.values()) {
                timer.stop();
            }
            idleShutdownTimers.clear();
        }
    }

    private void cancelIdleClientShutdown(String modeName) {
        synchronized (this) {
            Timer timer = idleShutdownTimers.remove(modeName);
            if (timer != null) {
                timer.stop();
            }
        }
    }

    private void scheduleIdleClientShutdown(String modeName, GenericLspClient client) {
        if (stopped) {
            return;
        }
        cancelIdleClientShutdown(modeName);
        Timer timer = new Timer(2000, e -> {
            if (stopped) {
                return;
            }
            synchronized (LspPlugin.this) {
                if (stopped || client.getBufferCount() > 0) {
                    return;
                }
                if (clients.get(modeName) != client) {
                    return;
                }
                clients.remove(modeName);
                startedClients.remove(client);
            }
            client.shutdownForExit();
        });
        timer.setRepeats(false);
        synchronized (this) {
            idleShutdownTimers.put(modeName, timer);
        }
        timer.start();
    }

    private void shutdownHookKill() {
        for (GenericLspClient client : startedClients) {
            client.shutdownForExit();
        }
    }

    private void shutdownAllClientsForExit() {
        for (GenericLspClient client : startedClients) {
            client.shutdownForExit();
        }
    }

    private void markAllHandlersClosedForExit() {
        synchronized (this) {
            for (BufferLspHandler handler : handlers.values()) {
                handler.markClosedForExit();
            }
        }
    }

    private void markAllClientsShuttingDown() {
        for (GenericLspClient client : startedClients) {
            client.markShuttingDown();
        }
    }

    private void uninstallLspUi() {
        LspSignatureHelp.uninstall();
        LspNavigationHistory.uninstall();
        LspGoToDefinition.uninstall();
        LspDiagnosticHighlights.uninstall();
    }

    private void removeShutdownHook() {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }
    }

    /**
     * Request LSP completions at the current caret position.
     * This can be called from actions or other plugins to trigger completion.
     */
    public static void completeLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspCompletion.completeLsp(v, client));
    }

    /**
     * Request LSP code actions (quick fixes, refactorings) at the caret or selection.
     */
    public static void codeActionsLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspCodeActions.codeActionsLsp(v, client));
    }

    /**
     * Request LSP refactorings at the caret or selection.
     */
    public static void refactorLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspCodeActions.refactorLsp(v, client));
    }

    /**
     * Rename the LSP symbol at the caret.
     */
    public static void renameLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspRename.renameLsp(v, client));
    }

    /**
     * Go to the LSP definition of the symbol at the caret.
     */
    public static void goToDefinitionLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspGoToDefinition.goToDefinitionLsp(v, client));
    }

    public static void findReferencesLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.findReferencesLsp(v, client));
    }

    public static void findImplementationsLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.findImplementationsLsp(v, client));
    }

    public static void findTypeDefinitionLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.findTypeDefinitionLsp(v, client));
    }

    public static void findDeclarationLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.findDeclarationLsp(v, client));
    }

    public static void documentSymbolsLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.documentSymbolsLsp(v, client));
    }

    public static void workspaceSymbolLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.workspaceSymbolsLsp(v, client));
    }

    public static void callHierarchyLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSymbolSearches.callHierarchyLsp(v, client));
    }

    public static void showSymbolResults(View view) {
        if (view != null) {
            LspSymbolSearches.showResults(view);
        }
    }

    public static void showStructure(View view) {
        LspStructureView.show(view);
    }

    /**
     * Show method parameter info when the caret is inside a method call's parentheses.
     */
    public static void signatureHelpLsp(View view) {
        invokeLspFeature(view, (v, client) -> LspSignatureHelp.showSignatureHelp(v, client));
    }

    /**
     * Navigate back to the previous location before an LSP go-to-definition jump.
     */
    public static void goBackLsp(View view) {
        if (view != null) {
            LspNavigationHistory.goBack(view);
        }
    }

    /**
     * Navigate forward after an LSP go-back action.
     */
    public static void goForwardLsp(View view) {
        if (view != null) {
            LspNavigationHistory.goForward(view);
        }
    }

    @FunctionalInterface
    private interface LspFeature {
        void run(View view, GenericLspClient client);
    }

    /**
     * LSP document version last sent to the server for this buffer
     * ({@code didOpen} or {@code didClose}/{@code didOpen} republish).
     */
    static int getDocumentVersion(Buffer buffer) {
        BufferLspHandler handler = getInstance().handlers.get(buffer);
        if (handler == null) {
            return 0;
        }
        return handler.getLastSyncedVersion();
    }

    /**
     * Same LSP sync as saving the buffer: cancel pending {@code didChange},
     * then {@code didClose} + {@code didOpen} with current text.
     */
    static void republishBufferToServer(Buffer buffer) {
        republishBufferToServerAsync(buffer);
    }

    static CompletableFuture<Void> republishBufferToServerAsync(Buffer buffer) {
        BufferLspHandler handler = getInstance().handlers.get(buffer);
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        return handler.syncToServerAsync();
    }

    /**
     * Sends any buffered {@code didChange} events immediately so LSP features
     * (completion, signature help, etc.) see the latest editor text.
     */
    static CompletableFuture<Void> flushBufferChangesAsync(Buffer buffer) {
        LspPlugin plugin = getInstance();
        if (plugin == null || buffer == null) {
            return CompletableFuture.completedFuture(null);
        }
        BufferLspHandler handler = plugin.handlers.get(buffer);
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        return handler.flushPendingChangeAsync();
    }

    static void beginApplyingLspEdits() {
        applyingLspEdits = true;
    }

    static void endApplyingLspEdits() {
        applyingLspEdits = false;
    }

    private static volatile boolean applyingLspEdits;

    static GenericLspClient getClientForBuffer(Buffer buffer) {
        LspPlugin plugin = getInstance();
        if (plugin == null || buffer == null) {
            return null;
        }
        return plugin.resolveClientForMode(resolveLspMode(buffer), buffer);
    }

    /**
     * Returns an already-running client without starting a new server on the
     * calling thread (safe for the EDT).
     */
    static GenericLspClient getExistingClientForBuffer(Buffer buffer) {
        LspPlugin plugin = getInstance();
        if (plugin == null || buffer == null) {
            return null;
        }
        return plugin.lookupClientForMode(resolveLspMode(buffer), buffer);
    }

    private static void invokeLspFeature(View view, LspFeature feature) {
        if (view == null) {
            return;
        }

        LspAsync.runOffEdt(() -> {
            Buffer buffer = view.getBuffer();
            if (buffer == null || buffer.isClosed()) {
                return;
            }

            final String modeName = resolveLspMode(buffer);
            LspPlugin lspPlugin = getInstance();
            if (lspPlugin == null || lspPlugin.stopped) {
                return;
            }

            GenericLspClient client = lspPlugin.resolveClientForMode(modeName, buffer);
            if (client != null && client.hasActiveSession() && client.isAlive()
                && !client.isShuttingDown()) {
                LspAsync.runOnEdt(() -> feature.run(view, client));
            } else if (client != null) {
                Log.log(Log.WARNING, LspPlugin.class,
                    "LSP server for " + modeName + " is not running, attempting restart");
                if (lspPlugin.restartServer(client) && client.hasActiveSession()) {
                    LspAsync.runOnEdt(() -> feature.run(view, client));
                } else {
                    LspAsync.runOnEdt(() -> {
                        Log.log(Log.ERROR, LspPlugin.class,
                            "Failed to restart LSP server for " + modeName);
                        javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                    });
                }
            } else {
                LspAsync.runOnEdt(() -> {
                    Log.log(Log.WARNING, LspPlugin.class,
                        "LSP client not available for mode " + modeName);
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                });
            }
        });
    }

    @EditBus.EBHandler
    public void handleBufferUpdate(BufferUpdate message) {
        Buffer buffer = message.getBuffer();
        if (message.getWhat() == BufferUpdate.LOADED) {
            LspAsync.runOffEdt(() -> startLspForBuffer(buffer));
        } else if (message.getWhat() == BufferUpdate.CLOSED) {
            stopLspForBuffer(buffer);
        } else if (message.getWhat() == BufferUpdate.SAVED) {
            BufferLspHandler handler = handlers.get(buffer);
            if (handler != null) {
                handler.syncOnSave();
            }
        }
    }

    static String resolveLspMode(Buffer buffer) {
        String buildMode = BuildConfigLspSupport.resolveLspMode(buffer);
        return buildMode != null ? buildMode : buffer.getMode().getName();
    }

    private synchronized void startLspForBuffer(Buffer buffer) {
        if (stopped) {
            return;
        }
        String modeName = resolveLspMode(buffer);
        if (!LspConfig.isServerConfigured(modeName)) {
            Log.log(DEFAULT_LEVEL, this,"LSP not available for mode " + modeName + ".");
            return;
        }

        cancelIdleClientShutdown(modeName);

        Optional.ofNullable(clients.get(modeName))
            .orElseGet(() -> createLspClient(modeName))
            .incrementBufferCount();

        BufferLspHandler handler = new BufferLspHandler(buffer, clients.get(modeName));
        buffer.addBufferListener(handler);
        handlers.put(buffer, handler);
        handler.notifyOpen();
    }

    private GenericLspClient createLspClient(final String modeName) {
        final var client = new GenericLspClient(modeName);
        startedClients.add(client);
        startMetaClient(client);
        clients.put(modeName, client);
        return client;
    }

    private void startMetaClient(GenericLspClient client) {
        if (stopped || client.hasActiveSession()) {
            return;
        }
        String projectRoot = resolveLspProjectRoot(client);
        if (projectRoot == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            if (stopped) {
                return;
            }
            startMetaClientBlocking(client, projectRoot);
        });
    }

    private void startMetaClientBlocking(GenericLspClient client, String projectRoot) {
        if (stopped || projectRoot == null) {
            return;
        }
        if (client.hasActiveSession()) {
            stopMetaClient(client);
            if (stopped) {
                return;
            }
        }
        Try.of(() -> {
            client.start(client.getMode(), projectRoot);
            return client;
        }).onSuccess(m -> {
            m.setServerStarted(true);
            SwingUtilities.invokeLater(() -> {
                if (!stopped) {
                    reopenBuffersForClient(m);
                }
            });
        }).onFailure(e -> Log.log(Log.ERROR, this,
            "Failed to start LSP server for " + client.getMode(), e));
    }

    private String resolveLspProjectRoot(GenericLspClient client) {
        if (currentProjectRoot != null) {
            if (JdtlsSupport.isJavaMode(client.getMode())) {
                return JdtlsSupport.resolveProjectRoot(currentProjectRoot);
            }
            return currentProjectRoot;
        }
        if (!JdtlsSupport.isJavaMode(client.getMode())) {
            return null;
        }
        for (BufferLspHandler handler : handlers.values()) {
            if (!client.getMode().equals(resolveLspMode(handler.buffer))) {
                continue;
            }
            String path = handler.buffer.getPath();
            if (path != null && !path.isBlank()) {
                File parent = new File(path).getParentFile();
                if (parent != null) {
                    return JdtlsSupport.resolveProjectRoot(parent.getAbsolutePath());
                }
            }
        }
        return null;
    }

    private void stopMetaClient(GenericLspClient client) {
        if (stopped) {
            client.shutdownForExit();
            return;
        }
        if (client.hasActiveSession()) {
            client.shutdownAndWait();
        }
        client.resetSessionState();
    }

    private void stopMetaClientAsync(GenericLspClient client) {
        if (stopped) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            if (stopped) {
                return;
            }
            synchronized (workspaceLock) {
                if (stopped) {
                    return;
                }
                stopMetaClient(client);
            }
        });
    }

    private synchronized void stopLspForBuffer(Buffer buffer) {
        BufferLspHandler handler = handlers.remove(buffer);
        if (handler != null) {
            buffer.removeBufferListener(handler);
            handler.notifyClose();
        }
        String modeName = resolveLspMode(buffer);
        Optional.ofNullable(clients.get(modeName))
            .stream()
            .peek(GenericLspClient::decrementBufferCount)
            .filter(m -> m.getBufferCount() == 0)
            .findFirst()
            .ifPresent(c -> scheduleIdleClientShutdown(modeName, c));
    }

    private static class BufferLspHandler extends BufferAdapter {
        private static final int CHANGE_SYNC_DELAY_MS = 400;

        private final Buffer buffer;
        private final GenericLspClient client;
        private final Timer changeSyncTimer;
        private final List<TextDocumentContentChangeEvent> pendingChanges =
            new ArrayList<>();
        private int version = 0;
        private volatile boolean needsSync;
        private volatile boolean documentOpenOnServer;
        private volatile boolean closed;

        BufferLspHandler(Buffer buffer, GenericLspClient client) {
            this.buffer = buffer;
            this.client = client;
            changeSyncTimer = new Timer(CHANGE_SYNC_DELAY_MS,
                e -> LspAsync.runOffEdt(this::flushChangeToServer));
            changeSyncTimer.setRepeats(false);
        }

        /** Version sent in the last {@code didOpen} or {@code didChange} for this buffer. */
        int getLastSyncedVersion() {
            return Math.max(0, version - 1);
        }

        void notifyOpen() {
            notifyOpen(0);
        }

        private void notifyOpen(int attempt) {
            if (closed || isStopped()) {
                return;
            }
            if (client.isShuttingDown() || client.getServer() == null) {
                scheduleNotifyOpenRetry(attempt);
                return;
            }
            client.whenReady().thenComposeAsync(ignored ->
                LspAsync.run(() -> {
                    if (shouldSkipLspSync() || client.isShuttingDown()) {
                        return;
                    }
                    if (client.isReadyForDocumentSync()) {
                        sendDidOpen();
                    }
                }), LspAsync.EXECUTOR).exceptionally(ex -> {
                if (!closed && !isStopped() && attempt < 12) {
                    scheduleNotifyOpenRetry(attempt);
                    return null;
                }
                if (!shouldSkipLspSync() && !isTransportClosed(ex)) {
                    Log.log(Log.ERROR, this,
                        "Failed waiting for LSP server before didOpen: " + buffer.getPath(), ex);
                }
                return null;
            });
        }

        private void scheduleNotifyOpenRetry(int attempt) {
            if (closed || isStopped() || attempt >= 12) {
                return;
            }
            int delayMs = Math.min(250 * (attempt + 1), 2000);
            Timer timer = new Timer(delayMs, e -> notifyOpen(attempt + 1));
            timer.setRepeats(false);
            timer.start();
        }

        void markClosedForExit() {
            closed = true;
            changeSyncTimer.stop();
            documentOpenOnServer = false;
            pendingChanges.clear();
        }

        void resetVersionForServerRestart() {
            changeSyncTimer.stop();
            pendingChanges.clear();
            version = 0;
            needsSync = false;
            documentOpenOnServer = false;
        }

        void flushPendingChange() {
            changeSyncTimer.stop();
            LspAsync.runOffEdt(this::flushChangeToServer);
        }

        CompletableFuture<Void> flushPendingChangeAsync() {
            if (client.getServer() == null || client.isShuttingDown()) {
                return CompletableFuture.completedFuture(null);
            }
            return client.whenReady().thenComposeAsync(ignored ->
                LspAsync.run(() -> {
                    changeSyncTimer.stop();
                    if (shouldSkipLspSync() || !client.isReadyForDocumentSync()) {
                        return;
                    }
                    if (!needsSync || pendingChanges.isEmpty()) {
                        return;
                    }
                    if (!documentOpenOnServer) {
                        sendDidOpen();
                        return;
                    }
                    flushChangeToServer();
                }), LspAsync.EXECUTOR);
        }

        void syncOnSave() {
            syncToServerAsync();
        }

        CompletableFuture<Void> syncToServerAsync() {
            changeSyncTimer.stop();
            pendingChanges.clear();
            needsSync = false;
            return republishDocumentToServerAsync();
        }

        CompletableFuture<Void> republishDocumentToServerAsync() {
            if (client.getServer() == null || client.isShuttingDown()) {
                return CompletableFuture.completedFuture(null);
            }
            return client.whenReady().thenComposeAsync(ignored ->
                LspAsync.run(() -> {
                    if (LspPlugin.isStopped() || shouldSkipLspSync()
                        || !client.isReadyForDocumentSync()) {
                        return;
                    }
                    sendDidClose();
                    if (!shouldSkipLspSync() && client.isReadyForDocumentSync()) {
                        sendDidOpen();
                    }
                }), LspAsync.EXECUTOR);
        }

        private void sendDidOpen() {
            if (shouldSkipLspSync() || !client.isReadyForDocumentSync()) {
                return;
            }
            LanguageServer server = client.getServer();
            if (server == null) {
                return;
            }

            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
            TextDocumentItem item = new TextDocumentItem();
            item.setUri(LspDocumentUri.pathToUri(buffer.getPath()));
            item.setLanguageId(resolveLspMode(buffer));
            item.setVersion(version++);
            item.setText(buffer.getText(0, buffer.getLength()));
            params.setTextDocument(item);

            try {
                if (shouldSkipLspSync() || !client.isReadyForDocumentSync()) {
                    return;
                }
                server.getTextDocumentService().didOpen(params);
                documentOpenOnServer = true;
                pendingChanges.clear();
                needsSync = false;
                LspStructureHub.getInstance().requestRefresh(buffer);
            } catch (RuntimeException ex) {
                if (!shouldSkipLspSync() && !isTransportClosed(ex)) {
                    Log.log(Log.ERROR, this,
                        "Failed to send didOpen for buffer " + buffer.getPath(), ex);
                }
            }
        }

        void notifyClose() {
            closed = true;
            changeSyncTimer.stop();
            boolean wasOpenOnServer = documentOpenOnServer;
            documentOpenOnServer = false;
            pendingChanges.clear();
            LspDiagnosticsHub.getInstance().setDiagnostics(
                LspDocumentUri.pathToUri(buffer.getPath()), List.of());
            LspStructureHub.getInstance().clear(
                LspDocumentUri.pathToUri(buffer.getPath()));

            if (!wasOpenOnServer) {
                return;
            }
            ThreadUtilities.runInBackground(() -> {
                if (LspPlugin.isStopped() || shouldSkipLspSync()) {
                    return;
                }
                sendDidClose();
            });
        }

        private void sendDidClose() {
            if (shouldSkipLspSync()) {
                return;
            }
            LanguageServer server = client.getServer();
            if (server == null) {
                return;
            }
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
            params.setTextDocument(new TextDocumentIdentifier(
                LspDocumentUri.pathToUri(buffer.getPath())));
            try {
                server.getTextDocumentService().didClose(params);
            } catch (RuntimeException ex) {
                if (!shouldSkipLspSync() && !isTransportClosed(ex)) {
                    Log.log(Log.ERROR, this,
                        "Failed to send didClose for buffer " + buffer.getPath(), ex);
                }
            }
        }

        @Override
        public void preContentRemoved(JEditBuffer buffer, int startLine, int offset,
            int numLines, int length)
        {
            if (shouldIgnoreBufferChange()) {
                return;
            }

            Position start = offsetToPosition(offset);
            Position end = offsetToPosition(offset + length);
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
            event.setRange(new Range(start, end));
            event.setRangeLength(length);
            event.setText("");
            pendingChanges.add(event);
            needsSync = true;
        }

        @Override
        public void contentInserted(JEditBuffer buffer, int startLine, int offset,
            int numLines, int length)
        {
            if (shouldIgnoreBufferChange()) {
                return;
            }

            Position start = offsetToPosition(offset);
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
            event.setRange(new Range(start, start));
            event.setRangeLength(0);
            event.setText(this.buffer.getText(offset, length));
            pendingChanges.add(event);
            needsSync = true;
            notifyChange();
            LspCompletionTriggers.onTextInserted(this.buffer, offset, length);
        }

        @Override
        public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
            int numLines, int length)
        {
            if (shouldIgnoreBufferChange()) {
                return;
            }
            notifyChange();
        }

        private boolean shouldIgnoreBufferChange() {
            return applyingLspEdits || buffer.isLoading() || shouldSkipLspSync();
        }

        private boolean shouldSkipLspSync() {
            return closed || isStopped() || client.isShuttingDown();
        }

        private Position offsetToPosition(int offset) {
            int line = buffer.getLineOfOffset(offset);
            int lineStart = buffer.getLineStartOffset(line);
            return new Position(line, offset - lineStart);
        }

        private void notifyChange() {
            if (client.getServer() == null || !needsSync) {
                return;
            }
            changeSyncTimer.restart();
        }

        private void flushChangeToServer() {
            if (shouldSkipLspSync() || !client.isReadyForDocumentSync()
                || client.getServer() == null || !needsSync || pendingChanges.isEmpty()) {
                return;
            }
            if (!documentOpenOnServer) {
                changeSyncTimer.restart();
                return;
            }

            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
            VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier();
            id.setUri(LspDocumentUri.pathToUri(buffer.getPath()));
            id.setVersion(version++);
            params.setTextDocument(id);
            params.setContentChanges(new ArrayList<>(pendingChanges));

            List<TextDocumentContentChangeEvent> sentChanges =
                new ArrayList<>(pendingChanges);
            LanguageServer server = client.getServer();
            if (server == null) {
                return;
            }
            Try.of(() -> {
                if (shouldSkipLspSync() || !client.isReadyForDocumentSync()) {
                    return false;
                }
                server.getTextDocumentService().didChange(params);
                pendingChanges.removeAll(sentChanges);
                if (pendingChanges.isEmpty()) {
                    needsSync = false;
                }
                return true;
            }).onFailure(e -> {
                if (!shouldSkipLspSync() && !isTransportClosed(e)) {
                    Log.log(Log.ERROR, this,
                        "Failed to send didChange for buffer " + buffer.getPath(), e);
                }
            });
        }
    }

    private static boolean isTransportClosed(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.io.IOException io) {
                String message = io.getMessage();
                if (message != null) {
                    String lower = message.toLowerCase(Locale.ROOT);
                    if (lower.contains("pipe")
                        || lower.contains("stream closed")
                        || lower.contains("broken pipe")) {
                        return true;
                    }
                }
            }
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("pipe is being closed")
                    || lower.contains("pipe has been ended")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof EditorExiting) {
            stopForExit();
            return;
        }
        if (message instanceof ProjectFolderOpened opened) {
            final String openedFolder = opened.getFolder();
            if (!Objects.equals(currentProjectRoot, openedFolder)) {
                currentProjectRoot = openedFolder;
                clearAllDiagnostics();
                // Defer until WorkspaceOpenFiles.restore() finishes closing/opening
                // buffers on the same EDT turn; otherwise notifyOpen() can be skipped
                // while servers restart and never retried.
                SwingUtilities.invokeLater(this::scheduleWorkspaceReload);
            }
            LspProjectInstallDialog.maybePromptForProject(openedFolder);
        }
        if (message instanceof ProjectFolderClosed) {
            currentProjectRoot = null;
            clearAllDiagnostics();
            SwingUtilities.invokeLater(this::scheduleWorkspaceStop);
        }
    }

    private void scheduleWorkspaceReload() {
        if (stopped) {
            return;
        }
        final int generation = ++workspaceGeneration;
        ThreadUtilities.runInBackground(() -> {
            runWorkspaceReload(generation);
            if (!stopped && generation == workspaceGeneration) {
                LspAsync.runOffEdt(this::resyncAllOpenBuffers);
            }
        });
    }

    private void scheduleWorkspaceStop() {
        if (stopped) {
            return;
        }
        final int generation = ++workspaceGeneration;
        ThreadUtilities.runInBackground(() -> runWorkspaceStop(generation));
    }

    private void runWorkspaceReload(int generation) {
        if (stopped || generation != workspaceGeneration) {
            return;
        }
        synchronized (workspaceLock) {
            if (stopped || generation != workspaceGeneration) {
                return;
            }
            for (GenericLspClient client : collectTrackedClients()) {
                if (stopped || generation != workspaceGeneration) {
                    return;
                }
                restartLspClientBlocking(client);
            }
        }
    }

    private void runWorkspaceStop(int generation) {
        if (stopped || generation != workspaceGeneration) {
            return;
        }
        synchronized (workspaceLock) {
            if (stopped || generation != workspaceGeneration) {
                return;
            }
            for (GenericLspClient client : collectTrackedClients()) {
                if (stopped || generation != workspaceGeneration) {
                    return;
                }
                stopMetaClient(client);
            }
        }
    }

    private synchronized List<GenericLspClient> collectTrackedClients() {
        LinkedHashSet<GenericLspClient> tracked = new LinkedHashSet<>(clients.values());
        for (BufferLspHandler handler : handlers.values()) {
            tracked.add(handler.client);
            clients.putIfAbsent(handler.client.getMode(), handler.client);
        }
        return new ArrayList<>(tracked);
    }

    private synchronized GenericLspClient lookupClientForMode(String modeName, Buffer buffer) {
        GenericLspClient client = clients.get(modeName);
        if (client != null) {
            return client;
        }
        BufferLspHandler handler = handlers.get(buffer);
        if (handler != null) {
            clients.putIfAbsent(modeName, handler.client);
            return handler.client;
        }
        return null;
    }

    private synchronized GenericLspClient resolveClientForMode(String modeName, Buffer buffer) {
        GenericLspClient client = lookupClientForMode(modeName, buffer);
        if (client != null) {
            return client;
        }
        if (buffer != null && LspConfig.isServerConfigured(modeName) && !buffer.isClosed()) {
            startLspForBuffer(buffer);
            return clients.get(modeName);
        }
        return null;
    }

    private void restartLspClientBlocking(GenericLspClient client) {
        if (stopped) {
            client.shutdownForExit();
            return;
        }
        clearDiagnosticsForClient(client);
        stopMetaClient(client);
        if (stopped) {
            return;
        }
        String projectRoot = resolveLspProjectRoot(client);
        if (projectRoot != null) {
            startMetaClientBlocking(client, projectRoot);
        }
    }

    private static void clearAllDiagnostics() {
        LspDiagnosticsHub.getInstance().clearAll();
        LspStructureHub.getInstance().clearAll();
    }

    private void clearDiagnosticsForClient(GenericLspClient client) {
        synchronized (this) {
            for (BufferLspHandler handler : handlers.values()) {
                if (handler.client == client) {
                    LspDiagnosticsHub.getInstance().setDiagnostics(
                        LspDocumentUri.pathToUri(handler.buffer.getPath()), List.of());
                }
            }
        }
    }

    private void reopenBuffersForClient(GenericLspClient client) {
        if (stopped || client.isShuttingDown()) {
            return;
        }
        List<BufferLspHandler> toReopen = new ArrayList<>();
        synchronized (this) {
            for (BufferLspHandler handler : handlers.values()) {
                if (handler.client == client) {
                    toReopen.add(handler);
                }
            }
        }
        LspAsync.runOffEdt(() -> notifyOpenStaggered(toReopen));
    }

    private void resyncAllOpenBuffers() {
        if (stopped) {
            return;
        }
        List<BufferLspHandler> toSync;
        synchronized (this) {
            toSync = new ArrayList<>(handlers.values());
        }
        LspAsync.runOffEdt(() -> notifyOpenStaggered(toSync));
    }

    private void notifyOpenStaggered(List<BufferLspHandler> handlers) {
        for (int i = 0; i < handlers.size(); i++) {
            if (stopped) {
                return;
            }
            BufferLspHandler handler = handlers.get(i);
            handler.resetVersionForServerRestart();
            if (i > 0) {
                try {
                    Thread.sleep(100L * i);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            handler.notifyOpen();
        }
    }

    /**
     * Reload LSP configuration after options change: restart servers for open buffers.
     */
    public synchronized void reloadConfiguration() {
        clearAllDiagnostics();
        List<Buffer> openBuffers = new ArrayList<>(handlers.keySet());
        for (Buffer buffer : openBuffers) {
            stopLspForBuffer(buffer);
        }
        for (Buffer buffer : openBuffers) {
            if (!buffer.isClosed()) {
                Buffer openBuffer = buffer;
                LspAsync.runOffEdt(() -> startLspForBuffer(openBuffer));
            }
        }
    }

    boolean restartServer(GenericLspClient client) {
        if (stopped) {
            return false;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            final int generation = ++workspaceGeneration;
            ThreadUtilities.runInBackground(() -> {
                if (stopped || generation != workspaceGeneration) {
                    return;
                }
                synchronized (workspaceLock) {
                    if (stopped || generation != workspaceGeneration) {
                        return;
                    }
                    restartLspClientBlocking(client);
                }
            });
            return false;
        }
        try {
            synchronized (workspaceLock) {
                restartLspClientBlocking(client);
            }
            return client.hasActiveSession();
        } catch (Exception e) {
            Log.log(Log.ERROR, this, "Error restarting server for " + client.getMode(), e);
            return false;
        }
    }
}
