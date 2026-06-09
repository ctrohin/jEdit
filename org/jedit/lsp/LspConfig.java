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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.jEdit;

public class LspConfig {

    private static final String COMMAND_PREFIX = "lsp.server.";
    private static final String COMMAND_SUFFIX = ".command";
    private static final String ENABLED_SUFFIX = ".enabled";

    /** @deprecated Use {@link #getServerCommand(String)} or {@link #isServerConfigured(String)} */
    @Deprecated
    public static final Map<String, String[]> SERVER_COMMANDS = new LinkedHashMap<>();

    private static final List<LspServerDefinition> SERVER_DEFINITIONS = List.of(
        server("python", "Python",
            new String[] {"pyright-langserver", "--stdio"},
            "pyright-langserver",
            new String[] {"python", "-m", "pip", "install", "--upgrade", "pyright"},
            new String[] {"python3", "-m", "pip", "install", "--upgrade", "pyright"},
            new String[] {"python3", "-m", "pip", "install", "--upgrade", "pyright"},
            "options.lsp-servers.python.install-help"),
        server("java", "Java",
            new String[] {"jdtls"},
            "jdtls",
            null,
            new String[] {"brew", "install", "jdtls"},
            new String[] {"sdk", "install", "jdtls"},
            "options.lsp-servers.java.install-help"),
        server("c", "C",
            new String[] {"clangd"},
            "clangd",
            new String[] {"winget", "install", "-e", "--id", "LLVM.LLVM",
                "--accept-package-agreements", "--accept-source-agreements"},
            new String[] {"brew", "install", "llvm"},
            new String[] {"sh", "-c",
                "command -v apt-get >/dev/null && sudo apt-get install -y clangd "
                + "|| command -v dnf >/dev/null && sudo dnf install -y clang-tools-extra "
                + "|| command -v pacman >/dev/null && sudo pacman -S --noconfirm clang"},
            "options.lsp-servers.c.install-help"),
        server("c++", "C++",
            new String[] {"clangd", "--log=verbose"},
            "clangd",
            new String[] {"winget", "install", "-e", "--id", "LLVM.LLVM",
                "--accept-package-agreements", "--accept-source-agreements"},
            new String[] {"brew", "install", "llvm"},
            new String[] {"sh", "-c",
                "command -v apt-get >/dev/null && sudo apt-get install -y clangd "
                + "|| command -v dnf >/dev/null && sudo dnf install -y clang-tools-extra "
                + "|| command -v pacman >/dev/null && sudo pacman -S --noconfirm clang"},
            "options.lsp-servers.cpp.install-help"),
        server("javascript", "JavaScript / TypeScript",
            new String[] {"typescript-language-server", "--stdio"},
            "typescript-language-server",
            new String[] {"npm", "install", "-g", "typescript", "typescript-language-server"},
            new String[] {"npm", "install", "-g", "typescript", "typescript-language-server"},
            new String[] {"npm", "install", "-g", "typescript", "typescript-language-server"},
            "options.lsp-servers.javascript.install-help"),
        server("dart", "Dart",
            new String[] {"dart", "language-server"},
            "dart",
            new String[] {"winget", "install", "-e", "--id", "Google.DartSDK",
                "--accept-package-agreements", "--accept-source-agreements"},
            new String[] {"brew", "install", "dart"},
            new String[] {"sh", "-c",
                "command -v apt-get >/dev/null && sudo apt-get install -y dart "
                + "|| command -v dnf >/dev/null && sudo dnf install -y dart "
                + "|| command -v pacman >/dev/null && sudo pacman -S --noconfirm dart"},
            "options.lsp-servers.dart.install-help"),
        server("rust", "Rust",
            new String[] {"rust-analyzer"},
            "rust-analyzer",
            new String[] {"rustup", "component", "add", "rust-analyzer"},
            new String[] {"rustup", "component", "add", "rust-analyzer"},
            new String[] {"rustup", "component", "add", "rust-analyzer"},
            "options.lsp-servers.rust.install-help")
    );

    private static final Map<String, LspServerDefinition> DEFINITIONS_BY_MODE =
        new LinkedHashMap<>();

    static {
        for (LspServerDefinition definition : SERVER_DEFINITIONS) {
            DEFINITIONS_BY_MODE.put(definition.getModeName(), definition);
            SERVER_COMMANDS.put(definition.getModeName(), definition.getDefaultCommand());
        }
        // Legacy aliases used by older configuration and docs.
        SERVER_COMMANDS.put("cpp", SERVER_COMMANDS.get("c++"));
        SERVER_COMMANDS.put("typescript", SERVER_COMMANDS.get("javascript"));
    }

    private LspConfig() {}

    public static List<LspServerDefinition> getServerDefinitions() {
        return SERVER_DEFINITIONS;
    }

    public static LspServerDefinition getDefinition(String modeName) {
        if (modeName == null) {
            return null;
        }
        LspServerDefinition definition = DEFINITIONS_BY_MODE.get(modeName);
        if (definition != null) {
            return definition;
        }
        return switch (modeName) {
            case "cpp" -> DEFINITIONS_BY_MODE.get("c++");
            case "typescript" -> DEFINITIONS_BY_MODE.get("javascript");
            default -> null;
        };
    }

    public static String resolveModeName(String modeName) {
        if (modeName == null) {
            return null;
        }
        if (DEFINITIONS_BY_MODE.containsKey(modeName)) {
            return modeName;
        }
        return switch (modeName) {
            case "cpp" -> "c++";
            case "typescript" -> "javascript";
            default -> null;
        };
    }

    public static boolean isServerConfigured(String modeName) {
        String resolved = resolveModeName(modeName);
        return resolved != null && isServerEnabled(resolved);
    }

    public static boolean isServerAvailable(final String modeName) {
        return isServerConfigured(modeName);
    }

    public static boolean isServerEnabled(String modeName) {
        String resolved = resolveModeName(modeName);
        if (resolved == null) {
            return false;
        }
        return jEdit.getBooleanProperty(enabledProperty(resolved), true);
    }

    public static void setServerEnabled(String modeName, boolean enabled) {
        String resolved = resolveModeName(modeName);
        if (resolved != null) {
            jEdit.setBooleanProperty(enabledProperty(resolved), enabled);
        }
    }

    public static String[] getServerCommand(String modeName) {
        String resolved = resolveModeName(modeName);
        if (resolved == null) {
            return null;
        }
        String configured = jEdit.getProperty(commandProperty(resolved));
        if (configured != null && !configured.isBlank()) {
            return LspServerInstaller.parseCommand(configured);
        }
        LspServerDefinition definition = DEFINITIONS_BY_MODE.get(resolved);
        return definition == null ? null : definition.getDefaultCommand();
    }

    public static String getServerCommandProperty(String modeName) {
        String resolved = resolveModeName(modeName);
        if (resolved == null) {
            return "";
        }
        String configured = jEdit.getProperty(commandProperty(resolved));
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        LspServerDefinition definition = DEFINITIONS_BY_MODE.get(resolved);
        return definition == null ? "" : commandToString(definition.getDefaultCommand());
    }

    public static void setServerCommand(String modeName, String commandLine) {
        String resolved = resolveModeName(modeName);
        if (resolved != null) {
            jEdit.setProperty(commandProperty(resolved), commandLine == null ? "" : commandLine);
        }
    }

    public static String commandToString(String[] command) {
        if (command == null || command.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String part : command) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (part.indexOf(' ') >= 0 || part.indexOf('\t') >= 0) {
                builder.append('"').append(part).append('"');
            } else {
                builder.append(part);
            }
        }
        return builder.toString();
    }

    static List<String> getConfiguredModeNames() {
        return new ArrayList<>(DEFINITIONS_BY_MODE.keySet());
    }

    private static LspServerDefinition server(String modeName,
                                              String displayName,
                                              String[] defaultCommand,
                                              String executable,
                                              String[] windowsInstall,
                                              String[] macInstall,
                                              String[] unixInstall,
                                              String installHelpProperty) {
        return new LspServerDefinition(modeName, displayName, defaultCommand, executable,
            windowsInstall, macInstall, unixInstall, installHelpProperty);
    }

    private static String commandProperty(String modeName) {
        return COMMAND_PREFIX + modeName + COMMAND_SUFFIX;
    }

    private static String enabledProperty(String modeName) {
        return COMMAND_PREFIX + modeName + ENABLED_SUFFIX;
    }
}
