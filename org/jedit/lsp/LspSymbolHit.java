/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.gjt.sp.jedit.Buffer;

/**
 * One navigable symbol location shown in the LSP Symbol Results view.
 */
public final class LspSymbolHit implements Comparable<LspSymbolHit> {

    private final String uri;
    private final int line;
    private final int character;
    private final int endLine;
    private final int endCharacter;
    private final String label;
    private final String detail;
    private final List<LspSymbolHit> children;

    public LspSymbolHit(String uri, Range range, String label, String detail,
                        List<LspSymbolHit> children) {
        this.uri = Objects.requireNonNull(uri, "uri");
        Position start = range != null ? range.getStart() : null;
        Position end = range != null ? range.getEnd() : null;
        this.line = start != null ? start.getLine() : 0;
        this.character = start != null ? start.getCharacter() : 0;
        this.endLine = end != null ? end.getLine() : this.line;
        this.endCharacter = end != null ? end.getCharacter() : this.character;
        this.label = label != null && !label.isBlank() ? label : "(symbol)";
        this.detail = detail;
        this.children = children == null || children.isEmpty()
            ? List.of()
            : List.copyOf(children);
    }

    public static LspSymbolHit fromLocation(Location location, String label) {
        return new LspSymbolHit(
            location.getUri(),
            location.getRange(),
            label,
            null,
            List.of());
    }

    public String getUri() {
        return uri;
    }

    public int getLine() {
        return line;
    }

    public int getCharacter() {
        return character;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndCharacter() {
        return endCharacter;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public List<LspSymbolHit> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public Location toLocation() {
        Location location = new Location();
        location.setUri(uri);
        Range range = new Range();
        range.setStart(new Position(line, character));
        range.setEnd(new Position(endLine, endCharacter));
        location.setRange(range);
        return location;
    }

    public int getStartOffset(Buffer buffer) {
        if (buffer == null) {
            return 0;
        }
        try {
            int lineStart = buffer.getLineStartOffset(line);
            return Math.min(lineStart + character, buffer.getLength());
        } catch (Exception ignored) {
            return 0;
        }
    }

    public boolean containsOffset(Buffer buffer, int offset) {
        if (buffer == null) {
            return false;
        }
        try {
            int caretLine = buffer.getLineOfOffset(offset);
            int caretColumn = offset - buffer.getLineStartOffset(caretLine);
            if (caretLine < line || caretLine > endLine) {
                return false;
            }
            if (caretLine == line && caretColumn < character) {
                return false;
            }
            if (caretLine == endLine && caretColumn > endCharacter) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public String formatLineColumn() {
        return (line + 1) + ":" + (character + 1);
    }

    public String formatDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append(" (").append(formatLineColumn()).append(')');
        if (detail != null && !detail.isBlank()) {
            sb.append(" — ").append(detail);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(LspSymbolHit other) {
        int byLine = Integer.compare(line, other.line);
        if (byLine != 0) {
            return byLine;
        }
        return Integer.compare(character, other.character);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LspSymbolHit other)) {
            return false;
        }
        return line == other.line && character == other.character
            && endLine == other.endLine && endCharacter == other.endCharacter
            && Objects.equals(uri, other.uri)
            && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, line, character, label);
    }

    static List<LspSymbolHit> sortedCopy(List<LspSymbolHit> hits) {
        List<LspSymbolHit> copy = new ArrayList<>(hits);
        Collections.sort(copy);
        return List.copyOf(copy);
    }
}
