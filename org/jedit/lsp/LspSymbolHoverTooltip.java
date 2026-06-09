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
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.TextUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.syntax.KeywordMap;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

/**
 * Shows LSP hover documentation when the pointer rests over a symbol.
 */
final class LspSymbolHoverTooltip {

    private static final int SHOW_DELAY_MS = 600;
    private static final int HIDE_DELAY_MS = 400;
    private static final int HOVER_CHECK_MS = 300;
    private static final int CURSOR_OFFSET_X = 0;
    private static final int CURSOR_OFFSET_Y = 0;
    private static final int SCREEN_EDGE_MARGIN = 12;
    private static final int PREFERRED_TOOLTIP_WIDTH = 400;
    private static final int MIN_TOOLTIP_WIDTH = 220;
    private static final double MAX_HEIGHT_SCREEN_FRACTION = 0.70;

    private final JEditTextArea textArea;
    private final TextAreaPainter painter;
    private final View view;
    private final LspDiagnosticTooltip diagnosticTooltip;
    private final JPanel panel;
    private final JEditorPane contentPane;
    private final JScrollPane scrollPane;
    private final JWindow window;
    private final Timer showTimer;
    private final Timer hideTimer;
    private final Timer hoverCheckTimer;
    private final MouseAdapter painterMouseHandler = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
            handleMouseAt(e.getX(), e.getY());
        }
    };

    private String pendingHoverKey;
    private Position pendingPosition;
    private String inFlightHoverKey;
    private String shownHoverKey;
    private int anchorScreenX;
    private int anchorScreenY;

    LspSymbolHoverTooltip(JEditTextArea textArea, LspDiagnosticTooltip diagnosticTooltip) {
        this.textArea = textArea;
        this.painter = textArea.getPainter();
        this.view = textArea.getView();
        this.diagnosticTooltip = diagnosticTooltip;

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
        applyLookAndFeelColors();

        showTimer = new Timer(SHOW_DELAY_MS, e -> showPendingTooltip());
        showTimer.setRepeats(false);

        hideTimer = new Timer(HIDE_DELAY_MS, e -> hideTooltip());
        hideTimer.setRepeats(false);

        hoverCheckTimer = new Timer(HOVER_CHECK_MS, e -> updateHideTimer());
        hoverCheckTimer.setRepeats(true);

        painter.addMouseMotionListener(painterMouseHandler);
    }

    void hide() {
        hideTooltip();
    }

    void onBufferChanged() {
        hideTooltip();
    }

    void dispose() {
        showTimer.stop();
        hideTimer.stop();
        hoverCheckTimer.stop();
        LspHover.cancelPendingRequests();
        hideTooltip();
        painter.removeMouseMotionListener(painterMouseHandler);
    }

    private void handleMouseAt(int x, int y) {
        if (diagnosticTooltip.isShowing() || diagnosticTooltip.hasProblemAt(x, y)) {
            pendingHoverKey = null;
            pendingPosition = null;
            showTimer.stop();
            if (window.isVisible()) {
                hideTooltip();
            }
            return;
        }

        if (!(textArea.getBuffer() instanceof Buffer buffer) || buffer.isLoading()) {
            if (window.isVisible()) {
                hideTooltip();
            }
            clearPending();
            return;
        }

        GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
        if (client == null || client.getServer() == null || !client.isAlive()) {
            if (window.isVisible()) {
                hideTooltip();
            }
            clearPending();
            return;
        }

        SymbolAtPointer symbol = symbolAt(x, y);
        if (symbol == null) {
            if (window.isVisible()) {
                hideTooltip();
            }
            clearPending();
            return;
        }

        updateAnchorFromPainterPoint(x, y);

        if (window.isVisible()) {
            if (symbol.key().equals(shownHoverKey)) {
                hideTimer.stop();
                return;
            }
            hideTooltip();
        }

        if (symbol.key().equals(pendingHoverKey)) {
            return;
        }

        cancelInFlightRequest();
        pendingHoverKey = symbol.key();
        pendingPosition = symbol.position();
        showTimer.restart();
    }

    private void showPendingTooltip() {
        if (pendingHoverKey == null || pendingPosition == null) {
            return;
        }
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return;
        }

        GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
        if (client == null || client.getServer() == null || !client.isAlive()) {
            return;
        }

        final String requestKey = pendingHoverKey;
        final Position requestPosition = pendingPosition;
        inFlightHoverKey = requestKey;
        updateAnchorFromPointer();

        LspHover.requestHover(client, buffer, requestPosition, hover ->
            SwingUtilities.invokeLater(() -> setHoverContent(requestKey, hover)));
    }

    private void setHoverContent(String requestKey, Hover hover) {
        if (!requestKey.equals(inFlightHoverKey)
            || !requestKey.equals(pendingHoverKey)) {
            return;
        }
        inFlightHoverKey = null;
        updateAnchorFromPointer();

        Color foreground = labelForeground();
        Rectangle screenBounds = screenBounds();
        int maxWidth = maxTooltipWidth(screenBounds);
        int width = Math.min(PREFERRED_TOOLTIP_WIDTH, maxWidth);
        String html = LspHover.hoverToHtml(hover, width, foreground);
        if (html == null) {
            return;
        }

        applyLookAndFeelColors();
        contentPane.setText(html);
        scrollTooltipToTop();

        int initialWidth = width;
        width = measureExpandedWidth(width, maxWidth);
        if (width != initialWidth) {
            html = LspHover.hoverToHtml(hover, width, foreground);
            contentPane.setText(html);
            scrollTooltipToTop();
        }

        layoutWindow(width, screenBounds);
        shownHoverKey = requestKey;
        window.setVisible(true);
        hideTimer.stop();
        hoverCheckTimer.start();
        updateHideTimer();
    }

    private void cancelInFlightRequest() {
        if (inFlightHoverKey != null) {
            LspHover.cancelPendingRequests();
            inFlightHoverKey = null;
        }
    }

    private void clearPending() {
        pendingHoverKey = null;
        pendingPosition = null;
        showTimer.stop();
        cancelInFlightRequest();
    }

    private void updateAnchorFromPainterPoint(int x, int y) {
        Point painterScreen = painter.getLocationOnScreen();
        anchorScreenX = painterScreen.x + x;
        anchorScreenY = painterScreen.y + y;
    }

    private void updateAnchorFromPointer() {
        Point pointer = MouseInfo.getPointerInfo().getLocation();
        anchorScreenX = pointer.x;
        anchorScreenY = pointer.y;
    }

    private Rectangle screenBounds() {
        return painter.getGraphicsConfiguration().getBounds();
    }

    private int maxTooltipWidth(Rectangle screenBounds) {
        int available = screenBounds.x + screenBounds.width - anchorScreenX
            - SCREEN_EDGE_MARGIN;
        return Math.max(MIN_TOOLTIP_WIDTH, available);
    }

    private int maxTooltipHeight(Rectangle screenBounds) {
        return Math.max(120, (int) (screenBounds.height * MAX_HEIGHT_SCREEN_FRACTION));
    }

    private int measureExpandedWidth(int width, int maxWidth) {
        contentPane.setSize(width, Integer.MAX_VALUE);
        int needed = contentPane.getPreferredSize().width;
        if (needed <= width) {
            return width;
        }
        return Math.min(Math.max(needed, PREFERRED_TOOLTIP_WIDTH), maxWidth);
    }

    private void scrollTooltipToTop() {
        contentPane.setCaretPosition(0);
        scrollPane.getViewport().setViewPosition(new Point(0, 0));
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    private void layoutWindow(int width, Rectangle screenBounds) {
        int maxHeight = maxTooltipHeight(screenBounds);
        contentPane.setSize(width, Integer.MAX_VALUE);
        int height = Math.min(contentPane.getPreferredSize().height, maxHeight);

        scrollPane.setPreferredSize(new Dimension(width, height));
        panel.revalidate();
        window.pack();
        scrollTooltipToTop();
        positionWindow(screenBounds);
    }

    private void positionWindow(Rectangle screenBounds) {
        Dimension size = window.getSize();
        int windowX = anchorScreenX + CURSOR_OFFSET_X;
        int windowY = anchorScreenY + CURSOR_OFFSET_Y;

        if (windowX + size.width > screenBounds.x + screenBounds.width - SCREEN_EDGE_MARGIN) {
            windowX = anchorScreenX - size.width - CURSOR_OFFSET_X;
        }
        if (windowY + size.height > screenBounds.y + screenBounds.height - SCREEN_EDGE_MARGIN) {
            windowY = anchorScreenY - size.height - CURSOR_OFFSET_Y;
        }

        windowX = Math.max(screenBounds.x + SCREEN_EDGE_MARGIN, windowX);
        windowY = Math.max(screenBounds.y + SCREEN_EDGE_MARGIN, windowY);
        if (windowX + size.width > screenBounds.x + screenBounds.width - SCREEN_EDGE_MARGIN) {
            windowX = screenBounds.x + screenBounds.width - size.width - SCREEN_EDGE_MARGIN;
        }
        if (windowY + size.height > screenBounds.y + screenBounds.height - SCREEN_EDGE_MARGIN) {
            windowY = screenBounds.y + screenBounds.height - size.height - SCREEN_EDGE_MARGIN;
        }

        window.setLocation(windowX, windowY);
    }

    private void updateHideTimer() {
        if (!window.isVisible()) {
            return;
        }
        if (isPointerOverPopup() || isPointerOverShownSymbol()) {
            hideTimer.stop();
        } else if (!hideTimer.isRunning()) {
            hideTimer.start();
        }
    }

    private boolean isPointerOverPopup() {
        Point pointer = MouseInfo.getPointerInfo().getLocation();
        return window.getBounds().contains(pointer);
    }

    private boolean isPointerOverShownSymbol() {
        if (shownHoverKey == null) {
            return false;
        }

        Point pointer = MouseInfo.getPointerInfo().getLocation();
        Point painterLoc = painter.getLocationOnScreen();
        int x = pointer.x - painterLoc.x;
        int y = pointer.y - painterLoc.y;
        if (x < 0 || y < 0 || x >= painter.getWidth() || y >= painter.getHeight()) {
            return false;
        }

        SymbolAtPointer symbol = symbolAt(x, y);
        return symbol != null && shownHoverKey.equals(symbol.key());
    }

    private void hideTooltip() {
        showTimer.stop();
        hideTimer.stop();
        hoverCheckTimer.stop();
        window.setVisible(false);
        shownHoverKey = null;
        pendingHoverKey = null;
        pendingPosition = null;
        cancelInFlightRequest();
    }

    private record SymbolAtPointer(String key, Position position) {}

    private SymbolAtPointer symbolAt(int x, int y) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return null;
        }

        int offset = textArea.xyToOffset(x, y,
            !(painter.isBlockCaretEnabled() || textArea.isOverwriteEnabled()));
        if (offset < 0) {
            return null;
        }

        int line = buffer.getLineOfOffset(offset);
        int lineStart = buffer.getLineStartOffset(line);
        int dot = offset - lineStart;
        CharSequence lineText = buffer.getLineSegment(line);
        if (lineText.isEmpty()) {
            return null;
        }

        KeywordMap keywordMap = buffer.getKeywordMapAtOffset(offset);
        String noWordSep = getNonAlphaNumericWordChars(buffer, keywordMap);

        int index = dot;
        if (index >= lineText.length()) {
            index = lineText.length() - 1;
        }
        if (index < 0) {
            return null;
        }

        char ch = lineText.charAt(index);
        if (!isWordChar(ch, noWordSep)) {
            if (index == 0) {
                return null;
            }
            index--;
            if (!isWordChar(lineText.charAt(index), noWordSep)) {
                return null;
            }
        }

        boolean joinNonWordChars = textArea.getJoinNonWordChars();
        int wordStart = TextUtilities.findWordStart(lineText, index, noWordSep,
            joinNonWordChars, false, false);
        int wordEnd = TextUtilities.findWordEnd(lineText, index + 1, noWordSep,
            joinNonWordChars, false, false);
        if (wordEnd <= wordStart) {
            return null;
        }

        String key = LspDocumentUri.pathToUri(buffer.getPath()) + ':' + line
            + ':' + wordStart + ':' + wordEnd;
        Position position = new Position(line, dot);
        return new SymbolAtPointer(key, position);
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

    private void applyLookAndFeelColors() {
        Color background = panelBackground();
        Color foreground = labelForeground();
        Color borderColor = tooltipBorderColor(background, foreground);

        Border border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 2, 1, borderColor),
            BorderFactory.createEmptyBorder(8, 10, 8, 10));

        panel.setOpaque(true);
        panel.setBackground(background);
        panel.setBorder(border);
        contentPane.setOpaque(true);
        contentPane.setBackground(background);
        contentPane.setForeground(foreground);
        contentPane.setCaretPosition(0);
        scrollPane.getViewport().setBackground(background);

        if (window != null) {
            JPanel content = (JPanel) window.getContentPane();
            content.setOpaque(true);
            content.setBackground(background);
            content.setBorder(BorderFactory.createLineBorder(borderColor, 1));
        }
    }

    private static Color tooltipBorderColor(Color background, Color foreground) {
        Color border = UIManager.getColor("Component.borderColor");
        if (border == null) {
            border = UIManager.getColor("controlShadow");
        }
        if (border == null || isLowContrast(border, background)) {
            border = foreground;
        }
        return border;
    }

    private static boolean isLowContrast(Color a, Color b) {
        if (a == null || b == null) {
            return false;
        }
        int delta = Math.abs(a.getRed() - b.getRed())
            + Math.abs(a.getGreen() - b.getGreen())
            + Math.abs(a.getBlue() - b.getBlue());
        return delta < 40;
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
}
