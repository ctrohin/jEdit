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
        if (context == null) {
            AiAssistLog.message("fetch skipped: empty context");
            return "";
        }
        if (!context.ghostCapable && context.prompt.isBlank()) {
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
            + ", ghost=" + context.ghostCapable
            + (context.ghostCapable ? "" : ", promptChars=" + context.prompt.length())
            + ")");
        if (provider == AiAssistProvider.CURSOR || provider == AiAssistProvider.AUTO) {
            if (isCursorAvailable()) {
                AiAssistLog.message("trying Cursor inline completion");
                long start = System.nanoTime();
                String suggestion = sanitize(CursorPlugin.completeInline(context.prompt));
                AiAssistLog.message("Cursor returned " + suggestion.length() + " characters in "
                    + elapsedMs(start) + "ms");
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
                if (context.ghostCapable) {
                    AiAssistLog.message("trying GitHub Copilot ghost inline completion at "
                        + context.line + ":" + context.character);
                    long start = System.nanoTime();
                    String suggestion = "";
                    try {
                        suggestion = sanitizeGhost(CopilotPlugin.ghostInline(
                            context.documentUri,
                            context.workspaceUri,
                            context.languageId,
                            context.documentText,
                            context.line,
                            context.character,
                            context.tabSize,
                            context.insertSpaces));
                    } catch (IOException e) {
                        if (isGhostAuthError(e)) {
                            AiAssistLog.message("Copilot ghost auth required, using chat fallback");
                            CopilotPlugin.authenticateGhostLsp();
                        } else {
                            throw e;
                        }
                    }
                    AiAssistLog.message("Copilot ghost returned " + suggestion.length()
                        + " characters in " + elapsedMs(start) + "ms");
                    if (!suggestion.isBlank()) {
                        return suggestion;
                    }
                    if (provider == AiAssistProvider.COPILOT && context.prompt.isBlank()) {
                        return "";
                    }
                }
                if (!context.prompt.isBlank()) {
                    AiAssistLog.message("trying GitHub Copilot chat inline completion");
                    long start = System.nanoTime();
                    String suggestion = sanitize(CopilotPlugin.completeInline(context.prompt));
                    AiAssistLog.message("Copilot chat returned " + suggestion.length()
                        + " characters in " + elapsedMs(start) + "ms");
                    return suggestion;
                }
                return "";
            }
            AiAssistLog.message("GitHub Copilot not available (not signed in)");
        }
        AiAssistLog.message("no suggestion from any provider");
        return "";
    }

    private static String sanitize(String text) {
        return AiInlineCompletionContext.sanitizeSuggestion(text);
    }

    private static String sanitizeGhost(String text) {
        return AiInlineCompletionContext.sanitizeGhostSuggestion(text);
    }

    private static boolean isGhostAuthError(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("not authenticated")
            || lower.contains("could not retrieve token")
            || lower.contains("lsp sign-in");
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
