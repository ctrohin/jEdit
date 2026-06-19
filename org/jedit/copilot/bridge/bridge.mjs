import { createInterface } from "node:readline";
import { CopilotClient, approveAll } from "@github/copilot-sdk";
import { ghostComplete, ghostAuth, shutdownGhostLsp } from "./ghost-lsp.mjs";

const rl = createInterface({ input: process.stdin, terminal: false });

let client = null;
let activeSession = null;
let inlineSession = null;
let activeUnsubscribers = [];
let lastAuthKey = null;

function emit(event) {
  process.stdout.write(JSON.stringify(event) + "\n");
}

function authKey(cmd) {
  return cmd.gitHubToken ? `token:${cmd.gitHubToken}` : "logged-in-user";
}

function buildClientOptions(cmd) {
  const options = {
    workingDirectory: cmd.cwd,
    env: { ...process.env },
  };
  if (cmd.gitHubToken) {
    options.gitHubToken = cmd.gitHubToken;
    options.useLoggedInUser = false;
    options.env.COPILOT_GITHUB_TOKEN = cmd.gitHubToken;
    options.env.GH_TOKEN = cmd.gitHubToken;
    options.env.GITHUB_TOKEN = cmd.gitHubToken;
  } else {
    options.useLoggedInUser = true;
  }
  return options;
}

function sessionConfig(cmd) {
  const config = {
    streaming: true,
    onPermissionRequest: approveAll,
  };
  if (cmd.modelId && cmd.modelId !== "auto") {
    config.model = cmd.modelId;
  }
  return config;
}

function inlineSessionConfig(cmd) {
  const config = {
    streaming: true,
    onPermissionRequest: approveAll,
  };
  if (cmd.modelId && cmd.modelId !== "auto") {
    config.model = cmd.modelId;
  }
  return config;
}

async function ensureInlineSession(cmd) {
  await ensureClient(cmd);
  if (inlineSession) {
    return inlineSession;
  }
  inlineSession = await client.createSession(inlineSessionConfig(cmd));
  return inlineSession;
}

async function ensureClient(cmd) {
  const key = authKey(cmd);
  if (client && lastAuthKey !== key) {
    await disposeClient();
  }
  if (!client) {
    lastAuthKey = key;
    client = new CopilotClient(buildClientOptions(cmd));
    await client.start();
  }
}

async function disposeClient() {
  if (client) {
    try {
      await client.stop();
    } catch (_) {
    }
    client = null;
  }
  lastAuthKey = null;
}

function clearActiveHandlers() {
  for (const unsub of activeUnsubscribers) {
    try {
      unsub();
    } catch (_) {
    }
  }
  activeUnsubscribers = [];
}

async function ensureSession(cmd) {
  await ensureClient(cmd);
  const wantId = cmd.sessionId || null;
  const currentId = activeSession?.sessionId ?? null;
  if (activeSession && currentId === wantId) {
    return activeSession;
  }
  if (activeSession) {
    try {
      await activeSession.disconnect();
    } catch (_) {
    }
    activeSession = null;
  }
  clearActiveHandlers();
  const config = sessionConfig(cmd);
  activeSession = wantId
    ? await client.resumeSession(wantId, config)
    : await client.createSession(config);
  emit({ type: "session", requestId: cmd.id, sessionId: activeSession.sessionId });
  return activeSession;
}

function extractAssistantText(event) {
  const data = event?.data ?? event;
  if (!data) {
    return "";
  }
  if (typeof data.deltaContent === "string" && data.deltaContent) {
    return data.deltaContent;
  }
  if (typeof data.content === "string" && data.content) {
    return data.content;
  }
  if (Array.isArray(data.content)) {
    return data.content
      .filter((block) => block && block.type === "text" && block.text)
      .map((block) => block.text)
      .join("");
  }
  return "";
}

function longestText(...candidates) {
  let best = "";
  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.length > best.length) {
      best = candidate;
    }
  }
  return best;
}

async function lastAssistantMessageFromEvents(session) {
  try {
    const events = await session.getEvents();
    let last = "";
    for (const event of events) {
      if (event?.type === "assistant.message") {
        const text = extractAssistantText(event);
        if (text) {
          last = text;
        }
      }
    }
    return last;
  } catch (_) {
    return "";
  }
}

async function handleSend(cmd) {
  const session = await ensureSession(cmd);
  clearActiveHandlers();

  let streamedAssistant = "";
  let lastAssistantMessage = "";
  let streamedChars = 0;

  activeUnsubscribers.push(
    session.on("assistant.message_delta", (event) => {
      const text = extractAssistantText(event);
      if (!text) {
        return;
      }
      streamedChars += text.length;
      streamedAssistant += text;
      emit({ type: "assistant", requestId: cmd.id, text });
    })
  );

  activeUnsubscribers.push(
    session.on("assistant.message", (event) => {
      const text = extractAssistantText(event);
      if (!text) {
        return;
      }
      lastAssistantMessage = text;
      if (streamedChars === 0) {
        streamedAssistant = text;
        emit({ type: "assistant", requestId: cmd.id, text });
      }
    })
  );

  activeUnsubscribers.push(
    session.on("assistant.reasoning_delta", (event) => {
      const text = extractAssistantText(event);
      if (text) {
        emit({ type: "thinking", requestId: cmd.id, text });
      }
    })
  );

  activeUnsubscribers.push(
    session.on("assistant.reasoning", (event) => {
      const text = extractAssistantText(event);
      if (text) {
        emit({ type: "thinking", requestId: cmd.id, text });
      }
    })
  );

  activeUnsubscribers.push(
    session.on("tool.execution_start", (event) => {
      emit({
        type: "tool_call",
        requestId: cmd.id,
        name: event.data?.toolName ?? event.data?.name ?? "tool",
        status: "started",
        args: event.data?.arguments ?? null,
      });
    })
  );

  activeUnsubscribers.push(
    session.on("tool.execution_complete", (event) => {
      emit({
        type: "tool_call",
        requestId: cmd.id,
        name: event.data?.toolName ?? event.data?.name ?? "tool",
        status: "completed",
        args: null,
      });
    })
  );

  activeUnsubscribers.push(
    session.on("session.error", (event) => {
      const message = event.data?.message ?? event.data?.error ?? "Session error";
      emit({ type: "error", requestId: cmd.id, message });
    })
  );

  try {
    const finalEvent = await session.sendAndWait({ prompt: cmd.prompt });
    const finalFromWait = extractAssistantText(finalEvent);
    const fromEvents = await lastAssistantMessageFromEvents(session);
    const resultText = longestText(
      finalFromWait,
      lastAssistantMessage,
      streamedAssistant,
      fromEvents
    );

    emit({
      type: "result",
      requestId: cmd.id,
      status: "completed",
      text: resultText,
      sessionId: session.sessionId,
    });
  } finally {
    clearActiveHandlers();
  }
}

async function handleGhostComplete(cmd) {
  const text = await ghostComplete(cmd);
  emit({
    type: "result",
    requestId: cmd.id,
    status: "completed",
    text: text || "",
  });
}

async function handleGhostAuth(cmd) {
  await ghostAuth(cmd);
  emit({
    type: "ghost_authenticated",
    requestId: cmd.id,
  });
}

async function handleComplete(cmd) {
  const session = await ensureInlineSession(cmd);
  clearActiveHandlers();

  let streamed = "";
  activeUnsubscribers.push(
    session.on("assistant.message_delta", (event) => {
      const text = extractAssistantText(event);
      if (text) {
        streamed += text;
      }
    })
  );
  activeUnsubscribers.push(
    session.on("assistant.message", (event) => {
      const text = extractAssistantText(event);
      if (text && !streamed) {
        streamed = text;
      }
    })
  );

  try {
    const finalEvent = await session.sendAndWait({ prompt: cmd.prompt });
    const finalText = extractAssistantText(finalEvent);
    const text = longestText(finalText, streamed);
    emit({
      type: "result",
      requestId: cmd.id,
      status: "completed",
      text: text || "",
      sessionId: session.sessionId,
    });
  } finally {
    clearActiveHandlers();
  }
}

async function handleListModels(cmd) {
  await ensureClient(cmd);
  const models = await client.listModels();
  emit({
    type: "models",
    requestId: cmd.id,
    models: models.map((model) => ({
      id: model.id,
      name: model.name ?? model.id,
      description: model.description ?? "",
    })),
  });
}

async function handleValidate(cmd) {
  await ensureClient(cmd);
  await client.listModels();
  emit({ type: "validated", requestId: cmd.id });
}

async function handleCancel(cmd) {
  if (activeSession) {
    try {
      await activeSession.abort();
    } catch (_) {
    }
  }
  emit({ type: "cancelled", requestId: cmd.id });
}

async function handleShutdown(cmd) {
  clearActiveHandlers();
  try {
    await shutdownGhostLsp();
  } catch (_) {
  }
  if (inlineSession) {
    try {
      await inlineSession.disconnect();
    } catch (_) {
    }
    inlineSession = null;
  }
  if (activeSession) {
    try {
      await activeSession.disconnect();
    } catch (_) {
    }
    activeSession = null;
  }
  await disposeClient();
  emit({ type: "shutdown", requestId: cmd.id });
  process.exit(0);
}

rl.on("line", (line) => {
  if (!line.trim()) {
    return;
  }
  let cmd;
  try {
    cmd = JSON.parse(line);
  } catch (err) {
    emit({ type: "error", requestId: null, message: "Invalid JSON: " + err.message });
    return;
  }
  (async () => {
    try {
      switch (cmd.cmd) {
        case "send":
          await handleSend(cmd);
          break;
        case "complete":
          await handleComplete(cmd);
          break;
        case "ghostComplete":
          await handleGhostComplete(cmd);
          break;
        case "ghostAuth":
          await handleGhostAuth(cmd);
          break;
        case "listModels":
          await handleListModels(cmd);
          break;
        case "validate":
          await handleValidate(cmd);
          break;
        case "cancel":
          await handleCancel(cmd);
          break;
        case "shutdown":
          await handleShutdown(cmd);
          break;
        default:
          emit({
            type: "error",
            requestId: cmd.id ?? null,
            message: "Unknown command: " + cmd.cmd,
          });
      }
    } catch (err) {
      emit({
        type: "error",
        requestId: cmd.id ?? null,
        message: err?.message ?? String(err),
      });
    }
  })();
});

emit({ type: "ready" });
