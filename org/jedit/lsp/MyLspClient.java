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

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;
import java.awt.Component;
import java.util.ArrayList;
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
        if (isJdtlsNonProjectNoise(params)) {
            Log.log(Log.DEBUG, MyLspClient.class, "[jdtls] " + params.getMessage());
            return;
        }
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
     * jdtls requests workspace configuration when the client advertises
     * {@code workspace.configuration} support.
     */
    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
        List<Object> result = new ArrayList<>();
        if (params != null && params.getItems() != null) {
            for (ConfigurationItem item : params.getItems()) {
                String section = item != null ? item.getSection() : null;
                result.add(JdtlsSupport.configurationForSection(section));
            }
        }
        return CompletableFuture.completedFuture(result);
    }

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

    /**
     * jdtls extension: startup / workspace status (not part of standard LSP).
     */
    @JsonNotification("language/status")
    public void languageStatus(JdtlsProtocol.StatusReport report) {
        JdtlsNotifications.handleStatus(report);
    }

    /**
     * jdtls extension: import/build progress during startup.
     */
    @JsonNotification("language/progressReport")
    public void languageProgressReport(JdtlsProtocol.ProgressReport report) {
        JdtlsNotifications.handleProgress(report);
    }

    /**
     * jdtls extension: internal events (classpath changes, project import, etc.).
     */
    @JsonNotification("language/eventNotification")
    public void languageEventNotification(JdtlsProtocol.EventNotification notification) {
        JdtlsNotifications.handleEvent(notification);
    }

    /**
     * jdtls extension: messages with optional client commands.
     */
    @JsonNotification("language/actionableNotification")
    public void languageActionableNotification(JdtlsProtocol.ActionableNotification notification) {
        JdtlsNotifications.handleActionable(notification);
    }

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(
        ShowMessageRequestParams requestParams) {
        CompletableFuture<MessageActionItem> result = new CompletableFuture<>();
        runOnEdtCompleting(result, () -> showMessageRequestOnEdt(requestParams));
        return result;
    }

    /**
     * Server applies a workspace edit through the client (e.g. after
     * {@code workspace/executeCommand} for a code action).
     */
    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        CompletableFuture<ApplyWorkspaceEditResponse> result = new CompletableFuture<>();
        WorkspaceEdit edit = params != null ? params.getEdit() : null;
        runOnEdtCompleting(result, () -> {
            boolean applied = LspWorkspaceEdits.apply(edit);
            return new ApplyWorkspaceEditResponse(applied);
        });
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

    /**
     * Runs work on the EDT without {@code invokeAndWait}, so LSP threads never
     * block waiting for the EDT while the EDT is waiting on the language server.
     */
    private static <T> void runOnEdtCompleting(CompletableFuture<T> result,
                                               java.util.concurrent.Callable<T> task) {
        Runnable edtTask = () -> {
            try {
                result.complete(task.call());
            } catch (Exception e) {
                Log.log(Log.ERROR, MyLspClient.class, "LSP client callback failed", e);
                result.complete(null);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            edtTask.run();
        } else {
            SwingUtilities.invokeLater(edtTask);
        }
    }

    private static boolean isJdtlsNonProjectNoise(MessageParams params) {
        if (params == null || params.getMessage() == null) {
            return false;
        }
        String message = params.getMessage().toLowerCase();
        return message.contains("non-project file")
            && message.contains("only syntax errors are reported");
    }
}
