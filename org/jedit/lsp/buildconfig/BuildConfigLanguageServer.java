/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jedit.lsp.LspCompletionTriggerCharacters;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class BuildConfigLanguageServer implements LanguageServer {

    private final String languageId;
    private final String projectRoot;
    private final BuildConfigDocumentStore documents = new BuildConfigDocumentStore();
    private final BuildConfigTextDocumentService textDocumentService;
    private final WorkspaceService workspaceService = new BuildConfigWorkspaceService();

    public BuildConfigLanguageServer(String languageId, String projectRoot) {
        this.languageId = languageId;
        this.projectRoot = projectRoot;
        this.textDocumentService = new BuildConfigTextDocumentService(documents, projectRoot);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        TextDocumentSyncOptions sync = new TextDocumentSyncOptions();
        sync.setOpenClose(true);
        sync.setChange(TextDocumentSyncKind.Incremental);
        capabilities.setTextDocumentSync(sync);
        capabilities.setCompletionProvider(new CompletionOptions(
            true, LspCompletionTriggerCharacters.charactersForMode(languageId)));
        WorkspaceServerCapabilities workspace = new WorkspaceServerCapabilities();
        WorkspaceFoldersOptions folderOptions = new WorkspaceFoldersOptions();
        folderOptions.setSupported(true);
        workspace.setWorkspaceFolders(folderOptions);
        capabilities.setWorkspace(workspace);
        InitializeResult result = new InitializeResult(capabilities);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(org.eclipse.lsp4j.InitializedParams params) {
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // no-op
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    String getLanguageId() {
        return languageId;
    }
}
