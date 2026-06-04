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

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.ResourceOperation;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

/**
 * Applies LSP {@link WorkspaceEdit} payloads to open jEdit buffers.
 */
final class LspWorkspaceEdits {

    private LspWorkspaceEdits() {}

    /**
     * Builds a {@link WorkspaceEdit} from an {@code workspace/executeCommand} result
     * (often a Gson {@code JsonObject} or {@code Map}, not a typed LSP4J object).
     */
    static WorkspaceEdit workspaceEditFromExecuteResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof WorkspaceEdit) {
            return (WorkspaceEdit) result;
        }

        Map<String, Object> root = LspGsonArgs.asStringObjectMap(result);
        if (root == null) {
            return null;
        }

        WorkspaceEdit edit = new WorkspaceEdit();

        Object changes = root.get("changes");
        if (changes instanceof Map) {
            Map<String, List<TextEdit>> changeMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) changes).entrySet()) {
                if (entry.getKey() instanceof String) {
                    List<TextEdit> textEdits = textEditsFromObject(entry.getValue());
                    if (!textEdits.isEmpty()) {
                        changeMap.put((String) entry.getKey(), textEdits);
                    }
                }
            }
            if (!changeMap.isEmpty()) {
                edit.setChanges(changeMap);
            }
        }

        Object documentChanges = root.get("documentChanges");
        if (documentChanges instanceof List) {
            List<Either<TextDocumentEdit, ResourceOperation>> docChanges = new ArrayList<>();
            for (Object item : (List<?>) documentChanges) {
                Map<String, Object> changeMap = LspGsonArgs.asStringObjectMap(item);
                if (changeMap == null) {
                    continue;
                }
                Object textDocument = changeMap.get("textDocument");
                Object edits = changeMap.get("edits");
                Map<String, Object> textDocumentMap = LspGsonArgs.asStringObjectMap(textDocument);
                if (textDocumentMap == null) {
                    continue;
                }
                String uri = LspGsonArgs.asString(textDocumentMap.get("uri"));
                if (uri == null || uri.isEmpty()) {
                    continue;
                }
                List<TextEdit> textEdits = textEditsFromObject(edits);
                if (textEdits.isEmpty()) {
                    continue;
                }
                VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier();
                identifier.setUri(uri);
                Integer version = LspGsonArgs.asInteger(textDocumentMap.get("version"));
                if (version != null) {
                    identifier.setVersion(version);
                }
                TextDocumentEdit documentEdit = new TextDocumentEdit();
                documentEdit.setTextDocument(identifier);
                documentEdit.setEdits(textEdits);
                docChanges.add(Either.forLeft(documentEdit));
            }
            if (!docChanges.isEmpty()) {
                edit.setDocumentChanges(docChanges);
            }
        }

        if (edit.getChanges() == null && edit.getDocumentChanges() == null) {
            return null;
        }
        return edit;
    }

    /**
     * Applies all text edits in the workspace edit to matching open buffers.
     *
     * @return true if at least one edit was applied
     */
    static boolean apply(WorkspaceEdit edit) {
        if (edit == null) {
            return false;
        }

        boolean applied = false;

        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    TextDocumentEdit docEdit = change.getLeft();
                    if (docEdit != null && docEdit.getTextDocument() != null) {
                        applied |= applyToUri(
                            docEdit.getTextDocument().getUri(),
                            docEdit.getEdits());
                    }
                }
            }
        }

        if (edit.getChanges() != null) {
            for (Map.Entry<String, List<TextEdit>> entry : edit.getChanges().entrySet()) {
                applied |= applyToUri(entry.getKey(), entry.getValue());
            }
        }

        return applied;
    }

    static boolean applyToBuffer(Buffer buffer, String documentUri, WorkspaceEdit edit) {
        if (edit == null || buffer == null) {
            return false;
        }

        boolean applied = false;

        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    TextDocumentEdit docEdit = change.getLeft();
                    if (docEdit != null
                        && docEdit.getTextDocument() != null
                        && documentUri.equals(docEdit.getTextDocument().getUri())) {
                        applied |= applyTextEdits(buffer, docEdit.getEdits());
                    }
                }
            }
        }

        if (edit.getChanges() != null) {
            List<TextEdit> textEdits = edit.getChanges().get(documentUri);
            if (textEdits != null) {
                applied |= applyTextEdits(buffer, textEdits);
            }
        }

        return applied;
    }

    private static boolean applyToUri(String documentUri, List<TextEdit> edits) {
        Buffer buffer = bufferForUri(documentUri);
        if (buffer == null || !buffer.isEditable()) {
            Log.log(Log.WARNING, LspWorkspaceEdits.class,
                "No editable buffer open for URI: " + documentUri);
            return false;
        }
        return applyTextEdits(buffer, edits);
    }

    static Buffer bufferForUri(String documentUri) {
        if (documentUri == null) {
            return null;
        }
        try {
            URI uri = URI.create(documentUri);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            return jEdit.getBuffer(new File(uri).getPath());
        } catch (IllegalArgumentException e) {
            Log.log(Log.WARNING, LspWorkspaceEdits.class,
                "Invalid document URI: " + documentUri, e);
            return null;
        }
    }

    private static boolean applyTextEdits(Buffer buffer, List<TextEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return false;
        }

        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator
            .comparing((TextEdit e) -> e.getRange().getStart().getLine())
            .thenComparing(e -> e.getRange().getStart().getCharacter())
            .reversed());

        buffer.beginCompoundEdit();
        try {
            for (TextEdit edit : sorted) {
                applyTextEdit(buffer, edit);
            }
        } finally {
            buffer.endCompoundEdit();
        }
        return true;
    }

    private static void applyTextEdit(Buffer buffer, TextEdit textEdit) {
        Range range = textEdit.getRange();
        String newText = textEdit.getNewText();
        if (range == null || newText == null) {
            return;
        }

        int startOffset = positionToOffset(buffer, range.getStart());
        int endOffset = positionToOffset(buffer, range.getEnd());
        if (startOffset < 0 || endOffset < 0 || startOffset > endOffset) {
            Log.log(Log.WARNING, LspWorkspaceEdits.class,
                "Invalid text edit range: start=" + startOffset + ", end=" + endOffset);
            return;
        }

        buffer.remove(startOffset, endOffset - startOffset);
        buffer.insert(startOffset, newText);
    }

    private static List<TextEdit> textEditsFromObject(Object value) {
        List<TextEdit> edits = new ArrayList<>();
        if (!(value instanceof List)) {
            return edits;
        }
        for (Object item : (List<?>) value) {
            Map<String, Object> editMap = LspGsonArgs.asStringObjectMap(item);
            if (editMap == null) {
                continue;
            }
            Range range = rangeFromMap(LspGsonArgs.asStringObjectMap(editMap.get("range")));
            if (range == null) {
                continue;
            }
            TextEdit textEdit = new TextEdit();
            textEdit.setRange(range);
            String newText = LspGsonArgs.asString(editMap.get("newText"));
            textEdit.setNewText(newText != null ? newText : "");
            edits.add(textEdit);
        }
        return edits;
    }

    private static Range rangeFromMap(Map<String, Object> rangeMap) {
        if (rangeMap == null) {
            return null;
        }
        Position start = positionFromMap(LspGsonArgs.asStringObjectMap(rangeMap.get("start")));
        Position end = positionFromMap(LspGsonArgs.asStringObjectMap(rangeMap.get("end")));
        if (start == null || end == null) {
            return null;
        }
        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);
        return range;
    }

    private static Position positionFromMap(Map<String, Object> positionMap) {
        if (positionMap == null) {
            return null;
        }
        Integer line = LspGsonArgs.asInteger(positionMap.get("line"));
        Integer character = LspGsonArgs.asInteger(positionMap.get("character"));
        if (line == null || character == null) {
            return null;
        }
        Position position = new Position();
        position.setLine(line);
        position.setCharacter(character);
        return position;
    }

    private static int positionToOffset(Buffer buffer, Position position) {
        if (position == null) {
            return -1;
        }

        int line = position.getLine();
        int character = position.getCharacter();
        if (line < 0 || line >= buffer.getLineCount()) {
            return -1;
        }

        CharSequence lineContent = buffer.getLineSegment(line);
        if (character < 0 || character > lineContent.length()) {
            return -1;
        }

        return buffer.getLineStartOffset(line) + character;
    }
}
