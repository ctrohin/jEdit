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

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

/**
 * Opens the LSP completion popup when the user types a mode-specific trigger character.
 */
final class LspCompletionTriggers {

    private LspCompletionTriggers() {}

    /**
     * Called after text is inserted into an LSP-managed buffer.
     *
     * @param buffer the buffer that changed
     * @param offset start offset of the insertion
     * @param length length of the inserted text
     */
    static void onTextInserted(Buffer buffer, int offset, int length) {
        if (buffer == null || !buffer.isEditable() || buffer.isLoading()
            || length != 1 || !jEdit.getBooleanProperty("lsp.completion.autoTrigger", true)) {
            return;
        }

        int caret = offset + length;
        char ch = buffer.getText(offset, 1).charAt(0);
        if (ch == '\n' || ch == '\r' || ch == '\t') {
            return;
        }

        String modeName = LspPlugin.resolveLspMode(buffer);
        if (!LspConfig.isServerConfigured(modeName)) {
            return;
        }

        String triggerCharacter = LspCompletionTriggerCharacters.resolveTrigger(
            modeName, buffer.getText(0, caret), caret, ch);
        if (triggerCharacter == null) {
            return;
        }

        View view = findViewForBuffer(buffer);
        if (view == null) {
            return;
        }

        final String trigger = triggerCharacter;
        LspAsync.runOffEdt(() -> {
            GenericLspClient client = LspPlugin.getExistingClientForBuffer(buffer);
            if (client == null || !client.hasActiveSession() || !client.isAlive()) {
                return;
            }
            LspAsync.runOnEdt(() -> {
                if (view.getBuffer() != buffer || view.getTextArea().getCaretPosition() != caret) {
                    return;
                }
                LspCompletion.completeLspOnTrigger(view, client, trigger);
            });
        });
    }

    private static View findViewForBuffer(Buffer buffer) {
        View active = jEdit.getActiveView();
        if (active != null && active.getBuffer() == buffer) {
            return active;
        }
        for (View view : jEdit.getViews()) {
            for (EditPane editPane : view.getEditPanes()) {
                if (editPane.getBuffer() == buffer) {
                    return view;
                }
            }
        }
        return null;
    }
}
