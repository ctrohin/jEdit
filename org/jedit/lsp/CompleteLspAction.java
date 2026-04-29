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

import org.gjt.sp.jedit.EditAction;
import org.gjt.sp.jedit.View;

/**
 * jEdit action to trigger LSP-based code completions.
 *
 * This action can be bound to any keyboard shortcut (e.g., Ctrl+Space)
 * and provides intelligent, context-aware completions from language servers.
 *
 * Similar to the built-in "complete-word" action from CompleteWord class,
 * but uses LSP servers instead of buffer scanning.
 *
 * Usage:
 * 1. Bind this action to a keyboard shortcut in Global Options → Shortcuts
 2. When invoked, it requests completions from the LSP server
 *    for the current language and displays them in a popup
 *
 * Example binding:
 * - Action: "lsp-complete"
 * - Keyboard shortcut: Ctrl+Space
 *
 * @see org.jedit.lsp.LspCompletion
 * @see org.jedit.lsp.LspPlugin
 */
public class CompleteLspAction extends EditAction {

    public CompleteLspAction() {
        super("lsp-complete");
    }

    /**
     * Invoked when the action is triggered (e.g., keyboard shortcut pressed).
     *
     * @param view the current jEdit View
     */
    @Override
    public void invoke(View view) {
        if (view == null) {
            return;
        }

        // Delegate to LspPlugin which manages LSP clients
        LspPlugin.completeLsp(view);
    }

    /**
     * Determines whether this action should be enabled.
     *
     * The action is enabled if:
     * - The view is not null
     * - The active buffer is editable
     * - LSP support is available for the current language mode
     *
     * @param view the current View
     * @return true if the action can be invoked
     */
    public boolean isEnabled(View view) {
        if (view == null) {
            return false;
        }

        // Check if buffer is editable
        if (!view.getBuffer().isEditable()) {
            return false;
        }

        // Check if LSP is configured for this language mode
        final String modeName = view.getBuffer().getMode().getName();
        return LspConfig.isServerAvailable(modeName);
    }
}

