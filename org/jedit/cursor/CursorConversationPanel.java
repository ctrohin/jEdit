/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;

final class CursorConversationPanel extends JPanel {

    private final View view;
    private final CursorConversation conversation;
    private final Runnable loginRequired;
    private final Runnable runningStateChanged;
    private final Consumer<CursorConversation> onConversationUpdated;
    private final JTextArea conversationArea;
    private final JButton openAgentButton;

    private volatile boolean running;
    private String activeRunId;
    private final StringBuilder currentResponse = new StringBuilder();
    private String pendingQuery;

    CursorConversationPanel(View view, CursorConversation conversation,
                            Runnable loginRequired, Runnable runningStateChanged,
                            Consumer<CursorConversation> onConversationUpdated) {
        super(new BorderLayout(0, 4));
        this.view = view;
        this.conversation = conversation;
        this.loginRequired = loginRequired;
        this.runningStateChanged = runningStateChanged;
        this.onConversationUpdated = onConversationUpdated;

        conversationArea = new JTextArea();
        conversationArea.setEditable(false);
        conversationArea.setLineWrap(true);
        conversationArea.setWrapStyleWord(true);
        conversationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN,
            conversationArea.getFont().getSize()));
        conversationArea.setText(conversation.formatDisplay());

        openAgentButton = new JButton(jEdit.getProperty("cursor.open-agent"));
        openAgentButton.setEnabled(conversation.agentUrl != null && !conversation.agentUrl.isBlank());
        openAgentButton.addActionListener(e -> {
            if (conversation.agentUrl != null && !conversation.agentUrl.isBlank()) {
                org.gjt.sp.jedit.MiscUtilities.openInDesktop(conversation.agentUrl);
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolbar.add(openAgentButton);
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(conversationArea), BorderLayout.CENTER);
    }

    CursorConversation conversation() {
        return conversation;
    }

    CursorMode mode() {
        return conversation.mode;
    }

    void setMode(CursorMode mode) {
        conversation.mode = mode;
    }

    boolean isRunning() {
        return running;
    }

    void refreshAuthState(boolean loggedIn) {
        openAgentButton.setEnabled(loggedIn
            && conversation.agentUrl != null && !conversation.agentUrl.isBlank());
        if (!loggedIn) {
            stopRun();
        }
    }

    void stopActiveRun() {
        stopRun();
    }

    void sendMessage(String userText, String modelId) {
        if (running || userText == null || userText.isBlank()) {
            return;
        }
        String apiKey = CursorConfig.apiKey();
        if (apiKey == null) {
            loginRequired.run();
            return;
        }

        pendingQuery = userText.trim();
        currentResponse.setLength(0);
        appendLine(jEdit.getProperty("cursor.you-prefix") + pendingQuery + "\n\n");
        setRunning(true);

        String fullPrompt = CursorWorkspaceContext.buildPromptPrefix(view, conversation.mode)
            + pendingQuery;
        CursorWorkspaceContext.GitHubRepo repo = CursorWorkspaceContext.findGitHubRepo();
        boolean attachRepo = conversation.mode == CursorMode.AGENT && repo != null;
        String existingAgentId = conversation.agentId;

        ThreadUtilities.runInBackground(() -> {
            CursorApiClient client = new CursorApiClient(apiKey);
            try {
                CursorApiClient.RunStart start = client.startRun(
                    existingAgentId, fullPrompt, conversation.mode, repo, attachRepo, modelId);
                conversation.agentId = start.agentId;
                activeRunId = start.runId;
                conversation.agentUrl = start.agentUrl;
                SwingUtilities.invokeLater(() -> {
                    boolean loggedIn = CursorConfig.apiKey() != null;
                    openAgentButton.setEnabled(loggedIn
                        && conversation.agentUrl != null && !conversation.agentUrl.isBlank());
                });

                appendAssistantHeader();
                client.streamRun(conversation.agentId, activeRunId,
                    new CursorApiClient.StreamListener() {
                        @Override
                        public void onAssistantDelta(String text) {
                            appendAssistant(text);
                            currentResponse.append(text);
                        }

                        @Override
                        public void onThinkingDelta(String text) {
                            appendMeta(jEdit.getProperty("cursor.thinking-prefix"), text);
                        }

                        @Override
                        public void onToolCall(String name, String status) {
                            if (name == null) {
                                return;
                            }
                            String label = name + (status != null ? " (" + status + ")" : "");
                            appendMeta(jEdit.getProperty("cursor.tool-prefix"), label + "\n");
                        }

                        @Override
                        public void onStatus(String status) {
                            if (status != null) {
                                appendMeta(jEdit.getProperty("cursor.status-prefix"), status + "\n");
                            }
                        }

                        @Override
                        public void onResult(String text, String status) {
                            if (text != null && !text.isBlank()) {
                                appendAssistant(text);
                                if (currentResponse.length() == 0) {
                                    currentResponse.append(text);
                                }
                            }
                            if (status != null) {
                                appendMeta(jEdit.getProperty("cursor.status-prefix"), status + "\n");
                            }
                            appendLine("\n");
                        }

                        @Override
                        public void onError(String message) {
                            appendError(message);
                            if (currentResponse.length() == 0 && message != null) {
                                currentResponse.append(CursorApiClient.formatErrorMessage(message));
                            }
                        }
                    });
            } catch (IOException e) {
                appendError(e.getMessage());
                if (currentResponse.length() == 0) {
                    currentResponse.append(e.getMessage());
                }
            } finally {
                activeRunId = null;
                String query = pendingQuery;
                String response = currentResponse.toString().trim();
                pendingQuery = null;
                if (query != null && !query.isBlank()) {
                    conversation.addExchange(query, response);
                    if (onConversationUpdated != null) {
                        SwingUtilities.invokeLater(() -> onConversationUpdated.accept(conversation));
                    }
                }
                SwingUtilities.invokeLater(() -> setRunning(false));
            }
        });
    }

    private void stopRun() {
        String runId = activeRunId;
        String currentAgentId = conversation.agentId;
        String apiKey = CursorConfig.apiKey();
        if (apiKey != null && currentAgentId != null && runId != null) {
            ThreadUtilities.runInBackground(() -> {
                try {
                    new CursorApiClient(apiKey).cancelRun(currentAgentId, runId);
                } catch (IOException ignored) {
                }
            });
        }
        setRunning(false);
    }

    private void setRunning(boolean active) {
        running = active;
        if (runningStateChanged != null) {
            runningStateChanged.run();
        }
    }

    private void appendAssistantHeader() {
        appendLine(jEdit.getProperty("cursor.assistant-prefix"));
    }

    private void appendAssistant(String text) {
        appendLine(text);
    }

    private void appendMeta(String prefix, String text) {
        appendLine(prefix + text);
    }

    private void appendError(String message) {
        String formatted = CursorApiClient.formatErrorMessage(message);
        String line = formatted == null || formatted.isBlank()
            ? jEdit.getProperty("cursor.error.generic")
            : formatted;
        appendLine(jEdit.getProperty("cursor.error-prefix") + line + "\n\n");
    }

    private void appendLine(String text) {
        SwingUtilities.invokeLater(() -> {
            conversationArea.append(text);
            conversationArea.setCaretPosition(conversationArea.getDocument().getLength());
        });
    }
}
