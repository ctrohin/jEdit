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

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import java.util.concurrent.CompletableFuture;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.Log;

public class MyLspClient implements LanguageClient {

    // The interface uses MessageParams here, not LogMessageParams
    @Override
    public void logMessage(MessageParams params) {
        System.out.println("[SERVER LOG] " + params.getMessage());
    }

    @Override
    public void showMessage(MessageParams params) {
        System.out.println("[SERVER MESSAGE] " + params.getType() + ": " + params.getMessage());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        diagnostics.getDiagnostics().forEach(d ->
            System.out.println("[DIAGNOSTIC] " + d.getMessage())
        );
    }

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Server applies a workspace edit through the client (e.g. after
     * {@code workspace/executeCommand} for a code action).
     */
    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        CompletableFuture<ApplyWorkspaceEditResponse> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                WorkspaceEdit edit = params != null ? params.getEdit() : null;
                boolean applied = LspWorkspaceEdits.apply(edit);
                ApplyWorkspaceEditResponse response = new ApplyWorkspaceEditResponse(applied);
                result.complete(response);
            } catch (Exception e) {
                Log.log(Log.ERROR, MyLspClient.class, "workspace/applyEdit failed", e);
                result.complete(new ApplyWorkspaceEditResponse(false));
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
        return result;
    }
}