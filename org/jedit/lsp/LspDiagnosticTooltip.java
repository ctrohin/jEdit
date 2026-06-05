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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

/**
 * Custom hover tooltip for LSP diagnostics: problem message plus clickable code actions.
 */
final class LspDiagnosticTooltip {

    private static final int SHOW_DELAY_MS = 500;
    private static final int HIDE_DELAY_MS = 2000;
    private static final int HOVER_CHECK_MS = 100;
    private static final int CURSOR_OFFSET_X = 8;
    private static final int CURSOR_OFFSET_Y = 8;
    private static final int TOOLTIP_MAX_WIDTH = 380;

    private final JEditTextArea textArea;
    private final TextAreaPainter painter;
    private final View view;
    private final JPanel panel;
    private final JLabel problemLabel;
    private final JPanel actionsPanel;
    private final JLabel statusLabel;
    private final JWindow window;
    private final Timer showTimer;
    private final Timer hideTimer;
    private final Timer hoverCheckTimer;
    private final Map<String, List<LspCodeActionItem>> actionCache = new HashMap<>();
    private final MouseAdapter painterMouseHandler = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
            handleMouseAt(e.getX(), e.getY());
        }
    };

    private LspDiagnosticProblem pendingProblem;
    private LspDiagnosticProblem shownProblem;
    private String shownProblemKey;
    private int anchorScreenX;
    private int anchorScreenY;
    private boolean actionsRequested;

    LspDiagnosticTooltip(JEditTextArea textArea) {
        this.textArea = textArea;
        this.painter = textArea.getPainter();
        this.view = textArea.getView();

        problemLabel = new JLabel();
        problemLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        actionsPanel = new JPanel();
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setOpaque(false);

        statusLabel = new JLabel("Loading quick fixes...");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        panel = new JPanel(new BorderLayout(0, 4));
        panel.add(problemLabel, BorderLayout.NORTH);
        panel.add(actionsPanel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        applyLookAndFeelColors();

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
        painter.addMouseListener(painterMouseHandler);
    }

    void hide() {
        hideTooltip();
    }

    void dispose() {
        showTimer.stop();
        hideTimer.stop();
        hoverCheckTimer.stop();
        hideTooltip();
        painter.removeMouseMotionListener(painterMouseHandler);
        painter.removeMouseListener(painterMouseHandler);
    }

    private void handleMouseAt(int x, int y) {
        if (window.isVisible()) {
            return;
        }

        LspDiagnosticProblem problem = problemAt(x, y);
        if (problem == null) {
            pendingProblem = null;
            showTimer.stop();
            return;
        }

        Point screen = painter.getLocationOnScreen();
        anchorScreenX = screen.x + x;
        anchorScreenY = screen.y + y;

        String problemKey = problemKey(problem);
        if (problemKey.equals(problemKey(pendingProblem))) {
            return;
        }

        pendingProblem = problem;
        showTimer.restart();
    }

    private void showPendingTooltip() {
        if (pendingProblem == null) {
            return;
        }
        shownProblem = pendingProblem;
        shownProblemKey = problemKey(shownProblem);
        actionsRequested = false;

        Point pointer = MouseInfo.getPointerInfo().getLocation();
        anchorScreenX = pointer.x;
        anchorScreenY = pointer.y;

        applyLookAndFeelColors();
        setProblemContent(shownProblem);
        window.pack();
        positionWindow();
        window.setVisible(true);
        hideTimer.stop();
        hoverCheckTimer.start();
        updateHideTimer();
        loadActions(shownProblem);
    }

    private void setProblemContent(LspDiagnosticProblem problem) {
        Color foreground = labelForeground();
        String html = "<html><body style='width: " + TOOLTIP_MAX_WIDTH + "px; color: "
            + colorHex(foreground) + ";'>"
            + "<b><font color='" + colorHex(problem.getSeverity().getColor()) + "'>"
            + escapeHtml(problem.getSeverity().getLabel()) + ":</font></b> "
            + escapeHtml(problem.getMessage())
            + "</body></html>";
        problemLabel.setText(html);
        actionsPanel.removeAll();
        statusLabel.setText("Loading quick fixes...");
        statusLabel.setVisible(true);
        panel.revalidate();
    }

    private void loadActions(LspDiagnosticProblem problem) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            setActions(problem, List.of());
            return;
        }

        String key = problemKey(problem);
        List<LspCodeActionItem> cached = actionCache.get(key);
        if (cached != null) {
            setActions(problem, cached);
            return;
        }

        if (actionsRequested) {
            return;
        }

        GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
        if (client == null || client.getServer() == null) {
            setActions(problem, List.of());
            return;
        }

        actionsRequested = true;
        final String requestKey = key;
        LspCodeActions.requestCodeActionsForProblem(view, client, buffer, problem, items -> {
            if (!requestKey.equals(shownProblemKey) || !window.isVisible()) {
                return;
            }
            actionCache.put(requestKey, items);
            setActions(problem, items);
        });
    }

    private void setActions(LspDiagnosticProblem problem, List<LspCodeActionItem> items) {
        if (!problemKey(problem).equals(shownProblemKey)) {
            return;
        }

        actionsPanel.removeAll();
        if (items.isEmpty()) {
            statusLabel.setText("No quick fixes available");
            statusLabel.setVisible(true);
        } else {
            statusLabel.setVisible(false);
            for (LspCodeActionItem item : items) {
                actionsPanel.add(createActionButton(problem, item));
            }
            actionsPanel.add(Box.createVerticalGlue());
        }
        panel.revalidate();
        window.pack();
        if (window.isVisible()) {
            positionWindow();
        }
    }

    private JButton createActionButton(LspDiagnosticProblem problem, LspCodeActionItem item) {
        JButton button = new JButton(item.getTitle());
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(linkForeground());
        button.setBackground(panelBackground());
        button.setAlignmentX(JButton.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(TOOLTIP_MAX_WIDTH, 28));
        button.addActionListener(e -> {
            if (!(textArea.getBuffer() instanceof Buffer buffer)) {
                return;
            }
            GenericLspClient client = LspPlugin.getClientForBuffer(buffer);
            if (client != null) {
                LspCodeActions.applyCodeAction(view, client, buffer, problem, item);
            }
            hideTooltip();
        });
        return button;
    }

    private void positionWindow() {
        int windowX = anchorScreenX + CURSOR_OFFSET_X;
        int windowY = anchorScreenY + CURSOR_OFFSET_Y;

        Dimension size = window.getSize();
        java.awt.Rectangle screenBounds = painter.getGraphicsConfiguration().getBounds();
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
        if (isPointerOverPopup()) {
            hideTimer.stop();
        } else if (!hideTimer.isRunning()) {
            hideTimer.start();
        }
    }

    private boolean isPointerOverPopup() {
        Point pointer = MouseInfo.getPointerInfo().getLocation();
        Rectangle bounds = window.getBounds();
        return bounds.contains(pointer);
    }

    private void hideTooltip() {
        showTimer.stop();
        hideTimer.stop();
        hoverCheckTimer.stop();
        window.setVisible(false);
        shownProblem = null;
        shownProblemKey = null;
        pendingProblem = null;
        actionsRequested = false;
    }

    private LspDiagnosticProblem problemAt(int x, int y) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            return null;
        }

        int offset = textArea.xyToOffset(x, y, false);
        if (offset < 0) {
            return null;
        }

        for (LspDiagnosticProblem problem :
            LspDiagnosticsHub.getInstance().getProblemsForBuffer(buffer)) {
            int rangeStart = problem.getStartOffset(buffer);
            int rangeEnd = problem.getEndOffset(buffer);
            if (offset >= rangeStart && offset < rangeEnd) {
                return problem;
            }
        }
        return null;
    }

    private static String problemKey(LspDiagnosticProblem problem) {
        if (problem == null) {
            return "";
        }
        return problem.getUri() + ':' + problem.getLine() + ':' + problem.getCharacter();
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

    private static String colorHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
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
        problemLabel.setOpaque(true);
        problemLabel.setBackground(background);
        problemLabel.setForeground(foreground);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(background);
        statusLabel.setForeground(foreground);
        actionsPanel.setOpaque(true);
        actionsPanel.setBackground(background);

        if (window != null) {
            var content = (JPanel) window.getContentPane();
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

    private static Color linkForeground() {
        Color linkColor = UIManager.getColor("Component.linkColor");
        if (linkColor == null) {
            linkColor = UIManager.getColor("MenuItem.selectionForeground");
        }
        if (linkColor == null) {
            linkColor = labelForeground();
        }
        return linkColor;
    }
}
