/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.Selection;
import org.gjt.sp.util.Log;

/**
 * LSP document formatting and organize-imports actions.
 */
public final class LspFormatting {

    private LspFormatting() {}

    public static void formatDocument(View view, GenericLspClient lspClient) {
        Buffer buffer = view.getBuffer();
        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }
        formatRange(view, lspClient, fullDocumentRange(buffer));
    }

    public static void formatSelection(View view, GenericLspClient lspClient) {
        Buffer buffer = view.getBuffer();
        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }
        JEditTextArea textArea = view.getTextArea();
        Selection[] selection = textArea.getSelection();
        if (selection == null || selection.length == 0) {
            formatDocument(view, lspClient);
            return;
        }
        Range range = selectionToRange(buffer, selection[0]);
        if (range == null) {
            formatDocument(view, lspClient);
            return;
        }
        formatRange(view, lspClient, range);
    }

    public static void organizeImports(View view, GenericLspClient lspClient) {
        Buffer buffer = view.getBuffer();
        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        Range fullRange = fullDocumentRange(buffer);
        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setRange(fullRange);
        CodeActionContext context = new CodeActionContext();
        context.setOnly(List.of(CodeActionKind.SourceOrganizeImports));
        params.setContext(context);

        LspBusyCursor.show(view);
        LspAsync.runOffEdt(() ->
            LspPlugin.flushBufferChangesAsync(buffer)
                .thenComposeAsync(ignored -> lspClient.whenReady(), LspAsync.EXECUTOR)
                .thenComposeAsync(ignored ->
                    lspClient.getServer().getTextDocumentService().codeAction(params),
                    LspAsync.EXECUTOR)
                .thenAccept(result -> LspAsync.runOnEdt(() ->
                    applyOrganizeImportsResult(view, lspClient, uri, buffer, result)))
                .exceptionally(ex -> {
                    Log.log(Log.WARNING, LspFormatting.class, ex);
                    LspAsync.runOnEdt(() ->
                        javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null));
                    return null;
                })
                .whenComplete((ignored, ex) -> LspBusyCursor.hide(view)));
    }

    public static void formatOnSave(Buffer buffer, GenericLspClient lspClient) {
        if (buffer == null || !buffer.isEditable() || lspClient == null
            || lspClient.getServer() == null) {
            return;
        }
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        Range range = fullDocumentRange(buffer);
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        params.setOptions(formattingOptions(buffer));

        LspAsync.runOffEdt(() ->
            lspClient.whenReady()
                .thenComposeAsync(ignored ->
                    lspClient.getServer().getTextDocumentService().formatting(params),
                    LspAsync.EXECUTOR)
                .thenAccept(edits -> LspAsync.runOnEdt(() ->
                    applyTextEdits(buffer, uri, edits)))
                .exceptionally(ex -> {
                    Log.log(Log.DEBUG, LspFormatting.class, "Format on save failed", ex);
                    return null;
                }));
    }

    private static void formatRange(View view, GenericLspClient lspClient, Range range) {
        Buffer buffer = view.getBuffer();
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        boolean wholeDocument = isFullDocument(buffer, range);

        LspBusyCursor.show(view);
        CompletableFuture<? extends List<? extends TextEdit>> future;
        if (wholeDocument) {
            DocumentFormattingParams params = new DocumentFormattingParams();
            params.setTextDocument(new TextDocumentIdentifier(uri));
            params.setOptions(formattingOptions(buffer));
            future = lspClient.whenReady()
                .thenComposeAsync(ignored ->
                    lspClient.getServer().getTextDocumentService().formatting(params),
                    LspAsync.EXECUTOR);
        } else {
            DocumentRangeFormattingParams params = new DocumentRangeFormattingParams();
            params.setTextDocument(new TextDocumentIdentifier(uri));
            params.setRange(range);
            params.setOptions(formattingOptions(buffer));
            future = lspClient.whenReady()
                .thenComposeAsync(ignored ->
                    lspClient.getServer().getTextDocumentService().rangeFormatting(params),
                    LspAsync.EXECUTOR);
        }

        LspAsync.runOffEdt(() ->
            LspPlugin.flushBufferChangesAsync(buffer)
                .thenComposeAsync(ignored -> future, LspAsync.EXECUTOR)
                .thenAccept(edits -> LspAsync.runOnEdt(() -> {
                    applyTextEdits(buffer, uri, edits);
                    LspBusyCursor.hide(view);
                }))
                .exceptionally(ex -> {
                    Log.log(Log.WARNING, LspFormatting.class, ex);
                    LspAsync.runOnEdt(() -> {
                        LspBusyCursor.hide(view);
                        javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                    });
                    return null;
                }));
    }

    private static void applyOrganizeImportsResult(View view, GenericLspClient lspClient,
                                                   String uri, Buffer buffer,
                                                   List<Either<Command, CodeAction>> result) {
        if (result == null || result.isEmpty()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }
        for (Either<Command, CodeAction> either : result) {
            if (either.isRight()) {
                CodeAction action = either.getRight();
                if (action.getEdit() != null) {
                    LspWorkspaceEdits.applyToBuffer(buffer, uri, action.getEdit());
                    return;
                }
                if (action.getCommand() != null) {
                    LspCodeActions.executeOrganizeImportsCommand(
                        view, lspClient, buffer, action.getCommand());
                    return;
                }
            }
        }
        javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
    }

    private static void applyTextEdits(Buffer buffer, String uri,
                                       List<? extends TextEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return;
        }
        org.eclipse.lsp4j.WorkspaceEdit edit = new org.eclipse.lsp4j.WorkspaceEdit();
        edit.setChanges(java.util.Map.of(uri, List.copyOf(edits)));
        LspWorkspaceEdits.applyToBuffer(buffer, uri, edit);
    }

    private static FormattingOptions formattingOptions(Buffer buffer) {
        int tabSize = buffer.getIntegerProperty("tabSize", 4);
        boolean insertSpaces = !buffer.getBooleanProperty("noTabs", false);
        FormattingOptions options = new FormattingOptions();
        options.setTabSize(tabSize);
        options.setInsertSpaces(insertSpaces);
        return options;
    }

    private static Range fullDocumentRange(Buffer buffer) {
        int lastLine = Math.max(0, buffer.getLineCount() - 1);
        int endCharacter = buffer.getLineLength(lastLine);
        return new Range(new org.eclipse.lsp4j.Position(0, 0),
            new org.eclipse.lsp4j.Position(lastLine, endCharacter));
    }

    private static boolean isFullDocument(Buffer buffer, Range range) {
        Range full = fullDocumentRange(buffer);
        return range.getStart().getLine() == full.getStart().getLine()
            && range.getStart().getCharacter() == full.getStart().getCharacter()
            && range.getEnd().getLine() == full.getEnd().getLine()
            && range.getEnd().getCharacter() == full.getEnd().getCharacter();
    }

    private static Range selectionToRange(Buffer buffer, Selection selection) {
        try {
            int start = selection.getStart();
            int end = selection.getEnd();
            if (start > end) {
                int swap = start;
                start = end;
                end = swap;
            }
            return new Range(
                offsetToPosition(buffer, start),
                offsetToPosition(buffer, end));
        } catch (Exception ex) {
            return null;
        }
    }

    private static org.eclipse.lsp4j.Position offsetToPosition(Buffer buffer, int offset) {
        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        return new org.eclipse.lsp4j.Position(line, offset - lineStart);
    }
}
