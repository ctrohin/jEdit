import { createInterface } from "node:readline";
import { Agent } from "@cursor/sdk";

const rl = createInterface({ input: process.stdin, terminal: false });

let agent = null;
let inlineAgent = null;
let activeRun = null;

function emit(event) {
  process.stdout.write(JSON.stringify(event) + "\n");
}

function assistantText(event) {
  if (!event.message?.content) {
    return "";
  }
  return event.message.content
    .filter((block) => block.type === "text")
    .map((block) => block.text)
    .join("");
}

function buildAgentOptions(cmd) {
  const options = {
    apiKey: cmd.apiKey,
    local: { cwd: cmd.cwd },
  };
  const modelId = cmd.modelId && cmd.modelId !== "auto" ? cmd.modelId : "auto";
  options.model = { id: modelId };
  if (cmd.mode) {
    options.mode = cmd.mode;
  }
  return options;
}

async function disposeAgent() {
  if (!agent) {
    return;
  }
  const current = agent;
  agent = null;
  if (typeof current[Symbol.asyncDispose] === "function") {
    await current[Symbol.asyncDispose]();
  } else if (typeof current.close === "function") {
    await current.close();
  }
}

async function ensureAgent(cmd) {
  const wantId = cmd.agentId || null;
  const currentId = agent?.agentId ?? null;
  if (agent && currentId === wantId) {
    return;
  }
  await disposeAgent();
  const options = buildAgentOptions(cmd);
  agent = wantId ? await Agent.resume(wantId, options) : await Agent.create(options);
  emit({ type: "agent", requestId: cmd.id, agentId: agent.agentId });
}

async function handleSend(cmd) {
  await ensureAgent(cmd);
  const sendOpts = {};
  if (cmd.mode) {
    sendOpts.mode = cmd.mode;
  }
  if (cmd.modelId && cmd.modelId !== "auto") {
    sendOpts.model = { id: cmd.modelId };
  }

  const run = await agent.send(cmd.prompt, sendOpts);
  activeRun = run;
  emit({
    type: "run",
    requestId: cmd.id,
    runId: run.id,
    agentId: agent.agentId,
  });

  try {
    for await (const event of run.stream()) {
      switch (event.type) {
        case "assistant": {
          const text = assistantText(event);
          if (text) {
            emit({ type: "assistant", requestId: cmd.id, text });
          }
          break;
        }
        case "thinking":
          if (event.text) {
            emit({ type: "thinking", requestId: cmd.id, text: event.text });
          }
          break;
        case "tool_call":
          emit({
            type: "tool_call",
            requestId: cmd.id,
            name: event.name,
            status: event.status,
            args: event.args ?? null,
          });
          break;
        case "status": {
          const status = event.message
            ? event.status + ": " + event.message
            : event.status;
          emit({ type: "status", requestId: cmd.id, status });
          break;
        }
        default:
          break;
      }
    }
    const result = await run.wait();
    emit({
      type: "result",
      requestId: cmd.id,
      status: result.status,
      text: result.result ?? "",
      runId: run.id,
      agentId: agent.agentId,
    });
  } finally {
    if (activeRun === run) {
      activeRun = null;
    }
  }
}

async function disposeInlineAgent() {
  if (!inlineAgent) {
    return;
  }
  const current = inlineAgent;
  inlineAgent = null;
  if (typeof current[Symbol.asyncDispose] === "function") {
    await current[Symbol.asyncDispose]();
  } else if (typeof current.close === "function") {
    await current.close();
  }
}

async function ensureInlineAgent(cmd) {
  if (inlineAgent) {
    return inlineAgent;
  }
  const options = buildAgentOptions({ ...cmd, mode: "plan" });
  inlineAgent = await Agent.create(options);
  return inlineAgent;
}

async function handleComplete(cmd) {
  const ephemeral = await ensureInlineAgent(cmd);
  const sendOpts = { mode: "plan" };
  if (cmd.modelId && cmd.modelId !== "auto") {
    sendOpts.model = { id: cmd.modelId };
  }
  const run = await ephemeral.send(cmd.prompt, sendOpts);
  let streamed = "";
  try {
    for await (const event of run.stream()) {
      if (event.type === "assistant") {
        const text = assistantText(event);
        if (text) {
          streamed += text;
        }
      }
    }
    const result = await run.wait();
    const text = streamed || result.result || "";
    emit({
      type: "result",
      requestId: cmd.id,
      status: result.status,
      text,
    });
  } finally {
    if (activeRun === run) {
      activeRun = null;
    }
  }
}

async function handleCancel(cmd) {
  if (activeRun) {
    try {
      if (activeRun.supports?.("cancel")) {
        await activeRun.cancel();
      }
    } catch (_) {
    }
    activeRun = null;
  }
  emit({ type: "cancelled", requestId: cmd.id });
}

async function handleShutdown(cmd) {
  if (activeRun) {
    try {
      if (activeRun.supports?.("cancel")) {
        await activeRun.cancel();
      }
    } catch (_) {
    }
    activeRun = null;
  }
  await disposeAgent();
  await disposeInlineAgent();
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
