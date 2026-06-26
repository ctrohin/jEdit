/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;

/**
 * One navigable symbol location shown in the LSP Symbol Results view.
 */
public final class LspSymbolHit implements Comparable<LspSymbolHit> {

    private final String uri;
    private final int line;
    private final int character;
    private final int endLine;
    private final int endCharacter;
    private final SymbolKind kind;
    private final String name;
    private final String label;
    private final String detail;
    private final String containerName;
    private final List<LspSymbolHit> children;

    public LspSymbolHit(String uri, Range range, SymbolKind kind, String name,
                        String detail, List<LspSymbolHit> children) {
        this(uri, range, kind, name, detail, null, children);
    }

    public LspSymbolHit(String uri, Range range, SymbolKind kind, String name,
                        String detail, String containerName,
                        List<LspSymbolHit> children) {
        this.uri = Objects.requireNonNull(uri, "uri");
        Position start = range != null ? range.getStart() : null;
        Position end = range != null ? range.getEnd() : null;
        this.line = start != null ? start.getLine() : 0;
        this.character = start != null ? start.getCharacter() : 0;
        this.endLine = end != null ? end.getLine() : this.line;
        this.endCharacter = end != null ? end.getCharacter() : this.character;
        this.kind = kind;
        this.name = name != null && !name.isBlank() ? name : "(symbol)";
        this.label = kind != null ? formatKindLabel(kind) + " " + this.name : this.name;
        this.detail = detail;
        this.containerName = containerName;
        this.children = children == null || children.isEmpty()
            ? List.of()
            : List.copyOf(children);
    }

    public static LspSymbolHit fromLocation(Location location, String label) {
        return new LspSymbolHit(
            location.getUri(),
            location.getRange(),
            null,
            label,
            null,
            List.of());
    }

    private static String formatKindLabel(SymbolKind kind) {
        String kindName = kind.name();
        return Character.toLowerCase(kindName.charAt(0)) + kindName.substring(1).toLowerCase();
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

    public SymbolKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getDetail() {
        return detail;
    }

    public String getContainerName() {
        return containerName;
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

    /**
     * Plain-text tree label: line number and full source line.
     */
    public String formatLineDisplayPlain() {
        LineDisplay display = buildLineDisplay();
        return display != null ? display.plainText() : formatDisplayText();
    }

    /**
     * HTML tree label: full source line with the symbol span in bold.
     */
    public String formatLineDisplayHtml() {
        LineDisplay display = buildLineDisplay();
        return display != null ? display.htmlText() : null;
    }

    private LineDisplay buildLineDisplay() {
        String lineText = resolveLineText();
        if (lineText == null) {
            return null;
        }
        lineText = lineText.replace('\t', ' ');
        int startCol = Math.max(0, Math.min(character, lineText.length()));
        int endCol;
        if (line < endLine) {
            endCol = lineText.length();
        } else {
            endCol = Math.max(startCol, Math.min(endCharacter, lineText.length()));
        }
        if (endCol == startCol && name != null && !"(symbol)".equals(name)) {
            int searchFrom = Math.max(0, startCol - name.length());
            int idx = lineText.indexOf(name, searchFrom);
            if (idx >= 0) {
                startCol = idx;
                endCol = Math.min(idx + name.length(), lineText.length());
            }
        }
        return new LineDisplay(line, lineText, startCol, endCol);
    }

    private String resolveLineText() {
        String path = LspDocumentUri.uriToPath(uri);
        if (path == null) {
            return null;
        }
        Buffer buffer = jEdit.getBuffer(path);
        if (buffer != null && buffer.isLoaded()) {
            try {
                if (line >= 0 && line < buffer.getLineCount()) {
                    return buffer.getLineText(line);
                }
            } catch (Exception ignored) {
            }
        }
        return readLineFromFile(path, line);
    }

    private static String readLineFromFile(String path, int lineIndex) {
        if (lineIndex < 0) {
            return null;
        }
        String encoding = jEdit.getProperty("buffer.encoding", "UTF-8");
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (Exception e) {
            charset = Charset.defaultCharset();
        }
        try (BufferedReader reader = Files.newBufferedReader(Path.of(path), charset)) {
            String text = null;
            for (int i = 0; i <= lineIndex; i++) {
                text = reader.readLine();
                if (text == null) {
                    return null;
                }
            }
            return text;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record LineDisplay(int line, String lineText, int startCol, int endCol) {
        String plainText() {
            return (line + 1) + ": " + lineText;
        }

        String htmlText() {
            String prefix = (line + 1) + ": ";
            String before = LspHover.escapeHtml(lineText.substring(0, startCol));
            String match = LspHover.escapeHtml(lineText.substring(startCol, endCol));
            String after = LspHover.escapeHtml(lineText.substring(endCol));
            return "<html>" + LspHover.escapeHtml(prefix) + before
                + "<b>" + match + "</b>" + after + "</html>";
        }
    }

    @Override
    public String toString() {
        return formatLineDisplayPlain();
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
            && kind == other.kind
            && Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, line, character, kind, name);
    }

    static List<LspSymbolHit> sortedCopy(List<LspSymbolHit> hits) {
        List<LspSymbolHit> copy = new ArrayList<>(hits);
        Collections.sort(copy);
        return List.copyOf(copy);
    }
}
