/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.BorderLayout;
import java.awt.Container;
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
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.gjt.sp.jedit.jEdit;

public final class CursorChatView extends JPanel {

    private final String propsPrefix;
    private final JPanel messageList;
    private final JScrollPane scrollPane;
    private final CursorRunStatusPanel runStatusPanel;
    private final StringBuilder streamingText = new StringBuilder();
    private JEditorPane streamingPane;
    private Timer streamTimer;

    public CursorChatView() {
        this("cursor");
    }

    public CursorChatView(String propsPrefix) {
        super(new BorderLayout());
        this.propsPrefix = propsPrefix;
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

        runStatusPanel = new CursorRunStatusPanel(propsPrefix);

        add(scrollPane, BorderLayout.CENTER);
        add(runStatusPanel, BorderLayout.SOUTH);
    }

    public void loadConversation(CursorConversation conversation) {
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

    public void beginRunStatus() {
        runStatusPanel.reset();
        runStatusPanel.setStatus(jEdit.getProperty(propsPrefix + ".run.starting"));
    }

    public void clearRunStatus() {
        runStatusPanel.reset();
    }

    public void updateRunStatus(String status) {
        runStatusPanel.setStatus(status);
    }

    public void updateRunThinking(String text) {
        runStatusPanel.setThinking(text);
    }

    public void updateRunTool(String name, String status) {
        runStatusPanel.setTool(name, status);
    }

    public void addUserMessage(String text) {
        removeEmptyState();
        addMessageRow(buildMessageBlock(
            jEdit.getProperty(propsPrefix + ".chat.you"),
            CursorMarkdown.plainHtml(text),
            false));
    }

    public void beginAssistantMessage() {
        removeEmptyState();
        streamingText.setLength(0);
        addMessageRow(buildMessageBlock(
            jEdit.getProperty(propsPrefix + ".chat.assistant"),
            CursorMarkdown.documentHtml(""),
            true));
    }

    public void appendAssistantDelta(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (streamingPane == null) {
            beginAssistantMessage();
        }
        streamingText.append(text);
        scheduleStreamRender();
    }

    public void completeAssistantMessage(String text) {
        if (streamTimer != null) {
            streamTimer.stop();
        }
        String display = text == null ? "" : text.trim();
        String streamed = streamingText.toString().trim();
        if (streamed.length() > display.length()) {
            display = streamed;
        }
        boolean hadStreamingRow = streamingPane != null;
        streamingPane = null;
        streamingText.setLength(0);
        if (hadStreamingRow) {
            removeLastMessageRow();
        }
        if (!display.isEmpty()) {
            addAssistantMessage(display);
            return;
        }
        scrollToBottom();
    }

    public void finishAssistantMessage() {
        if (streamTimer != null) {
            streamTimer.stop();
        }
        if (streamingPane == null) {
            scrollToBottom();
            return;
        }
        String display = streamingText.toString().trim();
        streamingPane = null;
        streamingText.setLength(0);
        removeLastMessageRow();
        if (!display.isEmpty()) {
            addAssistantMessage(display);
            return;
        }
        scrollToBottom();
    }

    private void removeLastMessageRow() {
        int count = messageList.getComponentCount();
        if (count == 0) {
            return;
        }
        messageList.remove(count - 1);
        if (count >= 2 && messageList.getComponent(count - 2) instanceof Box.Filler) {
            messageList.remove(count - 2);
        }
        messageList.revalidate();
        messageList.repaint();
    }

    public void addAssistantMessage(String text) {
        removeEmptyState();
        JEditorPane pane = createHtmlPane(CursorMarkdown.documentHtml(text));
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(pane, BorderLayout.CENTER);
        addMessageRow(buildMessageBlock(
            jEdit.getProperty(propsPrefix + ".chat.assistant"),
            null,
            false,
            body));
        scrollToBottom();
    }

    public void addErrorMessage(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        removeEmptyState();
        Color foreground = CursorMarkdown.textForeground();
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(6, 0, 6, 0));

        JLabel header = createHeader(jEdit.getProperty(propsPrefix + ".chat.error"), true);
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
        if (pane == null) {
            return;
        }
        int width = htmlPaneWidth();
        if (width <= 0) {
            return; // viewport not laid out yet
        }
        fitEditorPane(pane, width);
    }

    private void fitEditorPane(JEditorPane pane, int width) {
        pane.setCaretPosition(0);
        // Lock width to the viewport so preferred height matches actual wrapping.
        pane.setSize(width, 1);
        Dimension pref = pane.getPreferredSize();
        int height = Math.max(24, pref.height);
        Dimension size = new Dimension(width, height);
        pane.setPreferredSize(size);
        pane.setMinimumSize(size);
    }

    private int htmlPaneWidth() {
        if (scrollPane.getViewport() == null) {
            return 0;
        }
        int viewportWidth = scrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            return 0;
        }
        return Math.max(200, viewportWidth - 24);
    }

    private void refreshMessageListLayout() {
        messageList.revalidate();
        messageList.repaint();

        // Ensure HTML pane height matches the final viewport width.
        int width = htmlPaneWidth();
        if (width > 0) {
            for (Component child : messageList.getComponents()) {
                fitDescendantHtmlPanes(child, width);
            }
        }
        scrollPane.revalidate();
    }

    private void fitDescendantHtmlPanes(Component component, int width) {
        if (component instanceof JEditorPane pane) {
            fitEditorPane(pane, width);
            return;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                fitDescendantHtmlPanes(child, width);
            }
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
        JLabel empty = new JLabel(jEdit.getProperty(propsPrefix + ".chat.empty"));
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
        refreshMessageListLayout();
        scrollToBottom();
    }

    private void scrollToBottom() {
        Runnable scroll = () -> {
            refreshMessageListLayout();
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        };
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(scroll));
    }
}
