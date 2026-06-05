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

import java.awt.Point;
import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.Selection;
import org.gjt.sp.util.GenericGUIUtilities;
import org.gjt.sp.util.Log;

/**
 * LSP code actions (quick fixes, refactorings) at the caret or selection.
 */
public final class LspCodeActions {

    private LspCodeActions() {}

    public static void codeActionsLsp(View view, GenericLspClient lspClient) {
        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();

        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        if (lspClient == null || lspClient.getServer() == null) {
            Log.log(Log.WARNING, LspCodeActions.class, "LSP server not available for code actions");
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        requestCodeActions(view, lspClient);
    }

    /**
     * Requests code actions for a specific diagnostic (e.g. diagnostic hover tooltip).
     */
    static void requestCodeActionsForProblem(View view, GenericLspClient lspClient,
                                             Buffer buffer,
                                             LspDiagnosticProblem problem,
                                             Consumer<List<LspCodeActionItem>> onResult) {
        if (lspClient == null || lspClient.getServer() == null || problem == null) {
            onResult.accept(List.of());
            return;
        }

        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());
        Range requestRange = problem.toRange();

        CodeActionParams params = new CodeActionParams();
        params.setTextDocument(new TextDocumentIdentifier(documentUri));
        params.setRange(requestRange);

        CodeActionContext context = new CodeActionContext();
        context.setDiagnostics(List.of(problem.toDiagnostic()));
        params.setContext(context);

        lspClient.getServer().getTextDocumentService().codeAction(params)
            .thenAccept(result -> {
                List<LspCodeActionItem> items = parseActions(result);
                SwingUtilities.invokeLater(() -> onResult.accept(items));
            })
            .exceptionally(ex -> {
                Log.log(Log.ERROR, LspCodeActions.class,
                    "Error requesting LSP code actions for diagnostic", ex);
                SwingUtilities.invokeLater(() -> onResult.accept(List.of()));
                return null;
            });
    }

    static void applyCodeAction(View view, GenericLspClient lspClient, Buffer buffer,
                                LspDiagnosticProblem problem, LspCodeActionItem item) {
        if (item == null || problem == null || !buffer.isEditable()) {
            return;
        }
        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());
        applyAction(view, lspClient, documentUri, problem.toRange(), item);
    }

    private static void requestCodeActions(View view, GenericLspClient lspClient) {
        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();
        String documentUri = new File(buffer.getPath()).toURI().toString();

        try {
            Range requestRange = buildRequestRange(buffer, textArea);

            CodeActionParams params = new CodeActionParams();
            params.setTextDocument(new TextDocumentIdentifier(documentUri));
            params.setRange(requestRange);

            CodeActionContext context = new CodeActionContext();
            context.setDiagnostics(Collections.emptyList());
            params.setContext(context);

            CompletableFuture<List<Either<Command, CodeAction>>> future =
                lspClient.getServer().getTextDocumentService().codeAction(params);

            future.thenAccept(result -> {
                List<LspCodeActionItem> items = parseActions(result);
                if (items.isEmpty()) {
                    SwingUtilities.invokeLater(() ->
                        javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null));
                    return;
                }
                SwingUtilities.invokeLater(() ->
                    showActionMenu(view, lspClient, documentUri, requestRange, items));
            }).exceptionally(ex -> {
                Log.log(Log.ERROR, LspCodeActions.class, "Error requesting LSP code actions", ex);
                SwingUtilities.invokeLater(() ->
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null));
                return null;
            });
        } catch (Exception e) {
            Log.log(Log.ERROR, LspCodeActions.class, "Error in LSP code action request", e);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    private static Range buildRequestRange(Buffer buffer, JEditTextArea textArea) {
        int caret = textArea.getCaretPosition();
        if (textArea.getSelectionCount() > 0) {
            Selection sel = textArea.getSelectionAtOffset(caret);
            if (sel == null) {
                sel = textArea.getSelection(0);
            }
            int selStart = Math.min(sel.getStart(), sel.getEnd());
            int selEnd = Math.max(sel.getStart(), sel.getEnd());
            if (selStart != selEnd) {
                return new Range(
                    offsetToPosition(buffer, selStart),
                    offsetToPosition(buffer, selEnd));
            }
        }

        Position caretPos = offsetToPosition(buffer, caret);
        return new Range(caretPos, caretPos);
    }

    private static Position offsetToPosition(Buffer buffer, int offset) {
        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        return new Position(line, offset - lineStart);
    }

    private static List<LspCodeActionItem> parseActions(List<Either<Command, CodeAction>> result) {
        List<LspCodeActionItem> items = new ArrayList<>();
        if (result == null) {
            return items;
        }
        for (Either<Command, CodeAction> either : result) {
            if (either == null) {
                continue;
            }
            if (either.isLeft()) {
                Command command = either.getLeft();
                if (command != null && command.getTitle() != null) {
                    items.add(LspCodeActionItem.forCommand(command));
                }
            } else if (either.isRight()) {
                CodeAction action = either.getRight();
                if (action != null && action.getTitle() != null) {
                    items.add(LspCodeActionItem.forCodeAction(action));
                }
            }
        }
        return items;
    }

    private static void showActionMenu(View view, GenericLspClient lspClient,
                                       String documentUri, Range requestRange,
                                       List<LspCodeActionItem> items) {
        JEditTextArea textArea = view.getTextArea();
        JPopupMenu popup = new JPopupMenu();

        for (LspCodeActionItem item : items) {
            JMenuItem menuItem = new JMenuItem(item.getTitle());
            menuItem.addActionListener(e ->
                applyAction(view, lspClient, documentUri, requestRange, item));
            popup.add(menuItem);
        }

        Point caretPos = textArea.offsetToXY(textArea.getCaretPosition());
        if (caretPos != null) {
            int lineHeight = textArea.getPainter().getLineHeight();
            GenericGUIUtilities.showPopupMenu(popup, textArea.getPainter(),
                caretPos.x, caretPos.y + lineHeight, true);
        }
    }

    private static void applyAction(View view, GenericLspClient lspClient,
                                    String documentUri, Range requestRange,
                                    LspCodeActionItem item) {
        Buffer buffer = view.getBuffer();
        try {
            CodeAction action = item.getCodeAction();
            if (action != null) {
                if (action.getEdit() == null) {
                    CodeAction resolved = lspClient.getServer().getTextDocumentService()
                        .resolveCodeAction(action)
                        .get();
                    if (resolved != null) {
                        action = resolved;
                    }
                }

                if (action.getEdit() != null) {
                    LspWorkspaceEdits.applyToBuffer(buffer, documentUri, action.getEdit());
                    return;
                }
            }

            Command command = item.isCommand() ? item.getCommand() : action.getCommand();
            if (command != null) {
                executeWorkspaceCommand(lspClient, command, buffer);
            }
        } catch (Exception e) {
            Log.log(Log.ERROR, LspCodeActions.class, "Error applying LSP code action", e);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    /**
     * Runs {@code workspace/executeCommand}, building Dart-compatible arguments when needed.
     * Dart returns many code actions as plain {@link Command} (Either left), not {@link CodeAction}.
     * Uri, range, kind, and version are taken from {@link Command#getArguments()}, not the
     * code-action request context (they can differ).
     */
    private static void executeWorkspaceCommand(GenericLspClient lspClient,
                                                Command command,
                                                Buffer buffer) throws Exception {
        ExecuteCommandParams params = new ExecuteCommandParams();
        final String commandId = command.getCommand();
        params.setCommand(commandId);

        if ("dart.edit.codeAction.apply".equals(commandId)) {
            executeDartApplyCodeActionCommand(lspClient, params, command, buffer);
            return;
        }

        if (isCodeActionCommand(commandId)) {
            List<Object> originalArgs = LspGsonArgs.toExecuteArguments(command.getArguments());
            List<Object> threeArgs = buildThreeArgumentCommandArguments(command, buffer);
            List<Object> oneMapArg = buildSingleMapCommandArguments(command, buffer);

            // Try Gson-converted server args first, then fall back based on runtime errors.
            try {
                params.setArguments(originalArgs);
                applyExecuteCommandResult(
                    lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                    command, buffer);
                return;
            } catch (Exception firstError) {
                String message = getRootMessage(firstError);
                if (message != null && message.contains("requires a single Map argument")) {
                    params.setArguments(oneMapArg);
                    applyExecuteCommandResult(
                        lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                        command, buffer);
                    return;
                }
                if (message != null && message.contains("requires 3 parameters")) {
                    params.setArguments(threeArgs);
                    applyExecuteCommandResult(
                        lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                        command, buffer);
                    return;
                }

                // Unknown mismatch: try both known Dart shapes before surfacing error.
                try {
                    params.setArguments(threeArgs);
                    applyExecuteCommandResult(
                        lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                        command, buffer);
                    return;
                } catch (Exception ignored) {
                    params.setArguments(oneMapArg);
                    applyExecuteCommandResult(
                        lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                        command, buffer);
                }
            }
        } else {
            params.setArguments(LspGsonArgs.toExecuteArguments(command.getArguments()));
            applyExecuteCommandResult(
                lspClient.getServer().getWorkspaceService().executeCommand(params).get(),
                command, buffer);
        }
    }

    /**
     * Dart {@code dart.edit.codeAction.apply} never uses {@link Command#getArguments()} as-is.
     * The server often embeds {@code textDocument.version = null}, which Dart rejects.
     * We build fresh argument lists (copies) with a real version from {@link LspPlugin#getDocumentVersion}.
     */
    private static void executeDartApplyCodeActionCommand(GenericLspClient lspClient,
                                                          ExecuteCommandParams params,
                                                          Command command,
                                                          Buffer buffer) {
        List<Object> oneMapArg = buildSingleMapCommandArguments(command, buffer);
        params.setArguments(oneMapArg);
        lspClient.getServer().getWorkspaceService().executeCommand(params)
            .thenAccept(result -> applyExecuteCommandResult(result, command, buffer))
            .exceptionally(ex -> {
                Exception error = toException(ex);
                String message = getRootMessage(error);
                if (message != null && message.contains("requires 3 parameters")) {
                    params.setArguments(buildThreeArgumentCommandArguments(command, buffer));
                    lspClient.getServer().getWorkspaceService().executeCommand(params)
                        .thenAccept(retryResult ->
                            applyExecuteCommandResult(retryResult, command, buffer))
                        .exceptionally(retryEx -> {
                            reportApplyError(toException(retryEx));
                            return null;
                        });
                } else {
                    reportApplyError(error);
                }
                return null;
            });
    }

    /**
     * Applies a {@link WorkspaceEdit} returned by {@code workspace/executeCommand}.
     * Servers may also send {@code workspace/applyEdit} separately; this handles the
     * direct return value when present.
     */
    private static void applyExecuteCommandResult(Object result, Command command,
                                                  Buffer buffer) {
        WorkspaceEdit edit = LspWorkspaceEdits.workspaceEditFromExecuteResult(result);
        if (edit == null) {
            return;
        }

        String documentUri = parseCommandContext(command, buffer).documentUri;

        Runnable apply = () -> {
            if (!buffer.isEditable()) {
                Log.log(Log.WARNING, LspCodeActions.class,
                    "Buffer is not editable, cannot apply code action edit");
                return;
            }
            boolean applied = LspWorkspaceEdits.applyToBuffer(buffer, documentUri, edit);
            if (!applied) {
                applied = LspWorkspaceEdits.apply(edit);
            }
            if (!applied) {
                Log.log(Log.WARNING, LspCodeActions.class,
                    "executeCommand returned a WorkspaceEdit but no edits were applied");
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            apply.run();
        } else {
            SwingUtilities.invokeLater(apply);
        }
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

    private static void reportApplyError(Exception e) {
        Log.log(Log.ERROR, LspCodeActions.class, "Error applying LSP code action", e);
        SwingUtilities.invokeLater(() ->
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null));
    }

    private static boolean isCodeActionCommand(String commandId) {
        return commandId != null && commandId.contains("codeAction");
    }

    private static List<Object> buildSingleMapCommandArguments(Command command, Buffer buffer) {
        CommandContext ctx = parseCommandContext(command, buffer);
        Map<String, Object> arg = LspGsonArgs.copyFirstArgumentMap(command.getArguments());
        if (arg == null) {
            arg = new LinkedHashMap<>();
        }

        arg.put("textDocument", normalizeTextDocumentMap(arg.get("textDocument"), ctx));

        Map<String, Object> rangeMap = LspGsonArgs.asStringObjectMap(arg.get("range"));
        arg.put("range", rangeMap != null ? rangeMap : ctx.rangeMap);

        String kind = LspGsonArgs.asString(arg.get("kind"));
        if (kind == null || kind.isEmpty()) {
            arg.put("kind", ctx.kind);
        }

        return List.of(arg);
    }

    private static List<Object> buildThreeArgumentCommandArguments(Command command,
                                                                   Buffer buffer) {
        CommandContext ctx = parseCommandContext(command, buffer);
        return List.of(
            buildTextDocumentMap(ctx),
            ctx.rangeMap,
            ctx.kind);
    }

    /** Fields parsed from {@link Command#getArguments()}. */
    private static final class CommandContext {
        final String documentUri;
        final Map<String, Object> rangeMap;
        final String kind;
        final int documentVersion;

        CommandContext(String documentUri, Map<String, Object> rangeMap, String kind,
                       int documentVersion) {
            this.documentUri = documentUri;
            this.rangeMap = rangeMap;
            this.kind = kind;
            this.documentVersion = documentVersion;
        }
    }

    private static CommandContext parseCommandContext(Command command, Buffer buffer) {
        List<Object> arguments = command.getArguments();
        Map<String, Object> textDocument = null;
        Map<String, Object> rangeMap = null;
        String kind = null;

        Map<String, Object> argMap = LspGsonArgs.copyFirstArgumentMap(arguments);
        if (argMap != null) {
            textDocument = LspGsonArgs.asStringObjectMap(argMap.get("textDocument"));
            rangeMap = LspGsonArgs.asStringObjectMap(argMap.get("range"));
            kind = LspGsonArgs.asString(argMap.get("kind"));
        } else if (arguments != null && arguments.size() >= 3) {
            textDocument = LspGsonArgs.asStringObjectMap(arguments.get(0));
            rangeMap = LspGsonArgs.asStringObjectMap(arguments.get(1));
            kind = LspGsonArgs.asString(arguments.get(2));
        }

        String documentUri = null;
        Integer version = null;
        if (textDocument != null) {
            documentUri = LspGsonArgs.asString(textDocument.get("uri"));
            if (documentUri != null && documentUri.isEmpty()) {
                documentUri = null;
            }
            version = LspGsonArgs.asInteger(textDocument.get("version"));
        }
        if (documentUri == null) {
            documentUri = new File(buffer.getPath()).toURI().toString();
        }
        int documentVersion = version != null ? version : LspPlugin.getDocumentVersion(buffer);

        if (rangeMap == null) {
            rangeMap = rangeToMap(new Range(new Position(0, 0), new Position(0, 0)));
        }
        if (kind == null || kind.isEmpty()) {
            kind = CodeActionKind.QuickFix;
        }

        return new CommandContext(documentUri, rangeMap, kind, documentVersion);
    }

    private static Map<String, Object> buildTextDocumentMap(CommandContext ctx) {
        Map<String, Object> textDocument = new LinkedHashMap<>();
        textDocument.put("uri", ctx.documentUri);
        textDocument.put("version", ctx.documentVersion);
        return textDocument;
    }

    private static Map<String, Object> normalizeTextDocumentMap(Object value, CommandContext ctx) {
        Map<String, Object> textDocument = LspGsonArgs.asStringObjectMap(value);
        if (textDocument == null) {
            textDocument = new LinkedHashMap<>();
        }

        String uri = LspGsonArgs.asString(textDocument.get("uri"));
        if (uri == null || uri.isEmpty()) {
            textDocument.put("uri", ctx.documentUri);
        }

        if (LspGsonArgs.asInteger(textDocument.get("version")) == null) {
            textDocument.put("version", ctx.documentVersion);
        }

        return textDocument;
    }

    private static Map<String, Object> rangeToMap(Range range) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("start", positionToMap(range.getStart()));
        map.put("end", positionToMap(range.getEnd()));
        return map;
    }

    private static Map<String, Object> positionToMap(Position position) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line", position.getLine());
        map.put("character", position.getCharacter());
        return map;
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
}
