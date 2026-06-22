/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import org.gjt.sp.jedit.jEdit;

public final class AiAssistConfig {

    static final String PROVIDER_PROPERTY = "ai-assist.provider";
    static final String AUTOMATIC_PROPERTY = "ai-assist.inline.automatic";
    static final String IDLE_DELAY_PROPERTY = "ai-assist.inline.idle-ms";

    private static final int DEFAULT_IDLE_MS = 1200;

    private AiAssistConfig() {}

    public static boolean inlineAutomatic() {
        return jEdit.getBooleanProperty(AUTOMATIC_PROPERTY, false);
    }

    public static void setInlineAutomatic(boolean automatic) {
        jEdit.setBooleanProperty(AUTOMATIC_PROPERTY, automatic);
    }

    public static AiAssistProvider provider() {
        return AiAssistProvider.fromProperty();
    }

    public static void setProvider(AiAssistProvider provider) {
        if (provider == null || provider == AiAssistProvider.AUTO) {
            jEdit.setProperty(PROVIDER_PROPERTY, AiAssistProvider.AUTO.name().toLowerCase());
        } else {
            jEdit.setProperty(PROVIDER_PROPERTY, provider.name().toLowerCase());
        }
    }

    public static int idleDelayMs() {
        return Math.max(300, jEdit.getIntegerProperty(IDLE_DELAY_PROPERTY, DEFAULT_IDLE_MS));
    }

    public static void setIdleDelayMs(int delayMs) {
        jEdit.setIntegerProperty(IDLE_DELAY_PROPERTY, Math.max(300, delayMs));
    }
}
