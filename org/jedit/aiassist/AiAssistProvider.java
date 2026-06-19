/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import org.gjt.sp.jedit.jEdit;

public enum AiAssistProvider {

    AUTO,
    COPILOT,
    OFF;

    public String label() {
        return jEdit.getProperty("ai-assist.provider." + name().toLowerCase());
    }

    public static AiAssistProvider fromProperty() {
        String value = jEdit.getProperty(AiAssistConfig.PROVIDER_PROPERTY, "auto");
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toUpperCase();
        if ("CURSOR".equals(normalized)) {
            return COPILOT;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }
}
