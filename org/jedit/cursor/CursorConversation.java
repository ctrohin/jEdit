/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.gjt.sp.jedit.jEdit;

final class CursorConversation {

    final String id;
    String title;
    CursorMode mode;
    String agentId;
    String agentUrl;
    final List<CursorExchange> exchanges = new ArrayList<>();

    CursorConversation(String id, String title, CursorMode mode,
                       String agentId, String agentUrl) {
        this.id = id;
        this.title = title;
        this.mode = mode;
        this.agentId = agentId;
        this.agentUrl = agentUrl;
    }

    static CursorConversation createNew(CursorMode mode) {
        return new CursorConversation(
            UUID.randomUUID().toString(),
            jEdit.getProperty("cursor.tab.new"),
            mode,
            null,
            null);
    }

    void addExchange(String query, String response) {
        exchanges.add(new CursorExchange(query, response, System.currentTimeMillis()));
        updateTitleFromQuery(query);
    }

    void updateTitleFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        String defaultTitle = jEdit.getProperty("cursor.tab.new");
        if (defaultTitle.equals(title)) {
            title = truncate(query.trim(), 48);
        }
    }

    String formatDisplay() {
        StringBuilder sb = new StringBuilder();
        String youPrefix = jEdit.getProperty("cursor.you-prefix");
        String assistantPrefix = jEdit.getProperty("cursor.assistant-prefix");
        for (CursorExchange exchange : exchanges) {
            if (!exchange.query.isBlank()) {
                sb.append(youPrefix).append(exchange.query).append("\n\n");
            }
            if (!exchange.response.isBlank()) {
                sb.append(assistantPrefix).append(exchange.response).append("\n\n");
            }
        }
        return sb.toString();
    }

    boolean hasExchanges() {
        return !exchanges.isEmpty();
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
