/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.syntax.KeywordMap;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.util.Log;

/**
 * LSP symbol searches: references, implementations, type definition, declaration,
 * document/workspace symbols, and call hierarchy.
 */
public final class LspSymbolSearches {

    private LspSymbolSearches() {}

    public static void findReferencesLsp(View view, GenericLspClient lspClient) {
        runPositionSearch(view, lspClient, LspSymbolSearchResult.Kind.REFERENCES,
            (service, uri, position) -> {
                ReferenceParams params = new ReferenceParams();
                params.setTextDocument(new TextDocumentIdentifier(uri));
                params.setPosition(position);
                ReferenceContext context = new ReferenceContext();
                context.setIncludeDeclaration(true);
                params.setContext(context);
                return service.references(params)
                    .thenApply(LspLocations::fromLocationList);
            });
    }

    public static void findImplementationsLsp(View view, GenericLspClient lspClient) {
        runLocationSearch(view, lspClient, LspSymbolSearchResult.Kind.IMPLEMENTATION,
            (service, uri, position) -> {
                ImplementationParams params = new ImplementationParams();
                params.setTextDocument(new TextDocumentIdentifier(uri));
                params.setPosition(position);
                return service.implementation(params)
                    .thenApply(LspLocations::fromDefinition);
            });
    }

    public static void findTypeDefinitionLsp(View view, GenericLspClient lspClient) {
        runLocationSearch(view, lspClient, LspSymbolSearchResult.Kind.TYPE_DEFINITION,
            (service, uri, position) -> {
                TypeDefinitionParams params = new TypeDefinitionParams();
                params.setTextDocument(new TextDocumentIdentifier(uri));
                params.setPosition(position);
                return service.typeDefinition(params)
                    .thenApply(LspLocations::fromDefinition);
            });
    }

    public static void findDeclarationLsp(View view, GenericLspClient lspClient) {
        if (!prepare(view, lspClient)) {
            return;
        }
        if (!lspClient.supportsDeclaration()) {
            LspGoToDefinition.goToDefinitionLsp(view, lspClient);
            return;
        }
        runLocationSearch(view, lspClient, LspSymbolSearchResult.Kind.DECLARATION,
            (service, uri, position) -> {
                DeclarationParams params = new DeclarationParams();
                params.setTextDocument(new TextDocumentIdentifier(uri));
                params.setPosition(position);
                return service.declaration(params)
                    .thenApply(LspLocations::fromDefinition);
            },
            () -> LspGoToDefinition.goToDefinitionLsp(view, lspClient));
    }

    public static void documentSymbolsLsp(View view, GenericLspClient lspClient) {
        if (!prepare(view, lspClient)) {
            return;
        }
        Buffer buffer = view.getBuffer();
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        String title = buffer.getName();

        LspPlugin.flushBufferChangesAsync(buffer)
            .thenComposeAsync(ignored -> lspClient.whenReady(), LspAsync.EXECUTOR)
            .thenComposeAsync(ignored ->
                lspClient.getServer().getTextDocumentService().documentSymbol(
                    documentSymbolParams(uri)))
            .thenAccept(symbols -> SwingUtilities.invokeLater(() -> {
                List<LspSymbolHit> hits = LspLocations.fromDocumentSymbols(uri, symbols);
                publishOrFeedback(view, LspSymbolSearchResult.forDocumentSymbols(uri, hits), title);
            }))
            .exceptionally(ex -> {
                logAndFeedback(view, "document symbols", ex);
                return null;
            });
    }

    public static void workspaceSymbolsLsp(View view, GenericLspClient lspClient) {
        if (!prepare(view, lspClient)) {
            return;
        }
        String query = JOptionPane.showInputDialog(
            view,
            jEdit.getProperty("lsp-symbol-results.workspace-query-prompt"),
            jEdit.getProperty("lsp-symbol-results.workspace-query-title"),
            JOptionPane.QUESTION_MESSAGE);
        if (query == null) {
            return;
        }
        query = query.trim();
        if (query.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        final String finalQuery = query;
        Buffer buffer = view.getBuffer();
        LspPlugin.flushBufferChangesAsync(buffer)
            .thenComposeAsync(ignored -> lspClient.whenReady(), LspAsync.EXECUTOR)
            .thenComposeAsync(ignored -> {
                WorkspaceSymbolParams params = new WorkspaceSymbolParams();
                params.setQuery(finalQuery);
                return lspClient.getServer().getWorkspaceService().symbol(params);
            })
            .thenAccept(symbols -> SwingUtilities.invokeLater(() -> {
                List<LspSymbolHit> hits = parseWorkspaceSymbols(symbols);
                publishOrFeedback(view,
                    LspSymbolSearchResult.forHits(
                        LspSymbolSearchResult.Kind.WORKSPACE_SYMBOLS, finalQuery, hits),
                    finalQuery);
            }))
            .exceptionally(ex -> {
                logAndFeedback(view, "workspace symbols", ex);
                return null;
            });
    }

    public static void callHierarchyLsp(View view, GenericLspClient lspClient) {
        if (!prepare(view, lspClient)) {
            return;
        }
        Buffer buffer = view.getBuffer();
        JEditTextArea textArea = view.getTextArea();
        int caret = textArea.getCaretPosition();
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        Position position = offsetToPosition(buffer, caret);
        String query = symbolNameAtCaret(buffer, caret);

        LspPlugin.flushBufferChangesAsync(buffer)
            .thenComposeAsync(ignored -> lspClient.whenReady(), LspAsync.EXECUTOR)
            .thenComposeAsync(ignored -> {
                CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
                params.setTextDocument(new TextDocumentIdentifier(uri));
                params.setPosition(position);
                return lspClient.getServer().getTextDocumentService().prepareCallHierarchy(params);
            })
            .thenAccept(items -> SwingUtilities.invokeLater(() -> {
                List<CallHierarchyItem> roots = items != null ? items : List.of();
                publishOrFeedback(view,
                    LspSymbolSearchResult.forCallHierarchy(query, roots),
                    query);
            }))
            .exceptionally(ex -> {
                logAndFeedback(view, "call hierarchy", ex);
                return null;
            });
    }

    /**
     * Lazy-loads incoming and outgoing call hierarchy children for a tree node.
     */
    public static void loadCallHierarchyChildren(View view, GenericLspClient lspClient,
                                                   CallHierarchyItem item,
                                                   LspCallHierarchyBranch branch) {
        if (view == null || lspClient == null || item == null || branch == null
            || branch.isLoaded()) {
            return;
        }
        branch.setLoading(true);
        LspSymbolSearchHub.getInstance().fireStructureChanged();

        CompletableFuture<List<LspSymbolHit>> incomingFuture =
            lspClient.whenReady().thenComposeAsync(ignored -> {
                CallHierarchyIncomingCallsParams params = new CallHierarchyIncomingCallsParams();
                params.setItem(item);
                return lspClient.getServer().getTextDocumentService()
                    .callHierarchyIncomingCalls(params)
                    .thenApply(LspLocations::fromIncomingCalls);
            }, LspAsync.EXECUTOR);
        CompletableFuture<List<LspSymbolHit>> outgoingFuture =
            lspClient.whenReady().thenComposeAsync(ignored -> {
                CallHierarchyOutgoingCallsParams params = new CallHierarchyOutgoingCallsParams();
                params.setItem(item);
                return lspClient.getServer().getTextDocumentService()
                    .callHierarchyOutgoingCalls(params)
                    .thenApply(LspLocations::fromOutgoingCalls);
            }, LspAsync.EXECUTOR);

        incomingFuture.thenCombine(outgoingFuture, (incoming, outgoing) -> {
            branch.setIncoming(LspSymbolHit.sortedCopy(incoming));
            branch.setOutgoing(LspSymbolHit.sortedCopy(outgoing));
            branch.setLoaded(true);
            branch.setLoading(false);
            return null;
        }).whenComplete((ignored, ex) -> SwingUtilities.invokeLater(() -> {
            if (ex != null) {
                branch.setLoading(false);
                Log.log(Log.ERROR, LspSymbolSearches.class,
                    "Error loading call hierarchy children", ex);
                UIManager.getLookAndFeel().provideErrorFeedback(view);
            }
            LspSymbolSearchHub.getInstance().fireStructureChanged();
        }));
    }

    @FunctionalInterface
    private interface LocationSearch {
        CompletableFuture<List<Location>> search(TextDocumentService service,
                                                 String uri, Position position);
    }

    private static void runLocationSearch(View view, GenericLspClient lspClient,
                                          LspSymbolSearchResult.Kind kind,
                                          LocationSearch search) {
        runPositionSearch(view, lspClient, kind, search, null);
    }

    private static void runLocationSearch(View view, GenericLspClient lspClient,
                                          LspSymbolSearchResult.Kind kind,
                                          LocationSearch search,
                                          Runnable unsupportedFallback) {
        runPositionSearch(view, lspClient, kind, search, unsupportedFallback);
    }

    private static void runPositionSearch(View view, GenericLspClient lspClient,
                                          LspSymbolSearchResult.Kind kind,
                                          LocationSearch search) {
        runPositionSearch(view, lspClient, kind, search, null);
    }

    private static void runPositionSearch(View view, GenericLspClient lspClient,
                                          LspSymbolSearchResult.Kind kind,
                                          LocationSearch search,
                                          Runnable unsupportedFallback) {
        if (!prepare(view, lspClient)) {
            return;
        }
        Buffer buffer = view.getBuffer();
        JEditTextArea textArea = view.getTextArea();
        int caret = textArea.getCaretPosition();
        String uri = LspDocumentUri.pathToUri(buffer.getPath());
        Position position = offsetToPosition(buffer, caret);
        String query = symbolNameAtCaret(buffer, caret);

        LspPlugin.flushBufferChangesAsync(buffer)
            .thenComposeAsync(ignored -> lspClient.whenReady(), LspAsync.EXECUTOR)
            .thenComposeAsync(ignored ->
                search.search(lspClient.getServer().getTextDocumentService(), uri, position))
            .thenAccept(locations -> SwingUtilities.invokeLater(() ->
                publishOrFeedback(view,
                    LspSymbolSearchResult.forLocations(kind, query, locations),
                    query)))
            .exceptionally(ex -> {
                if (unsupportedFallback != null && LspRpcSupport.isUnsupportedMethod(ex)) {
                    unsupportedFallback.run();
                    return null;
                }
                logAndFeedback(view, kind.name(), ex);
                return null;
            });
    }

    private static boolean prepare(View view, GenericLspClient lspClient) {
        if (view == null) {
            return false;
        }
        if (lspClient == null || lspClient.getServer() == null) {
            Log.log(Log.WARNING, LspSymbolSearches.class,
                "LSP server not available for symbol search");
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return false;
        }
        return true;
    }

    private static void publishOrFeedback(View view, LspSymbolSearchResult result,
                                          String query) {
        if (result.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }
        LspSymbolSearchHub.getInstance().publish(result);
        showResults(view);
    }

    static void showResults(View view) {
        if (view != null) {
            view.getDockableWindowManager().showDockableWindow(LspSymbolResultsView.NAME);
        }
    }

    private static void logAndFeedback(View view, String feature, Throwable ex) {
        if (LspRpcSupport.isUnsupportedMethod(ex)) {
            Log.log(Log.WARNING, LspSymbolSearches.class,
                "LSP " + feature + " is not supported by this language server");
            SwingUtilities.invokeLater(() ->
                UIManager.getLookAndFeel().provideErrorFeedback(view));
            return;
        }
        Log.log(Log.ERROR, LspSymbolSearches.class,
            "Error requesting LSP " + feature, ex);
        SwingUtilities.invokeLater(() ->
            UIManager.getLookAndFeel().provideErrorFeedback(view));
    }

    private static List<LspSymbolHit> parseWorkspaceSymbols(
        Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>> symbols) {
        List<LspSymbolHit> hits = new ArrayList<>();
        if (symbols == null) {
            return hits;
        }
        if (symbols.isLeft()) {
            List<? extends SymbolInformation> list = symbols.getLeft();
            if (list != null) {
                for (SymbolInformation info : list) {
                    if (info != null && info.getLocation() != null) {
                        hits.add(LspLocations.symbolInformationHit(info));
                    }
                }
            }
        } else {
            List<? extends WorkspaceSymbol> list = symbols.getRight();
            if (list != null) {
                for (WorkspaceSymbol symbol : list) {
                    if (symbol != null) {
                        hits.add(LspLocations.workspaceSymbolHit(symbol));
                    }
                }
            }
        }
        return hits;
    }

    private static DocumentSymbolParams documentSymbolParams(String uri) {
        DocumentSymbolParams params = new DocumentSymbolParams();
        params.setTextDocument(new TextDocumentIdentifier(uri));
        return params;
    }

    private static Position offsetToPosition(Buffer buffer, int offset) {
        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        return new Position(line, offset - lineStart);
    }

    private static String symbolNameAtCaret(Buffer buffer, int caret) {
        int line = buffer.getLineOfOffset(caret);
        CharSequence lineText = buffer.getLineSegment(line);
        int lineOffset = caret - buffer.getLineStartOffset(line);

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(caret);
        String noWordSep = buffer.getStringProperty("noWordSep");
        if (noWordSep == null) {
            noWordSep = "";
        }
        if (keywordMap != null) {
            String keywordNoWordSep = keywordMap.getNonAlphaNumericChars();
            if (keywordNoWordSep != null) {
                noWordSep += keywordNoWordSep;
            }
        }
        if (noWordSep.isEmpty()) {
            noWordSep = "_";
        }

        if (lineText.isEmpty()) {
            return jEdit.getProperty("lsp-symbol-results.unknown-symbol");
        }

        int index = lineOffset;
        if (index >= lineText.length()) {
            index = lineText.length() - 1;
        } else if (index > 0 && !isWordChar(lineText.charAt(index), noWordSep)) {
            index--;
        }
        if (index < 0 || !isWordChar(lineText.charAt(index), noWordSep)) {
            return jEdit.getProperty("lsp-symbol-results.unknown-symbol");
        }

        int wordStart = TextUtilities.findWordStart(lineText, index, noWordSep);
        int wordEnd = TextUtilities.findWordEnd(lineText, index + 1, noWordSep);
        if (wordStart >= wordEnd) {
            return jEdit.getProperty("lsp-symbol-results.unknown-symbol");
        }
        return lineText.subSequence(wordStart, wordEnd).toString();
    }

    private static boolean isWordChar(char ch, String noWordSep) {
        return Character.isLetterOrDigit(ch) || noWordSep.indexOf(ch) != -1;
    }
}
