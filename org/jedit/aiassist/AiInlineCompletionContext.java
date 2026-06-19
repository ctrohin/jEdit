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

    private static final int CONTEXT_CHARS = 1500;
    private static final int SURROUNDING_LINES = 12;

    final String filePath;
    final String language;
    final String prompt;
    final String prefix;
    final String suffix;
    final String linePrefix;
    final String lineSuffix;
    final int caret;
    final boolean emptyLine;
    final boolean ghostCapable;
    final String documentUri;
    final String workspaceUri;
    final String languageId;
    final String documentText;
    final int line;
    final int character;
    final int tabSize;
    final boolean insertSpaces;

    private AiInlineCompletionContext(String filePath, String language, String prompt,
            String prefix, String suffix, String linePrefix, String lineSuffix,
            int caret, boolean emptyLine, boolean ghostCapable, String documentUri,
            String workspaceUri, String languageId, String documentText, int line,
            int character, int tabSize, boolean insertSpaces) {
        this.filePath = filePath;
        this.language = language;
        this.prompt = prompt;
        this.prefix = prefix;
        this.suffix = suffix;
        this.linePrefix = linePrefix;
        this.lineSuffix = lineSuffix;
        this.caret = caret;
        this.emptyLine = emptyLine;
        this.ghostCapable = ghostCapable;
        this.documentUri = documentUri;
        this.workspaceUri = workspaceUri;
        this.languageId = languageId;
        this.documentText = documentText;
        this.line = line;
        this.character = character;
        this.tabSize = tabSize;
        this.insertSpaces = insertSpaces;
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
        int character = caret - lineStart;

        String linePrefix = buffer.getText(lineStart, caret - lineStart);
        String lineSuffix = buffer.getText(caret, lineEnd - caret);

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

        String documentUri = documentUriFor(buffer);
        boolean ghostCapable = documentUri != null;
        String documentText = ghostCapable
            ? buffer.getText(0, buffer.getLength())
            : "";
        String languageId = mapLanguageId(buffer);
        String workspaceUri = workspaceUriFor(view);
        int tabSize = buffer.getTabSize();
        boolean insertSpaces = !buffer.getBooleanProperty("noTabs");

        return new AiInlineCompletionContext(path, language, prompt, prefix, suffix,
            linePrefix, lineSuffix, caret, emptyLine, ghostCapable, documentUri,
            workspaceUri, languageId, documentText, line, character, tabSize,
            insertSpaces);
    }

    private static String documentUriFor(Buffer buffer) {
        if (buffer == null || buffer.isUntitled()) {
            return null;
        }
        String path = buffer.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        return new File(MiscUtilities.resolveSymlinks(path)).toURI().toString();
    }

    private static String workspaceUriFor(View view) {
        String folder = jEdit.getProperty(WorkspaceTreeView.FOLDER_KEY);
        if (folder == null || folder.isBlank()) {
            return null;
        }
        File root = new File(MiscUtilities.resolveSymlinks(folder));
        if (!root.isDirectory()) {
            return null;
        }
        return root.toURI().toString();
    }

    private static String mapLanguageId(Buffer buffer) {
        if (buffer.getMode() == null) {
            return "plaintext";
        }
        return switch (buffer.getMode().getName()) {
            case "c++" -> "cpp";
            case "c#" -> "csharp";
            case "text" -> "plaintext";
            default -> buffer.getMode().getName();
        };
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

    static String sanitizeGhostSuggestion(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        while (text.endsWith("\n") || text.endsWith("\r")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
