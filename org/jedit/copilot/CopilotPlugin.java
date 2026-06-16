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
}
