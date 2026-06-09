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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.event.CaretListener;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpContext;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureHelpTriggerKind;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.util.Log;

/**
 * LSP {@code textDocument/signatureHelp}: parameter info tooltip at the caret.
 */
final class LspSignatureHelp {

    private static final int TOOLTIP_WIDTH = 480;
    private static final int TOOLTIP_MAX_HEIGHT = 180;
    private static final int SCREEN_EDGE_MARGIN = 8;

    private static final AtomicInteger REQUEST_GENERATION = new AtomicInteger(0);
    private static final Map<View, SignatureHelpPopup> activePopups = new IdentityHashMap<>();

    private static final EBComponent VIEW_LISTENER = new EBComponent() {
        @Override
        public void handleMessage(EBMessage msg) {
            if (msg instanceof ViewUpdate update
                && update.getWhat() == ViewUpdate.CLOSED) {
                hide(update.getView());
            } else if (msg instanceof EditPaneUpdate update
                && update.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
                hide(update.getEditPane().getView());
            }
        }
    };

    private LspSignatureHelp() {}

    static void install() {
        EditBus.addToBus(VIEW_LISTENER);
    }

    static void uninstall() {
        EditBus.removeFromBus(VIEW_LISTENER);
        for (View view : List.copyOf(activePopups.keySet())) {
            hide(view);
        }
    }

    static void showSignatureHelp(View view, GenericLspClient lspClient) {
        if (view == null) {
            return;
        }

        JEditTextArea textArea = view.getTextArea();
        Buffer buffer = view.getBuffer();
        if (!buffer.isEditable() || buffer.isLoading()) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        int caret = textArea.getCaretPosition();
        if (!isInsideParentheses(buffer, caret)) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        if (lspClient == null || lspClient.getServer() == null) {
            UIManager.getLookAndFeel().provideErrorFeedback(view);
            return;
        }

        int line = buffer.getLineOfOffset(caret);
        int character = caret - buffer.getLineStartOffset(line);
        Position position = new Position(line, character);
        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());

        SignatureHelpParams params = new SignatureHelpParams();
        params.setTextDocument(new TextDocumentIdentifier(documentUri));
        params.setPosition(position);
        SignatureHelpContext context = new SignatureHelpContext();
        context.setTriggerKind(SignatureHelpTriggerKind.Invoked);
        params.setContext(context);

        int generation = REQUEST_GENERATION.incrementAndGet();
        lspClient.whenReady().thenCompose(ignored ->
            lspClient.getServer().getTextDocumentService().signatureHelp(params))
            .whenComplete((help, ex) -> {
                if (generation != REQUEST_GENERATION.get()) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    if (ex != null) {
                        Log.log(Log.ERROR, LspSignatureHelp.class,
                            "Error requesting LSP signature help", ex);
                        UIManager.getLookAndFeel().provideErrorFeedback(view);
                        return;
                    }
                    if (help == null || help.getSignatures() == null
                        || help.getSignatures().isEmpty()) {
                        hide(view);
                        UIManager.getLookAndFeel().provideErrorFeedback(view);
                        return;
                    }
                    if (!isInsideParentheses(view.getBuffer(),
                        textArea.getCaretPosition())) {
                        return;
                    }
                    showPopup(view, help);
                });
            });
    }

    static void hide(View view) {
        if (view == null) {
            return;
        }
        SignatureHelpPopup popup = activePopups.remove(view);
        if (popup != null) {
            popup.dispose();
        }
    }

    private static void showPopup(View view, SignatureHelp help) {
        hide(view);
        SignatureHelpPopup popup = new SignatureHelpPopup(view);
        activePopups.put(view, popup);
        popup.showHelp(help);
    }

    /**
     * Returns true when the caret is between an unmatched {@code (} and {@code )}.
     */
    static boolean isInsideParentheses(Buffer buffer, int offset) {
        return findEnclosingOpenParen(buffer, offset) >= 0;
    }

    /**
     * Returns the buffer offset of the {@code (} enclosing the caret, or {@code -1}.
     */
    static int findEnclosingOpenParen(Buffer buffer, int offset) {
        if (buffer == null || offset <= 0) {
            return -1;
        }

        String prefix = buffer.getText(0, offset);
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = prefix.length() - 1; i >= 0; i--) {
            char ch = prefix.charAt(i);
            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }

            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static String signatureHelpToHtml(SignatureHelp help, Color foreground) {
        List<SignatureInformation> signatures = help.getSignatures();
        if (signatures == null || signatures.isEmpty()) {
            return null;
        }

        int activeSignature = help.getActiveSignature() != null
            ? help.getActiveSignature() : 0;
        if (activeSignature < 0 || activeSignature >= signatures.size()) {
            activeSignature = 0;
        }

        int activeParameter = help.getActiveParameter() != null
            ? help.getActiveParameter() : 0;

        SignatureInformation signature = signatures.get(activeSignature);
        StringBuilder body = new StringBuilder();

        if (signatures.size() > 1) {
            body.append("<i>")
                .append(activeSignature + 1).append(" of ")
                .append(signatures.size()).append("</i><br>");
        }

        body.append(formatSignatureLabel(signature, activeParameter));

        String signatureDoc = formatDocumentation(signature.getDocumentation(), foreground);
        if (signatureDoc != null && !signatureDoc.isBlank()) {
            body.append("<br><br>").append(signatureDoc);
        }

        List<ParameterInformation> parameters = signature.getParameters();
        if (parameters != null && activeParameter >= 0
            && activeParameter < parameters.size()) {
            String paramDoc = formatDocumentation(
                parameters.get(activeParameter).getDocumentation(), foreground);
            if (paramDoc != null && !paramDoc.isBlank()) {
                body.append("<br>").append(paramDoc);
            }
        }

        return "<html><body style='width: " + TOOLTIP_WIDTH + "px; color: "
            + LspHover.colorHex(foreground) + ";'>" + body + "</body></html>";
    }

    private static String formatSignatureLabel(SignatureInformation signature,
                                               int activeParameter) {
        String label = signature.getLabel();
        if (label == null) {
            return "";
        }

        List<ParameterInformation> parameters = signature.getParameters();
        if (parameters == null || activeParameter < 0
            || activeParameter >= parameters.size()) {
            return LspHover.escapeHtml(label);
        }

        ParameterInformation param = parameters.get(activeParameter);
        Either<String, Tuple.Two<Integer, Integer>> paramLabel = param.getLabel();
        if (paramLabel != null && paramLabel.isRight()) {
            Tuple.Two<Integer, Integer> range = paramLabel.getRight();
            if (range != null) {
                int start = range.getFirst();
                int end = range.getSecond();
                if (start >= 0 && end <= label.length() && start < end) {
                    return LspHover.escapeHtml(label.substring(0, start))
                        + "<b>" + LspHover.escapeHtml(label.substring(start, end)) + "</b>"
                        + LspHover.escapeHtml(label.substring(end));
                }
            }
        }

        if (paramLabel != null && paramLabel.isLeft()) {
            String paramName = paramLabel.getLeft();
            int index = label.indexOf(paramName);
            if (index >= 0) {
                return LspHover.escapeHtml(label.substring(0, index))
                    + "<b>" + LspHover.escapeHtml(paramName) + "</b>"
                    + LspHover.escapeHtml(label.substring(index + paramName.length()));
            }
        }

        return LspHover.escapeHtml(label);
    }

    private static String formatDocumentation(
            Either<String, MarkupContent> documentation, Color foreground) {
        if (documentation == null) {
            return null;
        }
        String html = LspHover.documentationToHtml(documentation, TOOLTIP_WIDTH, foreground);
        if (html == null) {
            return null;
        }
        int bodyStart = html.indexOf("<body");
        int bodyContent = html.indexOf('>', bodyStart);
        int bodyEnd = html.lastIndexOf("</body>");
        if (bodyStart >= 0 && bodyContent >= 0 && bodyEnd > bodyContent) {
            return html.substring(bodyContent + 1, bodyEnd);
        }
        return html;
    }

    private static final class SignatureHelpPopup {
        private final View view;
        private final JEditTextArea textArea;
        private final JWindow window;
        private final JEditorPane contentPane;
        private final JScrollPane scrollPane;
        private final JPanel panel;
        private final CaretListener caretListener;
        private final KeyAdapter keyListener;
        private final BufferAdapter bufferListener;
        private int openParenOffset = -1;
        private boolean listenersInstalled;

        SignatureHelpPopup(View view) {
            this.view = view;
            this.textArea = view.getTextArea();

            contentPane = new JEditorPane();
            contentPane.setContentType("text/html");
            contentPane.setEditable(false);
            contentPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            contentPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            scrollPane = new JScrollPane(contentPane,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);

            panel = new JPanel(new BorderLayout());
            panel.add(scrollPane, BorderLayout.CENTER);

            window = new JWindow(view);
            window.getContentPane().add(panel);
            window.setFocusableWindowState(false);
            applyColors();

            caretListener = e -> checkStillInsideCall();
            keyListener = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE && window.isVisible()) {
                        LspSignatureHelp.hide(view);
                        e.consume();
                    }
                }
            };
            bufferListener = new BufferAdapter() {
                @Override
                public void contentInserted(JEditBuffer buffer, int startLine, int offset,
                    int numLines, int length) {
                    checkStillInsideCall();
                }

                @Override
                public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
                    int numLines, int length) {
                    checkStillInsideCall();
                }
            };
        }

        void showHelp(SignatureHelp help) {
            Buffer buffer = view.getBuffer();
            openParenOffset = findEnclosingOpenParen(buffer, textArea.getCaretPosition());
            if (openParenOffset < 0) {
                LspSignatureHelp.hide(view);
                return;
            }

            Color foreground = labelForeground();
            String html = signatureHelpToHtml(help, foreground);
            if (html == null) {
                LspSignatureHelp.hide(view);
                return;
            }

            applyColors();
            contentPane.setText(html);
            scrollToTop();
            layoutAndPosition();
            installListeners(buffer);
            window.setVisible(true);
            textArea.requestFocusInWindow();
        }

        void dispose() {
            removeListeners();
            if (window.isDisplayable()) {
                window.setVisible(false);
                window.dispose();
            }
        }

        private void installListeners(Buffer buffer) {
            if (listenersInstalled) {
                return;
            }
            textArea.addCaretListener(caretListener);
            textArea.addKeyListener(keyListener);
            buffer.addBufferListener(bufferListener);
            listenersInstalled = true;
        }

        private void removeListeners() {
            if (!listenersInstalled) {
                return;
            }
            textArea.removeCaretListener(caretListener);
            textArea.removeKeyListener(keyListener);
            if (view.getBuffer() != null) {
                view.getBuffer().removeBufferListener(bufferListener);
            }
            listenersInstalled = false;
        }

        private void checkStillInsideCall() {
            if (!window.isVisible()) {
                return;
            }
            Buffer buffer = view.getBuffer();
            int caret = textArea.getCaretPosition();
            if (findEnclosingOpenParen(buffer, caret) != openParenOffset) {
                LspSignatureHelp.hide(view);
            }
        }

        private void layoutAndPosition() {
            contentPane.setSize(TOOLTIP_WIDTH, Integer.MAX_VALUE);
            int contentHeight = contentPane.getPreferredSize().height;
            int height = Math.min(contentHeight + 16, TOOLTIP_MAX_HEIGHT);
            scrollPane.setPreferredSize(new Dimension(TOOLTIP_WIDTH, height));
            panel.revalidate();
            window.pack();
            scrollToTop();

            int caret = textArea.getCaretPosition();
            Point location = textArea.offsetToXY(caret);
            if (location == null) {
                location = new Point(0, 0);
            }
            location.y += textArea.getPainter().getLineHeight();
            SwingUtilities.convertPointToScreen(location, textArea.getPainter());

            Rectangle screen = textArea.getGraphicsConfiguration().getBounds();
            Dimension size = window.getSize();
            int x = location.x;
            int y = location.y;
            if (x + size.width > screen.x + screen.width - SCREEN_EDGE_MARGIN) {
                x = screen.x + screen.width - size.width - SCREEN_EDGE_MARGIN;
            }
            if (y + size.height > screen.y + screen.height - SCREEN_EDGE_MARGIN) {
                y = location.y - size.height - textArea.getPainter().getLineHeight();
            }
            window.setLocation(
                Math.max(screen.x + SCREEN_EDGE_MARGIN, x),
                Math.max(screen.y + SCREEN_EDGE_MARGIN, y));
        }

        private void scrollToTop() {
            contentPane.setCaretPosition(0);
            scrollPane.getViewport().setViewPosition(new Point(0, 0));
            scrollPane.getVerticalScrollBar().setValue(0);
        }

        private void applyColors() {
            Color background = panelBackground();
            Color foreground = labelForeground();
            Color borderColor = borderColor(background, foreground);

            Border border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 2, 1, borderColor),
                BorderFactory.createEmptyBorder(8, 10, 8, 10));

            panel.setOpaque(true);
            panel.setBackground(background);
            panel.setBorder(border);
            contentPane.setOpaque(true);
            contentPane.setBackground(background);
            contentPane.setForeground(foreground);
            scrollPane.getViewport().setBackground(background);

            JPanel content = (JPanel) window.getContentPane();
            content.setOpaque(true);
            content.setBackground(background);
            content.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        }

        private static Color panelBackground() {
            Color background = UIManager.getColor("ToolTip.background");
            if (background == null) {
                background = UIManager.getColor("Panel.background");
            }
            return background;
        }

        private static Color labelForeground() {
            Color foreground = UIManager.getColor("ToolTip.foreground");
            if (foreground == null) {
                foreground = UIManager.getColor("Label.foreground");
            }
            return foreground;
        }

        private static Color borderColor(Color background, Color foreground) {
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
    }
}
