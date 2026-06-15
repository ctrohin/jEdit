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
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;

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
    private final CursorChangesPanel changesPanel;

    private volatile boolean running;
    private volatile CursorRuntime activeRuntime;
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
        openAgentButton.setEnabled(hasCloudAgentUrl());
        openAgentButton.addActionListener(e -> {
            if (conversation.agentUrl != null && !conversation.agentUrl.isBlank()) {
                org.gjt.sp.jedit.MiscUtilities.openInDesktop(conversation.agentUrl);
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolbar.add(openAgentButton);

        changesPanel = new CursorChangesPanel(view, conversation, () -> {
            if (onConversationUpdated != null) {
                onConversationUpdated.accept(conversation);
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(new JScrollPane(conversationArea), BorderLayout.CENTER);
        center.add(changesPanel, BorderLayout.SOUTH);

        add(toolbar, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        changesPanel.refresh();
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

    void disposeBridge() {
        CursorLocalBridgePool.release(conversation.id);
    }

    void refreshAuthState(boolean loggedIn) {
        openAgentButton.setEnabled(loggedIn && hasCloudAgentUrl());
        if (!loggedIn) {
            stopRun();
        }
    }

    void stopActiveRun() {
        stopRun();
    }

    void sendMessage(String userText, String modelId, CursorRuntime runtime) {
        if (running || userText == null || userText.isBlank()) {
            return;
        }
        String apiKey = CursorConfig.apiKey();
        if (apiKey == null) {
            loginRequired.run();
            return;
        }
        if (runtime == null) {
            runtime = CursorConfig.runtime();
        }
        final CursorRuntime selectedRuntime = runtime;

        File workspace = CursorWorkspaceContext.workspaceRoot();
        if (selectedRuntime == CursorRuntime.LOCAL && workspace == null) {
            appendError(jEdit.getProperty("cursor.no-workspace"));
            return;
        }

        CursorWorkspaceChanges.beginRun(conversation, workspace);
        SwingUtilities.invokeLater(() -> changesPanel.refresh());

        pendingQuery = userText.trim();
        currentResponse.setLength(0);
        appendLine(jEdit.getProperty("cursor.you-prefix") + pendingQuery + "\n\n");
        setRunning(true, selectedRuntime);

        String fullPrompt = CursorWorkspaceContext.buildPromptPrefix(view, conversation.mode)
            + pendingQuery;
        String effectiveAgentId = CursorRuntime.effectiveAgentId(conversation.agentId, selectedRuntime);

        ThreadUtilities.runInBackground(() -> {
            try {
                appendAssistantHeader();
                if (selectedRuntime == CursorRuntime.LOCAL) {
                    runLocal(apiKey, workspace, effectiveAgentId, modelId, fullPrompt);
                } else {
                    runCloud(apiKey, workspace, effectiveAgentId, modelId, fullPrompt);
                }
            } catch (IOException e) {
                appendError(e.getMessage());
                if (currentResponse.length() == 0) {
                    currentResponse.append(e.getMessage());
                }
            } finally {
                finishRun(workspace);
            }
        });
    }

    private void runCloud(String apiKey, File workspace, String existingAgentId, String modelId,
                          String fullPrompt) throws IOException {
        CursorWorkspaceContext.GitHubRepo repo = CursorWorkspaceContext.findGitHubRepo();
        boolean attachRepo = conversation.mode == CursorMode.AGENT && repo != null;
        CursorApiClient client = new CursorApiClient(apiKey);
        CursorApiClient.RunStart start = client.startRun(
            existingAgentId, fullPrompt, conversation.mode, repo, attachRepo, modelId);
        conversation.agentId = start.agentId;
        activeRunId = start.runId;
        conversation.agentUrl = start.agentUrl;
        SwingUtilities.invokeLater(() ->
            openAgentButton.setEnabled(CursorConfig.apiKey() != null && hasCloudAgentUrl()));
        client.streamRun(conversation.agentId, activeRunId, createRunListener(workspace));
    }

    private void runLocal(String apiKey, File workspace, String existingAgentId, String modelId,
                          String fullPrompt) throws IOException {
        conversation.agentUrl = null;
        SwingUtilities.invokeLater(() -> openAgentButton.setEnabled(false));
        CursorLocalBridge bridge = CursorLocalBridgePool.bridgeFor(conversation.id);
        CursorLocalBridge.RunOutcome outcome = bridge.run(
            apiKey,
            workspace.getAbsolutePath(),
            existingAgentId,
            modelId,
            conversation.mode,
            fullPrompt,
            createRunListener(workspace));
        if (outcome.agentId != null && !outcome.agentId.isBlank()) {
            conversation.agentId = outcome.agentId;
        }
        activeRunId = outcome.runId;
    }

    private CursorRunListener createRunListener(File workspace) {
        return new CursorRunListener() {
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
            public void onToolCall(String name, String status, JsonObject args) {
                if (name == null) {
                    return;
                }
                String label = name + (status != null ? " (" + status + ")" : "");
                appendMeta(jEdit.getProperty("cursor.tool-prefix"), label + "\n");
                String path = CursorToolCallFiles.extractPath(name, args);
                if (path != null) {
                    CursorWorkspaceChanges.noteToolPath(conversation, path, workspace);
                    SwingUtilities.invokeLater(() -> changesPanel.refresh());
                }
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
        };
    }

    private void finishRun(File workspace) {
        activeRunId = null;
        activeRuntime = null;
        String query = pendingQuery;
        String response = currentResponse.toString().trim();
        pendingQuery = null;
        CursorWorkspaceChanges.syncRunChanges(conversation, workspace);
        if (query != null && !query.isBlank()) {
            conversation.addExchange(query, response);
            SwingUtilities.invokeLater(() -> {
                changesPanel.refresh();
                if (onConversationUpdated != null) {
                    onConversationUpdated.accept(conversation);
                }
            });
        } else {
            SwingUtilities.invokeLater(() -> changesPanel.refresh());
        }
        SwingUtilities.invokeLater(() -> setRunning(false, null));
    }

    private void stopRun() {
        CursorRuntime runtime = activeRuntime;
        if (runtime == CursorRuntime.LOCAL) {
            CursorLocalBridgePool.bridgeFor(conversation.id).cancelActiveRun();
        } else {
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
        }
        setRunning(false, null);
    }

    private void setRunning(boolean active, CursorRuntime runtime) {
        running = active;
        activeRuntime = active ? runtime : null;
        if (runningStateChanged != null) {
            runningStateChanged.run();
        }
    }

    private boolean hasCloudAgentUrl() {
        return conversation.agentUrl != null && !conversation.agentUrl.isBlank();
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
