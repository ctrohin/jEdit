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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.syntax.KeywordMap;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.util.Log;

/**
 * LSP rename symbol at the caret via {@code textDocument/prepareRename} and
 * {@code textDocument/rename}.
 */
public final class LspRename {

    private static final int ANALYSIS_RETRY_COUNT = 8;
    private static final long ANALYSIS_RETRY_DELAY_MS = 400L;
    private static final ScheduledExecutorService RETRY_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "lsp-rename-retry");
            thread.setDaemon(true);
            return thread;
        });

    private LspRename() {}

    public static void renameLsp(View view, GenericLspClient lspClient) {
        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();

        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        if (lspClient == null || lspClient.getServer() == null) {
            Log.log(Log.WARNING, LspRename.class, "LSP server not available for rename");
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());
        int caret = textArea.getCaretPosition();
        Position position = offsetToPosition(buffer, caret);

        PrepareRenameParams prepareParams = new PrepareRenameParams();
        prepareParams.setTextDocument(versionedDocument(buffer, documentUri));
        prepareParams.setPosition(position);

        lspClient.whenReady().thenCompose(ignored ->
            prepareRenameWithRetry(lspClient, prepareParams, ANALYSIS_RETRY_COUNT))
            .thenAccept(result -> SwingUtilities.invokeLater(() ->
                handlePrepareRename(view, lspClient, buffer, documentUri, position, caret, result)))
            .exceptionally(ex -> {
                Log.log(Log.ERROR, LspRename.class, "Error in LSP prepareRename", ex);
                SwingUtilities.invokeLater(() -> {
                    showRenameError(view, toException(ex));
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                });
                return null;
            });
    }

    private static void handlePrepareRename(View view, GenericLspClient lspClient,
                                            Buffer buffer, String documentUri,
                                            Position position, int caretOffset,
                                            Either3<Range, PrepareRenameResult,
                                                PrepareRenameDefaultBehavior> result) {
        if (result == null) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        String defaultName = resolveDefaultName(buffer, caretOffset, result);
        if (defaultName == null || defaultName.isEmpty()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        String newName = (String) JOptionPane.showInputDialog(
            view,
            "Enter the new name:",
            "Rename Symbol",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            defaultName);

        if (newName == null) {
            return;
        }

        final String trimmedName = newName.trim();
        if (trimmedName.isEmpty() || trimmedName.equals(defaultName)) {
            return;
        }

        LspPlugin.republishBufferToServerAsync(buffer)
            .thenCompose(ignored -> scheduleRetry(() -> {
                RenameParams renameParams = new RenameParams();
                renameParams.setTextDocument(versionedDocument(buffer, documentUri));
                renameParams.setPosition(position);
                renameParams.setNewName(trimmedName);
                return lspClient.whenReady().thenCompose(ready ->
                    renameWithRetry(lspClient, renameParams, ANALYSIS_RETRY_COUNT));
            }))
            .thenAccept(edit -> SwingUtilities.invokeLater(() -> applyRenameEdit(edit)))
            .exceptionally(ex -> {
                Log.log(Log.ERROR, LspRename.class, "Error in LSP rename", ex);
                SwingUtilities.invokeLater(() -> {
                    showRenameError(view, toException(ex));
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                });
                return null;
            });
    }

    private static CompletableFuture<Either3<Range, PrepareRenameResult,
        PrepareRenameDefaultBehavior>> prepareRenameWithRetry(
            GenericLspClient lspClient, PrepareRenameParams params, int retriesLeft) {
        CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> attempt =
            lspClient.getServer().getTextDocumentService().prepareRename(params);
        return attempt.handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(result);
            }
            if (retriesLeft > 0 && isAnalysisPendingError(error)) {
                CompletableFuture<Either3<Range, PrepareRenameResult,
                    PrepareRenameDefaultBehavior>> retry = new CompletableFuture<>();
                RETRY_EXECUTOR.schedule(() ->
                    prepareRenameWithRetry(lspClient, params, retriesLeft - 1)
                        .whenComplete((value, retryError) -> {
                            if (retryError != null) {
                                retry.completeExceptionally(retryError);
                            } else {
                                retry.complete(value);
                            }
                        }),
                    ANALYSIS_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                return retry;
            }
            CompletableFuture<Either3<Range, PrepareRenameResult,
                PrepareRenameDefaultBehavior>> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }).thenCompose(future -> future);
    }

    private static CompletableFuture<WorkspaceEdit> renameWithRetry(
            GenericLspClient lspClient, RenameParams params, int retriesLeft) {
        CompletableFuture<WorkspaceEdit> attempt =
            lspClient.getServer().getTextDocumentService().rename(params);
        return attempt.handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(result);
            }
            if (retriesLeft > 0 && isAnalysisPendingError(error)) {
                CompletableFuture<WorkspaceEdit> retry = new CompletableFuture<>();
                RETRY_EXECUTOR.schedule(() ->
                    renameWithRetry(lspClient, params, retriesLeft - 1)
                        .whenComplete((value, retryError) -> {
                            if (retryError != null) {
                                retry.completeExceptionally(retryError);
                            } else {
                                retry.complete(value);
                            }
                        }),
                    ANALYSIS_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
                return retry;
            }
            CompletableFuture<WorkspaceEdit> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }).thenCompose(future -> future);
    }

    private static boolean isAnalysisPendingError(Throwable error) {
        String message = getRootMessage(toException(error));
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("not being analyzed")
            || lower.contains("contentmodified")
            || lower.contains("content modified");
    }

    private static <T> CompletableFuture<T> scheduleRetry(
            java.util.function.Supplier<CompletableFuture<T>> retry) {
        CompletableFuture<T> delayed = new CompletableFuture<>();
        RETRY_EXECUTOR.schedule(() -> retry.get().whenComplete((value, error) -> {
            if (error != null) {
                delayed.completeExceptionally(error);
            } else {
                delayed.complete(value);
            }
        }), ANALYSIS_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        return delayed;
    }

    private static void applyRenameEdit(WorkspaceEdit edit) {
        if (edit == null) {
            Log.log(Log.WARNING, LspRename.class, "LSP rename returned no workspace edit");
            return;
        }

        int editCount = LspWorkspaceEdits.countTextEdits(edit);
        Log.log(Log.WARNING, LspRename.class,
            "LSP rename workspace edit contains " + editCount + " text edit(s)");

        if (!LspWorkspaceEdits.apply(edit)) {
            Log.log(Log.WARNING, LspRename.class,
                "LSP rename returned a workspace edit but no edits were applied");
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    private static String resolveDefaultName(Buffer buffer, int caretOffset,
        Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> result) {
        if (result.isSecond()) {
            PrepareRenameResult prepareResult = result.getSecond();
            if (prepareResult == null) {
                return null;
            }
            if (prepareResult.getPlaceholder() != null
                && !prepareResult.getPlaceholder().isEmpty()) {
                return prepareResult.getPlaceholder();
            }
            if (prepareResult.getRange() != null) {
                return textInRange(buffer, prepareResult.getRange());
            }
        }
        if (result.isFirst()) {
            return textInRange(buffer, result.getFirst());
        }
        if (result.isThird()) {
            return identifierAtOffset(buffer, caretOffset);
        }
        return null;
    }

    private static String textInRange(Buffer buffer, Range range) {
        if (range == null || range.getStart() == null || range.getEnd() == null) {
            return null;
        }
        int start = positionToOffset(buffer, range.getStart());
        int end = positionToOffset(buffer, range.getEnd());
        if (start < 0 || end < start) {
            return null;
        }
        return buffer.getText(start, end - start);
    }

    private static String identifierAtOffset(Buffer buffer, int offset) {
        int line = buffer.getLineOfOffset(offset);
        CharSequence lineText = buffer.getLineSegment(line);
        int lineStart = buffer.getLineStartOffset(line);
        int lineOffset = offset - lineStart;

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(offset);
        String noWordSep = keywordMap != null ? keywordMap.getNonAlphaNumericChars() : "_";
        if (noWordSep == null) {
            noWordSep = "_";
        }

        int wordStart = TextUtilities.findWordStart(lineText, lineOffset, noWordSep);
        int wordEnd = TextUtilities.findWordEnd(lineText, lineOffset, noWordSep);
        if (wordStart >= wordEnd) {
            return null;
        }
        return lineText.subSequence(wordStart, wordEnd).toString();
    }

    private static void showRenameError(View view, Exception error) {
        String message = getRootMessage(error);
        if (message == null || message.isEmpty()) {
            message = "Rename failed.";
        }
        JOptionPane.showMessageDialog(
            view,
            message,
            "Rename Error",
            JOptionPane.ERROR_MESSAGE);
    }

    private static Exception toException(Throwable throwable) {
        Throwable current = throwable;
        if (current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof Exception) {
            return (Exception) current;
        }
        return new Exception(current);
    }

    private static String getRootMessage(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof ResponseErrorException) {
            return current.getMessage();
        }
        return current.getMessage();
    }

    private static VersionedTextDocumentIdentifier versionedDocument(
            Buffer buffer, String documentUri) {
        VersionedTextDocumentIdentifier textDocument = new VersionedTextDocumentIdentifier();
        textDocument.setUri(documentUri);
        textDocument.setVersion(LspPlugin.getDocumentVersion(buffer));
        return textDocument;
    }

    private static Position offsetToPosition(Buffer buffer, int offset) {
        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        return new Position(line, offset - lineStart);
    }

    private static int positionToOffset(Buffer buffer, Position position) {
        if (position == null) {
            return -1;
        }

        int line = position.getLine();
        int character = position.getCharacter();
        if (line < 0 || line >= buffer.getLineCount()) {
            return -1;
        }

        CharSequence lineContent = buffer.getLineSegment(line);
        if (character < 0 || character > lineContent.length()) {
            return -1;
        }

        return buffer.getLineStartOffset(line) + character;
    }
}
