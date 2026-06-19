/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.io.IOException;

import org.jedit.copilot.CopilotPlugin;
import org.jedit.cursor.CursorPlugin;

final class AiInlineCompletionService {

    private AiInlineCompletionService() {}

    static boolean isCursorAvailable() {
        return CursorPlugin.isSignedIn();
    }

    static boolean isCopilotAvailable() {
        return CopilotPlugin.isSignedIn();
    }

    static String fetchSuggestion(AiInlineCompletionContext context) throws IOException {
        if (context == null || context.prompt.isBlank()) {
            AiAssistLog.message("fetch skipped: empty context");
            return "";
        }
        AiAssistProvider provider = AiAssistConfig.provider();
        if (provider == AiAssistProvider.OFF) {
            AiAssistLog.message("fetch skipped: provider is off");
            return "";
        }
        AiAssistLog.message("fetching suggestion (provider="
            + provider + ", emptyLine=" + context.emptyLine
            + ", file=" + context.filePath + ", caret=" + context.caret
            + ", promptChars=" + context.prompt.length() + ")");
        if (provider == AiAssistProvider.CURSOR || provider == AiAssistProvider.AUTO) {
            if (isCursorAvailable()) {
                AiAssistLog.message("trying Cursor inline completion");
                String suggestion = sanitize(CursorPlugin.completeInline(context.prompt));
                AiAssistLog.message("Cursor returned " + suggestion.length() + " characters");
                if (!suggestion.isBlank() || provider == AiAssistProvider.CURSOR) {
                    return suggestion;
                }
            } else {
                AiAssistLog.message("Cursor not available (not signed in)");
                if (provider == AiAssistProvider.CURSOR) {
                    return "";
                }
            }
        }
        if (provider == AiAssistProvider.COPILOT || provider == AiAssistProvider.AUTO) {
            if (isCopilotAvailable()) {
                AiAssistLog.message("trying GitHub Copilot inline completion");
                String suggestion = sanitize(CopilotPlugin.completeInline(context.prompt));
                AiAssistLog.message("Copilot returned " + suggestion.length() + " characters");
                return suggestion;
            }
            AiAssistLog.message("GitHub Copilot not available (not signed in)");
        }
        AiAssistLog.message("no suggestion from any provider");
        return "";
    }

    private static String sanitize(String text) {
        return AiInlineCompletionContext.sanitizeSuggestion(text);
    }
}
