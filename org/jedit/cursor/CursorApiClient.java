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

package org.jedit.cursor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.gjt.sp.jedit.jEdit;

final class CursorApiClient {

    static final class AccountInfo {
        final String apiKeyName;
        final String userEmail;
        final String displayName;

        AccountInfo(String apiKeyName, String userEmail, String displayName) {
            this.apiKeyName = apiKeyName;
            this.userEmail = userEmail;
            this.displayName = displayName;
        }
    }

    static final class RunStart {
        final String agentId;
        final String runId;
        final String agentUrl;

        RunStart(String agentId, String runId, String agentUrl) {
            this.agentId = agentId;
            this.runId = runId;
            this.agentUrl = agentUrl;
        }
    }

    interface StreamListener extends CursorRunListener {
    }

    private static final URI BASE = URI.create("https://api.cursor.com");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private final String apiKey;

    CursorApiClient(String apiKey) {
        this.apiKey = apiKey;
    }

    AccountInfo fetchAccount() throws IOException {
        HttpResponse<String> response = send("GET", "/v1/me", null);
        if (response.statusCode() != 200) {
            throw apiError(response);
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        String apiKeyName = stringOrNull(json, "apiKeyName");
        String email = stringOrNull(json, "userEmail");
        String first = stringOrNull(json, "userFirstName");
        String last = stringOrNull(json, "userLastName");
        String displayName = buildDisplayName(first, last, email, apiKeyName);
        return new AccountInfo(apiKeyName, email, displayName);
    }

    List<CursorModelInfo> fetchModels() throws IOException {
        HttpResponse<String> response = send("GET", "/v1/models", null);
        if (response.statusCode() != 200) {
            throw apiError(response);
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        List<CursorModelInfo> models = new ArrayList<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            for (var element : json.getAsJsonArray("items")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                String id = stringOrNull(item, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                String displayName = stringOrNull(item, "displayName");
                if (displayName == null || displayName.isBlank()) {
                    displayName = id;
                }
                CursorModelInfo model = new CursorModelInfo(
                    id, displayName, stringOrNull(item, "description"));
                if (!CursorModelInfo.isDuplicateAuto(model)) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    RunStart startRun(String existingAgentId, String promptText, CursorMode mode,
                      CursorWorkspaceContext.GitHubRepo repo, boolean attachRepo,
                      String modelId)
        throws IOException {
        JsonObject body = new JsonObject();
        JsonObject prompt = new JsonObject();
        prompt.addProperty("text", promptText);
        body.add("prompt", prompt);
        body.addProperty("mode", mode.apiMode());

        boolean creatingAgent = existingAgentId == null || existingAgentId.isBlank();
        if (creatingAgent && modelId != null && !modelId.isBlank()) {
            JsonObject model = new JsonObject();
            model.addProperty("id", modelId);
            body.add("model", model);
        }

        if (creatingAgent && attachRepo && repo != null) {
            JsonObject repoEntry = new JsonObject();
            repoEntry.addProperty("url", repo.url);
            if (repo.startingRef != null && !repo.startingRef.isBlank()) {
                repoEntry.addProperty("startingRef", repo.startingRef);
            }
            JsonArray repos = new JsonArray();
            repos.add(repoEntry);
            body.add("repos", repos);
        }

        String path;
        if (existingAgentId == null || existingAgentId.isBlank()) {
            path = "/v1/agents";
        } else {
            path = "/v1/agents/" + existingAgentId + "/runs";
        }

        HttpResponse<String> response = send("POST", path, body.toString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw apiError(response);
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject agent = json.has("agent") ? json.getAsJsonObject("agent") : null;
        JsonObject run = json.has("run") ? json.getAsJsonObject("run") : null;
        if (run == null && agent != null && agent.has("latestRunId")) {
            run = new JsonObject();
            run.addProperty("id", agent.get("latestRunId").getAsString());
            run.addProperty("agentId", agent.get("id").getAsString());
        }
        if (run == null) {
            throw new IOException("Cursor API response did not include a run.");
        }
        String agentId = stringOrNull(run, "agentId");
        if (agentId == null && agent != null) {
            agentId = stringOrNull(agent, "id");
        }
        if (agentId == null) {
            agentId = existingAgentId;
        }
        String runId = stringOrNull(run, "id");
        String agentUrl = agent != null ? stringOrNull(agent, "url") : null;
        return new RunStart(agentId, runId, agentUrl);
    }

    void streamRun(String agentId, String runId, CursorRunListener listener) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(BASE.resolve(
            "/v1/agents/" + agentId + "/runs/" + runId + "/stream"))
            .timeout(Duration.ofMinutes(30))
            .header("Authorization", authorization())
            .header("Accept", "text/event-stream")
            .GET()
            .build();
        try {
            HttpResponse<InputStream> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                try (InputStream in = response.body()) {
                    String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    String message = formatErrorMessage(body);
                    if (message == null || message.isBlank()) {
                        message = "HTTP " + response.statusCode();
                    }
                    throw new IOException(message);
                }
            }
            parseSse(response.body(), listener);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Cursor stream interrupted.", e);
        }
    }

    void cancelRun(String agentId, String runId) throws IOException {
        HttpResponse<String> response = send("POST",
            "/v1/agents/" + agentId + "/runs/" + runId + "/cancel", null);
        if (response.statusCode() != 200 && response.statusCode() != 202
            && response.statusCode() != 204 && response.statusCode() != 409) {
            throw apiError(response);
        }
    }

    private HttpResponse<String> send(String method, String path, String body) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(BASE.resolve(path))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", authorization());
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Cursor request interrupted.", e);
        }
    }

    private String authorization() {
        String token = Base64.getEncoder().encodeToString(
            (apiKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static IOException apiError(HttpResponse<String> response) {
        String message = formatErrorMessage(response.body());
        if (message == null || message.isBlank()) {
            message = "HTTP " + response.statusCode();
        }
        return new IOException(message);
    }

    static String formatErrorMessage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            JsonObject json = JsonParser.parseString(trimmed).getAsJsonObject();
            JsonObject error = json.has("error") && json.get("error").isJsonObject()
                ? json.getAsJsonObject("error")
                : json;
            String code = stringOrNull(error, "code");
            String message = stringOrNull(error, "message");
            if ("feature_unavailable".equals(code) && isStorageDisabledMessage(message)) {
                return jEdit.getProperty("cursor.error.storage-disabled");
            }
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (RuntimeException ignored) {
        }
        return trimmed;
    }

    private static boolean isStorageDisabledMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("storage") || lower.contains("privacy mode");
    }

    private static void parseSse(InputStream stream, CursorRunListener listener) throws IOException {
        String eventName = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    dispatchEvent(eventName, data, listener);
                } else if (line.isEmpty()) {
                    eventName = null;
                }
            }
        }
    }

    private static void dispatchEvent(String eventName, String data, CursorRunListener listener) {
        if (eventName == null || data == null || data.isEmpty()) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(data).getAsJsonObject();
            switch (eventName) {
                case "assistant" -> {
                    String text = stringOrNull(json, "text");
                    if (text != null) {
                        listener.onAssistantDelta(text);
                    }
                }
                case "thinking" -> {
                    String text = stringOrNull(json, "text");
                    if (text != null) {
                        listener.onThinkingDelta(text);
                    }
                }
                case "tool_call" -> {
                    JsonObject args = json.has("args") && json.get("args").isJsonObject()
                        ? json.getAsJsonObject("args")
                        : null;
                    listener.onToolCall(
                        stringOrNull(json, "name"),
                        stringOrNull(json, "status"),
                        args);
                }
                case "status" -> listener.onStatus(stringOrNull(json, "status"));
                case "result" -> listener.onResult(
                    stringOrNull(json, "text"),
                    stringOrNull(json, "status"));
                case "error" -> listener.onError(formatErrorMessage(data));
                default -> {
                }
            }
        } catch (RuntimeException ignored) {
            listener.onError(formatErrorMessage(data));
        }
    }

    private static String stringOrNull(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        return json.get(key).getAsString();
    }

    private static String buildDisplayName(String first, String last, String email, String apiKeyName) {
        StringBuilder name = new StringBuilder();
        if (first != null && !first.isBlank()) {
            name.append(first.trim());
        }
        if (last != null && !last.isBlank()) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(last.trim());
        }
        if (name.length() > 0) {
            return name.toString();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return apiKeyName != null ? apiKeyName : "Cursor";
    }
}
