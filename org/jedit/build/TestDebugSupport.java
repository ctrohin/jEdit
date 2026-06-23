/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.Map;

import org.gjt.sp.jedit.jEdit;

final class TestDebugSupport {

    private static final String DEFAULT_JDWP =
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005";

    private TestDebugSupport() {}

    static void applyDebugOptions(Map<String, String> environment, ProjectKind kind,
                                  String existingProjectOpts, String envKey) {
        String jdwp = jEdit.getProperty("test-debug.vm-options", DEFAULT_JDWP).trim();
        if (jdwp.isEmpty()) {
            jdwp = DEFAULT_JDWP;
        }
        String merged = ShellCommands.mergeSpaceSeparated(existingProjectOpts, jdwp);
        if (!ShellCommands.isBlank(merged)) {
            environment.put(envKey, merged.trim());
        }
    }

    static String debugCaptionSuffix() {
        return jEdit.getProperty("test-debug.caption-suffix", " [debug]");
    }
}
