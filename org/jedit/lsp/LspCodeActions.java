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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
                List<ActionItem> items = parseActions(result);
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

    private static List<ActionItem> parseActions(List<Either<Command, CodeAction>> result) {
        List<ActionItem> items = new ArrayList<>();
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
                    items.add(ActionItem.forCommand(command));
                }
            } else if (either.isRight()) {
                CodeAction action = either.getRight();
                if (action != null && action.getTitle() != null) {
                    items.add(ActionItem.forCodeAction(action));
                }
            }
        }
        return items;
    }

    private static void showActionMenu(View view, GenericLspClient lspClient,
                                       String documentUri, Range requestRange,
                                       List<ActionItem> items) {
        JEditTextArea textArea = view.getTextArea();
        JPopupMenu popup = new JPopupMenu();

        for (ActionItem item : items) {
            JMenuItem menuItem = new JMenuItem(item.getTitle());
            menuItem.addActionListener(e ->
                applyAction(view, lspClient, documentUri, requestRange, item));
            popup.add(menuItem);
        }

        Point location = textArea.offsetToXY(textArea.getCaretPosition());
        location.y += textArea.getPainter().getLineHeight();
        SwingUtilities.convertPointToScreen(location, textArea.getPainter());
        GenericGUIUtilities.showPopupMenu(popup, textArea.getPainter(), location.x, location.y, false);
    }

    private static void applyAction(View view, GenericLspClient lspClient,
                                    String documentUri, Range requestRange,
                                    ActionItem item) {
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
                String kind = action != null ? action.getKind() : null;
                executeWorkspaceCommand(lspClient, command, documentUri, requestRange, kind);
            }
        } catch (Exception e) {
            Log.log(Log.ERROR, LspCodeActions.class, "Error applying LSP code action", e);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    /**
     * Runs {@code workspace/executeCommand}, building Dart-compatible arguments when needed.
     * Dart returns many code actions as plain {@link Command} (Either left), not {@link CodeAction}.
     */
    private static void executeWorkspaceCommand(GenericLspClient lspClient,
                                                Command command,
                                                String documentUri,
                                                Range range,
                                                String kind) throws Exception {
        ExecuteCommandParams params = new ExecuteCommandParams();
        final String commandId = command.getCommand();
        params.setCommand(commandId);

        if ("dart.edit.codeAction.apply".equals(commandId)) {
            executeDartApplyCodeActionCommand(lspClient, params, command, documentUri, range, kind);
            return;
        }

        if (isCodeActionCommand(commandId)) {
            List<Object> originalArgs = command.getArguments();
            List<Object> threeArgs = buildThreeArgumentCommandArguments(documentUri, range, kind);
            List<Object> oneMapArg = buildSingleMapCommandArguments(command, documentUri, range, kind);

            // Try server-provided arguments first, then fall back based on runtime errors.
            try {
                params.setArguments(originalArgs);
                lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                return;
            } catch (Exception firstError) {
                String message = getRootMessage(firstError);
                if (message != null && message.contains("requires a single Map argument")) {
                    params.setArguments(oneMapArg);
                    lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                    return;
                }
                if (message != null && message.contains("requires 3 parameters")) {
                    params.setArguments(threeArgs);
                    lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                    return;
                }

                // Unknown mismatch: try both known Dart shapes before surfacing error.
                try {
                    params.setArguments(threeArgs);
                    lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                    return;
                } catch (Exception ignored) {
                    params.setArguments(oneMapArg);
                    lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                    return;
                }
            }
        } else {
            params.setArguments(command.getArguments());
            lspClient.getServer().getWorkspaceService().executeCommand(params).get();
        }
    }

    private static void executeDartApplyCodeActionCommand(GenericLspClient lspClient,
                                                          ExecuteCommandParams params,
                                                          Command command,
                                                          String documentUri,
                                                          Range range,
                                                          String kind) throws Exception {
        List<Object> originalArgs = command.getArguments();
        if (originalArgs != null) {
            try {
                params.setArguments(originalArgs);
                lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                return;
            } catch (Exception ignored) {
                ignored.printStackTrace();
                // Fall through to synthesized arguments.
            }
        }

        List<Object> oneMapArg = buildSingleMapCommandArguments(command, documentUri, range, kind);
        try {
            params.setArguments(oneMapArg);
            lspClient.getServer().getWorkspaceService().executeCommand(params).get();
            return;
        } catch (Exception oneMapError) {
            String message = getRootMessage(oneMapError);
            if (message != null && message.contains("requires 3 parameters")) {
                List<Object> threeArgs = buildThreeArgumentCommandArguments(documentUri, range, kind);
                params.setArguments(threeArgs);
                lspClient.getServer().getWorkspaceService().executeCommand(params).get();
                return;
            }

            // If single-map still fails for any other reason, preserve that exact error.
            throw oneMapError;
        }
    }

    private static boolean isCodeActionCommand(String commandId) {
        return commandId != null && commandId.contains("codeAction");
    }

    private static List<Object> buildSingleMapCommandArguments(Command command,
                                                               String documentUri,
                                                               Range range,
                                                               String kind) {
        Map<String, Object> arg = copyFirstArgumentMap(command.getArguments());
        if (arg == null) {
            arg = new LinkedHashMap<>();
        }

        if (!(arg.get("textDocument") instanceof Map)) {
            arg.put("textDocument", buildTextDocumentMap(documentUri));
        }
        if (!(arg.get("range") instanceof Map)) {
            arg.put("range", rangeToMap(range));
        }

        String resolvedKind = kind;
        if (resolvedKind == null || resolvedKind.isEmpty()) {
            Object existingKind = arg.get("kind");
            if (existingKind instanceof String) {
                resolvedKind = (String) existingKind;
            }
        }
        if (resolvedKind == null || resolvedKind.isEmpty()) {
            resolvedKind = CodeActionKind.QuickFix;
        }
        if (!(arg.get("kind") instanceof String)) {
            arg.put("kind", resolvedKind);
        }

        return List.of(arg);
    }

    private static List<Object> buildThreeArgumentCommandArguments(String documentUri,
                                                                   Range range,
                                                                   String kind) {
        String resolvedKind = kind;
        if (resolvedKind == null || resolvedKind.isEmpty()) {
            resolvedKind = CodeActionKind.QuickFix;
        }
        return List.of(buildTextDocumentMap(documentUri), rangeToMap(range), resolvedKind);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyFirstArgumentMap(List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        Object first = arguments.get(0);
        if (first instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) first);
        }
        return null;
    }

    private static Map<String, Object> buildTextDocumentMap(String documentUri) {
        Map<String, Object> textDocument = new LinkedHashMap<>();
        textDocument.put("uri", documentUri);
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

    private static final class ActionItem {
        private final String title;
        private final Command command;
        private final CodeAction codeAction;

        private ActionItem(String title, Command command, CodeAction codeAction) {
            this.title = title;
            this.command = command;
            this.codeAction = codeAction;
        }

        static ActionItem forCommand(Command command) {
            return new ActionItem(command.getTitle(), command, null);
        }

        static ActionItem forCodeAction(CodeAction codeAction) {
            return new ActionItem(codeAction.getTitle(), null, codeAction);
        }

        String getTitle() {
            return title;
        }

        boolean isCommand() {
            return command != null;
        }

        Command getCommand() {
            return command;
        }

        CodeAction getCodeAction() {
            return codeAction;
        }
    }
}
