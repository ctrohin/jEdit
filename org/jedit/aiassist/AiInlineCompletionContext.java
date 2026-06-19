/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.io.File;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.WorkspaceTreeView;
import org.gjt.sp.jedit.jEdit;

final class AiInlineCompletionContext {

    private static final int CONTEXT_CHARS = 4000;
    private static final int SURROUNDING_LINES = 30;

    final String filePath;
    final String language;
    final String prompt;
    final int caret;
    final boolean emptyLine;

    private AiInlineCompletionContext(String filePath, String language, String prompt,
            int caret, boolean emptyLine) {
        this.filePath = filePath;
        this.language = language;
        this.prompt = prompt;
        this.caret = caret;
        this.emptyLine = emptyLine;
    }

    static AiInlineCompletionContext forBuffer(View view, Buffer buffer, int caret) {
        if (buffer == null || caret < 0) {
            return null;
        }
        int line = buffer.getLineOfOffset(caret);
        int lineStart = buffer.getLineStartOffset(line);
        int lineEnd = buffer.getLineEndOffset(line) - 1;
        if (lineEnd < lineStart) {
            lineEnd = lineStart;
        }
        String lineText = buffer.getText(lineStart, lineEnd - lineStart);
        boolean emptyLine = lineText.trim().isEmpty();

        int prefixStart = Math.max(0, caret - CONTEXT_CHARS);
        int suffixEnd = Math.min(buffer.getLength(), caret + CONTEXT_CHARS);
        String prefix = buffer.getText(prefixStart, caret - prefixStart);
        String suffix = buffer.getText(caret, suffixEnd - caret);

        String path = buffer.getPath();
        String language = buffer.getMode() != null ? buffer.getMode().getName() : "text";
        String workspacePrefix = buildWorkspacePrefix(view, buffer);
        String surrounding = buildSurroundingLines(buffer, line);
        String prompt = workspacePrefix
            + buildPrompt(path, language, line + 1, surrounding, prefix, suffix, emptyLine);
        return new AiInlineCompletionContext(path, language, prompt, caret, emptyLine);
    }

    private static String buildWorkspacePrefix(View view, Buffer buffer) {
        StringBuilder sb = new StringBuilder();
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder != null && !folder.isBlank()) {
            File root = new File(MiscUtilities.resolveSymlinks(folder));
            if (root.isDirectory()) {
                sb.append("Workspace: ").append(root.getPath()).append('\n');
            }
        }
        if (buffer != null && !buffer.isClosed()) {
            String path = buffer.getPath();
            if (path != null && !path.isBlank()) {
                sb.append("Current file: ").append(path).append('\n');
            }
            String modeName = buffer.getMode() != null ? buffer.getMode().getName() : null;
            if (modeName != null) {
                sb.append("Editor mode: ").append(modeName).append('\n');
            }
        }
        if (view != null) {
            String selection = view.getTextArea().getSelectedText();
            if (selection != null && !selection.isBlank()) {
                String trimmed = selection.length() > 2000
                    ? selection.substring(0, 2000) + "\n…"
                    : selection;
                sb.append("Selected text:\n```\n").append(trimmed).append("\n```\n");
            }
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String buildSurroundingLines(Buffer buffer, int caretLine) {
        int firstLine = Math.max(0, caretLine - SURROUNDING_LINES);
        int lastLine = Math.min(buffer.getLineCount() - 1, caretLine + SURROUNDING_LINES);
        StringBuilder sb = new StringBuilder();
        sb.append("Nearby lines:\n");
        for (int i = firstLine; i <= lastLine; i++) {
            int start = buffer.getLineStartOffset(i);
            int end = buffer.getLineEndOffset(i) - 1;
            if (end < start) {
                end = start;
            }
            String text = buffer.getText(start, end - start);
            sb.append(i + 1).append(i == caretLine ? ">| " : "|  ").append(text).append('\n');
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String buildPrompt(String path, String language, int lineNumber,
            String surrounding, String prefix, String suffix, boolean emptyLine) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an inline code completion assistant.\n");
        sb.append("Return ONLY the text to insert at the cursor. No markdown fences, ");
        sb.append("no explanation, no surrounding code.\n");
        if (emptyLine) {
            sb.append("The cursor is on an empty line. Suggest a natural continuation ");
            sb.append("for this file.\n");
        }
        sb.append("\nFile: ").append(path != null ? path : "untitled").append('\n');
        sb.append("Language: ").append(language).append('\n');
        sb.append("Caret line: ").append(lineNumber).append("\n\n");
        sb.append(surrounding);
        sb.append("Prefix before cursor:\n");
        sb.append(prefix);
        sb.append("\n[CURSOR]\n");
        sb.append("Suffix after cursor:\n");
        sb.append(suffix);
        sb.append("\n\nInsertion text:");
        return sb.toString();
    }

    static String sanitizeSuggestion(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closing = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && closing > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, closing).trim();
            }
        }
        while (trimmed.endsWith("\n")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
