import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const BRIDGE_DIR = path.dirname(fileURLToPath(import.meta.url));
const LS_SCRIPT = path.join(
  BRIDGE_DIR,
  "node_modules/@github/copilot-language-server/dist/language-server.js"
);

let client = null;

class CopilotGhostLspClient {
  constructor() {
    this.proc = null;
    this.buffer = Buffer.alloc(0);
    this.pending = new Map();
    this.nextId = 1;
    this.initialized = false;
    this.initPromise = null;
    this.openDocs = new Map();
    this.lastAuthKey = null;
    this.cwd = null;
    this.node = null;
    this.authenticated = false;
  }

  async isSignedIn() {
    try {
      const result = await this.request("checkStatus", {});
      const status = result?.status;
      return (
        status === "SignedIn"
        || status === "AlreadySignedIn"
        || status === "OK"
        || status === "MaybeOk"
      );
    } catch (_) {
      return false;
    }
  }

  async ensureAuthenticated(cmd) {
    if (this.authenticated || (await this.isSignedIn())) {
      this.authenticated = true;
      return;
    }
    if (!cmd.allowInteractiveAuth) {
      throw new Error("Not authenticated: Copilot ghost text requires LSP sign-in");
    }
    await this.signInInteractive(cmd);
    this.authenticated = true;
  }

  async signInInteractive(cmd) {
    const signIn = await this.request("signIn", {});
    const status = signIn?.status;
    if (
      status === "SignedIn"
      || status === "AlreadySignedIn"
      || status === "OK"
      || status === "MaybeOk"
    ) {
      return;
    }
    const userCode = signIn?.userCode;
    if (!userCode) {
      throw new Error("Copilot sign-in did not return a user code");
    }
    const command = signIn.command ?? {
      command: "github.copilot.finishDeviceFlow",
      arguments: [],
    };
    await this.request("workspace/executeCommand", {
      command: command.command,
      arguments: command.arguments ?? [],
    });
    await this.waitForSignedIn(120_000);
  }

  waitForSignedIn(timeoutMs) {
    const deadline = Date.now() + timeoutMs;
    return new Promise((resolve, reject) => {
      const poll = async () => {
        if (await this.isSignedIn()) {
          resolve();
          return;
        }
        if (Date.now() > deadline) {
          reject(new Error("Copilot sign-in timed out"));
          return;
        }
        setTimeout(poll, 1000);
      };
      poll();
    });
  }

  async ghostComplete(cmd) {
    await this.ensure(cmd);
    await this.ensureInitialized(cmd);
    await this.ensureAuthenticated(cmd);

    const uri = cmd.documentUri;
    const languageId = cmd.languageId || "plaintext";
    const text = cmd.documentText ?? "";
    let doc = this.openDocs.get(uri);
    if (!doc) {
      doc = { text: "", version: 0, languageId };
      this.openDocs.set(uri, doc);
    }
    if (doc.text !== text) {
      doc.version += 1;
      doc.text = text;
      doc.languageId = languageId;
      if (doc.version === 1) {
        this.notify("textDocument/didOpen", {
          textDocument: {
            uri,
            languageId,
            version: doc.version,
            text,
          },
        });
      } else {
        this.notify("textDocument/didChange", {
          textDocument: { uri, version: doc.version },
          contentChanges: [{ text }],
        });
      }
    }

    this.notify("textDocument/didFocus", {
      textDocument: { uri },
    });

    const result = await this.request("textDocument/inlineCompletion", {
      textDocument: { uri, version: doc.version },
      position: { line: cmd.line, character: cmd.character },
      context: { triggerKind: 2 },
      formattingOptions: {
        tabSize: cmd.tabSize || 4,
        insertSpaces: cmd.insertSpaces !== false,
      },
    });

    const items = result?.items ?? [];
    const insertText = items[0]?.insertText ?? "";
    return typeof insertText === "string" ? insertText : "";
  }

  async ensure(cmd) {
    const authKey = cmd.copilotLspToken
      ? `lsp-token:${cmd.copilotLspToken}`
      : "device-flow";
    if (this.proc && this.lastAuthKey !== authKey) {
      await this.shutdown();
    }
    if (!this.proc) {
      await this.start(cmd);
    }
  }

  async start(cmd) {
    this.lastAuthKey = cmd.copilotLspToken
      ? `lsp-token:${cmd.copilotLspToken}`
      : "device-flow";
    this.cwd = cmd.cwd;
    this.node = cmd.node || process.execPath;
    const env = { ...process.env };
    // The SDK GitHub token is not a Copilot LSP token. Only pass an explicit
    // LSP token; otherwise rely on ~/.config/github-copilot/apps.json or signIn.
    if (cmd.copilotLspToken) {
      env.GITHUB_COPILOT_TOKEN = cmd.copilotLspToken;
      env.GH_COPILOT_TOKEN = cmd.copilotLspToken;
    } else {
      delete env.GITHUB_COPILOT_TOKEN;
      delete env.GH_COPILOT_TOKEN;
      delete env.COPILOT_GITHUB_TOKEN;
    }

    this.proc = spawn(this.node, [LS_SCRIPT, "--stdio"], {
      cwd: cmd.cwd,
      env,
      stdio: ["pipe", "pipe", "pipe"],
    });
    this.buffer = Buffer.alloc(0);
    this.proc.stdout.on("data", (chunk) => this.onData(chunk));
    this.proc.stderr.on("data", () => {});
    this.proc.on("exit", () => {
      this.rejectAll(new Error("Copilot language server exited"));
      this.proc = null;
      this.initialized = false;
      this.initPromise = null;
      this.openDocs.clear();
    });

    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        reject(new Error("Copilot language server start timeout"));
      }, 60_000);
      const check = () => {
        if (this.proc && !this.proc.killed) {
          clearTimeout(timer);
          resolve();
          return;
        }
        setTimeout(check, 25);
      };
      check();
    });
  }

  async ensureInitialized(cmd) {
    if (this.initialized) {
      return;
    }
    if (this.initPromise) {
      return this.initPromise;
    }
    this.initPromise = this.doInitialize(cmd);
    try {
      await this.initPromise;
      this.initialized = true;
    } catch (err) {
      this.initPromise = null;
      throw err;
    }
  }

  async doInitialize(cmd) {
    const workspaceUri = cmd.workspaceUri || fileUri(cmd.cwd);
    const result = await this.request("initialize", {
      processId: process.pid,
      rootUri: workspaceUri,
      workspaceFolders: workspaceUri
        ? [{ uri: workspaceUri, name: path.basename(cmd.cwd || ".") }]
        : [],
      capabilities: {
        workspace: { workspaceFolders: true },
        textDocument: {
          inlineCompletion: {
            dynamicRegistration: false,
          },
        },
      },
      initializationOptions: {
        editorInfo: { name: "jEdit", version: "6.0" },
        editorPluginInfo: { name: "jEdit Copilot", version: "1.0.0" },
      },
    });
    this.serverCapabilities = result ?? {};
    this.notify("initialized", {});
    this.notify("workspace/didChangeConfiguration", {
      settings: {
        telemetry: { telemetryLevel: "all" },
      },
    });
    if (workspaceUri) {
      this.notify("workspace/didChangeWorkspaceFolders", {
        event: {
          added: [{ uri: workspaceUri, name: path.basename(cmd.cwd || ".") }],
          removed: [],
        },
      });
    }
  }

  request(method, params) {
    const id = this.nextId++;
    const message = { jsonrpc: "2.0", id, method, params };
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`LSP request timed out: ${method}`));
      }, 30_000);
      this.pending.set(id, {
        resolve: (value) => {
          clearTimeout(timer);
          resolve(value);
        },
        reject: (err) => {
          clearTimeout(timer);
          reject(err);
        },
      });
      this.writeMessage(message);
    });
  }

  notify(method, params) {
    this.writeMessage({ jsonrpc: "2.0", method, params });
  }

  writeMessage(message) {
    if (!this.proc?.stdin?.writable) {
      throw new Error("Copilot language server is not running");
    }
    const json = JSON.stringify(message);
    const header = `Content-Length: ${Buffer.byteLength(json, "utf8")}\r\n\r\n`;
    this.proc.stdin.write(header + json);
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (true) {
      const headerEnd = this.buffer.indexOf("\r\n\r\n");
      if (headerEnd < 0) {
        return;
      }
      const header = this.buffer.slice(0, headerEnd).toString("utf8");
      const match = /Content-Length:\s*(\d+)/i.exec(header);
      if (!match) {
        this.buffer = this.buffer.slice(headerEnd + 4);
        continue;
      }
      const length = Number.parseInt(match[1], 10);
      const start = headerEnd + 4;
      if (this.buffer.length < start + length) {
        return;
      }
      const body = this.buffer.slice(start, start + length).toString("utf8");
      this.buffer = this.buffer.slice(start + length);
      let message;
      try {
        message = JSON.parse(body);
      } catch {
        continue;
      }
      this.dispatch(message);
    }
  }

  dispatch(message) {
    if (message.id != null && this.pending.has(message.id)) {
      const handler = this.pending.get(message.id);
      this.pending.delete(message.id);
      if (message.error) {
        handler.reject(new Error(message.error.message || "LSP error"));
      } else {
        handler.resolve(message.result);
      }
      return;
    }
    if (message.method === "window/logMessage") {
      return;
    }
    if (message.method === "didChangeStatus") {
      if (message.params?.kind === "Normal") {
        this.authenticated = true;
      }
      return;
    }
    if (message.method === "client/registerCapability") {
      return;
    }
  }

  rejectAll(err) {
    for (const handler of this.pending.values()) {
      handler.reject(err);
    }
    this.pending.clear();
  }

  async shutdown() {
    this.rejectAll(new Error("Copilot language server shutting down"));
    if (this.proc) {
      try {
        if (this.initialized) {
          await this.request("shutdown", null);
          this.notify("exit", null);
        }
      } catch (_) {
      }
      this.proc.kill();
      this.proc = null;
    }
    this.initialized = false;
    this.initPromise = null;
    this.openDocs.clear();
    this.lastAuthKey = null;
    this.authenticated = false;
  }
}

function fileUri(filePath) {
  if (!filePath) {
    return null;
  }
  const normalized = filePath.replace(/\\/g, "/");
  if (/^[A-Za-z]:\//.test(normalized)) {
    return `file:///${normalized}`;
  }
  if (normalized.startsWith("/")) {
    return `file://${normalized}`;
  }
  return `file:///${normalized}`;
}

export async function ghostComplete(cmd) {
  if (!client) {
    client = new CopilotGhostLspClient();
  }
  return client.ghostComplete({ ...cmd, allowInteractiveAuth: false });
}

export async function ghostAuth(cmd) {
  if (!client) {
    client = new CopilotGhostLspClient();
  }
  await client.ensure(cmd);
  await client.ensureInitialized(cmd);
  await client.ensureAuthenticated({ ...cmd, allowInteractiveAuth: true });
}

export async function shutdownGhostLsp() {
  if (client) {
    await client.shutdown();
    client = null;
  }
}
