/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

final class BuildConfigTextDocumentService implements TextDocumentService {

    private final BuildConfigDocumentStore documents;
    private final String projectRoot;

    BuildConfigTextDocumentService(BuildConfigDocumentStore documents, String projectRoot) {
        this.documents = documents;
        this.projectRoot = projectRoot;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
        CompletionParams position) {
        String uri = position.getTextDocument().getUri();
        String text = documents.getText(uri);
        List<CompletionItem> items = BuildConfigCompletionEngine.complete(
            uri, text, position.getPosition(), projectRoot);
        return CompletableFuture.completedFuture(Either.forLeft(items));
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        documents.open(params);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        documents.change(params);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        documents.close(params);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // no-op
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.completedFuture(null);
    }
}
