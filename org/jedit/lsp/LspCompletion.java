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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JEditorPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

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

    private static final int LIST_CELL_WIDTH = 600;
    private static final int DESCRIPTION_WIDTH = 300;
    private static final int DESCRIPTION_MAX_HEIGHT = 320;
    private static final int DESCRIPTION_GAP = 4;
    private static final Pattern ARROW_SIGNATURE =
        Pattern.compile("^\\((.*)\\)\\s*->\\s*(.+)$");

    private static final ConcurrentHashMap<GenericLspClient, CompletionCoordinator> completionCoordinators =
        new ConcurrentHashMap<>();
    private static final Map<View, LspCompletion> activePopups = new IdentityHashMap<>();

    private final View view;
    private final GenericLspClient lspClient;
    private final JEditTextArea textArea;
    private final Buffer buffer;
    private String word;
    private final String noWordSep;
    private List<CompletionItem> allItems;
    private List<CompletionItem> visibleItems;
    private final JWindow descriptionWindow;
    private final JEditorPane descriptionPane;
    private final JScrollPane descriptionScroll;
    private final JPanel descriptionPanel;

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

        closeActivePopup(view);

        completionCoordinators
            .computeIfAbsent(lspClient, c -> new CompletionCoordinator())
            .schedule(view, lspClient, wordToComplete, noWordSep, caret, false,
                CompletionTriggerKind.Invoked, null);
    }

    /**
     * Trigger LSP completion because the user typed a mode-specific trigger character.
     */
    static void completeLspOnTrigger(View view, GenericLspClient lspClient,
                                     String triggerCharacter) {
        if (view == null || triggerCharacter == null || triggerCharacter.isEmpty()) {
            return;
        }
        if (getActivePopup(view) != null) {
            return;
        }

        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();
        int caretLine = textArea.getCaretLine();
        int caret = textArea.getCaretPosition();

        if (!buffer.isEditable()) {
            return;
        }
        if (lspClient == null || lspClient.getServer() == null) {
            return;
        }

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(caret);
        String noWordSep = getNonAlphaNumericWordChars(buffer, keywordMap);
        String wordToComplete = getWordToComplete(buffer, caretLine, caret, noWordSep);

        completionCoordinators
            .computeIfAbsent(lspClient, c -> new CompletionCoordinator())
            .schedule(view, lspClient, wordToComplete, noWordSep, caret, false,
                CompletionTriggerKind.TriggerCharacter, triggerCharacter);
    }

    private static synchronized void closeActivePopup(View view) {
        LspCompletion active = activePopups.get(view);
        if (active != null) {
            active.dispose();
        }
    }

    private static synchronized void setActivePopup(View view, LspCompletion popup) {
        if (popup == null) {
            activePopups.remove(view);
        } else {
            LspCompletion existing = activePopups.get(view);
            if (existing != null && existing != popup) {
                existing.dispose();
            }
            activePopups.put(view, popup);
        }
    }

    private static synchronized LspCompletion getActivePopup(View view) {
        return activePopups.get(view);
    }

    /**
     * Serialize completion requests per LSP client so only one is in flight at a time.
     * Rapid re-triggers coalesce to the latest caret position.
     */
    private static final class CompletionCoordinator {
        private final AtomicInteger generation = new AtomicInteger(0);
        private final Object lock = new Object();
        private boolean inFlight;
        private PendingCompletion pending;

        void schedule(View view, GenericLspClient client, String word,
                        String noWordSep, int caret, boolean refreshExisting,
                        CompletionTriggerKind triggerKind, String triggerCharacter) {
            int requestGeneration = generation.incrementAndGet();
            PendingCompletion request = new PendingCompletion(
                view, word, noWordSep, caret, requestGeneration, refreshExisting,
                triggerKind, triggerCharacter);

            synchronized (lock) {
                if (inFlight) {
                    pending = request;
                    return;
                }
                inFlight = true;
            }

            sendRequest(client, request);
        }

        private void sendRequest(GenericLspClient client, PendingCompletion request) {
            try {
                View view = request.view;
                JEditTextArea textArea = view.getTextArea();
                Buffer buffer = view.getBuffer();
                int caretLine = textArea.getCaretLine();

                CompletionParams params = new CompletionParams();
                params.setTextDocument(new TextDocumentIdentifier(
                    LspDocumentUri.pathToUri(buffer.getPath())));

                int lineStartOffset = buffer.getLineStartOffset(caretLine);
                int character = request.caret - lineStartOffset;
                params.setPosition(new Position(caretLine, character));

                CompletionContext context = new CompletionContext();
                if (request.refreshExisting) {
                    context.setTriggerKind(
                        CompletionTriggerKind.TriggerForIncompleteCompletions);
                } else {
                    context.setTriggerKind(request.triggerKind);
                    if (request.triggerCharacter != null) {
                        context.setTriggerCharacter(request.triggerCharacter);
                    }
                }
                params.setContext(context);

                CompletableFuture<Either<List<CompletionItem>, CompletionList>> future =
                    client.getServer().getTextDocumentService().completion(params);

                future.whenComplete((result, ex) -> {
                    try {
                        if (request.generation != generation.get()) {
                            return;
                        }

                        if (ex != null) {
                            if (!isSupersededCompletionError(ex)) {
                                Log.log(Log.ERROR, LspCompletion.class,
                                    "Error requesting LSP completions", ex);
                                if (isConnectionError(ex)) {
                                    Log.log(Log.WARNING, LspCompletion.class,
                                        "LSP server connection error, server may have crashed");
                                }
                                SwingUtilities.invokeLater(() ->
                                    javax.swing.UIManager.getLookAndFeel()
                                        .provideErrorFeedback(null));
                            }
                            return;
                        }

                        List<CompletionItem> items = new ArrayList<>();
                        if (result != null) {
                            if (result.isLeft()) {
                                items = result.getLeft();
                            } else if (result.isRight()) {
                                items = result.getRight().getItems();
                            }
                        }

                        if (items.isEmpty()) {
                            SwingUtilities.invokeLater(() ->
                                javax.swing.UIManager.getLookAndFeel()
                                    .provideErrorFeedback(null));
                            return;
                        }

                        List<CompletionItem> finalItems = items;
                        String word = request.word;
                        String noWordSep = request.noWordSep;
                        int caret = request.caret;
                        boolean refreshExisting = request.refreshExisting;
                        SwingUtilities.invokeLater(() -> {
                            LspCompletion active = getActivePopup(view);
                            if (refreshExisting && active != null && active.isDisplayable()) {
                                active.refreshFromServer(finalItems, word, caret);
                                return;
                            }
                            closeActivePopup(view);
                            final int wordLength = word == null ? 0 : word.length();
                            textArea.scrollToCaret(false);
                            Point location = textArea.offsetToXY(caret - wordLength);
                            location.y += textArea.getPainter().getLineHeight();
                            SwingUtilities.convertPointToScreen(location,
                                textArea.getPainter());
                            new LspCompletion(view, client, word, finalItems, location, noWordSep);
                        });
                    } finally {
                        dispatchNext(client);
                    }
                });
            } catch (Exception e) {
                Log.log(Log.ERROR, LspCompletion.class, "Error in LSP completion request", e);
                javax.swing.UIManager.getLookAndFeel().provideErrorFeedback(null);
                dispatchNext(client);
            }
        }

        private void dispatchNext(GenericLspClient client) {
            PendingCompletion next;
            synchronized (lock) {
                next = pending;
                pending = null;
                if (next == null) {
                    inFlight = false;
                    return;
                }
            }
            sendRequest(client, next);
        }
    }

    private static final class PendingCompletion {
        final View view;
        final String word;
        final String noWordSep;
        final int caret;
        final int generation;
        final boolean refreshExisting;
        final CompletionTriggerKind triggerKind;
        final String triggerCharacter;

        PendingCompletion(View view, String word, String noWordSep,
                          int caret, int generation, boolean refreshExisting,
                          CompletionTriggerKind triggerKind, String triggerCharacter) {
            this.view = view;
            this.word = word;
            this.noWordSep = noWordSep;
            this.caret = caret;
            this.generation = generation;
            this.refreshExisting = refreshExisting;
            this.triggerKind = triggerKind != null
                ? triggerKind : CompletionTriggerKind.Invoked;
            this.triggerCharacter = triggerCharacter;
        }
    }

    /**
     * Dart and some other servers reject the previous request when a new one starts.
     */
    private static boolean isSupersededCompletionError(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null
                && message.contains("Another textDocument/completion request was started")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Create an LSP completion popup.
     */
    public LspCompletion(View view, GenericLspClient lspClient, String word,
                         List<CompletionItem> items, Point location, String noWordSep) {
        super(view, location);

        this.view = view;
        this.lspClient = lspClient;
        this.textArea = view.getTextArea();
        this.buffer = view.getBuffer();
        this.word = word == null ? "" : word;
        this.noWordSep = noWordSep;
        this.allItems = new ArrayList<>(items);

        descriptionPane = new JEditorPane();
        descriptionPane.setContentType("text/html");
        descriptionPane.setEditable(false);
        descriptionPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        descriptionPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        descriptionScroll = new JScrollPane(descriptionPane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        descriptionScroll.setBorder(BorderFactory.createEmptyBorder());
        descriptionScroll.getVerticalScrollBar().setUnitIncrement(16);

        descriptionPanel = new JPanel(new BorderLayout());
        descriptionPanel.add(descriptionScroll, BorderLayout.CENTER);

        descriptionWindow = new JWindow(view);
        descriptionWindow.getContentPane().add(descriptionPanel);
        descriptionWindow.setFocusableWindowState(false);
        applyDescriptionColors();

        setActivePopup(view, this);
        showFilteredItems(items, textArea.getCaretPosition(), 0);
    }

    @Override
    protected void onCandidateSelected(int index, String description) {
        updateDescriptionWindow(index);
    }

    @Override
    protected void reposition(Point location) {
        super.reposition(location);
        if (descriptionWindow.isVisible()) {
            positionDescriptionWindow();
        }
    }

    @Override
    protected void setListCellWidth(int width) {
        super.setListCellWidth(width);
        if (descriptionWindow.isVisible()) {
            layoutDescriptionWindow();
            positionDescriptionWindow();
        }
    }

    @Override
    public void dispose() {
        synchronized (LspCompletion.class) {
            if (activePopups.get(view) == this) {
                activePopups.remove(view);
            }
        }
        if (descriptionWindow != null) {
            descriptionWindow.dispose();
        }
        super.dispose();
    }

    private void updateDescriptionWindow(int index) {
        if (visibleItems == null || index < 0 || index >= visibleItems.size()) {
            descriptionWindow.setVisible(false);
            return;
        }

        CompletionItem item = visibleItems.get(index);
        Color foreground = descriptionForeground();
        String html = LspHover.documentationToHtml(
            item.getDocumentation(), DESCRIPTION_WIDTH, foreground);
        if (html == null) {
            descriptionWindow.setVisible(false);
            return;
        }

        applyDescriptionColors();
        descriptionPane.setText(html);
        scrollDescriptionToTop();
        layoutDescriptionWindow();
        positionDescriptionWindow();
        descriptionWindow.setVisible(true);
    }

    private void layoutDescriptionWindow() {
        descriptionPane.setSize(DESCRIPTION_WIDTH, Integer.MAX_VALUE);
        int contentHeight = descriptionPane.getPreferredSize().height;
        int height;
        if (isDisplayable() && getHeight() > 0) {
            height = getHeight();
        } else {
            height = Math.min(contentHeight + 16, DESCRIPTION_MAX_HEIGHT);
        }
        descriptionScroll.setPreferredSize(new Dimension(DESCRIPTION_WIDTH, height));
        descriptionPanel.revalidate();
        descriptionWindow.pack();
        scrollDescriptionToTop();
    }

    private void positionDescriptionWindow() {
        Rectangle screen = getGraphicsConfiguration().getBounds();
        Point popupLoc = getLocation();
        Dimension popupSize = getSize();
        Dimension descSize = descriptionWindow.getSize();

        int x = popupLoc.x + popupSize.width + DESCRIPTION_GAP;
        int y = popupLoc.y;

        if (x + descSize.width > screen.x + screen.width - DESCRIPTION_GAP) {
            x = popupLoc.x - descSize.width - DESCRIPTION_GAP;
        }
        if (x < screen.x + DESCRIPTION_GAP) {
            x = screen.x + screen.width - descSize.width - DESCRIPTION_GAP;
        }
        if (y + descSize.height > screen.y + screen.height - DESCRIPTION_GAP) {
            y = screen.y + screen.height - descSize.height - DESCRIPTION_GAP;
        }
        descriptionWindow.setLocation(
            Math.max(screen.x + DESCRIPTION_GAP, x),
            Math.max(screen.y + DESCRIPTION_GAP, y));
    }

    private void scrollDescriptionToTop() {
        descriptionPane.setCaretPosition(0);
        descriptionScroll.getViewport().setViewPosition(new Point(0, 0));
        descriptionScroll.getVerticalScrollBar().setValue(0);
    }

    private void applyDescriptionColors() {
        Color background = descriptionBackground();
        Color foreground = descriptionForeground();
        Color borderColor = descriptionBorderColor(background, foreground);

        Border border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 2, 1, borderColor),
            BorderFactory.createEmptyBorder(8, 10, 8, 10));

        descriptionPanel.setOpaque(true);
        descriptionPanel.setBackground(background);
        descriptionPanel.setBorder(border);
        descriptionPane.setOpaque(true);
        descriptionPane.setBackground(background);
        descriptionPane.setForeground(foreground);
        descriptionScroll.getViewport().setBackground(background);

        if (descriptionWindow != null) {
            JPanel content = (JPanel) descriptionWindow.getContentPane();
            content.setOpaque(true);
            content.setBackground(background);
            content.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        }
    }

    private static Color descriptionBackground() {
        Color background = UIManager.getColor("ToolTip.background");
        if (background == null) {
            background = UIManager.getColor("Panel.background");
        }
        return background;
    }

    private static Color descriptionForeground() {
        Color foreground = UIManager.getColor("ToolTip.foreground");
        if (foreground == null) {
            foreground = UIManager.getColor("Label.foreground");
        }
        return foreground;
    }

    private static Color descriptionBorderColor(Color background, Color foreground) {
        Color border = UIManager.getColor("Component.borderColor");
        if (border == null) {
            border = UIManager.getColor("controlShadow");
        }
        if (border == null) {
            return foreground;
        }
        int delta = Math.abs(border.getRed() - background.getRed())
            + Math.abs(border.getGreen() - background.getGreen())
            + Math.abs(border.getBlue() - background.getBlue());
        return delta < 40 ? foreground : border;
    }

    private void refreshFromServer(List<CompletionItem> items, String newWord, int caret) {
        allItems = new ArrayList<>(items);
        word = newWord == null ? "" : newWord;
        List<CompletionItem> filtered = filterItems(allItems, word);
        if (filtered.isEmpty()) {
            dispose();
            return;
        }
        int selected = Math.min(Math.max(getSelectedIndex(), 0), filtered.size() - 1);
        showFilteredItems(filtered, caret, selected);
    }

    private void showFilteredItems(List<CompletionItem> items, int caret, int selectedIndex) {
        if (items.isEmpty()) {
            dispose();
            return;
        }
        visibleItems = items;
        reset(new LspCompletionCandidates(items), false);
        setListCellWidth(LIST_CELL_WIDTH);
        repositionPopup(caret);
        setSelectedIndex(selectedIndex);
        textArea.requestFocusInWindow();
    }

    private void repositionPopup(int caret) {
        int wordLength = word.length();
        Point location = textArea.offsetToXY(Math.max(0, caret - wordLength));
        location.y += textArea.getPainter().getLineHeight();
        SwingUtilities.convertPointToScreen(location, textArea.getPainter());
        reposition(location);
    }

    private static List<CompletionItem> filterItems(List<CompletionItem> items, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(items);
        }
        List<CompletionItem> filtered = new ArrayList<>();
        for (CompletionItem item : items) {
            if (matchesPrefix(item, prefix)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private static boolean matchesPrefix(CompletionItem item, String prefix) {
        String filterText = getFilterText(item);
        return filterText.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String getFilterText(CompletionItem item) {
        String filter = item.getFilterText();
        if (filter != null && !filter.isEmpty()) {
            return filter;
        }
        String label = item.getLabel();
        if (label != null && !label.isEmpty()) {
            return label;
        }
        String insertText = item.getInsertText();
        if (insertText != null && !insertText.isEmpty()) {
            return insertText;
        }
        return "";
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
        if (wordChars.isEmpty()) {
            return null;
        }

        return wordChars.toString();
    }

    private static boolean isWordChar(char ch, String noWordSep) {
        return Character.isLetterOrDigit(ch) || noWordSep.indexOf(ch) != -1;
    }

    /**
     * Returns {@code [startOffset, endOffset]} for the word at the caret.
     */
    private int[] getWordRangeAtCaret(int caret) {
        int line = buffer.getLineOfOffset(caret);
        int lineStart = buffer.getLineStartOffset(line);
        int dot = caret - lineStart;
        CharSequence lineText = buffer.getLineSegment(line);

        if (lineText.isEmpty()) {
            return new int[] {caret, caret};
        }

        int index = dot;
        if (index >= lineText.length()) {
            index = lineText.length() - 1;
        } else if (index > 0 && !isWordChar(lineText.charAt(index), noWordSep)) {
            index--;
        }

        if (index < 0 || !isWordChar(lineText.charAt(index), noWordSep)) {
            return new int[] {caret, caret};
        }

        boolean joinNonWordChars = textArea.getJoinNonWordChars();
        int wordStart = TextUtilities.findWordStart(lineText, index, noWordSep,
            joinNonWordChars, false, false);
        int wordEnd = TextUtilities.findWordEnd(lineText, index + 1, noWordSep,
            joinNonWordChars, false, false);
        return new int[] {lineStart + wordStart, lineStart + wordEnd};
    }

    private void replaceWordAtCaret(String newText) {
        if (newText == null) {
            return;
        }

        int caret = textArea.getCaretPosition();
        int[] range = getWordRangeAtCaret(caret);
        int start = range[0];
        int end = range[1];

        buffer.beginCompoundEdit();
        try {
            if (end > start) {
                buffer.remove(start, end - start);
            }
            buffer.insert(start, newText);
        } finally {
            buffer.endCompoundEdit();
        }
        textArea.setCaretPosition(start + newText.length());
    }

    private static String getCompletionInsertText(CompletionItem item) {
        Either<TextEdit, InsertReplaceEdit> textEditEither = item.getTextEdit();
        if (textEditEither != null) {
            if (textEditEither.isLeft()) {
                TextEdit textEdit = textEditEither.getLeft();
                if (textEdit != null && textEdit.getNewText() != null) {
                    return textEdit.getNewText();
                }
            } else if (textEditEither.isRight()) {
                InsertReplaceEdit insertReplaceEdit = textEditEither.getRight();
                if (insertReplaceEdit != null && insertReplaceEdit.getNewText() != null) {
                    return insertReplaceEdit.getNewText();
                }
            }
        }

        String insertText = item.getInsertText();
        if (insertText != null && !insertText.isEmpty()) {
            return insertText;
        }
        return item.getLabel();
    }

    @Override
    protected void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            textArea.backspace();
            e.consume();

            if (word.isEmpty()) {
                dispose();
            } else if (word.length() == 1) {
                resetWords("");
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

    private void resetWords(String newWord) {
        word = newWord == null ? "" : newWord;
        int caret = textArea.getCaretPosition();
        List<CompletionItem> filtered = filterItems(allItems, word);
        if (!filtered.isEmpty()) {
            int selected = Math.min(getSelectedIndex(), filtered.size() - 1);
            showFilteredItems(filtered, caret, Math.max(selected, 0));
            return;
        }
        completionCoordinators
            .computeIfAbsent(lspClient, c -> new CompletionCoordinator())
            .schedule(this.view, lspClient, word, noWordSep, caret, true,
                CompletionTriggerKind.Invoked, null);
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
            if (index < 0 || index >= items.size()) {
                return;
            }

            String newText = getCompletionInsertText(items.get(index));
            if (newText != null && !newText.isEmpty()) {
                replaceWordAtCaret(newText);
            }
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Component getCellRenderer(JList list, int index,
                                        boolean isSelected, boolean cellHasFocus) {
            renderer.getListCellRendererComponent(list,
                null, index, isSelected, cellHasFocus);

            CompletionItem item = items.get(index);
            renderer.setText(formatItemHtml(item, index));
            renderer.setFont(list.getFont());
            return renderer;
        }

        @Override
        public String getDescription(int index) {
            if (index < 0 || index >= items.size()) {
                return null;
            }
            return getItemDocumentation(items.get(index));
        }

        private String formatItemHtml(CompletionItem item, int index) {
            String label = item.getLabel() != null ? item.getLabel() : "";
            String prefix = "";
            if (index < 9) {
                prefix = (index + 1) + ": ";
            } else if (index == 9) {
                prefix = "0: ";
            }

            String detail = getItemDetail(item);
            String display = formatSignatureDisplay(label, detail);
            StringBuilder html = new StringBuilder("<html>").append(escapeHtml(prefix));

            int returnTypeSep = findReturnTypeSeparator(display);
            if (returnTypeSep >= 0) {
                html.append(escapeHtml(display.substring(0, returnTypeSep)));
                html.append("<font color='").append(detailColorHex()).append("'>")
                    .append(escapeHtml(display.substring(returnTypeSep))).append("</font>");
            } else {
                html.append(escapeHtml(display));
                if (detail == null || detail.isEmpty()) {
                    String kind = getCompletionKindString(item.getKind());
                    if (kind != null) {
                        html.append(" <font color='").append(detailColorHex()).append("'>[")
                            .append(escapeHtml(kind)).append("]</font>");
                    }
                }
            }
            html.append("</html>");
            return html.toString();
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

    /**
     * Formats label and LSP detail into {@code name(params): returnType}.
     * Handles Dart-style details such as {@code () -> String}.
     */
    private static String formatSignatureDisplay(String label, String detail) {
        if (detail == null || detail.trim().isEmpty()) {
            return label;
        }
        return label + " - " + detail;
//        if (detail.startsWith("(")) {
//            label = label.replaceAll("\\([^)]*\\)", "");
//        }
//        else {
//            label = label + ": ";
//        }
//        return label + detail;
    }

    private static String extractMethodBaseName(String label) {
        int paren = label.indexOf('(');
        if (paren >= 0) {
            return label.substring(0, paren);
        }
        return label;
    }

    /**
     * Index of the colon that separates the signature from the return type.
     */
    private static int findReturnTypeSeparator(String display) {
        int lastParen = display.lastIndexOf(')');
        if (lastParen >= 0) {
            int colon = display.indexOf(": ", lastParen);
            if (colon >= 0) {
                return colon;
            }
        }
        int colon = display.indexOf(": ");
        return colon > 0 ? colon : -1;
    }

    private static String getItemDetail(CompletionItem item) {
        String detail = item.getDetail();
        if (detail != null && !detail.isEmpty()) {
            return detail;
        }
        CompletionItemLabelDetails labelDetails = item.getLabelDetails();
        if (labelDetails != null) {
            detail = labelDetails.getDetail();
            if (detail != null && !detail.isEmpty()) {
                return detail;
            }
        }
        return null;
    }

    private static String getItemDocumentation(CompletionItem item) {
        Either<String, MarkupContent> documentation = item.getDocumentation();
        if (documentation == null) {
            return null;
        }
        if (documentation.isLeft()) {
            return documentation.getLeft();
        }
        MarkupContent markup = documentation.getRight();
        return markup != null ? markup.getValue() : null;
    }

    private static String detailColorHex() {
        Color color = UIManager.getColor("Label.disabledForeground");
        if (color == null) {
            color = UIManager.getColor("Label.foreground");
        }
        if (color == null) {
            return "#707070";
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /**
     * Check if an exception indicates a connection error (server crashed).
     */
    private static boolean isConnectionError(Throwable ex) {
        if (ex == null) {
            return false;
        }

        // Check for common connection-related exceptions
        String message = ex.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("connection") ||
                lowerMessage.contains("broken pipe") ||
                lowerMessage.contains("reset") ||
                lowerMessage.contains("closed") ||
                lowerMessage.contains("eof")) {
                return true;
            }
        }

        // Check exception types
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.io.IOException ||
                cause instanceof java.net.SocketException ||
                cause instanceof java.nio.channels.ClosedChannelException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }
}
