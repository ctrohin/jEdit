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
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

public class MyLspClient implements LanguageClient {

    @Override
    public void logMessage(MessageParams params) {
        System.out.println("[SERVER LOG] " + params.getMessage());
    }

    @Override
    public void showMessage(MessageParams params) {
        runOnEdt(() -> showMessageOnEdt(params));
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        if (diagnostics == null) {
            return;
        }
        LspDiagnosticsHub.getInstance().setDiagnostics(
            diagnostics.getUri(),
            diagnostics.getDiagnostics());
    }

    @Override
    public void telemetryEvent(Object object) {}

    /**
     * Dart dynamically registers capabilities (e.g. rename, progress) after init.
     * Accept and ignore — jEdit does not use dynamic registration.
     */
    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Server may report rename/analysis progress when {@code workDoneProgress}
     * is advertised. Acknowledge creation; progress notifications are ignored.
     */
    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void notifyProgress(ProgressParams params) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
        ShowMessageRequestParams requestParams) {
        CompletableFuture<MessageActionItem> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                result.complete(showMessageRequestOnEdt(requestParams));
            } catch (Exception e) {
                Log.log(Log.ERROR, MyLspClient.class, "window/showMessageRequest failed", e);
                result.complete(null);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.log(Log.ERROR, MyLspClient.class, "window/showMessageRequest interrupted", e);
                result.complete(null);
            } catch (InvocationTargetException e) {
                Log.log(Log.ERROR, MyLspClient.class, "window/showMessageRequest failed",
                    e.getCause());
                result.complete(null);
            }
        }
        return result;
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
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.log(Log.ERROR, MyLspClient.class, "workspace/applyEdit interrupted", e);
                result.complete(new ApplyWorkspaceEditResponse(false));
            } catch (InvocationTargetException e) {
                Log.log(Log.ERROR, MyLspClient.class, "workspace/applyEdit failed", e.getCause());
                result.complete(new ApplyWorkspaceEditResponse(false));
            }
        }
        return result;
    }

    private static void showMessageOnEdt(MessageParams params) {
        if (params == null) {
            return;
        }
        Component parent = getDialogParent();
        JOptionPane.showMessageDialog(
            parent,
            params.getMessage(),
            dialogTitle(params.getType()),
            toOptionPaneMessageType(params.getType()));
    }

    private static MessageActionItem showMessageRequestOnEdt(ShowMessageRequestParams params) {
        if (params == null) {
            return null;
        }

        Component parent = getDialogParent();
        List<MessageActionItem> actions = params.getActions();
        int messageType = toOptionPaneMessageType(params.getType());
        String title = dialogTitle(params.getType());

        if (actions == null || actions.isEmpty()) {
            JOptionPane.showMessageDialog(parent, params.getMessage(), title, messageType);
            return null;
        }

        String[] options = new String[actions.size()];
        for (int i = 0; i < actions.size(); i++) {
            MessageActionItem action = actions.get(i);
            options[i] = action != null ? action.getTitle() : "";
        }

        int choice = JOptionPane.showOptionDialog(
            parent,
            params.getMessage(),
            title,
            JOptionPane.DEFAULT_OPTION,
            messageType,
            null,
            options,
            options[0]);

        if (choice < 0 || choice >= actions.size()) {
            return null;
        }
        return actions.get(choice);
    }

    private static Component getDialogParent() {
        View view = jEdit.getActiveView();
        return view != null ? view : null;
    }

    private static String dialogTitle(MessageType type) {
        if (type == null) {
            return "Language Server";
        }
        switch (type) {
            case Error:
                return "Error";
            case Warning:
                return "Warning";
            case Info:
                return "Information";
            case Log:
            default:
                return "Language Server";
        }
    }

    private static int toOptionPaneMessageType(MessageType type) {
        if (type == null) {
            return JOptionPane.INFORMATION_MESSAGE;
        }
        switch (type) {
            case Error:
                return JOptionPane.ERROR_MESSAGE;
            case Warning:
                return JOptionPane.WARNING_MESSAGE;
            case Info:
            case Log:
            default:
                return JOptionPane.INFORMATION_MESSAGE;
        }
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
