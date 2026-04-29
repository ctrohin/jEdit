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

import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.CompletionPopup;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.syntax.KeywordMap;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.util.Log;

/**
 * LSP-based word completion popup.
 * Similar to CompleteWord but uses Language Server Protocol for completions.
 */
public class LspCompletion extends CompletionPopup {

    private final JEditTextArea textArea;
    private final Buffer buffer;
    private final GenericLspClient lspClient;
    private String word;
    private final String noWordSep;
    private CompletableFuture<Void> requestFuture;

    /**
     * Trigger LSP completion at the current caret position.
     */
    public static void completeLsp(View view, GenericLspClient lspClient) {
        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();
        int caretLine = textArea.getCaretLine();
        int caret = textArea.getCaretPosition();

        if (!buffer.isEditable()) {
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        if (lspClient == null || lspClient.getServer() == null) {
            Log.log(Log.WARNING, LspCompletion.class, "LSP server not available for completion");
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
            return;
        }

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(caret);
        String noWordSep = getNonAlphaNumericWordChars(buffer, keywordMap);
        String wordToComplete = getWordToComplete(buffer, caretLine, caret, noWordSep);

//        if (wordToComplete == null) {
//            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
//            return;
//        }

        // Request completions from LSP server
        requestLspCompletions(view, lspClient, wordToComplete, noWordSep, caret);
    }

    /**
     * Request completions from the LSP server asynchronously.
     */
    private static void requestLspCompletions(View view, GenericLspClient lspClient,
                                               String word, String noWordSep, int caret) {
        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();
        int caretLine = textArea.getCaretLine();

        try {
            // Build completion request
            CompletionParams params = new CompletionParams();
            params.setTextDocument(new TextDocumentIdentifier(
                new File(buffer.getPath()).toURI().toString()));

            int lineStartOffset = buffer.getLineStartOffset(caretLine);
            int character = caret - lineStartOffset;
            params.setPosition(new Position(caretLine, character));

            CompletionContext context = new CompletionContext();
            context.setTriggerKind(CompletionTriggerKind.forValue(1)); // Invoked
            params.setContext(context);

            // Request from server asynchronously
            CompletableFuture<Either<List<CompletionItem>, CompletionList>> future =
                lspClient.getServer().getTextDocumentService().completion(params);

            future.thenAccept(result -> {
                List<CompletionItem> items = new ArrayList<>();

                if (result != null) {
                    if (result.isLeft()) {
                        items = result.getLeft();
                    } else if (result.isRight()) {
                        items = result.getRight().getItems();
                    }
                }

                if (items.isEmpty()) {
                    javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                    return;
                }

                // Display completions in popup
                List<CompletionItem> finalItems = items;
                SwingUtilities.invokeLater(() -> {
                    final int wordLength = word == null ? 0 : word.length();
                    textArea.scrollToCaret(false);
                    Point location = textArea.offsetToXY(caret - wordLength);
                    location.y += textArea.getPainter().getLineHeight();

                    SwingUtilities.convertPointToScreen(location,
                        textArea.getPainter());

                    new LspCompletion(view, word, finalItems, location, noWordSep, lspClient);
                });
            }).exceptionally(ex -> {
                Log.log(Log.ERROR, LspCompletion.class, "Error requesting LSP completions", ex);
                return null;
            });

        } catch (Exception e) {
            Log.log(Log.ERROR, LspCompletion.class, "Error in LSP completion request", e);
            javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
        }
    }

    /**
     * Create an LSP completion popup.
     */
    public LspCompletion(View view, String word, List<CompletionItem> items,
                         Point location, String noWordSep, GenericLspClient lspClient) {
        super(view, location);

        this.textArea = view.getTextArea();
        this.buffer = view.getBuffer();
        this.lspClient = lspClient;
        this.word = word;
        this.noWordSep = noWordSep;

        reset(new LspCompletionCandidates(items), true);
    }

    /**
     * Get non-alphanumeric word separator characters.
     */
    private static String getNonAlphaNumericWordChars(Buffer buffer, KeywordMap keywordMap) {
        String noWordSep = buffer.getStringProperty("noWordSep");
        if (noWordSep == null)
            noWordSep = "";
        if (keywordMap != null) {
            String keywordNoWordSep = keywordMap.getNonAlphaNumericChars();
            if (keywordNoWordSep != null)
                noWordSep += keywordNoWordSep;
        }
        return noWordSep;
    }

    /**
     * Get the word to complete at the caret position.
     */
    private static String getWordToComplete(Buffer buffer, int caretLine,
                                             int caret, String noWordSep) {
        CharSequence line = buffer.getLineSegment(caretLine);
        int dot = caret - buffer.getLineStartOffset(caretLine);
        if (dot == 0)
            return null;

        char ch = line.charAt(dot - 1);
        if (!Character.isLetterOrDigit(ch)
            && noWordSep.indexOf(ch) == -1) {
            return null;
        }

        int wordStart = TextUtilities.findWordStart(line, dot - 1, noWordSep);
        CharSequence wordChars = line.subSequence(wordStart, dot);
        if (wordChars.length() == 0)
            return null;

        return wordChars.toString();
    }

    @Override
    protected void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            textArea.backspace();
            e.consume();

            if (word.length() == 1) {
                dispose();
            } else {
                resetWords(word.substring(0, word.length() - 1));
            }
        }
    }

    @Override
    protected void keyTyped(KeyEvent e) {
        char ch = e.getKeyChar();
        if (jEdit.getBooleanProperty("insertCompletionWithDigit") && Character.isDigit(ch)) {
            int index = ch - '0';
            if (index == 0)
                index = 9;
            else
                index--;
            if (index < getCandidates().getSize()) {
                setSelectedIndex(index);
                if (doSelectedCompletion()) {
                    e.consume();
                    dispose();
                }
                return;
            }
        }

        if (ch != '\b' && ch != '\t') {
            if (!Character.isLetterOrDigit(ch) && noWordSep.indexOf(ch) == -1) {
                doSelectedCompletion();
                textArea.userInput(ch);
                e.consume();
                dispose();
                return;
            }

            textArea.userInput(ch);
            e.consume();
            resetWords(word + ch);
        }
    }

    /**
     * Reset candidates with a new word.
     */
    private void resetWords(String newWord) {
        // For simplicity, just dispose and let user request completion again
        // A more sophisticated implementation could filter the existing results
        dispose();
    }

    /**
     * Candidates implementation for LSP completion items.
     */
    private class LspCompletionCandidates implements Candidates {
        private final List<CompletionItem> items;
        private final DefaultListCellRenderer renderer;

        LspCompletionCandidates(List<CompletionItem> items) {
            this.items = items;
            this.renderer = new DefaultListCellRenderer();
        }

        @Override
        public int getSize() {
            return items.size();
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void complete(int index) {
            if (index < 0 || index >= items.size())
                return;

            CompletionItem item = items.get(index);
            String insertText = item.getInsertText();
            if (insertText == null || insertText.isEmpty()) {
                insertText = item.getLabel();
            }

            // Insert the completion text, removing the already-typed word
            String insertion = insertText.substring(word.length());
            textArea.replaceSelection(insertion);
        }

        @Override
        public Component getCellRenderer(JList list, int index,
                                        boolean isSelected, boolean cellHasFocus) {
            renderer.getListCellRendererComponent(list,
                null, index, isSelected, cellHasFocus);

            CompletionItem item = items.get(index);
            String text = item.getLabel();
            Font font = list.getFont();

            // Add completion kind indicator
            String kind = getCompletionKindString(item.getKind());
            if (kind != null) {
                text = text + " [" + kind + "]";
            }

            if (index < 9)
                text = (index + 1) + ": " + text;
            else if (index == 9)
                text = "0: " + text;

            renderer.setText(text);
            renderer.setFont(font);
            return renderer;
        }

        @Override
        public String getDescription(int index) {
            if (index < 0 || index >= items.size())
                return null;

            CompletionItem item = items.get(index);
            return item.getDetail();
        }

        /**
         * Convert LSP CompletionItemKind to a readable string.
         */
        private String getCompletionKindString(CompletionItemKind kind) {
            if (kind == null)
                return null;

            switch (kind) {
                case Text:
                    return "Text";
                case Method:
                    return "Method";
                case Function:
                    return "Function";
                case Constructor:
                    return "Constructor";
                case Field:
                    return "Field";
                case Variable:
                    return "Variable";
                case Class:
                    return "Class";
                case Interface:
                    return "Interface";
                case Module:
                    return "Module";
                case Property:
                    return "Property";
                case Unit:
                    return "Unit";
                case Value:
                    return "Value";
                case Enum:
                    return "Enum";
                case Keyword:
                    return "Keyword";
                case Snippet:
                    return "Snippet";
                case Color:
                    return "Color";
                case File:
                    return "File";
                case Reference:
                    return "Reference";
                case Folder:
                    return "Folder";
                case EnumMember:
                    return "EnumMember";
                case Constant:
                    return "Constant";
                case Struct:
                    return "Struct";
                case Event:
                    return "Event";
                case Operator:
                    return "Operator";
                case TypeParameter:
                    return "TypeParameter";
                default:
                    return null;
            }
        }
    }
}

