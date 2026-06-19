/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.util.Log;

/**
 * Built-in AI inline completion and assist settings integration.
 */
public class AiAssistPlugin extends EditPlugin {

    private static AiAssistPlugin instance;
    private AiInlineCompletionHub hub;

    public AiAssistPlugin() {
        instance = this;
    }

    public static AiAssistPlugin getInstance() {
        return instance;
    }

    @Override
    public void start() {
        Log.log(Log.MESSAGE, this, "AI assist integration starting...");
        hub = new AiInlineCompletionHub();
        hub.install();
    }

    @Override
    public void stop() {
        Log.log(Log.MESSAGE, this, "AI assist integration stopping...");
        if (hub != null) {
            hub.uninstall();
            hub = null;
        }
    }

    static void restartHub() {
        AiAssistPlugin plugin = getInstance();
        if (plugin == null || plugin.hub == null) {
            return;
        }
        plugin.hub.uninstall();
        plugin.hub = new AiInlineCompletionHub();
        plugin.hub.install();
    }
}
