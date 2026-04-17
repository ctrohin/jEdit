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

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.util.Log;

import org.eclipse.lsp4j.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Main entry point for the LSP integration in jEdit.
 * It listens for buffer events and manages LSP clients for different languages.
 */
public class LspPlugin extends EditPlugin {

    private final Map<String, GenericLspClient> clients = new HashMap<>();
    private final Map<Buffer, BufferLspHandler> handlers = new HashMap<>();
    private final int DEFAULT_LEVEL = Log.ERROR;

    @Override
    public void start() {
        Log.log(DEFAULT_LEVEL, this, "LSP Plugin starting...");
    }

    @Override
    public void stop() {
        // Shutdown all clients
        for (GenericLspClient client : clients.values()) {
            try {
                client.getServer().shutdown().get();
                client.getServer().exit();
            } catch (Exception e) {
                Log.log(Log.ERROR, this, "Error shutting down LSP client", e);
            }
        }
        clients.clear();
        handlers.clear();
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

        GenericLspClient client = clients.get(modeName);
        if (client == null) {
            client = new GenericLspClient();
            try {
                // For now, use the buffer's directory as project root if we can't find better
                String projectRoot = buffer.getDirectory();
                client.start(modeName, projectRoot);
                clients.put(modeName, client);
            } catch (Exception e) {
                Log.log(Log.ERROR, this, "Failed to start LSP server for " + modeName, e);
                return;
            }
        }

        BufferLspHandler handler = new BufferLspHandler(buffer, client);
        buffer.addBufferListener(handler);
        handlers.put(buffer, handler);
        handler.notifyOpen();
    }

    private synchronized void stopLspForBuffer(Buffer buffer) {
        BufferLspHandler handler = handlers.remove(buffer);
        if (handler != null) {
            buffer.removeBufferListener(handler);
            handler.notifyClose();
        }
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
            if (client.getServer() == null) return;
            
            DidOpenTextDocumentParams params = new DidOpenTextDocumentParams();
            TextDocumentItem item = new TextDocumentItem();
            item.setUri(new java.io.File(buffer.getPath()).toURI().toString());
            item.setLanguageId(buffer.getMode().getName());
            item.setVersion(version++);
            item.setText(buffer.getText(0, buffer.getLength()));
            params.setTextDocument(item);
            
            client.getServer().getTextDocumentService().didOpen(params);
        }

        void notifyClose() {
            if (client.getServer() == null) return;

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

            DidChangeTextDocumentParams params = new DidChangeTextDocumentParams();
            VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier();
            id.setUri(new java.io.File(this.buffer.getPath()).toURI().toString());
            id.setVersion(version++);
            params.setTextDocument(id);

            // Full sync for simplicity in this initial implementation
            TextDocumentContentChangeEvent event = new TextDocumentContentChangeEvent();
            event.setText(this.buffer.getText(0, this.buffer.getLength()));
            params.setContentChanges(Collections.singletonList(event));

            client.getServer().getTextDocumentService().didChange(params);
        }
    }
}
