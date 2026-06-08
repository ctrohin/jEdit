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
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
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
    private static final int HOVER_CHECK_MS = 100;
    private static final int CURSOR_OFFSET_X = 12;
    private static final int CURSOR_OFFSET_Y = 16;
    private static final int TOOLTIP_MAX_WIDTH = 480;

    private final JEditTextArea textArea;
    private final TextAreaPainter painter;
    private final View view;
    private final LspDiagnosticTooltip diagnosticTooltip;
    private final JPanel panel;
    private final JLabel contentLabel;
    private final JLabel statusLabel;
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
    private String shownHoverKey;
    private int anchorScreenX;
    private int anchorScreenY;

    LspSymbolHoverTooltip(JEditTextArea textArea, LspDiagnosticTooltip diagnosticTooltip) {
        this.textArea = textArea;
        this.painter = textArea.getPainter();
        this.view = textArea.getView();
        this.diagnosticTooltip = diagnosticTooltip;

        contentLabel = new JLabel();
        statusLabel = new JLabel("Loading...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        panel = new JPanel(new BorderLayout(0, 4));
        panel.add(contentLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);

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
        pendingHoverKey = null;
        pendingPosition = null;
        showTimer.stop();
        LspHover.cancelPendingRequests();
        if (window.isVisible()) {
            hideTooltip();
        }
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

        Point screen = painter.getLocationOnScreen();
        anchorScreenX = screen.x + x;
        anchorScreenY = screen.y + y;

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
        shownHoverKey = requestKey;

        Point pointer = MouseInfo.getPointerInfo().getLocation();
        anchorScreenX = pointer.x;
        anchorScreenY = pointer.y;

        applyLookAndFeelColors();
        contentLabel.setText("");
        statusLabel.setText("Loading...");
        statusLabel.setVisible(true);
        panel.revalidate();
        window.pack();
        positionWindow();
        window.setVisible(true);
        hideTimer.stop();
        hoverCheckTimer.start();
        updateHideTimer();

        LspHover.requestHover(client, buffer, requestPosition, hover ->
            SwingUtilities.invokeLater(() -> {
                if (!requestKey.equals(shownHoverKey) || !window.isVisible()) {
                    return;
                }
                setHoverContent(hover);
            }));
    }

    private void setHoverContent(Hover hover) {
        if (!window.isVisible()) {
            return;
        }

        Color foreground = labelForeground();
        String html = LspHover.hoverToHtml(hover, TOOLTIP_MAX_WIDTH, foreground);
        if (html == null) {
            hideTooltip();
            return;
        }

        contentLabel.setText(html);
        statusLabel.setVisible(false);
        panel.revalidate();
        window.pack();
        positionWindow();
    }

    private void clearPending() {
        pendingHoverKey = null;
        pendingPosition = null;
        showTimer.stop();
    }

    private void positionWindow() {
        int windowX = anchorScreenX + CURSOR_OFFSET_X;
        int windowY = anchorScreenY + CURSOR_OFFSET_Y;

        java.awt.Dimension size = window.getSize();
        Rectangle screenBounds = painter.getGraphicsConfiguration().getBounds();
        if (windowX + size.width > screenBounds.x + screenBounds.width) {
            windowX = anchorScreenX - size.width - CURSOR_OFFSET_X;
        }
        if (windowY + size.height > screenBounds.y + screenBounds.height) {
            windowY = anchorScreenY - size.height - CURSOR_OFFSET_Y;
        }
        window.setLocation(
            Math.max(screenBounds.x, windowX),
            Math.max(screenBounds.y, windowY));
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
        LspHover.cancelPendingRequests();
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
        contentLabel.setOpaque(true);
        contentLabel.setBackground(background);
        contentLabel.setForeground(foreground);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(background);
        statusLabel.setForeground(foreground);

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
