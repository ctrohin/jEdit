/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.io.File;
import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jedit.lsp.LspSymbolHit;

/**
 * One text match found while searching project files.
 */
public final class ProjectSearchMatch {

    private final String path;
    private final int line;
    private final int matchStart;
    private final int matchEnd;
    private final String lineText;

    public ProjectSearchMatch(String path, int line, int matchStart, int matchEnd,
                              String lineText) {
        this.path = path;
        this.line = line;
        this.matchStart = matchStart;
        this.matchEnd = matchEnd;
        this.lineText = lineText != null ? lineText : "";
    }

    public String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    public int getMatchStart() {
        return matchStart;
    }

    public int getMatchEnd() {
        return matchEnd;
    }

    public String getLineText() {
        return lineText;
    }

    public String formatListHtml() {
        String fileName = new File(path).getName();
        int end = Math.min(matchEnd, lineText.length());
        int start = Math.min(matchStart, end);
        String displayLine = lineText.replace('\t', ' ');
        String prefix = escapeHtml(fileName) + " - " + (line + 1) + ":"
            + (start + 1) + " - ";
        String before = escapeHtml(displayLine.substring(0, start));
        String match = escapeHtml(displayLine.substring(start, end));
        String after = escapeHtml(displayLine.substring(end));
        return "<html>" + prefix + before + "<b>" + match + "</b>" + after + "</html>";
    }

    public LspSymbolHit toLspSymbolHit() {
        String uri = new File(path).toURI().toString();
        int end = Math.min(matchEnd, lineText.length());
        int start = Math.min(matchStart, end);
        Range range = new Range(
            new Position(line, start),
            new Position(line, end));
        String matchText = lineText.substring(start, end);
        return new LspSymbolHit(uri, range, null, matchText, null, List.of());
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
