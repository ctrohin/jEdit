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

import java.util.HashMap;
import java.util.Map;

public class LspConfig {
    public static final Map<String, String[]> SERVER_COMMANDS = new HashMap<>();

    static {
        // Python: Requires 'pyright' installed via npm/pip
        SERVER_COMMANDS.put("python", new String[]{"pyright-langserver", "--stdio"});

        // Java: Requires Eclipse JDT.LS executable
        SERVER_COMMANDS.put("java", new String[]{"jdtls"});

        // C/C++: Requires 'clangd'
        SERVER_COMMANDS.put("cpp", new String[]{"clangd", "--log=verbose"});
        SERVER_COMMANDS.put("c", new String[]{"clangd"});

        // TypeScript/JS: Requires 'typescript-language-server'
        SERVER_COMMANDS.put("typescript", new String[]{"typescript-language-server", "--stdio"});

        // Dart: Requires 'dart' SDK installed
        SERVER_COMMANDS.put("dart", new String[]{"dart", "language-server"});

        // Rust: Requires 'rust-analyzer'
        SERVER_COMMANDS.put("rust", new String[]{"rust-analyzer"});
    }
}