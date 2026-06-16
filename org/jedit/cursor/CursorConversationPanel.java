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
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JPanel;
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
    private final CursorChatView chatView;
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

        chatView = new CursorChatView();
        chatView.loadConversation(conversation);

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
        center.add(chatView, BorderLayout.CENTER);
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
        if (!loggedIn && running) {
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
        chatView.addUserMessage(pendingQuery);
        setRunning(true, selectedRuntime);

        String fullPrompt = CursorWorkspaceContext.buildPromptPrefix(view, conversation.mode)
            + pendingQuery;
        String effectiveAgentId = CursorRuntime.effectiveAgentId(conversation.agentId, selectedRuntime);

        ThreadUtilities.runInBackground(() -> {
            try {
                beginRun();
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
                updateThinking(text);
            }

            @Override
            public void onToolCall(String name, String status, JsonObject args) {
                if (name == null) {
                    return;
                }
                updateTool(name, status);
                String path = CursorToolCallFiles.extractPath(name, args);
                if (path != null) {
                    CursorWorkspaceChanges.noteToolPath(conversation, name, path, workspace);
                    SwingUtilities.invokeLater(() -> changesPanel.refresh());
                }
            }

            @Override
            public void onStatus(String status) {
                if (status != null) {
                    updateStatus(status);
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
                    updateStatus(status);
                }
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
        SwingUtilities.invokeLater(() -> {
            chatView.finishAssistantMessage();
            chatView.clearRunStatus();
        });
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
        SwingUtilities.invokeLater(() -> chatView.clearRunStatus());
    }

    private void setRunning(boolean active, CursorRuntime runtime) {
        if (running == active) {
            return;
        }
        running = active;
        activeRuntime = active ? runtime : null;
        if (runningStateChanged != null) {
            runningStateChanged.run();
        }
    }

    private boolean hasCloudAgentUrl() {
        return conversation.agentUrl != null && !conversation.agentUrl.isBlank();
    }

    private void beginRun() {
        SwingUtilities.invokeLater(() -> {
            chatView.beginRunStatus();
            chatView.beginAssistantMessage();
        });
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> chatView.updateRunStatus(status));
    }

    private void updateThinking(String text) {
        SwingUtilities.invokeLater(() -> chatView.updateRunThinking(text));
    }

    private void updateTool(String name, String status) {
        SwingUtilities.invokeLater(() -> chatView.updateRunTool(name, status));
    }

    private void appendAssistant(String text) {
        SwingUtilities.invokeLater(() -> chatView.appendAssistantDelta(text));
    }

    private void appendError(String message) {
        String formatted = CursorApiClient.formatErrorMessage(message);
        String line = formatted == null || formatted.isBlank()
            ? jEdit.getProperty("cursor.error.generic")
            : formatted;
        SwingUtilities.invokeLater(() -> chatView.addErrorMessage(
            jEdit.getProperty("cursor.error-prefix") + line));
    }
}
