/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import org.gjt.sp.jedit.jEdit;

final class CopilotConfig {

    static final String TOKEN_PROPERTY = "copilot.github-token";
    static final String LSP_TOKEN_PROPERTY = "copilot.lsp-token";
    static final String MODE_PROPERTY = "copilot.selected-mode";
    static final String MODEL_PROPERTY = "copilot.selected-model-id";
    static final String NODE_PROPERTY = "copilot.node-executable";
    static final String NPM_PROPERTY = "copilot.npm-executable";

    private CopilotConfig() {}

    static String gitHubToken() {
        String token = jEdit.getProperty(TOKEN_PROPERTY);
        return token == null || token.isBlank() ? null : token.trim();
    }

    static String copilotLspToken() {
        String token = jEdit.getProperty(LSP_TOKEN_PROPERTY);
        return token == null || token.isBlank() ? null : token.trim();
    }

    static void setCopilotLspToken(String token) {
        if (token == null || token.isBlank()) {
            jEdit.unsetProperty(LSP_TOKEN_PROPERTY);
        } else {
            jEdit.setProperty(LSP_TOKEN_PROPERTY, token.trim());
        }
    }

    static void setGitHubToken(String token) {
        if (token == null || token.isBlank()) {
            jEdit.unsetProperty(TOKEN_PROPERTY);
        } else {
            jEdit.setProperty(TOKEN_PROPERTY, token.trim());
        }
    }

    static void clearSession() {
        jEdit.unsetProperty(TOKEN_PROPERTY);
        jEdit.unsetProperty(LSP_TOKEN_PROPERTY);
    }

    static String modelId() {
        String id = jEdit.getProperty(MODEL_PROPERTY);
        return id == null || id.isBlank() ? null : id.trim();
    }

    static void setModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            jEdit.unsetProperty(MODEL_PROPERTY);
        } else {
            jEdit.setProperty(MODEL_PROPERTY, modelId.trim());
        }
    }

    static String nodeExecutable() {
        String configured = jEdit.getProperty(NODE_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return isWindows() ? "node.exe" : "node";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
}
