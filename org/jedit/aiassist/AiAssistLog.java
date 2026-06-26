/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import org.gjt.sp.util.Log;

final class AiAssistLog {

    private AiAssistLog() {}

    static void message(String text) {
        Log.log(Log.MESSAGE, AiAssistPlugin.class, text);
    }

    static void debug(String text) {
        Log.log(Log.DEBUG, AiAssistPlugin.class, text);
    }

    static void warning(String text) {
        Log.log(Log.WARNING, AiAssistPlugin.class, text);
    }

    static void warning(String text, Throwable t) {
        Log.log(Log.WARNING, AiAssistPlugin.class, text, t);
    }
}
