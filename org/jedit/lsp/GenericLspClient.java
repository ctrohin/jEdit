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
import org.jedit.lsp.buildconfig.BuildConfigLanguageServer;
import org.jedit.lsp.buildconfig.BuildConfigLspSupport;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.ThreadUtilities;

public class GenericLspClient {

    private LanguageServer server;
    private MyLspClient clientImpl;
    private Process process;
    private Launcher<LanguageServer> launcher;
    private volatile int bufferCount = 0;
    private final String mode;
    private final Object sessionLock = new Object();
    private boolean serverStarted = false;
    private volatile CompletableFuture<Void> initializationComplete;
    private volatile ServerCapabilities serverCapabilities;
    private volatile boolean shuttingDown;

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

    public void start(String languageId, String projectRoot) throws Exception {
        synchronized (sessionLock) {
            startLocked(languageId, projectRoot);
        }
    }

    private void startLocked(String languageId, String projectRoot) throws Exception {
        shuttingDown = false;
        initializationComplete = new CompletableFuture<>();
        try {
            startLockedImpl(languageId, projectRoot);
        } catch (Exception ex) {
            initializationComplete.completeExceptionally(ex);
            throw ex;
        }
    }

    private void startLockedImpl(String languageId, String projectRoot) throws Exception {
        if (BuildConfigLspSupport.isBuiltinMode(languageId)) {
            startBuiltinLocked(languageId, projectRoot);
            return;
        }

        String[] command = LspConfig.getServerCommand(languageId);

        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("No server configured for: " + languageId);
        }

        if (JdtlsSupport.isJavaMode(languageId)) {
            command = JdtlsSupport.augmentCommand(command, projectRoot);
        }

        String executable = command[0];
        if (LspServerInstaller.findExecutable(executable) == null) {
            throw new IOException("Cannot find LSP server executable \"" + executable
                + "\". Install it or set the full path in Global Options → LSP Servers.");
        }

        // 1. Start the specific process
        ProcessBuilder builder = LspServerInstaller.createProcessBuilder(command);
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
        WorkspaceFolder rootFolder = JdtlsSupport.isJavaMode(languageId)
            ? JdtlsSupport.buildWorkspaceFolder(projectRoot)
            : new WorkspaceFolder(LspDocumentUri.pathToUri(projectRoot), "root");
        params.setWorkspaceFolders(List.of(rootFolder));
        params.setRootUri(rootFolder.getUri());
        if (JdtlsSupport.isJavaMode(languageId)) {
            params.setInitializationOptions(
                JdtlsSupport.buildInitializationOptions(projectRoot));
        }

        params.setCapabilities(buildClientCapabilities());

        CompletableFuture<InitializeResult> initResult = server.initialize(params);
        initResult.thenAccept(res -> {
            serverCapabilities = res.getCapabilities();
            server.initialized(new InitializedParams());
            if (JdtlsSupport.isJavaMode(languageId)) {
                JdtlsSupport.pushConfiguration(server);
            }
            initializationComplete.complete(null);
            Log.log(Log.MESSAGE, this, languageId + " LSP server is ready");
        }).exceptionally(ex -> {
            setServerStarted(false);
            initializationComplete.completeExceptionally(ex);
            Log.log(Log.ERROR, this, "Failed to initialize LSP server for " + languageId, ex);
            return null;
        });
    }

    private void startBuiltinLocked(String languageId, String projectRoot) {
        this.clientImpl = new MyLspClient();
        this.launcher = null;
        this.process = null;
        this.server = new BuildConfigLanguageServer(languageId, projectRoot);

        InitializeParams params = new InitializeParams();
        WorkspaceFolder rootFolder = new WorkspaceFolder(
            LspDocumentUri.pathToUri(projectRoot), "root");
        params.setWorkspaceFolders(List.of(rootFolder));
        params.setRootUri(rootFolder.getUri());
        params.setCapabilities(buildClientCapabilities());

        CompletableFuture<InitializeResult> initResult = server.initialize(params);
        initResult.thenAccept(res -> {
            serverCapabilities = res.getCapabilities();
            server.initialized(new InitializedParams());
            initializationComplete.complete(null);
            Log.log(Log.MESSAGE, this, languageId + " built-in LSP server is ready");
        }).exceptionally(ex -> {
            setServerStarted(false);
            initializationComplete.completeExceptionally(ex);
            Log.log(Log.ERROR, this,
                "Failed to initialize built-in LSP server for " + languageId, ex);
            return null;
        });
    }

    boolean supportsDeclaration() {
        return supportsProvider(
            serverCapabilities != null ? serverCapabilities.getDeclarationProvider() : null);
    }

    private static boolean supportsProvider(Object provider) {
        if (provider == null) {
            return false;
        }
        if (provider instanceof Boolean enabled) {
            return enabled;
        }
        return true;
    }

    CompletableFuture<Void> whenReady() {
        if (shuttingDown) {
            return CompletableFuture.failedFuture(
                new CancellationException("LSP client shutdown"));
        }
        CompletableFuture<Void> ready = initializationComplete;
        if (ready == null) {
            return CompletableFuture.failedFuture(
                new CancellationException("LSP client not initialized"));
        }
        return ready;
    }

    boolean isReadyForDocumentSync() {
        if (shuttingDown || server == null) {
            return false;
        }
        CompletableFuture<Void> ready = initializationComplete;
        return ready != null && ready.isDone() && !ready.isCompletedExceptionally();
    }

    boolean isAlive() {
        return serverStarted && server != null
            && (process == null || process.isAlive());
    }

    boolean hasActiveSession() {
        return server != null || (process != null && process.isAlive());
    }

    boolean isShuttingDown() {
        return shuttingDown;
    }

    void resetSessionState() {
        shuttingDown = false;
        synchronized (sessionLock) {
            setServerStarted(false);
            initializationComplete = null;
            server = null;
            launcher = null;
            process = null;
        }
        if (JdtlsSupport.isJavaMode(mode)) {
            JdtlsNotifications.resetSession();
        }
    }

    void notifyServer(String method, Object params) {
        Launcher<LanguageServer> activeLauncher = launcher;
        if (activeLauncher != null) {
            activeLauncher.getRemoteEndpoint().notify(method, params);
        }
    }

    void shutdown() {
        if (LspPlugin.isStopped()) {
            shutdownForExit();
            return;
        }
        ThreadUtilities.runInBackground(this::shutdownAndWait);
    }

    void shutdownAndWait() {
        synchronized (sessionLock) {
            shuttingDown = true;
            if (SwingUtilities.isEventDispatchThread()) {
                shutdownForExitLocked();
                return;
            }
            cancelPendingInitialization();
            setServerStarted(false);
            LanguageServer activeServer = server;
            Process activeProcess = detachProcessLocked();
            try {
                if (activeServer != null) {
                    activeServer.shutdown().get(3, TimeUnit.SECONDS);
                    activeServer.exit();
                }
            } catch (Exception e) {
                Log.log(Log.WARNING, this, "Error while shutting down LSP server for " + mode, e);
            } finally {
                destroyProcess(activeProcess);
            }
        }
    }

    /**
     * Fast shutdown for application exit. Never blocks the EDT waiting on the server.
     */
    void markShuttingDown() {
        shuttingDown = true;
        cancelPendingInitialization();
    }

    void shutdownForExit() {
        synchronized (sessionLock) {
            shuttingDown = true;
            shutdownForExitLocked();
        }
    }

    private void shutdownForExitLocked() {
        shuttingDown = true;
        cancelPendingInitialization();
        setServerStarted(false);
        LanguageServer activeServer = server;
        Process activeProcess = detachProcessLocked();
        if (activeServer != null && !SwingUtilities.isEventDispatchThread()) {
            try {
                activeServer.shutdown().get(500, TimeUnit.MILLISECONDS);
                activeServer.exit();
            } catch (Exception ignored) {
                // Fall through to process termination.
            }
        }
        destroyProcessForcibly(activeProcess);
    }

    private void cancelPendingInitialization() {
        CompletableFuture<Void> pending = initializationComplete;
        initializationComplete = null;
        if (pending != null && !pending.isDone()) {
            pending.completeExceptionally(
                new CancellationException("LSP client shutdown"));
        }
    }

    private Process detachProcessLocked() {
        Process activeProcess = process;
        server = null;
        launcher = null;
        process = null;
        return activeProcess;
    }

    private static void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            destroyProcessTree(process, false);
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                destroyProcessTree(process, true);
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyProcessTree(process, true);
        }
    }

    private static void destroyProcessForcibly(Process process) {
        if (process == null) {
            return;
        }
        try {
            destroyProcessTree(process, true);
            process.waitFor(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyProcessTree(process, true);
        }
    }

    private static void destroyProcessTree(Process process, boolean forcibly) {
        ProcessHandle.of(process.pid()).ifPresent(root -> {
            root.descendants().forEach(child -> terminateHandle(child, forcibly));
            terminateHandle(root, forcibly);
        });
        process.descendants().forEach(child -> terminateHandle(child, forcibly));
        if (forcibly) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    private static void terminateHandle(ProcessHandle handle, boolean forcibly) {
        if (forcibly) {
            handle.destroyForcibly();
        } else {
            handle.destroy();
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
        textDocCaps.setReferences(new ReferencesCapabilities());
        textDocCaps.setImplementation(new ImplementationCapabilities());
        textDocCaps.setTypeDefinition(new TypeDefinitionCapabilities());
        textDocCaps.setDeclaration(new DeclarationCapabilities(true));
        textDocCaps.setDocumentSymbol(new DocumentSymbolCapabilities());
        textDocCaps.setCallHierarchy(new CallHierarchyCapabilities());
        textDocCaps.setHover(buildHoverCapabilities());
        textDocCaps.setSignatureHelp(buildSignatureHelpCapabilities());
        textDocCaps.setCodeAction(buildCodeActionCapabilities());
        textDocCaps.setRename(new RenameCapabilities(false, true));
        capabilities.setTextDocument(textDocCaps);

        WorkspaceClientCapabilities workspaceCaps = new WorkspaceClientCapabilities();
        workspaceCaps.setApplyEdit(true);
        workspaceCaps.setConfiguration(true);
        WorkspaceEditCapabilities workspaceEditCaps = new WorkspaceEditCapabilities(true);
        workspaceEditCaps.setDocumentChanges(true);
        workspaceEditCaps.setResourceOperations(List.of(
            ResourceOperationKind.Create,
            ResourceOperationKind.Rename,
            ResourceOperationKind.Delete));
        workspaceCaps.setWorkspaceEdit(workspaceEditCaps);
        workspaceCaps.setSymbol(new SymbolCapabilities(true));
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