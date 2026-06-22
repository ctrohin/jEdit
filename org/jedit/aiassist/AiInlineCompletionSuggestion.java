/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import org.gjt.sp.jedit.Buffer;

final class AiInlineCompletionSuggestion {

    final String displayText;
    final String insertText;
    final int replaceStart;
    final int replaceEnd;

    private AiInlineCompletionSuggestion(String displayText, String insertText,
            int replaceStart, int replaceEnd) {
        this.displayText = displayText;
        this.insertText = insertText;
        this.replaceStart = replaceStart;
        this.replaceEnd = replaceEnd;
    }

    static AiInlineCompletionSuggestion fromRaw(Buffer buffer, int caret, String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return empty();
        }
        int line = buffer.getLineOfOffset(caret);
        int lineStart = buffer.getLineStartOffset(line);
        String linePrefix = buffer.getText(lineStart, caret - lineStart);

        int replaceStart = caret;
        int replaceEnd = caret;
        String insertText = rawText;

        if (rawText.startsWith(linePrefix)) {
            replaceStart = lineStart;
            replaceEnd = caret;
        } else {
            int wordStart = findWordStart(linePrefix);
            String wordPrefix = linePrefix.substring(wordStart);
            if (!wordPrefix.isEmpty() && rawText.startsWith(wordPrefix)) {
                replaceStart = lineStart + wordStart;
                replaceEnd = caret;
            }
        }

        return new AiInlineCompletionSuggestion(
            buildDisplayText(buffer, lineStart, replaceStart, replaceEnd, insertText),
            insertText,
            replaceStart,
            replaceEnd);
    }

    String[] ghostLines(Buffer buffer) {
        String suffix = ghostSuffix(buffer);
        if (suffix.isEmpty()) {
            return new String[0];
        }
        return suffix.split("\n", -1);
    }

    private String ghostSuffix(Buffer buffer) {
        if (insertText.isEmpty()) {
            return "";
        }
        int replacedLength = Math.max(0, replaceEnd - replaceStart);
        String replaced = replacedLength > 0
            ? buffer.getText(replaceStart, replacedLength)
            : "";
        if (!replaced.isEmpty() && insertText.startsWith(replaced)) {
            return insertText.substring(replaced.length());
        }
        return insertText;
    }

    static AiInlineCompletionSuggestion fromGhost(Buffer buffer, int caret, String text,
            boolean hasRange, int rangeStartLine, int rangeStartCharacter,
            int rangeEndLine, int rangeEndCharacter) {
        if (text == null || text.isEmpty()) {
            return empty();
        }
        if (hasRange) {
            int startLineStart = buffer.getLineStartOffset(rangeStartLine);
            int endLineStart = buffer.getLineStartOffset(rangeEndLine);
            int replaceStart = startLineStart + rangeStartCharacter;
            int replaceEnd = endLineStart + rangeEndCharacter;
            int lineStart = buffer.getLineStartOffset(buffer.getLineOfOffset(caret));
            return new AiInlineCompletionSuggestion(
                buildDisplayText(buffer, lineStart, replaceStart, replaceEnd, text),
                text,
                replaceStart,
                replaceEnd);
        }
        return fromRaw(buffer, caret, text);
    }

    private static AiInlineCompletionSuggestion empty() {
        return new AiInlineCompletionSuggestion("", "", 0, 0);
    }

    private static String buildDisplayText(Buffer buffer, int lineStart,
            int replaceStart, int replaceEnd, String insertText) {
        String before = buffer.getText(lineStart, replaceStart - lineStart);
        int line = buffer.getLineOfOffset(replaceStart);
        int lineEnd = buffer.getLineEndOffset(line) - 1;
        if (lineEnd < replaceEnd) {
            lineEnd = replaceEnd;
        }
        String after = replaceEnd < lineEnd
            ? buffer.getText(replaceEnd, lineEnd - replaceEnd)
            : "";
        return before + insertText + after;
    }

    private static int findWordStart(String linePrefix) {
        int index = linePrefix.length();
        while (index > 0 && Character.isJavaIdentifierPart(linePrefix.charAt(index - 1))) {
            index--;
        }
        return index;
    }
}
