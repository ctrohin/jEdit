/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

enum CursorRuntime {
    LOCAL,
    CLOUD;

    String label() {
        return org.gjt.sp.jedit.jEdit.getProperty("cursor.runtime." + name().toLowerCase());
    }

    static boolean isCloudAgentId(String agentId) {
        return agentId != null && agentId.startsWith("bc-");
    }

    static String effectiveAgentId(String agentId, CursorRuntime runtime) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        boolean cloudId = isCloudAgentId(agentId);
        if (runtime == CLOUD && cloudId) {
            return agentId;
        }
        if (runtime == LOCAL && !cloudId) {
            return agentId;
        }
        return null;
    }
}
