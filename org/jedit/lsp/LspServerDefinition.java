/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
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

/**
 * Metadata for a language-mode LSP server (command, install, documentation).
 */
final class LspServerDefinition {

    private final String modeName;
    private final String displayName;
    private final String[] defaultCommand;
    private final String executable;
    private final String[] windowsInstall;
    private final String[] macInstall;
    private final String[] unixInstall;
    private final String installHelpProperty;

    LspServerDefinition(String modeName,
                        String displayName,
                        String[] defaultCommand,
                        String executable,
                        String[] windowsInstall,
                        String[] macInstall,
                        String[] unixInstall,
                        String installHelpProperty) {
        this.modeName = modeName;
        this.displayName = displayName;
        this.defaultCommand = defaultCommand;
        this.executable = executable;
        this.windowsInstall = windowsInstall;
        this.macInstall = macInstall;
        this.unixInstall = unixInstall;
        this.installHelpProperty = installHelpProperty;
    }

    String getModeName() {
        return modeName;
    }

    String getDisplayName() {
        return displayName;
    }

    String[] getDefaultCommand() {
        return defaultCommand.clone();
    }

    String getExecutable() {
        return executable;
    }

    String[] getWindowsInstall() {
        return copyOrNull(windowsInstall);
    }

    String[] getMacInstall() {
        return copyOrNull(macInstall);
    }

    String[] getUnixInstall() {
        return copyOrNull(unixInstall);
    }

    String getInstallHelpProperty() {
        return installHelpProperty;
    }

    private static String[] copyOrNull(String[] command) {
        return command == null ? null : command.clone();
    }
}
