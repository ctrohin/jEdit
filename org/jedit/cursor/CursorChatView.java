/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.jEdit;

final class CursorChatView extends JPanel {

    private final JPanel messageList;
    private final JScrollPane scrollPane;
    private final CursorRunStatusPanel runStatusPanel;
    private final StringBuilder streamingText = new StringBuilder();
    private JEditorPane streamingPane;
    private Timer streamTimer;

    CursorChatView() {
        super(new BorderLayout());
        setOpaque(true);
        setBackground(CursorMarkdown.textBackground());

        messageList = new JPanel();
        messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
        messageList.setOpaque(false);
        messageList.setBorder(new EmptyBorder(12, 12, 8, 12));

        scrollPane = new JScrollPane(messageList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        runStatusPanel = new CursorRunStatusPanel();

        add(scrollPane, BorderLayout.CENTER);
        add(runStatusPanel, BorderLayout.SOUTH);
    }

    void loadConversation(CursorConversation conversation) {
        clear();
        runStatusPanel.reset();
        if (conversation == null || conversation.exchanges.isEmpty()) {
            addEmptyState();
            revalidate();
            repaint();
            return;
        }
        for (CursorExchange exchange : conversation.exchanges) {
            if (!exchange.query.isBlank()) {
                addUserMessage(exchange.query);
            }
            if (!exchange.response.isBlank()) {
                addAssistantMessage(exchange.response);
            }
        }
        scrollToBottom();
    }

    void clear() {
        messageList.removeAll();
        streamingPane = null;
        streamingText.setLength(0);
        if (streamTimer != null) {
            streamTimer.stop();
        }
    }

    void beginRunStatus() {
        runStatusPanel.reset();
        runStatusPanel.setStatus(jEdit.getProperty("cursor.run.starting"));
    }

    void clearRunStatus() {
        runStatusPanel.reset();
    }

    void updateRunStatus(String status) {
        runStatusPanel.setStatus(status);
    }

    void updateRunThinking(String text) {
        runStatusPanel.setThinking(text);
    }

    void updateRunTool(String name, String status) {
        runStatusPanel.setTool(name, status);
    }

    void addUserMessage(String text) {
        removeEmptyState();
        addMessageRow(buildMessageBlock(
            jEdit.getProperty("cursor.chat.you"),
            CursorMarkdown.plainHtml(text),
            false));
    }

    void beginAssistantMessage() {
        removeEmptyState();
        streamingText.setLength(0);
        addMessageRow(buildMessageBlock(
            jEdit.getProperty("cursor.chat.assistant"),
            CursorMarkdown.documentHtml(""),
            true));
    }

    void appendAssistantDelta(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (streamingPane == null) {
            beginAssistantMessage();
        }
        streamingText.append(text);
        scheduleStreamRender();
    }

    void finishAssistantMessage() {
        if (streamTimer != null) {
            streamTimer.stop();
        }
        if (streamingPane != null) {
            streamingPane.setText(CursorMarkdown.documentHtml(streamingText.toString()));
            fitEditorPane(streamingPane);
        }
        streamingPane = null;
        streamingText.setLength(0);
        scrollToBottom();
    }

    void addAssistantMessage(String text) {
        removeEmptyState();
        JEditorPane pane = createHtmlPane(CursorMarkdown.documentHtml(text));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(pane, BorderLayout.CENTER);
        addMessageRow(buildMessageBlock(
            jEdit.getProperty("cursor.chat.assistant"),
            null,
            false,
            body));
        scrollToBottom();
    }

    void addErrorMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        removeEmptyState();
        Color foreground = CursorMarkdown.textForeground();
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(new EmptyBorder(6, 0, 6, 0));

        JLabel header = createHeader(jEdit.getProperty("cursor.chat.error"), true);
        JEditorPane body = createHtmlPane(CursorMarkdown.plainHtml(text));
        body.setForeground(CursorMarkdown.errorForeground());

        JPanel block = new JPanel(new BorderLayout(0, 4));
        block.setOpaque(true);
        block.setBackground(CursorMarkdown.errorBackground(foreground));
        block.setBorder(new EmptyBorder(8, 10, 8, 10));
        block.add(header, BorderLayout.NORTH);
        block.add(body, BorderLayout.CENTER);
        row.add(block, BorderLayout.CENTER);
        addMessageRow(row);
        scrollToBottom();
    }

    private JPanel buildMessageBlock(String headerText, String html, boolean streaming) {
        JEditorPane pane = createHtmlPane(html != null ? html : CursorMarkdown.documentHtml(""));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(pane, BorderLayout.CENTER);
        if (streaming) {
            streamingPane = pane;
        }
        return buildMessageBlock(headerText, html, streaming, body);
    }

    private JPanel buildMessageBlock(String headerText, String html, boolean streaming,
                                     JPanel bodyWrapper) {
        JPanel row = new JPanel(new BorderLayout(0, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(new EmptyBorder(6, 0, 10, 0));
        row.add(createHeader(headerText, false), BorderLayout.NORTH);
        row.add(bodyWrapper, BorderLayout.CENTER);
        return row;
    }

    private JLabel createHeader(String text, boolean error) {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setForeground(error
            ? CursorMarkdown.errorForeground()
            : CursorMarkdown.metaForeground(CursorMarkdown.textForeground()));
        return header;
    }

    private JEditorPane createHtmlPane(String html) {
        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(new EmptyBorder(0, 0, 0, 0));
        pane.setText(html);
        fitEditorPane(pane);
        return pane;
    }

    private void fitEditorPane(JEditorPane pane) {
        pane.setCaretPosition(0);
        Dimension size = pane.getPreferredSize();
        if (size != null) {
            pane.setPreferredSize(new Dimension(
                Math.max(200, scrollPane.getViewport().getWidth() - 24),
                size.height));
        }
    }

    private void addMessageRow(JPanel row) {
        messageList.add(row);
        messageList.add(Box.createVerticalStrut(4));
        messageList.revalidate();
        messageList.repaint();
        scrollToBottom();
    }

    private void addEmptyState() {
        if (messageList.getComponentCount() > 0) {
            return;
        }
        JLabel empty = new JLabel(jEdit.getProperty("cursor.chat.empty"));
        empty.setForeground(CursorMarkdown.metaForeground(CursorMarkdown.textForeground()));
        empty.setAlignmentX(Component.CENTER_ALIGNMENT);
        empty.setBorder(new EmptyBorder(24, 12, 24, 12));
        messageList.add(empty);
    }

    private void removeEmptyState() {
        if (messageList.getComponentCount() == 1) {
            Component child = messageList.getComponent(0);
            if (child instanceof JLabel) {
                messageList.removeAll();
            }
        }
    }

    private void scheduleStreamRender() {
        if (streamTimer == null) {
            streamTimer = new Timer(80, e -> renderStreaming());
            streamTimer.setRepeats(false);
        }
        streamTimer.restart();
    }

    private void renderStreaming() {
        if (streamingPane == null) {
            return;
        }
        streamingPane.setText(CursorMarkdown.documentHtml(streamingText.toString()));
        fitEditorPane(streamingPane);
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            if (scrollPane.getViewport() == null) {
                return;
            }
            int max = scrollPane.getVerticalScrollBar().getMaximum();
            scrollPane.getVerticalScrollBar().setValue(max);
        });
    }
}
