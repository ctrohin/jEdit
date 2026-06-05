/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.lsp;

import java.awt.Color;
import java.util.Objects;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.gjt.sp.jedit.Buffer;

/**
 * A single LSP diagnostic shown in the Problems view.
 */
public final class LspDiagnosticProblem implements Comparable<LspDiagnosticProblem> {

    public enum Severity {
        ERROR("Error", new Color(0xD32F2F)),
        WARNING("Warning", new Color(0xF57C00)),
        INFO("Info", new Color(0x1976D2));

        private final String label;
        private final Color color;

        Severity(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() {
            return label;
        }

        public Color getColor() {
            return color;
        }

        static Severity fromLsp(DiagnosticSeverity severity) {
            if (severity == null) {
                return ERROR;
            }
            return switch (severity) {
                case Warning -> WARNING;
                case Information, Hint -> INFO;
                default -> ERROR;
            };
        }
    }

    private final String uri;
    private final int line;
    private final int character;
    private final int endLine;
    private final int endCharacter;
    private final String message;
    private final Severity severity;

    public LspDiagnosticProblem(String uri, int line, int character,
                                int endLine, int endCharacter,
                                String message, Severity severity) {
        this.uri = uri;
        this.line = line;
        this.character = character;
        this.endLine = endLine;
        this.endCharacter = endCharacter;
        this.message = message != null ? message : "";
        this.severity = severity != null ? severity : Severity.ERROR;
    }

    public static LspDiagnosticProblem fromLsp(String uri, Diagnostic diagnostic) {
        int line = 0;
        int character = 0;
        int endLine = 0;
        int endCharacter = 0;
        if (diagnostic.getRange() != null) {
            if (diagnostic.getRange().getStart() != null) {
                line = diagnostic.getRange().getStart().getLine();
                character = diagnostic.getRange().getStart().getCharacter();
            }
            if (diagnostic.getRange().getEnd() != null) {
                endLine = diagnostic.getRange().getEnd().getLine();
                endCharacter = diagnostic.getRange().getEnd().getCharacter();
            } else {
                endLine = line;
                endCharacter = character;
            }
        }
        return new LspDiagnosticProblem(
            uri,
            line,
            character,
            endLine,
            endCharacter,
            diagnostic.getMessage(),
            Severity.fromLsp(diagnostic.getSeverity()));
    }

    public String getUri() {
        return uri;
    }

    /** LSP line (0-based). */
    public int getLine() {
        return line;
    }

    /** LSP character (0-based). */
    public int getCharacter() {
        return character;
    }

    /** LSP end line (0-based), exclusive range end. */
    public int getEndLine() {
        return endLine;
    }

    /** LSP end character (0-based), exclusive range end. */
    public int getEndCharacter() {
        return endCharacter;
    }

    /** Start offset in the buffer for this diagnostic range. */
    public int getStartOffset(Buffer buffer) {
        return offsetAt(buffer, line, character);
    }

    /** End offset in the buffer (exclusive). */
    public int getEndOffset(Buffer buffer) {
        return offsetAt(buffer, endLine, endCharacter);
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    /**
     * Display line for the Problems tree: {@code Error (10:5) message}.
     */
    public String getDisplayText() {
        return severity.getLabel()
            + " (" + (line + 1) + ":" + (character + 1) + ") "
            + message;
    }

    private static int offsetAt(Buffer buffer, int line, int character) {
        if (buffer.getLineCount() == 0) {
            return 0;
        }
        if (line < 0) {
            line = 0;
        } else if (line >= buffer.getLineCount()) {
            line = buffer.getLineCount() - 1;
        }

        int lineStart = buffer.getLineStartOffset(line);
        int lineLength = buffer.getLineLength(line);
        if (character < 0) {
            character = 0;
        } else if (character > lineLength) {
            character = lineLength;
        }
        return Math.min(lineStart + character, buffer.getLength());
    }

    @Override
    public int compareTo(LspDiagnosticProblem other) {
        int byLine = Integer.compare(line, other.line);
        if (byLine != 0) {
            return byLine;
        }
        int byChar = Integer.compare(character, other.character);
        if (byChar != 0) {
            return byChar;
        }
        return message.compareTo(other.message);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LspDiagnosticProblem)) {
            return false;
        }
        LspDiagnosticProblem other = (LspDiagnosticProblem) obj;
        return line == other.line
            && character == other.character
            && endLine == other.endLine
            && endCharacter == other.endCharacter
            && severity == other.severity
            && Objects.equals(uri, other.uri)
            && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, line, character, endLine, endCharacter, message, severity);
    }
}
