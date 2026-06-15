/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

final class CursorHistoryStore {

    static final int MAX_EXCHANGES = 100;

    private CursorHistoryStore() {}

    static List<CursorConversation> load() {
        File file = historyFile();
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<CursorConversation> conversations = new ArrayList<>();
            if (!root.has("conversations") || !root.get("conversations").isJsonArray()) {
                return conversations;
            }
            for (JsonElement element : root.getAsJsonArray("conversations")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                CursorConversation conversation = parseConversation(element.getAsJsonObject());
                if (conversation != null && conversation.hasExchanges()) {
                    conversations.add(conversation);
                }
            }
            trimExchanges(conversations, MAX_EXCHANGES);
            return conversations;
        } catch (Exception ex) {
            Log.log(Log.WARNING, CursorHistoryStore.class, "Could not load " + file, ex);
            return new ArrayList<>();
        }
    }

    static void save(List<CursorConversation> conversations) {
        List<CursorConversation> toSave = new ArrayList<>();
        for (CursorConversation conversation : conversations) {
            if (conversation.hasExchanges()) {
                toSave.add(conversation);
            }
        }
        trimExchanges(toSave, MAX_EXCHANGES);
        File file = historyFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            Log.log(Log.WARNING, CursorHistoryStore.class, "Could not create " + parent);
            return;
        }
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (CursorConversation conversation : toSave) {
            array.add(toJson(conversation));
        }
        root.add("conversations", array);
        try (Writer writer = new FileWriter(file)) {
            writer.write(root.toString());
        } catch (IOException ex) {
            Log.log(Log.WARNING, CursorHistoryStore.class, "Could not save " + file, ex);
        }
    }

    static void trimExchanges(List<CursorConversation> conversations, int maxExchanges) {
        int total = countExchanges(conversations);
        while (total > maxExchanges && !conversations.isEmpty()) {
            CursorConversation oldest = conversations.get(0);
            if (!oldest.exchanges.isEmpty()) {
                oldest.exchanges.remove(0);
                total--;
            }
            if (oldest.exchanges.isEmpty()) {
                conversations.remove(0);
            }
        }
    }

    private static int countExchanges(List<CursorConversation> conversations) {
        int total = 0;
        for (CursorConversation conversation : conversations) {
            total += conversation.exchanges.size();
        }
        return total;
    }

    private static CursorConversation parseConversation(JsonObject json) {
        String id = stringOrNull(json, "id");
        if (id == null || id.isBlank()) {
            return null;
        }
        CursorMode mode = CursorMode.AGENT;
        String modeName = stringOrNull(json, "mode");
        if (modeName != null) {
            try {
                mode = CursorMode.valueOf(modeName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        CursorConversation conversation = new CursorConversation(
            id,
            stringOrNull(json, "title", jEdit.getProperty("cursor.tab.new")),
            mode,
            stringOrNull(json, "agentId"),
            stringOrNull(json, "agentUrl"));
        if (json.has("exchanges") && json.get("exchanges").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("exchanges")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject exchangeJson = element.getAsJsonObject();
                String query = stringOrNull(exchangeJson, "query", "");
                String response = stringOrNull(exchangeJson, "response", "");
                long timestamp = exchangeJson.has("timestamp")
                    ? exchangeJson.get("timestamp").getAsLong()
                    : System.currentTimeMillis();
                conversation.exchanges.add(new CursorExchange(query, response, timestamp));
            }
        }
        return conversation;
    }

    private static JsonObject toJson(CursorConversation conversation) {
        JsonObject json = new JsonObject();
        json.addProperty("id", conversation.id);
        json.addProperty("title", conversation.title);
        json.addProperty("mode", conversation.mode.name());
        if (conversation.agentId != null && !conversation.agentId.isBlank()) {
            json.addProperty("agentId", conversation.agentId);
        }
        if (conversation.agentUrl != null && !conversation.agentUrl.isBlank()) {
            json.addProperty("agentUrl", conversation.agentUrl);
        }
        JsonArray exchanges = new JsonArray();
        for (CursorExchange exchange : conversation.exchanges) {
            JsonObject item = new JsonObject();
            item.addProperty("query", exchange.query);
            item.addProperty("response", exchange.response);
            item.addProperty("timestamp", exchange.timestamp);
            exchanges.add(item);
        }
        json.add("exchanges", exchanges);
        return json;
    }

    private static String stringOrNull(JsonObject json, String key) {
        return stringOrNull(json, key, null);
    }

    private static String stringOrNull(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private static File historyFile() {
        String settings = jEdit.getSettingsDirectory();
        if (settings == null || settings.isBlank()) {
            return new File("cursor-history.json");
        }
        return new File(new File(settings, "cursor"), "history.json");
    }
}
