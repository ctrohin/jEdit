/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import com.google.gson.JsonObject;

interface CursorRunListener {
    void onAssistantDelta(String text);

    void onThinkingDelta(String text);

    void onToolCall(String name, String status, JsonObject args);

    void onStatus(String status);

    void onResult(String text, String status);

    void onError(String message);
}
