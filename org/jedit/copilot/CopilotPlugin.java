/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.View;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

/**
 * Built-in GitHub Copilot integration.
 */
public class CopilotPlugin extends EditPlugin {

    private static CopilotPlugin instance;

    public CopilotPlugin() {
        instance = this;
    }

    public static CopilotPlugin getInstance() {
        return instance;
    }

    @Override
    public void start() {
        Log.log(Log.MESSAGE, this, "GitHub Copilot integration starting...");
    }

    @Override
    public void stop() {
        Log.log(Log.MESSAGE, this, "GitHub Copilot integration stopping...");
    }

    public static void showCopilot(View view) {
        CopilotView.show(view);
    }

    public static void login(View view) {
        CopilotView copilotView = CopilotView.show(view);
        copilotView.promptLogin();
    }

    public static void logout(View view) {
        CopilotView copilotView = (CopilotView) view.getDockableWindowManager()
            .getDockableWindow(CopilotView.NAME);
        if (copilotView != null) {
            copilotView.promptLogout();
        } else {
            CopilotAuth.clear();
        }
    }

    public static boolean isSignedIn() {
        return CopilotAuth.isSignedIn();
    }

    public static String completeInline(String prompt) throws java.io.IOException {
        if (!CopilotAuth.isSignedIn()) {
            return "";
        }
        CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__inline__");
        String raw = bridge.complete(
            CopilotConfig.gitHubToken(),
            CopilotWorkspaceContext.defaultCwd(),
            CopilotConfig.modelId(),
            prompt);
        return raw != null ? raw : "";
    }

    public static CopilotGhostInlineResult ghostInline(String documentUri, String workspaceUri,
            String languageId, String documentText, int line, int character,
            int tabSize, boolean insertSpaces) throws java.io.IOException {
        if (!CopilotAuth.isSignedIn()) {
            return CopilotGhostInlineResult.textOnly("");
        }
        CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__inline__");
        return bridge.ghostComplete(
            CopilotWorkspaceContext.defaultCwd(),
            documentUri,
            workspaceUri,
            languageId,
            documentText,
            line,
            character,
            tabSize,
            insertSpaces);
    }

    public static void authenticateGhostLsp() {
        if (!CopilotAuth.isSignedIn()) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            long start = System.nanoTime();
            try {
                CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__inline__");
                bridge.ghostAuth(CopilotWorkspaceContext.defaultCwd());
                Log.log(Log.MESSAGE, CopilotPlugin.class,
                    "Copilot ghost LSP authenticated in "
                        + (System.nanoTime() - start) / 1_000_000L + "ms");
            } catch (Exception e) {
                Log.log(Log.DEBUG, CopilotPlugin.class,
                    "Copilot ghost LSP authentication failed: " + e.getMessage());
            }
        });
    }

    public static void warmInlineBridge() {
        if (!CopilotAuth.isSignedIn()) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            long start = System.nanoTime();
            try {
                CopilotLocalBridge bridge = CopilotLocalBridgePool.bridgeFor("__inline__");
                bridge.validate(
                    CopilotConfig.gitHubToken(),
                    CopilotWorkspaceContext.defaultCwd());
                Log.log(Log.MESSAGE, CopilotPlugin.class,
                    "Copilot inline bridge warmed in "
                        + (System.nanoTime() - start) / 1_000_000L + "ms");
                authenticateGhostLsp();
            } catch (Exception e) {
                Log.log(Log.DEBUG, CopilotPlugin.class,
                    "Copilot inline bridge warm-up failed: " + e.getMessage());
            }
        });
    }
}
