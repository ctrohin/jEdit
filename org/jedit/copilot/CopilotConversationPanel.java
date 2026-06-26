/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.google.gson.JsonObject;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;
import org.jedit.cursor.CursorChangesPanel;
import org.jedit.cursor.CursorChatView;
import org.jedit.cursor.CursorConversation;
import org.jedit.cursor.CursorMode;
import org.jedit.cursor.CursorRunListener;
import org.jedit.cursor.CursorToolCallFiles;
import org.jedit.cursor.CursorWorkspaceChanges;

final class CopilotConversationPanel extends JPanel {

    private static final String PROPS_PREFIX = "copilot";

    private final View view;
    private final CursorConversation conversation;
    private final Runnable loginRequired;
    private final Runnable runningStateChanged;
    private final Consumer<CursorConversation> onConversationUpdated;
    private final CursorChatView chatView;
    private final CursorChangesPanel changesPanel;

    private volatile boolean running;
    private final StringBuilder currentResponse = new StringBuilder();
    private String pendingQuery;

    CopilotConversationPanel(View view, CursorConversation conversation,
                             Runnable loginRequired, Runnable runningStateChanged,
                             Consumer<CursorConversation> onConversationUpdated) {
        super(new java.awt.BorderLayout(0, 4));
        this.view = view;
        this.conversation = conversation;
        this.loginRequired = loginRequired;
        this.runningStateChanged = runningStateChanged;
        this.onConversationUpdated = onConversationUpdated;

        chatView = new CursorChatView(PROPS_PREFIX);
        chatView.loadConversation(conversation);

        changesPanel = new CursorChangesPanel(PROPS_PREFIX, view, conversation, () -> {
            if (onConversationUpdated != null) {
                onConversationUpdated.accept(conversation);
            }
        });

        add(chatView, java.awt.BorderLayout.CENTER);
        add(changesPanel, java.awt.BorderLayout.SOUTH);
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
        CopilotLocalBridgePool.release(conversation.id);
    }

    void refreshAuthState(boolean loggedIn) {
        if (!loggedIn && running) {
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
        if (!CopilotAuth.isSignedIn()) {
            loginRequired.run();
            return;
        }

        File workspace = CopilotWorkspaceContext.workspaceRoot();
        if (workspace == null) {
            appendError(jEdit.getProperty("copilot.no-workspace"));
            return;
        }

        pendingQuery = userText.trim();
        currentResponse.setLength(0);
        chatView.addUserMessage(pendingQuery);
        chatView.beginRunStatus();
        chatView.beginAssistantMessage();
        setRunning(true);

        final CursorMode mode = conversation.mode;
        String sessionId = conversation.agentId;
        String token = CopilotConfig.gitHubToken();
        String cwd = workspace.getAbsolutePath();

        ThreadUtilities.runInBackground(() -> {
            CursorWorkspaceChanges.beginRun(conversation, workspace);
            String fullPrompt = CopilotWorkspaceContext.buildPromptPrefix(view, mode)
                + pendingQuery;
            SwingUtilities.invokeLater(() -> changesPanel.refresh());
            try {
                CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor(conversation.id);
                CopilotLocalBridge.RunOutcome outcome = bridge.run(
                    token,
                    cwd,
                    sessionId,
                    modelId,
                    fullPrompt,
                    createRunListener(workspace));
                if (outcome.sessionId != null && !outcome.sessionId.isBlank()) {
                    conversation.agentId = outcome.sessionId;
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
                    if (text.length() >= currentResponse.length()) {
                        currentResponse.setLength(0);
                        currentResponse.append(text);
                    }
                    if (currentResponse.length() == 0) {
                        appendAssistant(text);
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
                    currentResponse.append(CopilotTokenValidator.formatAuthError(message));
                }
            }
        };
    }

    private void finishRun(File workspace) {
        String query = pendingQuery;
        String response = currentResponse.toString().trim();
        pendingQuery = null;
        CursorWorkspaceChanges.syncRunChanges(conversation, workspace);
        if (response.isBlank()) {
            response = jEdit.getProperty("copilot.chat.no-response");
        }
        final String finalResponse = response;
        SwingUtilities.invokeLater(() -> {
            chatView.completeAssistantMessage(finalResponse);
            chatView.clearRunStatus();
        });
        if (query != null && !query.isBlank()) {
            conversation.addExchange(query, finalResponse);
            SwingUtilities.invokeLater(() -> {
                changesPanel.refresh();
                if (onConversationUpdated != null) {
                    onConversationUpdated.accept(conversation);
                }
            });
        } else {
            SwingUtilities.invokeLater(() -> changesPanel.refresh());
        }
        SwingUtilities.invokeLater(() -> setRunning(false));
    }

    private void stopRun() {
        CopilotLocalBridgePool.bridgeFor(conversation.id).cancelActiveRun();
        setRunning(false);
        SwingUtilities.invokeLater(() -> chatView.clearRunStatus());
    }

    private void setRunning(boolean active) {
        if (running == active) {
            return;
        }
        running = active;
        if (runningStateChanged != null) {
            runningStateChanged.run();
        }
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
        String formatted = CopilotTokenValidator.formatAuthError(message);
        String line = formatted == null || formatted.isBlank()
            ? jEdit.getProperty("copilot.error.generic")
            : formatted;
        SwingUtilities.invokeLater(() -> chatView.addErrorMessage(
            jEdit.getProperty("copilot.error-prefix") + line));
    }
}
