/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.io.IOException;

import org.gjt.sp.jedit.Buffer;
import org.jedit.copilot.CopilotGhostInlineResult;
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

    static AiInlineCompletionSuggestion fetchSuggestion(Buffer buffer,
            AiInlineCompletionContext context) throws IOException {
        if (context == null || buffer == null) {
            AiAssistLog.message("fetch skipped: empty context");
            return null;
        }
        if (!context.ghostCapable && context.prompt.isBlank()) {
            AiAssistLog.message("fetch skipped: empty context");
            return null;
        }
        AiAssistProvider provider = AiAssistConfig.provider();
        if (provider == AiAssistProvider.OFF) {
            AiAssistLog.message("fetch skipped: provider is off");
            return null;
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
                    return AiInlineCompletionSuggestion.fromRaw(
                        buffer, context.caret, suggestion);
                }
            } else {
                AiAssistLog.message("Cursor not available (not signed in)");
                if (provider == AiAssistProvider.CURSOR) {
                    return null;
                }
            }
        }
        if (provider == AiAssistProvider.COPILOT || provider == AiAssistProvider.AUTO) {
            if (isCopilotAvailable()) {
                if (context.ghostCapable) {
                    AiAssistLog.message("trying GitHub Copilot ghost inline completion at "
                        + context.line + ":" + context.character);
                    long start = System.nanoTime();
                    AiInlineCompletionSuggestion suggestion = null;
                    try {
                        CopilotGhostInlineResult ghost = CopilotPlugin.ghostInline(
                            context.documentUri,
                            context.workspaceUri,
                            context.languageId,
                            context.documentText,
                            context.line,
                            context.character,
                            context.tabSize,
                            context.insertSpaces);
                        suggestion = AiInlineCompletionSuggestion.fromGhost(
                            buffer,
                            context.caret,
                            sanitizeGhost(ghost.text),
                            ghost.hasRange,
                            ghost.rangeStartLine,
                            ghost.rangeStartCharacter,
                            ghost.rangeEndLine,
                            ghost.rangeEndCharacter);
                    } catch (IOException e) {
                        if (isGhostAuthError(e)) {
                            AiAssistLog.message("Copilot ghost auth required, using chat fallback");
                            CopilotPlugin.authenticateGhostLsp();
                        } else {
                            throw e;
                        }
                    }
                    if (suggestion != null && !suggestion.insertText.isBlank()) {
                        AiAssistLog.message("Copilot ghost returned "
                            + suggestion.insertText.length() + " characters in "
                            + elapsedMs(start) + "ms");
                        return suggestion;
                    }
                    if (provider == AiAssistProvider.COPILOT && context.prompt.isBlank()) {
                        return null;
                    }
                }
                if (!context.prompt.isBlank()) {
                    AiAssistLog.message("trying GitHub Copilot chat inline completion");
                    long start = System.nanoTime();
                    String suggestion = sanitize(CopilotPlugin.completeInline(context.prompt));
                    AiAssistLog.message("Copilot chat returned " + suggestion.length()
                        + " characters in " + elapsedMs(start) + "ms");
                    return AiInlineCompletionSuggestion.fromRaw(
                        buffer, context.caret, suggestion);
                }
                return null;
            }
            AiAssistLog.message("GitHub Copilot not available (not signed in)");
        }
        AiAssistLog.message("no suggestion from any provider");
        return null;
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
