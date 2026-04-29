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

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class GenericLspClient {

    private LanguageServer server;
    private MyLspClient clientImpl;

    private String[] getOSSpecificCommand() {
        return new String[]{"cmd", "/c "};
    }

    public void start(String languageId, String projectRoot) throws Exception {
        String[] command = LspConfig.SERVER_COMMANDS.get(languageId.toLowerCase());

        if (command == null) {
            throw new IllegalArgumentException("No server configured for: " + languageId);
        }

        // 1. Start the specific process
        final var osPrefix = getOSSpecificCommand();
        final var executeCommand = Stream.concat(Stream.of(osPrefix), Stream.of(command)).toArray(String[]::new);
        final var builder = new ProcessBuilder(executeCommand);
//        builder.environment();
//        builder.inheritIO();
        Process process = builder.start();

        // 2. Setup LSP4J Bridge
        this.clientImpl = new MyLspClient();
        Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(
            clientImpl,
            process.getInputStream(),
            process.getOutputStream()
        );

        launcher.startListening();
        this.server = launcher.getRemoteProxy();

        // 3. Handshake
        InitializeParams params = new InitializeParams();
        WorkspaceFolder rootFolder = new WorkspaceFolder(new File(projectRoot).toURI().toString(), "root");
        params.setWorkspaceFolders(List.of(rootFolder));

        // Tell the server we support specific features
        ClientCapabilities capabilities = new ClientCapabilities();
        TextDocumentClientCapabilities textDocCaps = new TextDocumentClientCapabilities();
        textDocCaps.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(true)));
        capabilities.setTextDocument(textDocCaps);
        params.setCapabilities(capabilities);

        CompletableFuture<InitializeResult> initResult = server.initialize(params);
        initResult.thenAccept(res -> {
            server.initialized(new InitializedParams());
            System.out.println(languageId + " server is ready!");
        });
    }

    public LanguageServer getServer() {
        return server;
    }
}