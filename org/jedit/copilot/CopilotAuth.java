/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import org.gjt.sp.jedit.jEdit;

final class CopilotAuth {

    static final String AUTHENTICATED_PROPERTY = "copilot.authenticated";

    private CopilotAuth() {}

    static boolean isSignedIn() {
        return jEdit.getBooleanProperty(AUTHENTICATED_PROPERTY);
    }

    static void setSignedIn(boolean signedIn) {
        jEdit.setBooleanProperty(AUTHENTICATED_PROPERTY, signedIn);
    }

    static void clear() {
        jEdit.unsetProperty(AUTHENTICATED_PROPERTY);
        CopilotConfig.clearSession();
    }
}
