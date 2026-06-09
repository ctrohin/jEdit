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
import org.gjt.sp.util.Log;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class GenericLspClient {

    private LanguageServer server;
    private MyLspClient clientImpl;
    private Process process;
    private Launcher<LanguageServer> launcher;
    private volatile int bufferCount = 0;
    private final String mode;
    private boolean serverStarted = false;
    private volatile CompletableFuture<Void> initializationComplete;

    public GenericLspClient(final String mode) {
        this.mode = mode;
    }

    synchronized void incrementBufferCount() {
        bufferCount++;
    }

    synchronized void decrementBufferCount() {
        bufferCount--;
    }

    synchronized int getBufferCount() {
        return bufferCount;
    }

    String getMode() {
        return mode;
    }

    boolean isServerStarted() {
        return serverStarted;
    }

    void setServerStarted(final boolean serverStarted) {
        this.serverStarted = serverStarted;
    }

    private String[] getOSSpecificCommand() {
        return new String[]{"cmd", "/c"};
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
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        this.process = builder.start();

        // 2. Setup LSP4J Bridge
        this.clientImpl = new MyLspClient();
        this.launcher = LSPLauncher.createClientLauncher(
            clientImpl,
            process.getInputStream(),
            process.getOutputStream()
        );

        launcher.startListening();
        this.server = launcher.getRemoteProxy();

        // 3. Handshake
        InitializeParams params = new InitializeParams();
        WorkspaceFolder rootFolder = new WorkspaceFolder(
            LspDocumentUri.pathToUri(projectRoot), "root");
        params.setWorkspaceFolders(List.of(rootFolder));

        params.setCapabilities(buildClientCapabilities());

        initializationComplete = new CompletableFuture<>();
        CompletableFuture<InitializeResult> initResult = server.initialize(params);
        initResult.thenAccept(res -> {
            server.initialized(new InitializedParams());
            initializationComplete.complete(null);
            System.out.println(languageId + " server is ready!");
        }).exceptionally(ex -> {
            setServerStarted(false);
            initializationComplete.completeExceptionally(ex);
            Log.log(Log.ERROR, this, "Failed to initialize LSP server for " + languageId, ex);
            return null;
        });
    }

    CompletableFuture<Void> whenReady() {
        CompletableFuture<Void> ready = initializationComplete;
        if (ready == null) {
            return CompletableFuture.completedFuture(null);
        }
        return ready;
    }

    boolean isAlive() {
        return serverStarted && process != null && process.isAlive() && server != null;
    }

    void shutdown() {
        setServerStarted(false);
        initializationComplete = null;
        try {
            if (server != null) {
                server.shutdown().get();
                server.exit();
            }
        } catch (Exception e) {
            Log.log(Log.WARNING, this, "Error while shutting down LSP server for " + mode, e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
            server = null;
            launcher = null;
            process = null;
        }
    }

    public LanguageServer getServer() {
        return server;
    }

    private static ClientCapabilities buildClientCapabilities() {
        ClientCapabilities capabilities = new ClientCapabilities();

        TextDocumentClientCapabilities textDocCaps = new TextDocumentClientCapabilities();
        textDocCaps.setCompletion(new CompletionCapabilities(new CompletionItemCapabilities(true)));
        textDocCaps.setDefinition(new DefinitionCapabilities(true));
        textDocCaps.setHover(buildHoverCapabilities());
        textDocCaps.setSignatureHelp(buildSignatureHelpCapabilities());
        textDocCaps.setCodeAction(buildCodeActionCapabilities());
        textDocCaps.setRename(new RenameCapabilities(false, true));
        capabilities.setTextDocument(textDocCaps);

        WorkspaceClientCapabilities workspaceCaps = new WorkspaceClientCapabilities();
        workspaceCaps.setApplyEdit(true);
        WorkspaceEditCapabilities workspaceEditCaps = new WorkspaceEditCapabilities(true);
        workspaceEditCaps.setDocumentChanges(true);
        workspaceEditCaps.setResourceOperations(List.of(
            ResourceOperationKind.Create,
            ResourceOperationKind.Rename,
            ResourceOperationKind.Delete));
        workspaceCaps.setWorkspaceEdit(workspaceEditCaps);
        capabilities.setWorkspace(workspaceCaps);

        WindowClientCapabilities windowCaps = new WindowClientCapabilities();
        windowCaps.setWorkDoneProgress(true);
        WindowShowMessageRequestCapabilities showMessageCaps =
            new WindowShowMessageRequestCapabilities();
        showMessageCaps.setMessageActionItem(
            new WindowShowMessageRequestActionItemCapabilities());
        windowCaps.setShowMessage(showMessageCaps);
        capabilities.setWindow(windowCaps);

        capabilities.setExperimental(buildExperimentalCapabilities());
        return capabilities;
    }

    private static HoverCapabilities buildHoverCapabilities() {
        HoverCapabilities hoverCaps = new HoverCapabilities();
        hoverCaps.setContentFormat(List.of(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT));
        return hoverCaps;
    }

    private static SignatureHelpCapabilities buildSignatureHelpCapabilities() {
        SignatureHelpCapabilities signatureHelpCaps = new SignatureHelpCapabilities();
        SignatureInformationCapabilities signatureInfoCaps =
            new SignatureInformationCapabilities();
        signatureInfoCaps.setParameterInformation(
            new ParameterInformationCapabilities(true));
        signatureHelpCaps.setSignatureInformation(signatureInfoCaps);
        return signatureHelpCaps;
    }

    private static CodeActionCapabilities buildCodeActionCapabilities() {
        CodeActionKindCapabilities kindCaps = new CodeActionKindCapabilities();
        kindCaps.setValueSet(List.of(
            CodeActionKind.Empty,
            CodeActionKind.QuickFix,
            CodeActionKind.Refactor,
            CodeActionKind.RefactorExtract,
            CodeActionKind.RefactorInline,
            CodeActionKind.RefactorRewrite,
            CodeActionKind.Source,
            CodeActionKind.SourceOrganizeImports));

        CodeActionLiteralSupportCapabilities literalSupport =
            new CodeActionLiteralSupportCapabilities();
        literalSupport.setCodeActionKind(kindCaps);

        CodeActionResolveSupportCapabilities resolveSupport =
            new CodeActionResolveSupportCapabilities();
        resolveSupport.setProperties(List.of("edit", "command"));

        CodeActionCapabilities codeActionCaps = new CodeActionCapabilities();
        codeActionCaps.setCodeActionLiteralSupport(literalSupport);
        codeActionCaps.setDataSupport(true);
        codeActionCaps.setIsPreferredSupport(true);
        codeActionCaps.setDisabledSupport(true);
        codeActionCaps.setResolveSupport(resolveSupport);
        return codeActionCaps;
    }

    private static Map<String, Object> buildExperimentalCapabilities() {
        Map<String, Object> commandParameterSupport = new LinkedHashMap<>();
        commandParameterSupport.put("supportedKinds", List.of(
            "saveUri", "openUri", "string", "text", "boolean", "pick", "selection"));

        Map<String, Object> dartCodeAction = new LinkedHashMap<>();
        dartCodeAction.put("commandParameterSupport", commandParameterSupport);

        Map<String, Object> interactiveResolve = new LinkedHashMap<>();
        interactiveResolve.put("inputTypes", List.of(
            "string", "text", "boolean", "number", "pick", "multiPick", "file", "directory"));

        Map<String, Object> experimental = new LinkedHashMap<>();
        experimental.put("dartCodeAction", dartCodeAction);
        experimental.put("supportsWindowShowMessageRequest", true);
        experimental.put("interactiveResolve", interactiveResolve);
        return experimental;
    }
}