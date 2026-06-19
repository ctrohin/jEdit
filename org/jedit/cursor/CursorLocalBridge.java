/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.gjt.sp.jedit.jEdit;

final class CursorLocalBridgePool {

    private static final Map<String, CursorLocalBridge> BRIDGES = new ConcurrentHashMap<>();

    private CursorLocalBridgePool() {}

    static CursorLocalBridge bridgeFor(String conversationId) {
        return BRIDGES.computeIfAbsent(conversationId, CursorLocalBridge::new);
    }

    static void release(String conversationId) {
        CursorLocalBridge bridge = BRIDGES.remove(conversationId);
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

final class CursorLocalBridge implements AutoCloseable {

    private static final String BRIDGE_SCRIPT = "bridge.mjs";

    static final class RunOutcome {
        String agentId;
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
    private volatile String activeCompleteText;
    private volatile boolean closed;

    CursorLocalBridge(String conversationId) {
        this.conversationId = conversationId;
    }

    RunOutcome run(String apiKey, String cwd, String agentId, String modelId, CursorMode mode,
                   String prompt, CursorRunListener listener) throws IOException {
        RunOutcome outcome = new RunOutcome();
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("cursor.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = listener;
            activeOutcome = outcome;
            activeLatch = new CountDownLatch(1);
            JsonObject command = new JsonObject();
            command.addProperty("id", activeRequestId);
            command.addProperty("cmd", "send");
            command.addProperty("apiKey", apiKey);
            command.addProperty("cwd", cwd);
            if (agentId != null && !agentId.isBlank()) {
                command.addProperty("agentId", agentId);
            }
            if (modelId != null && !modelId.isBlank()) {
                command.addProperty("modelId", modelId);
            } else {
                command.addProperty("modelId", "auto");
            }
            command.addProperty("mode", mode.apiMode());
            command.addProperty("prompt", prompt);
            writeLine(command.toString());
        }
        awaitActiveRun();
        return outcome;
    }

    String complete(String apiKey, String cwd, String modelId, String prompt) throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException(jEdit.getProperty("cursor.error.bridge-closed"));
            }
            ensureProcess();
            activeRequestId = nextRequestId.getAndIncrement();
            activeListener = null;
            activeOutcome = null;
            activeCompleteText = null;
            activeLatch = new CountDownLatch(1);
            JsonObject command = new JsonObject();
            command.addProperty("id", activeRequestId);
            command.addProperty("cmd", "complete");
            command.addProperty("apiKey", apiKey);
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
        String text = activeCompleteText;
        return text != null ? text : "";
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
        CursorBridgeInstaller.ensureInstalled();
        Path bridgeDir = CursorBridgeInstaller.bridgeDirectory();
        String node = CursorConfig.nodeExecutable();
        ProcessBuilder builder = new ProcessBuilder(node, BRIDGE_SCRIPT);
        builder.directory(bridgeDir.toFile());
        builder.redirectErrorStream(true);
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException(jEdit.getProperty("cursor.error.node-missing"), e);
        }
        stdin = new BufferedWriter(new OutputStreamWriter(
            process.getOutputStream(), StandardCharsets.UTF_8));
        readerThread = new Thread(this::readLoop, "cursor-local-bridge-" + conversationId);
        readerThread.setDaemon(true);
        readerThread.start();
        waitForReady();
    }

    private void waitForReady() throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!ready) {
            if (process == null || !process.isAlive()) {
                throw new IOException(jEdit.getProperty("cursor.error.bridge-start-failed"));
            }
            if (System.nanoTime() > deadline) {
                throw new IOException(jEdit.getProperty("cursor.error.bridge-start-timeout"));
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(jEdit.getProperty("cursor.error.bridge-interrupted"), e);
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
            throw new IOException(jEdit.getProperty("cursor.error.bridge-interrupted"), e);
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
                case "error" -> {
                    activeCompleteText = "";
                    completeActiveRun();
                }
                default -> {
                }
            }
            return;
        }
        switch (type) {
            case "agent", "run" -> noteOutcome(event);
            case "assistant" -> {
                String text = stringOrNull(event, "text");
                if (text != null) {
                    listener.onAssistantDelta(text);
                }
            }
            case "thinking" -> {
                String text = stringOrNull(event, "text");
                if (text != null) {
                    listener.onThinkingDelta(text);
                }
            }
            case "tool_call" -> listener.onToolCall(
                stringOrNull(event, "name"),
                stringOrNull(event, "status"),
                jsonObjectOrNull(event, "args"));
            case "status" -> listener.onStatus(stringOrNull(event, "status"));
            case "result" -> {
                noteOutcome(event);
                listener.onResult(stringOrNull(event, "text"), stringOrNull(event, "status"));
                completeActiveRun();
            }
            case "error" -> {
                listener.onError(stringOrNull(event, "message"));
                completeActiveRun();
            }
            case "cancelled" -> {
                listener.onStatus("cancelled");
                completeActiveRun();
            }
            default -> {
            }
        }
    }

    private void noteOutcome(JsonObject event) {
        RunOutcome outcome = activeOutcome;
        if (outcome == null) {
            return;
        }
        String agentId = stringOrNull(event, "agentId");
        if (agentId != null && !agentId.isBlank()) {
            outcome.agentId = agentId;
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
            throw new IOException(jEdit.getProperty("cursor.error.bridge-closed"));
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
        return json.get(key).getAsString();
    }

    private static JsonObject jsonObjectOrNull(JsonObject json, String key) {
        if (json == null || !json.has(key)) {
            return null;
        }
        JsonElement value = json.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
