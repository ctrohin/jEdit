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

import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import javax.swing.event.MouseInputAdapter;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.syntax.KeywordMap;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.jedit.textarea.TextAreaPainter;
import org.gjt.sp.util.Log;

/**
 * LSP {@code textDocument/definition}: navigate to the symbol definition.
 */
public final class LspGoToDefinition {

    private static final LspGoToDefinitionInput INPUT = new LspGoToDefinitionInput();

    private LspGoToDefinition() {}

    static void install() {
        INPUT.install();
    }

    static void uninstall() {
        INPUT.uninstall();
    }

    public static void goToDefinitionLsp(View view, GenericLspClient lspClient) {
        if (view == null) {
            return;
        }
        goToDefinitionAtOffset(view, lspClient, view.getTextArea().getCaretPosition());
    }

    static void goToDefinitionAtClick(View view, GenericLspClient lspClient,
                                      MouseEvent evt) {
        if (view == null || evt == null) {
            return;
        }
        JEditTextArea textArea = view.getTextArea();
        int offset = textArea.xyToOffset(evt.getX(), evt.getY(),
            !(textArea.getPainter().isBlockCaretEnabled()
                || textArea.isOverwriteEnabled()));
        if (offset < 0) {
            return;
        }
        goToDefinitionAtOffset(view, lspClient, offset);
    }

    private static void goToDefinitionAtOffset(View view, GenericLspClient lspClient,
                                               int offset) {
        Buffer buffer = view.getBuffer();
        if (lspClient == null || lspClient.getServer() == null) {
            Log.log(Log.WARNING, LspGoToDefinition.class,
                "LSP server not available for go to definition");
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());
        Position position = offsetToPosition(buffer, offset);

        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(documentUri));
        params.setPosition(position);

        lspClient.whenReady().thenCompose(ignored ->
            lspClient.getServer().getTextDocumentService().definition(params))
            .thenAccept(result -> SwingUtilities.invokeLater(() ->
                handleDefinitionResult(view, result)))
            .exceptionally(ex -> {
                Log.log(Log.ERROR, LspGoToDefinition.class,
                    "Error requesting LSP definition", ex);
                SwingUtilities.invokeLater(() ->
                    UIManager.getLookAndFeel().provideErrorFeedback(view));
                return null;
            });
    }

    private static void handleDefinitionResult(View view,
            Either<List<? extends Location>, List<? extends LocationLink>> result) {
        List<Location> locations = parseLocations(result);
        if (locations.isEmpty()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        Location target;
        if (locations.size() == 1) {
            target = locations.get(0);
        } else {
            target = chooseLocation(view, locations);
            if (target == null) {
                return;
            }
        }
        openLocation(view, target);
    }

    private static List<Location> parseLocations(
            Either<List<? extends Location>, List<? extends LocationLink>> result) {
        List<Location> locations = new ArrayList<>();
        if (result == null) {
            return locations;
        }

        if (result.isLeft()) {
            List<? extends Location> list = result.getLeft();
            if (list != null) {
                locations.addAll(list);
            }
        } else {
            List<? extends LocationLink> links = result.getRight();
            if (links != null) {
                for (LocationLink link : links) {
                    if (link == null) {
                        continue;
                    }
                    Location location = new Location();
                    location.setUri(link.getTargetUri());
                    location.setRange(link.getTargetRange());
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    private static Location chooseLocation(View view, List<Location> locations) {
        String[] labels = new String[locations.size()];
        for (int i = 0; i < locations.size(); i++) {
            labels[i] = formatLocationLabel(locations.get(i));
        }
        String choice = (String) JOptionPane.showInputDialog(
            view,
            "Multiple definitions found. Choose one:",
            "Go to Definition",
            JOptionPane.QUESTION_MESSAGE,
            null,
            labels,
            labels[0]);
        if (choice == null) {
            return null;
        }
        for (int i = 0; i < labels.length; i++) {
            if (choice.equals(labels[i])) {
                return locations.get(i);
            }
        }
        return locations.get(0);
    }

    private static String formatLocationLabel(Location location) {
        String path = LspDocumentUri.uriToPath(location.getUri());
        String file = path != null ? path : location.getUri();
        if (location.getRange() != null && location.getRange().getStart() != null) {
            Position start = location.getRange().getStart();
            return file + " (" + (start.getLine() + 1) + ":" + (start.getCharacter() + 1) + ")";
        }
        return file;
    }

    static void openLocation(View view, Location location) {
        if (location == null || location.getUri() == null) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        String path = LspDocumentUri.uriToPath(location.getUri());
        if (path == null) {
            Log.log(Log.WARNING, LspGoToDefinition.class,
                "Cannot open non-local definition URI: " + location.getUri());
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        int offset = 0;
        if (location.getRange() != null && location.getRange().getStart() != null) {
            offset = Math.max(0, positionToOffsetForPath(path, location.getRange().getStart()));
        }

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Buffer.CARET, Integer.valueOf(offset));
        props.put(Buffer.CARET_POSITIONED, Boolean.TRUE);

        Buffer buffer = jEdit.openFile(view, null, path, false, props);
        if (buffer == null) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        int targetOffset = offset;
        if (location.getRange() != null && location.getRange().getStart() != null) {
            int resolved = positionToOffset(buffer, location.getRange().getStart());
            if (resolved >= 0) {
                targetOffset = resolved;
            }
        }
        targetOffset = Math.min(Math.max(0, targetOffset), buffer.getLength());
        reserveCaret(buffer, targetOffset);
        navigateToOffset(view, buffer, targetOffset);
    }

    private static void reserveCaret(Buffer buffer, int offset) {
        buffer.setIntegerProperty(Buffer.CARET, offset);
        buffer.setBooleanProperty(Buffer.CARET_POSITIONED, true);
        buffer.unsetProperty(Buffer.SCROLL_VERT);
        buffer.unsetProperty(Buffer.SCROLL_HORIZ);
    }

    /**
     * Activate the target buffer, move the caret, and scroll after
     * {@link EditPane#setBuffer} finishes restoring saved pane state.
     */
    private static void navigateToOffset(View view, Buffer buffer, int offset) {
        Runnable navigate = () -> {
            EditPane editPane = view.goToBuffer(buffer);
            JEditTextArea textArea = editPane.getTextArea();
            reserveCaret(buffer, offset);
            textArea.selectNone();
            textArea.moveCaretPosition(offset, TextArea.NORMAL_SCROLL);
            if (!textArea.isCaretVisible()) {
                textArea.scrollToCaret(false);
            }
            editPane.focusOnTextArea();
            view.toFront();
            view.requestFocus();
        };
        SwingUtilities.invokeLater(navigate);
    }

    /**
     * Best-effort offset before the buffer is loaded (for open-file props).
     */
    private static int positionToOffsetForPath(String path, Position position) {
        if (position == null) {
            return 0;
        }
        Buffer buffer = jEdit.getBuffer(path);
        if (buffer == null) {
            return 0;
        }
        int offset = positionToOffset(buffer, position);
        return offset >= 0 ? offset : 0;
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

    private static boolean isNavigateModifier(MouseEvent evt) {
        return OperatingSystem.isMacOS() ? evt.isMetaDown() : evt.isControlDown();
    }

    private static boolean isNavigateModifierReleased(KeyEvent evt) {
        if (OperatingSystem.isMacOS()) {
            return evt.getKeyCode() == KeyEvent.VK_META;
        }
        return evt.getKeyCode() == KeyEvent.VK_CONTROL;
    }

    private static void restoreTextCursor(TextAreaPainter painter) {
        painter.resetCursor();
        painter.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
    }

    private static boolean hasLspClient(Buffer buffer) {
        GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
        return client != null && client.getServer() != null && client.isAlive();
    }

    private static boolean isSymbolAt(JEditTextArea textArea, int x, int y) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return false;
        }

        int offset = textArea.xyToOffset(x, y,
            !(textArea.getPainter().isBlockCaretEnabled()
                || textArea.isOverwriteEnabled()));
        if (offset < 0) {
            return false;
        }

        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        int dot = offset - lineStart;
        CharSequence lineText = buffer.getLineSegment(line);
        if (lineText.isEmpty()) {
            return false;
        }

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(offset);
        String noWordSep = getNonAlphaNumericWordChars(buffer, keywordMap);

        int index = dot;
        if (index >= lineText.length()) {
            index = lineText.length() - 1;
        }
        char ch = lineText.charAt(index);
        if (!isWordChar(ch, noWordSep) && index > 0) {
            ch = lineText.charAt(index - 1);
        }
        return isWordChar(ch, noWordSep);
    }

    private static boolean isWordChar(char ch, String noWordSep) {
        return Character.isLetterOrDigit(ch) || noWordSep.indexOf(ch) != -1;
    }

    private static String getNonAlphaNumericWordChars(Buffer buffer, KeywordMap keywordMap) {
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
        return noWordSep;
    }

    /**
     * Installs Ctrl+Click (Cmd+Click on macOS) handlers on text areas.
     */
    private static final class LspGoToDefinitionInput implements EBComponent {

        private static final class Handlers {
            final MouseInputAdapter mouseHandler;
            final KeyAdapter keyHandler;

            Handlers(MouseInputAdapter mouseHandler, KeyAdapter keyHandler) {
                this.mouseHandler = mouseHandler;
                this.keyHandler = keyHandler;
            }
        }

        private final Map<JEditTextArea, Handlers> handlers = new IdentityHashMap<>();

        void install() {
            EditBus.addToBus(this);
            attachAllTextAreas();
        }

        void uninstall() {
            EditBus.removeFromBus(this);
            detachAllTextAreas();
        }

        @Override
        public void handleMessage(org.gjt.sp.jedit.EBMessage message) {
            if (!(message instanceof EditPaneUpdate update)) {
                return;
            }
            if (update.getWhat() == EditPaneUpdate.CREATED
                || update.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
                ensureHandler(update.getEditPane().getTextArea());
            }
        }

        private void attachAllTextAreas() {
            for (View view : jEdit.getViews()) {
                for (EditPane editPane : view.getEditPanes()) {
                    ensureHandler(editPane.getTextArea());
                }
            }
        }

        private void detachAllTextAreas() {
            for (Map.Entry<JEditTextArea, Handlers> entry : handlers.entrySet()) {
                JEditTextArea textArea = entry.getKey();
                Handlers installed = entry.getValue();
                TextAreaPainter painter = textArea.getPainter();
                painter.removeMouseListener(installed.mouseHandler);
                painter.removeMouseMotionListener(installed.mouseHandler);
                textArea.removeKeyListener(installed.keyHandler);
                restoreTextCursor(painter);
            }
            handlers.clear();
        }

        private void ensureHandler(JEditTextArea textArea) {
            if (handlers.containsKey(textArea)) {
                return;
            }

            View view = textArea.getView();
            TextAreaPainter painter = textArea.getPainter();
            MouseInputAdapter mouseHandler = new MouseInputAdapter() {
                @Override
                public void mouseMoved(MouseEvent evt) {
                    updateNavigateCursor(evt);
                }

                @Override
                public void mouseDragged(MouseEvent evt) {
                    updateNavigateCursor(evt);
                }

                @Override
                public void mouseExited(MouseEvent evt) {
                    restoreTextCursor(painter);
                }

                @Override
                public void mousePressed(MouseEvent evt) {
                    if (evt.getButton() != MouseEvent.BUTTON1 || evt.isShiftDown()) {
                        return;
                    }

                    if (!isNavigateModifier(evt)) {
                        return;
                    }

                    if (!(textArea.getBuffer() instanceof Buffer buffer)) {
                        return;
                    }
                    if (buffer.isLoading()) {
                        return;
                    }

                    GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
                    if (client == null || client.getServer() == null || !client.isAlive()) {
                        return;
                    }

                    restoreTextCursor(painter);
                    goToDefinitionAtClick(view, client, evt);
                }

                private void updateNavigateCursor(MouseEvent evt) {
                    if (!isNavigateModifier(evt)) {
                        restoreTextCursor(painter);
                        return;
                    }

                    if (!(textArea.getBuffer() instanceof Buffer buffer)
                        || buffer.isLoading()
                        || !hasLspClient(buffer)
                        || !isSymbolAt(textArea, evt.getX(), evt.getY())) {
                        restoreTextCursor(painter);
                        return;
                    }

                    painter.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
            };

            KeyAdapter keyHandler = new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent evt) {
                    if (isNavigateModifierReleased(evt)) {
                        restoreTextCursor(painter);
                    }
                }
            };

            prependMouseListener(painter, mouseHandler);
            prependMouseMotionListener(painter, mouseHandler);
            textArea.addKeyListener(keyHandler);
            handlers.put(textArea, new Handlers(mouseHandler, keyHandler));
        }

        private static void prependMouseListener(TextAreaPainter painter,
                                                 MouseListener listener) {
            MouseListener[] existing = painter.getMouseListeners();
            for (MouseListener item : existing) {
                painter.removeMouseListener(item);
            }
            painter.addMouseListener(listener);
            for (MouseListener item : existing) {
                painter.addMouseListener(item);
            }
        }

        private static void prependMouseMotionListener(TextAreaPainter painter,
                                                       MouseMotionListener listener) {
            MouseMotionListener[] existing = painter.getMouseMotionListeners();
            for (MouseMotionListener item : existing) {
                painter.removeMouseMotionListener(item);
            }
            painter.addMouseMotionListener(listener);
            for (MouseMotionListener item : existing) {
                painter.addMouseMotionListener(item);
            }
        }
    }
}
