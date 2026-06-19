/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

public final class CopilotGhostInlineResult {

    public final String text;
    public final boolean hasRange;
    public final int rangeStartLine;
    public final int rangeStartCharacter;
    public final int rangeEndLine;
    public final int rangeEndCharacter;

    CopilotGhostInlineResult(String text, boolean hasRange, int rangeStartLine,
            int rangeStartCharacter, int rangeEndLine, int rangeEndCharacter) {
        this.text = text;
        this.hasRange = hasRange;
        this.rangeStartLine = rangeStartLine;
        this.rangeStartCharacter = rangeStartCharacter;
        this.rangeEndLine = rangeEndLine;
        this.rangeEndCharacter = rangeEndCharacter;
    }

    public static CopilotGhostInlineResult textOnly(String text) {
        return new CopilotGhostInlineResult(text != null ? text : "", false, 0, 0, 0, 0);
    }
}
