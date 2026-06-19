/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.gjt.sp.jedit.jEdit;
import org.jedit.cursor.CursorRunListener;

final class CopilotLocalBridgePool {

    private static final Map<String, CopilotLocalBridge> BRIDGES = new ConcurrentHashMap<>();

    private CopilotLocalBridgePool() {}

    static CopilotLocalBridge bridgeFor(String conversationId) {
        return BRIDGES.computeIfAbsent(conversationId, CopilotLocalBridge::new);
    }

    static void release(String conversationId) {
        CopilotLocalBridge bridge = BRIDGES.remove(conversationId);
        if (bridge != null) {
            bridge.close();
        }
    }

    static void releaseAll() {
        for (String conversationId : BRIDGES.keySet().toArray(String[]::new)) {
            release(conversationId);
        }
    }
}

final class CopilotLocalBridge implements AutoCloseable {

    private static final String BRIDGE_SCRIPT = "bridge.mjs";

    static final class RunOutcome {
        String sessionId;
        String runId;
    }

    private final String conversationId;
    private final Object lock = new Object();
    private final AtomicInteger nextRequestId = new AtomicInteger(1);

    private Process process;
    private BufferedWriter stdin;
    private Thread readerThread;
    private volatile boolean ready;
    private volatile int activeRequestId;
    private volatile CountDownLatch activeLatch;
    private volatile CursorRunListener activeListener;
    private volatile RunOutcome activeOutcome;
    private volatile List<CopilotModelInfo> activeModels;
    private volatile String activeError;
    private volatile String activeCompleteText;
    private volatile boolean closed;

    CopilotLocalBridge(String conversationId) {
        this.conversationId = conversationId;
    }

    RunOutcome run(String gitHubToken, String cwd, String sessionId, String modelId,
                   String prompt, CursorRunListener listener) throws IOException {
        RunOutcome outcome = new RunOutcome();
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = listener;
            activeOutcome = outcome;
            activeModels = null;
            activeError = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = new JsonObject();
            command.addProperty("id", activeRequestId);
            command.addProperty("cmd", "send");
            if (gitHubToken != null && !gitHubToken.isBlank()) {
                command.addProperty("gitHubToken", gitHubToken);
            }
            command.addProperty("cwd", cwd);
            if (sessionId != null && !sessionId.isBlank()) {
                command.addProperty("sessionId", sessionId);
            }
            if (modelId != null && !modelId.isBlank()) {
                command.addProperty("modelId", modelId);
            } else {
                command.addProperty("modelId", "auto");
            }
            command.addProperty("prompt", prompt);
            writeLine(command.toString());
        }
        awaitActiveRun();
        return outcome;
    }

    String complete(String gitHubToken, String cwd, String modelId, String prompt) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = null;
            activeOutcome = null;
            activeModels = null;
            activeError = null;
            activeCompleteText = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = new JsonObject();
            command.addProperty("id", activeRequestId);
            command.addProperty("cmd", "complete");
            if (gitHubToken != null && !gitHubToken.isBlank()) {
                command.addProperty("gitHubToken", gitHubToken);
            }
            command.addProperty("cwd", cwd);
            if (modelId != null && !modelId.isBlank()) {
                command.addProperty("modelId", modelId);
            } else {
                command.addProperty("modelId", "auto");
            }
            command.addProperty("prompt", prompt);
            writeLine(command.toString());
        }
        awaitActiveRun();
        if (activeError != null) {
            throw new IOException(activeError);
        }
        String text = activeCompleteText;
        return text != null ? text : "";
    }

    String ghostComplete(String cwd, String documentUri,
            String workspaceUri, String languageId, String documentText, int line,
            int character, int tabSize, boolean insertSpaces) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = null;
            activeOutcome = null;
            activeModels = null;
            activeError = null;
            activeCompleteText = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = buildGhostCommand("ghostComplete", cwd, documentUri,
                workspaceUri, languageId, documentText, line, character, tabSize,
                insertSpaces);
            command.addProperty("id", activeRequestId);
            writeLine(command.toString());
        }
        awaitActiveRun();
        if (activeError != null) {
            throw new IOException(activeError);
        }
        String text = activeCompleteText;
        return text != null ? text : "";
    }

    void ghostAuth(String cwd) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = null;
            activeOutcome = null;
            activeModels = null;
            activeError = null;
            activeCompleteText = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = buildGhostCommand("ghostAuth", cwd, null, null,
                null, null, 0, 0, 4, true);
            command.addProperty("id", activeRequestId);
            writeLine(command.toString());
        }
        awaitActiveRun();
        if (activeError != null) {
            throw new IOException(activeError);
        }
    }

    private JsonObject buildGhostCommand(String cmdName, String cwd, String documentUri,
            String workspaceUri, String languageId, String documentText, int line,
            int character, int tabSize, boolean insertSpaces) {
        JsonObject command = new JsonObject();
        command.addProperty("cmd", cmdName);
        command.addProperty("cwd", cwd);
        String lspToken = CopilotConfig.copilotLspToken();
        if (lspToken != null && !lspToken.isBlank()) {
            command.addProperty("copilotLspToken", lspToken);
        }
        command.addProperty("node", CopilotConfig.nodeExecutable());
        if (documentUri != null) {
            command.addProperty("documentUri", documentUri);
        }
        if (workspaceUri != null && !workspaceUri.isBlank()) {
            command.addProperty("workspaceUri", workspaceUri);
        }
        if (languageId != null) {
            command.addProperty("languageId", languageId);
        }
        if (documentText != null) {
            command.addProperty("documentText", documentText);
        }
        command.addProperty("line", line);
        command.addProperty("character", character);
        command.addProperty("tabSize", tabSize);
        command.addProperty("insertSpaces", insertSpaces);
        return command;
    }

    void validate(String gitHubToken, String cwd) throws IOException {
        runSimpleCommand(gitHubToken, cwd, "validate");
        if (activeError != null) {
            throw new IOException(activeError);
        }
    }

    List<CopilotModelInfo> listModels(String gitHubToken, String cwd) throws IOException {
        runSimpleCommand(gitHubToken, cwd, "listModels");
        if (activeError != null) {
            throw new IOException(activeError);
        }
        List<CopilotModelInfo> models = activeModels;
        return models != null ? models : List.of();
    }

    private void runSimpleCommand(String gitHubToken, String cwd, String cmdName) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = null;
            activeOutcome = null;
            activeModels = null;
            activeError = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = new JsonObject();
            command.addProperty("id", activeRequestId);
            command.addProperty("cmd", cmdName);
            if (gitHubToken != null && !gitHubToken.isBlank()) {
                command.addProperty("gitHubToken", gitHubToken);
            }
            command.addProperty("cwd", cwd);
            writeLine(command.toString());
        }
        awaitActiveRun();
    }

    void cancelActiveRun() {
        synchronized (lock) {
            if (process == null || !process.isAlive() || activeLatch == null) {
                return;
            }
            int requestId = activeRequestId;
            try {
                JsonObject command = new JsonObject();
                command.addProperty("id", requestId);
                command.addProperty("cmd", "cancel");
                writeLine(command.toString());
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
            if (process == null || !process.isAlive()) {
                destroyProcess();
                return;
            }
            try {
                JsonObject command = new JsonObject();
                command.addProperty("id", nextRequestId.getAndIncrement());
                command.addProperty("cmd", "shutdown");
                writeLine(command.toString());
                process.waitFor(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            } finally {
                destroyProcess();
            }
        }
    }

    private void ensureProcess() throws IOException {
        if (process != null && process.isAlive() && ready) {
            return;
        }
        destroyProcess();
        CopilotBridgeInstaller.ensureInstalled();
        Path bridgeDir = CopilotBridgeInstaller.bridgeDirectory();
        String node = CopilotConfig.nodeExecutable();
        ProcessBuilder builder = new ProcessBuilder(node, BRIDGE_SCRIPT);
        builder.directory(bridgeDir.toFile());
        builder.redirectErrorStream(true);
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException(jEdit.getProperty("copilot.error.node-missing"), e);
        }
        stdin = new BufferedWriter(new OutputStreamWriter(
            process.getOutputStream(), StandardCharsets.UTF_8));
        readerThread = new Thread(this::readLoop, "copilot-local-bridge-" + conversationId);
        readerThread.setDaemon(true);
        readerThread.start();
        waitForReady();
    }

    private void waitForReady() throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!ready) {
            if (process == null || !process.isAlive()) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-start-failed"));
            }
            if (System.nanoTime() > deadline) {
                throw new IOException(jEdit.getProperty("copilot.error.bridge-start-timeout"));
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(jEdit.getProperty("copilot.error.bridge-interrupted"), e);
            }
        }
    }

    private void awaitActiveRun() throws IOException {
        CountDownLatch latch = activeLatch;
        if (latch == null) {
            return;
        }
        try {
            latch.await(2, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(jEdit.getProperty("copilot.error.bridge-interrupted"), e);
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                dispatchLine(line);
            }
        } catch (IOException ignored) {
        } finally {
            completeActiveRun();
        }
    }

    private void dispatchLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        JsonObject event;
        try {
            event = JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            CursorRunListener listener = activeListener;
            if (listener != null) {
                listener.onError(line.trim());
            }
            completeActiveRun();
            return;
        }
        String type = stringOrNull(event, "type");
        if ("ready".equals(type)) {
            ready = true;
            return;
        }
        int requestId = event.has("requestId") ? event.get("requestId").getAsInt() : -1;
        if (requestId != activeRequestId) {
            return;
        }
        CursorRunListener listener = activeListener;
        if (listener == null) {
            switch (type) {
                case "result" -> {
                    activeCompleteText = stringOrNull(event, "text");
                    completeActiveRun();
                }
                case "models" -> {
                    activeModels = parseModels(event);
                    completeActiveRun();
                }
                case "validated" -> completeActiveRun();
                case "ghost_authenticated" -> completeActiveRun();
                case "error" -> {
                    activeError = stringOrNull(event, "message");
                    if (activeError == null) {
                        activeError = jEdit.getProperty("copilot.error.generic");
                    }
                    activeCompleteText = "";
                    completeActiveRun();
                }
                default -> {
                }
            }
            return;
        }
        switch (type) {
            case "session", "run" -> noteOutcome(event);
            case "assistant" -> {
                if (listener != null) {
                    String text = stringOrNull(event, "text");
                    if (text != null) {
                        listener.onAssistantDelta(text);
                    }
                }
            }
            case "thinking" -> {
                if (listener != null) {
                    String text = stringOrNull(event, "text");
                    if (text != null) {
                        listener.onThinkingDelta(text);
                    }
                }
            }
            case "tool_call" -> {
                if (listener != null) {
                    listener.onToolCall(
                        stringOrNull(event, "name"),
                        stringOrNull(event, "status"),
                        jsonObjectOrNull(event, "args"));
                }
            }
            case "status" -> {
                if (listener != null) {
                    listener.onStatus(stringOrNull(event, "status"));
                }
            }
            case "result" -> {
                noteOutcome(event);
                listener.onResult(stringOrNull(event, "text"), stringOrNull(event, "status"));
                completeActiveRun();
            }
            case "models" -> {
                activeModels = parseModels(event);
                completeActiveRun();
            }
            case "validated" -> completeActiveRun();
            case "ghost_authenticated" -> completeActiveRun();
            case "error" -> {
                listener.onError(stringOrNull(event, "message"));
                completeActiveRun();
            }
            case "cancelled" -> {
                if (listener != null) {
                    listener.onStatus("cancelled");
                }
                completeActiveRun();
            }
            default -> {
            }
        }
    }

    private List<CopilotModelInfo> parseModels(JsonObject event) {
        List<CopilotModelInfo> models = new ArrayList<>();
        if (!event.has("models") || !event.get("models").isJsonArray()) {
            return models;
        }
        for (JsonElement element : event.getAsJsonArray("models")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject model = element.getAsJsonObject();
            String id = stringOrNull(model, "id");
            if (id == null || id.isBlank()) {
                id = stringOrNull(model, "model");
            }
            if (id == null || id.isBlank()) {
                continue;
            }
            String name = stringOrNull(model, "name");
            if (name == null || name.isBlank()) {
                name = stringOrNull(model, "displayName");
            }
            String description = stringOrNull(model, "description");
            models.add(CopilotModelInfo.fromBridgeJson(id, name, description));
        }
        return models;
    }

    private void noteOutcome(JsonObject event) {
        RunOutcome outcome = activeOutcome;
        if (outcome == null) {
            return;
        }
        String sessionId = stringOrNull(event, "sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = stringOrNull(event, "agentId");
        }
        if (sessionId != null && !sessionId.isBlank()) {
            outcome.sessionId = sessionId;
        }
        String runId = stringOrNull(event, "runId");
        if (runId != null && !runId.isBlank()) {
            outcome.runId = runId;
        }
    }

    private void completeActiveRun() {
        CountDownLatch latch = activeLatch;
        if (latch != null) {
            latch.countDown();
        }
        activeOutcome = null;
    }

    private void writeLine(String line) throws IOException {
        if (stdin == null) {
            throw new IOException(jEdit.getProperty("copilot.error.bridge-closed"));
        }
        stdin.write(line);
        stdin.write('\n');
        stdin.flush();
    }

    private void destroyProcess() {
        ready = false;
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        stdin = null;
        activeLatch = null;
        activeListener = null;
    }

    private static String stringOrNull(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        JsonElement value = json.get(key);
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return null;
    }

    private static JsonObject jsonObjectOrNull(JsonObject json, String key) {
        if (json == null || !json.has(key)) {
            return null;
        }
        JsonElement value = json.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
