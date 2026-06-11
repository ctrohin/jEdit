/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

final class BuildConfigDocumentStore {

    private final Map<String, String> documents = new ConcurrentHashMap<>();

    void open(DidOpenTextDocumentParams params) {
        if (params.getTextDocument() != null) {
            documents.put(params.getTextDocument().getUri(),
                params.getTextDocument().getText());
        }
    }

    void change(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = documents.get(uri);
        if (text == null) {
            return;
        }
        for (TextDocumentContentChangeEvent event : params.getContentChanges()) {
            if (event.getRange() == null) {
                text = event.getText();
                continue;
            }
            int start = offset(text, event.getRange().getStart());
            int end = offset(text, event.getRange().getEnd());
            text = text.substring(0, start) + event.getText() + text.substring(end);
        }
        documents.put(uri, text);
    }

    void close(DidCloseTextDocumentParams params) {
        if (params.getTextDocument() != null) {
            documents.remove(params.getTextDocument().getUri());
        }
    }

    String getText(String uri) {
        return documents.get(uri);
    }

    private static int offset(String text, org.eclipse.lsp4j.Position position) {
        int line = position.getLine();
        int character = position.getCharacter();
        int offset = 0;
        int currentLine = 0;
        while (currentLine < line && offset < text.length()) {
            int next = text.indexOf('\n', offset);
            if (next < 0) {
                return text.length();
            }
            offset = next + 1;
            currentLine++;
        }
        return Math.min(text.length(), offset + character);
    }
}
