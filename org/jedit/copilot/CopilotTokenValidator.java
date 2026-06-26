/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import org.gjt.sp.jedit.jEdit;

final class CopilotTokenValidator {

    private CopilotTokenValidator() {}

    static String validateTokenOrNull(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.startsWith("ghp_")) {
            return jEdit.getProperty("copilot.login.classic-token");
        }
        return null;
    }

    static String formatAuthError(String message) {
        if (message == null || message.isBlank()) {
            return jEdit.getProperty("copilot.error.auth-failed");
        }
        String lower = message.toLowerCase();
        if (lower.contains("authorization")
            || lower.contains("authentication")
            || lower.contains("/login")
            || lower.contains("401")
            || lower.contains("403")) {
            return jEdit.getProperty("copilot.error.auth-failed-detail", new String[] { message });
        }
        return message;
    }
}
