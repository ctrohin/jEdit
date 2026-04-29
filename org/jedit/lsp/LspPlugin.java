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
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.util.Log;

import org.eclipse.lsp4j.*;

import java.io.File;
import java.util.*;

/**
 * Main entry point for the LSP integration in jEdit.
 * It listens for buffer events and manages LSP clients for different languages.
 */
public class LspPlugin extends EditPlugin implements EBComponent {

    // Package-protected so sub-components can access clients
    final Map<String, GenericLspClient> clients = new HashMap<>();
    private final Map<Buffer, BufferLspHandler> handlers = new HashMap<>();
    private final int DEFAULT_LEVEL = Log.ERROR;
    private String currentProjectRoot;
    private static LspPlugin instance;

    public LspPlugin() {
        instance = this;
    }

    public static LspPlugin getInstance() {
        return instance;
    }
    @Override
    public void start() {
        Log.log(DEFAULT_LEVEL, this, "LSP Plugin starting...");
    }

    @Override
    public void stop() {
        // Shutdown all clients
        clients.values()
            .forEach(client -> Optional.ofNullable(client.getServer())
            .ifPresent(this::closeLanguageServer));
        clients.clear();
        handlers.clear();
    }

    /**
     * Request LSP completions at the current caret position.
     * This can be called from actions or other plugins to trigger completion.
     */
    public static void completeLsp(View view) {
        if (view == null) {
            return;
        }

        final String modeName = view.getBuffer().getMode().getName();

        // Find the LSP client for this language mode
        // We need access to the plugin instance to get the clients map
        LspPlugin lspPlugin = getInstance();
        GenericLspClient client = lspPlugin.clients.get(modeName);
        if (client != null && client.getServer() != null) {
            // Check if server is still alive before attempting completion
            if (client.isServerStarted()) {
                LspCompletion.completeLsp(view, client);
            } else {
                Log.log(Log.WARNING, LspPlugin.class, "LSP server for " + modeName + " is not responding, attempting restart");
                // Try to restart the server
                if (lspPlugin.restartServer(client)) {
                    LspCompletion.completeLsp(view, client);
                } else {
                    Log.log(Log.ERROR, LspPlugin.class, "Failed to restart LSP server for " + modeName);
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                }
            }
        } else {
            Log.log(Log.WARNING, LspPlugin.class, "LSP client not available for mode " + modeName);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    private void closeLanguageServer(LanguageServer ls) {
        Try.of(() -> {
            ls.shutdown().get();
            ls.exit();
            return 1;
        }).onFailure(e -> Log.log(Log.ERROR, this, "Error shutting down LSP client", e)).get();
    }

    @EditBus.EBHandler
    public void handleBufferUpdate(BufferUpdate message) {
        Buffer buffer = message.getBuffer();
        if (message.getWhat() == BufferUpdate.LOADED) {
            startLspForBuffer(buffer);
        } else if (message.getWhat() == BufferUpdate.CLOSED) {
            stopLspForBuffer(buffer);
        }
    }

    private synchronized void startLspForBuffer(Buffer buffer) {
        String modeName = buffer.getMode().getName();
        if (!LspConfig.SERVER_COMMANDS.containsKey(modeName)) {
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
        startMetaClient(client);
        clients.put(modeName, client);
        return client;
    }

    private void startMetaClient(GenericLspClient client) {
        if (client.isServerStarted() || Objects.isNull(currentProjectRoot)) {
            return;
        }
        Try.of(() -> {
            client.start(client.getMode(), currentProjectRoot);
            return client;
        }).onSuccess(m -> m.setServerStarted(true))
            .onFailure(e -> Log.log(Log.ERROR, this, "Failed to start LSP server for " + client.getMode(), e))
            .get();
    }

    private void stopMetaClient(GenericLspClient client) {
        if (!client.isServerStarted()) {
            return;
        }
        try {
            client.setServerStarted(false);
            client.getServer().shutdown().get();
        }
        catch (Exception e) {
            Log.log(Log.ERROR, this, "Failed to stop LSP server for " + client.getMode(), e);
        }
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
            .map(GenericLspClient::getServer)
            .ifPresent(s -> {
                clients.remove(modeName);
                closeLanguageServer(s);
            });
    }

    private static class BufferLspHandler extends BufferAdapter {
        private final Buffer buffer;
        private final GenericLspClient client;
        private int version = 0;

        BufferLspHandler(Buffer buffer, GenericLspClient client) {
            this.buffer = buffer;
            this.client = client;
        }

        void notifyOpen() {
            if (client.getServer() == null) {
                return;
            }
            
            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
            TextDocumentItem item = new TextDocumentItem();
            item.setUri(new File(buffer.getPath()).toURI().toString());
            item.setLanguageId(buffer.getMode().getName());
            item.setVersion(version++);
            item.setText(buffer.getText(0, buffer.getLength()));
            params.setTextDocument(item);
            
            client.getServer().getTextDocumentService().didOpen(params);
        }

        void notifyClose() {
            if (client.getServer() == null) {
                return;
            }

            DidCloseTextDocumentParams params = new DidCloseTextDocumentParams();
            params.setTextDocument(new TextDocumentIdentifier(new java.io.File(buffer.getPath()).toURI().toString()));
            client.getServer().getTextDocumentService().didClose(params);
        }

        @Override
        public void contentInserted(JEditBuffer buffer, int startLine, int offset, int numLines, int length) {
            notifyChange();
        }

        @Override
        public void contentRemoved(JEditBuffer buffer, int startLine, int offset, int numLines, int length) {
            notifyChange();
        }

        private void notifyChange() {
            if (client.getServer() == null) return;
            if (true) return;

            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
            VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier();
            id.setUri(new java.io.File(this.buffer.getPath()).toURI().toString());
            id.setVersion(version++);
            params.setTextDocument(id);

            // Full sync for simplicity in this initial implementation
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
            event.setText(this.buffer.getText(0, this.buffer.getLength()));
            params.setContentChanges(Collections.singletonList(event));
            Log.log(Log.ERROR, BufferLspHandler.class, "Document version " + version);
            Try.of(() -> {
                client.getServer().getTextDocumentService().didChange(params);
                return true;
            }).onFailure(e -> {
                Log.log(Log.ERROR, this, "Failed to send didChange for buffer " + buffer.getPath(), e);
            });
        }
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof ProjectFolderOpened) {
            Log.log(Log.ERROR, this, "Handle message called " + message);
            final var openedFolder = ((ProjectFolderOpened) message).getFolder();
            // If a different folder is opened, then restart the LSP clients
            if (!Objects.equals(currentProjectRoot, openedFolder)) {
                currentProjectRoot = openedFolder;
                clients.values().forEach(this::restartLspClient);
            }
        }
        if (message instanceof ProjectFolderClosed) {
            Log.log(Log.ERROR, this, "Handle message called " + message);
            currentProjectRoot = null;
            clients.values().forEach(this::stopMetaClient);
        }
    }

    private void restartLspClient(GenericLspClient client) {
        stopMetaClient(client);
        startMetaClient(client);
    }

    boolean restartServer(GenericLspClient client) {
        try {
            closeLanguageServer(client.getServer());
            startMetaClient(client);
            return true;
        } catch (Exception e) {
            Log.log(Log.ERROR, this, "Error restarting server for " + client.getMode(), e);
            return false;
        }
    }
}
