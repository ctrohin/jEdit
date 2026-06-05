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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.ResourceOperation;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.textarea.JEditTextArea;
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
        Set<Buffer> modifiedBuffers = new HashSet<>();
        for (Map.Entry<String, List<TextEdit>> entry : collectEditsByCanonicalUri(edit).entrySet()) {
            if (applyToUri(entry.getKey(), entry.getValue())) {
                applied = true;
                Buffer buffer = bufferForUri(entry.getKey());
                if (buffer != null) {
                    modifiedBuffers.add(buffer);
                }
            }
        }
        for (Buffer buffer : modifiedBuffers) {
            LspPlugin.republishBufferToServer(buffer);
        }
        return applied;
    }

    static boolean applyToBuffer(Buffer buffer, String documentUri, WorkspaceEdit edit) {
        if (edit == null || buffer == null) {
            return false;
        }

        List<TextEdit> merged = new ArrayList<>();
        for (Map.Entry<String, List<TextEdit>> entry : collectEditsByCanonicalUri(edit).entrySet()) {
            if (LspDocumentUri.urisReferToSameFile(documentUri, entry.getKey())) {
                merged.addAll(entry.getValue());
            }
        }
        if (merged.isEmpty()) {
            return false;
        }
        return applyTextEdits(buffer, merged);
    }

    /**
     * Merges all text edits per file. LSP ranges are relative to the document
     * snapshot before any edit, so each file must be updated in a single pass.
     * When {@code documentChanges} is present it takes precedence over {@code changes}.
     */
    private static Map<String, List<TextEdit>> collectEditsByCanonicalUri(WorkspaceEdit edit) {
        Map<String, List<TextEdit>> byUri = new LinkedHashMap<>();

        if (edit.getDocumentChanges() != null) {
            for (Either<TextDocumentEdit, ResourceOperation> change : edit.getDocumentChanges()) {
                if (change.isLeft()) {
                    TextDocumentEdit docEdit = change.getLeft();
                    if (docEdit != null
                        && docEdit.getTextDocument() != null
                        && docEdit.getEdits() != null
                        && !docEdit.getEdits().isEmpty()) {
                        mergeEdits(byUri,
                            docEdit.getTextDocument().getUri(),
                            docEdit.getEdits());
                    }
                }
            }
        }

        if (edit.getChanges() != null) {
            for (Map.Entry<String, List<TextEdit>> entry : edit.getChanges().entrySet()) {
                mergeEdits(byUri, entry.getKey(), entry.getValue());
            }
        }
        return byUri;
    }

    static int countTextEdits(WorkspaceEdit edit) {
        int count = 0;
        for (List<TextEdit> edits : collectEditsByCanonicalUri(edit).values()) {
            count += edits.size();
        }
        return count;
    }

    private static void mergeEdits(Map<String, List<TextEdit>> byUri,
                                   String uri, List<TextEdit> edits) {
        if (uri == null || edits == null || edits.isEmpty()) {
            return;
        }
        byUri.computeIfAbsent(canonicalUriKey(uri), k -> new ArrayList<>()).addAll(edits);
    }

    private static String canonicalUriKey(String uri) {
        String path = LspDocumentUri.uriToPath(uri);
        if (path != null) {
            return LspDocumentUri.pathToUri(path);
        }
        return uri;
    }

    private static boolean applyToUri(String documentUri, List<TextEdit> edits) {
        Buffer buffer = bufferForUri(documentUri);
        if (buffer != null && buffer.isEditable()) {
            return applyTextEdits(buffer, edits);
        }

        String path = LspDocumentUri.uriToPath(documentUri);
        if (path == null) {
            Log.log(Log.WARNING, LspWorkspaceEdits.class,
                "Cannot resolve local path for URI: " + documentUri);
            return false;
        }

        buffer = jEdit.openFile((View) null, path);
        if (buffer != null && buffer.isEditable()) {
            return applyTextEdits(buffer, edits);
        }

        Log.log(Log.WARNING, LspWorkspaceEdits.class,
            "Could not open editable buffer for URI: " + documentUri);
        return false;
    }

    static Buffer bufferForUri(String documentUri) {
        String path = LspDocumentUri.uriToPath(documentUri);
        if (path == null) {
            return null;
        }

        Buffer buffer = jEdit.getBuffer(path);
        if (buffer != null) {
            return buffer;
        }

        for (Buffer openBuffer : jEdit.getBufferManager().getBuffers()) {
            if (MiscUtilities.pathsEqual(openBuffer.getPath(), path)) {
                return openBuffer;
            }
        }
        return null;
    }

    private static final class ResolvedEdit {
        final int start;
        final int end;
        final String newText;

        ResolvedEdit(int start, int end, String newText) {
            this.start = start;
            this.end = end;
            this.newText = newText;
        }
    }

    private static boolean applyTextEdits(Buffer buffer, List<TextEdit> edits) {
        if (edits == null || edits.isEmpty()) {
            return false;
        }

        List<ResolvedEdit> resolved = new ArrayList<>();
        for (TextEdit edit : edits) {
            Range range = edit.getRange();
            if (range == null || range.getStart() == null || range.getEnd() == null) {
                continue;
            }
            String newText = edit.getNewText() != null ? edit.getNewText() : "";
            int start = positionToOffset(buffer, range.getStart());
            int end = positionToOffset(buffer, range.getEnd());
            if (start < 0 || end < 0 || start > end) {
                Log.log(Log.WARNING, LspWorkspaceEdits.class,
                    "Invalid text edit range in " + buffer.getPath()
                        + ": start=" + start + ", end=" + end);
                continue;
            }
            resolved.add(new ResolvedEdit(start, end, newText));
        }

        if (resolved.isEmpty()) {
            return false;
        }

        if (resolved.size() < edits.size()) {
            Log.log(Log.WARNING, LspWorkspaceEdits.class,
                "Skipped " + (edits.size() - resolved.size())
                    + " invalid text edit(s) in " + buffer.getPath()
                    + " (buffer text may be out of sync with the language server)");
        }

        resolved.sort(Comparator.comparingInt((ResolvedEdit e) -> e.start).reversed());

        int caretTarget = resolved.stream().mapToInt(e -> e.start).min().orElse(0);
        for (ResolvedEdit edit : resolved) {
            if (edit.end <= caretTarget) {
                caretTarget += edit.newText.length() - (edit.end - edit.start);
            } else if (edit.start < caretTarget) {
                caretTarget = edit.start;
            }
        }

        LspPlugin.beginApplyingLspEdits();
        buffer.beginCompoundEdit();
        try {
            for (ResolvedEdit edit : resolved) {
                buffer.remove(edit.start, edit.end - edit.start);
                buffer.insert(edit.start, edit.newText);
            }
        } finally {
            buffer.endCompoundEdit();
            LspPlugin.endApplyingLspEdits();
        }

        Log.log(Log.DEBUG, LspWorkspaceEdits.class,
            "Applied " + resolved.size() + " text edit(s) to " + buffer.getPath());
        setBufferCaret(buffer, caretTarget);
        return true;
    }

    private static void setBufferCaret(Buffer buffer, int caret) {
        int length = buffer.getLength();
        caret = Math.max(0, Math.min(caret, length));
        buffer.setIntegerProperty(Buffer.CARET, caret);
        buffer.setBooleanProperty(Buffer.CARET_POSITIONED, true);
        for (View view : jEdit.getViews()) {
            if (view.getBuffer() == buffer) {
                JEditTextArea textArea = view.getTextArea();
                if (textArea != null) {
                    textArea.setCaretPosition(caret);
                }
            }
        }
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
