/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.lsp;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;

/**
 * Per-project preferences for LSP install prompts.
 */
final class LspProjectPreferences {

    private static final String SUPPRESS_KEY = "lsp.project.skip-install.folders";

    private LspProjectPreferences() {}

    static boolean isInstallPromptSuppressed(String folder) {
        String canonical = canonicalFolder(folder);
        if (canonical == null) {
            return false;
        }
        String list = jEdit.getProperty(SUPPRESS_KEY, "");
        for (String line : list.split("\n")) {
            if (canonical.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    static void suppressInstallPrompt(String folder) {
        String canonical = canonicalFolder(folder);
        if (canonical == null || isInstallPromptSuppressed(folder)) {
            return;
        }
        String list = jEdit.getProperty(SUPPRESS_KEY, "");
        if (!list.isEmpty()) {
            list += "\n";
        }
        jEdit.setProperty(SUPPRESS_KEY, list + canonical);
    }

    private static String canonicalFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }
        return MiscUtilities.resolveSymlinks(folder);
    }
}
