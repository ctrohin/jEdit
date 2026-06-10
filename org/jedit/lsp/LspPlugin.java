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
    private final int DEFAULT_LEVEL = Log.ERROR;
    private String currentProjectRoot;
    private static LspPlugin instance;
    private volatile boolean stopped;
    private Thread shutdownHook;

    public LspPlugin() {
        instance = this;
    }

    public static LspPlugin getInstance() {
        return instance;
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
        removeShutdownHook();
        uninstallLspUi();
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
        removeShutdownHook();
        uninstallLspUi();
        for (GenericLspClient client : startedClients) {
            if (client.hasActiveSession()) {
                client.shutdownAndWait();
            }
        }
        startedClients.clear();
        clients.clear();
        handlers.clear();
        EditBus.removeFromBus(this);
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
        return plugin.clients.get(buffer.getMode().getName());
    }

    private static void invokeLspFeature(View view, LspFeature feature) {
        if (view == null) {
            return;
        }

        final String modeName = view.getBuffer().getMode().getName();
        LspPlugin lspPlugin = getInstance();
        if (lspPlugin == null || lspPlugin.stopped) {
            return;
        }
        GenericLspClient client = lspPlugin.resolveClientForMode(modeName, view.getBuffer());
        if (client != null && client.hasActiveSession() && client.isAlive()) {
            feature.run(view, client);
        } else if (client != null) {
            Log.log(Log.WARNING, LspPlugin.class,
                "LSP server for " + modeName + " is not running, attempting restart");
            if (lspPlugin.restartServer(client)) {
                if (client.hasActiveSession()) {
                    feature.run(view, client);
                } else {
                    Log.log(Log.ERROR, LspPlugin.class,
                        "Failed to restart LSP server for " + modeName);
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                }
            } else {
                Log.log(Log.ERROR, LspPlugin.class,
                    "Failed to restart LSP server for " + modeName);
                javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            }
        } else {
            Log.log(Log.WARNING, LspPlugin.class, "LSP client not available for mode " + modeName);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    @EditBus.EBHandler
    public void handleBufferUpdate(BufferUpdate message) {
        Buffer buffer = message.getBuffer();
        if (message.getWhat() == BufferUpdate.LOADED) {
            startLspForBuffer(buffer);
        } else if (message.getWhat() == BufferUpdate.CLOSED) {
            stopLspForBuffer(buffer);
        } else if (message.getWhat() == BufferUpdate.SAVED) {
            BufferLspHandler handler = handlers.get(buffer);
            if (handler != null) {
                handler.syncOnSave();
            }
        }
    }

    private synchronized void startLspForBuffer(Buffer buffer) {
        String modeName = buffer.getMode().getName();
        if (!LspConfig.isServerConfigured(modeName)) {
            Log.log(DEFAULT_LEVEL, this,"LSP not available for mode " + modeName + ".");
            return;
        }

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
        if (client.hasActiveSession()) {
            return;
        }
        String projectRoot = resolveLspProjectRoot(client);
        if (projectRoot == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> startMetaClientBlocking(client, projectRoot));
    }

    private void startMetaClientBlocking(GenericLspClient client, String projectRoot) {
        if (projectRoot == null || client.hasActiveSession()) {
            return;
        }
        Try.of(() -> {
            client.start(client.getMode(), projectRoot);
            return client;
        }).onSuccess(m -> {
            m.setServerStarted(true);
            SwingUtilities.invokeLater(() -> reopenBuffersForClient(m));
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
            if (!client.getMode().equals(handler.buffer.getMode().getName())) {
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
        if (client.hasActiveSession()) {
            client.shutdownAndWait();
        } else {
            client.resetSessionState();
        }
    }

    private void stopMetaClientAsync(GenericLspClient client) {
        ThreadUtilities.runInBackground(() -> stopMetaClient(client));
    }

    private synchronized void stopLspForBuffer(Buffer buffer) {
        BufferLspHandler handler = handlers.remove(buffer);
        if (handler != null) {
            buffer.removeBufferListener(handler);
            handler.notifyClose();
        }
        String modeName = buffer.getMode().getName();
        Optional.ofNullable(clients.get(modeName))
            .stream()
            .peek(GenericLspClient::decrementBufferCount)
            .filter(m -> m.getBufferCount() == 0)
            .findFirst()
            .ifPresent(c -> {
                clients.remove(modeName);
                c.shutdown();
            });
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

        BufferLspHandler(Buffer buffer, GenericLspClient client) {
            this.buffer = buffer;
            this.client = client;
            changeSyncTimer = new Timer(CHANGE_SYNC_DELAY_MS, e -> flushChangeToServer());
            changeSyncTimer.setRepeats(false);
        }

        /** Version sent in the last {@code didOpen} or {@code didChange} for this buffer. */
        int getLastSyncedVersion() {
            return Math.max(0, version - 1);
        }

        void notifyOpen() {
            if (client.getServer() == null) {
                return;
            }
            client.whenReady().thenRun(() ->
                ThreadUtilities.runInBackground(this::sendDidOpen)).exceptionally(ex -> {
                Log.log(Log.ERROR, this,
                    "Failed waiting for LSP server before didOpen: " + buffer.getPath(), ex);
                return null;
            });
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
            flushChangeToServer();
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
            if (client.getServer() == null) {
                return CompletableFuture.completedFuture(null);
            }
            return client.whenReady().thenRun(() ->
                ThreadUtilities.runInBackground(() -> {
                    sendDidClose();
                    sendDidOpen();
                }));
        }

        private void sendDidOpen() {
            if (client.getServer() == null) {
                return;
            }

            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
            TextDocumentItem item = new TextDocumentItem();
            item.setUri(LspDocumentUri.pathToUri(buffer.getPath()));
            item.setLanguageId(buffer.getMode().getName());
            item.setVersion(version++);
            item.setText(buffer.getText(0, buffer.getLength()));
            params.setTextDocument(item);

            client.getServer().getTextDocumentService().didOpen(params);
            documentOpenOnServer = true;
            pendingChanges.clear();
            needsSync = false;
        }

        void notifyClose() {
            changeSyncTimer.stop();
            documentOpenOnServer = false;
            pendingChanges.clear();
            LspDiagnosticsHub.getInstance().setDiagnostics(
                LspDocumentUri.pathToUri(buffer.getPath()), List.of());

            if (client.getServer() == null) {
                return;
            }
            ThreadUtilities.runInBackground(this::sendDidClose);
        }

        private void sendDidClose() {
            if (client.getServer() == null) {
                return;
            }
            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
            params.setTextDocument(new TextDocumentIdentifier(
                LspDocumentUri.pathToUri(buffer.getPath())));
            client.getServer().getTextDocumentService().didClose(params);
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
            return applyingLspEdits || buffer.isLoading();
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
            if (client.getServer() == null || !needsSync || pendingChanges.isEmpty()) {
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
            Try.of(() -> {
                client.getServer().getTextDocumentService().didChange(params);
                pendingChanges.removeAll(sentChanges);
                if (pendingChanges.isEmpty()) {
                    needsSync = false;
                }
                return true;
            }).onFailure(e -> {
                Log.log(Log.ERROR, this,
                    "Failed to send didChange for buffer " + buffer.getPath(), e);
            });
        }
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
                scheduleRestartAllClients();
            }
            LspProjectInstallDialog.maybePromptForProject(openedFolder);
        }
        if (message instanceof ProjectFolderClosed) {
            currentProjectRoot = null;
            clearAllDiagnostics();
            ThreadUtilities.runInBackground(() -> {
                for (GenericLspClient client : collectTrackedClients()) {
                    stopMetaClient(client);
                }
            });
        }
    }

    private void scheduleRestartAllClients() {
        ThreadUtilities.runInBackground(() -> {
            for (GenericLspClient client : collectTrackedClients()) {
                restartLspClientBlocking(client);
            }
        });
    }

    private synchronized List<GenericLspClient> collectTrackedClients() {
        LinkedHashSet<GenericLspClient> tracked = new LinkedHashSet<>(clients.values());
        for (BufferLspHandler handler : handlers.values()) {
            tracked.add(handler.client);
            clients.putIfAbsent(handler.client.getMode(), handler.client);
        }
        return new ArrayList<>(tracked);
    }

    private synchronized GenericLspClient resolveClientForMode(String modeName, Buffer buffer) {
        GenericLspClient client = clients.get(modeName);
        if (client != null) {
            return client;
        }
        BufferLspHandler handler = handlers.get(buffer);
        if (handler != null) {
            clients.put(modeName, handler.client);
            return handler.client;
        }
        if (buffer != null && LspConfig.isServerConfigured(modeName) && !buffer.isClosed()) {
            startLspForBuffer(buffer);
            return clients.get(modeName);
        }
        return null;
    }

    private void restartLspClientBlocking(GenericLspClient client) {
        clearDiagnosticsForClient(client);
        stopMetaClient(client);
        String projectRoot = resolveLspProjectRoot(client);
        if (projectRoot != null) {
            startMetaClientBlocking(client, projectRoot);
        }
    }

    private static void clearAllDiagnostics() {
        LspDiagnosticsHub.getInstance().clearAll();
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
        List<BufferLspHandler> toReopen = new ArrayList<>();
        synchronized (this) {
            for (BufferLspHandler handler : handlers.values()) {
                if (handler.client == client) {
                    toReopen.add(handler);
                }
            }
        }
        for (int i = 0; i < toReopen.size(); i++) {
            BufferLspHandler handler = toReopen.get(i);
            handler.resetVersionForServerRestart();
            int delayMs = i * 100;
            if (delayMs == 0) {
                handler.notifyOpen();
            } else {
                Timer timer = new Timer(delayMs, e -> handler.notifyOpen());
                timer.setRepeats(false);
                timer.start();
            }
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
                startLspForBuffer(buffer);
            }
        }
    }

    boolean restartServer(GenericLspClient client) {
        if (SwingUtilities.isEventDispatchThread()) {
            ThreadUtilities.runInBackground(() -> restartLspClientBlocking(client));
            return false;
        }
        try {
            restartLspClientBlocking(client);
            return client.hasActiveSession();
        } catch (Exception e) {
            Log.log(Log.ERROR, this, "Error restarting server for " + client.getMode(), e);
            return false;
        }
    }
}
