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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageType;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.gui.StatusBar;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

/**
 * Handles Eclipse JDT LS client notifications the way full Java IDEs do:
 * status in the status bar, progress during import/build, and reactions to
 * project lifecycle events.
 */
final class JdtlsNotifications {

    private static final Gson GSON = new Gson();
    private static volatile boolean serviceReady;
    private static volatile String lastProgressMessage;

    private JdtlsNotifications() {}

    static void handleStatus(JdtlsProtocol.StatusReport report) {
        if (report == null || report.type == null) {
            return;
        }
        JdtlsProtocol.ServiceStatus status;
        try {
            status = JdtlsProtocol.ServiceStatus.valueOf(report.type);
        } catch (IllegalArgumentException ex) {
            Log.log(Log.DEBUG, JdtlsNotifications.class,
                "Unknown jdtls status type: " + report.type);
            return;
        }

        switch (status) {
            case Starting:
            case Message:
                if (report.message != null && !report.message.isBlank()) {
                    setPersistentStatus(report.message);
                }
                break;
            case ServiceReady:
                serviceReady = true;
                lastProgressMessage = null;
                setStatusBriefly(jEdit.getProperty("lsp.jdtls.status.ready"));
                break;
            case Started:
                serviceReady = true;
                setPersistentStatus(jEdit.getProperty("lsp.jdtls.status.started"));
                break;
            case Error:
                serviceReady = false;
                showError(report.message);
                setPersistentStatus(jEdit.getProperty("lsp.jdtls.status.error",
                    new String[] {nullToEmpty(report.message)}));
                break;
            case ProjectStatus:
                if ("WARNING".equalsIgnoreCase(report.message)) {
                    setPersistentStatus(jEdit.getProperty("lsp.jdtls.status.project-warning"));
                } else if ("OK".equalsIgnoreCase(report.message)) {
                    serviceReady = true;
                    setPersistentStatus(jEdit.getProperty("lsp.jdtls.status.project-ok"));
                }
                break;
            default:
                break;
        }
    }

    static void handleProgress(JdtlsProtocol.ProgressReport report) {
        if (report == null) {
            return;
        }
        if (report.complete) {
            if (serviceReady) {
                clearStartupStatus();
            }
            return;
        }
        String message = formatProgress(report);
        if (message == null || message.isBlank()) {
            return;
        }
        lastProgressMessage = message;
        setPersistentStatus(message);
    }

    static void handleEvent(JdtlsProtocol.EventNotification notification) {
        if (notification == null) {
            return;
        }
        JdtlsProtocol.EventType eventType =
            JdtlsProtocol.EventType.fromCode(notification.eventType);
        if (eventType == null) {
            Log.log(Log.DEBUG, JdtlsNotifications.class,
                "Unknown jdtls event type: " + notification.eventType);
            return;
        }

        switch (eventType) {
            case ClasspathUpdated:
                onClasspathUpdated(stringValue(notification.data));
                break;
            case ProjectsImported:
                onProjectsImported(stringList(notification.data));
                break;
            case ProjectsDeleted:
                onProjectsDeleted(stringList(notification.data));
                break;
            case IncompatibleGradleJdkIssue:
                showGradleCompatibilityIssue(
                    GSON.fromJson(GSON.toJsonTree(notification.data),
                        JdtlsProtocol.GradleCompatibilityInfo.class));
                break;
            case UpgradeGradleWrapper:
                showUpgradeGradleWrapper(
                    GSON.fromJson(GSON.toJsonTree(notification.data),
                        JdtlsProtocol.UpgradeGradleWrapperInfo.class));
                break;
            case SourceInvalidated:
                onSourceInvalidated(notification.data);
                break;
            case PreviewFeaturesNotAllowed:
                showPreviewFeatureIssues(notification.data);
                break;
            default:
                break;
        }
    }

    static void handleActionable(JdtlsProtocol.ActionableNotification notification) {
        if (notification == null || notification.message == null) {
            return;
        }
        runOnEdt(() -> showActionableOnEdt(notification));
    }

    static void resetSession() {
        serviceReady = false;
        lastProgressMessage = null;
    }

    private static void onClasspathUpdated(String projectUri) {
        if (projectUri == null || projectUri.isBlank()) {
            return;
        }
        GenericLspClient client = javaClient();
        if (client == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            JdtlsSupport.requestProjectConfigurationUpdate(client, projectUri);
            JdtlsSupport.pushConfiguration(client.getServer());
        });
        setStatusBriefly(jEdit.getProperty("lsp.jdtls.event.classpath-updated",
            new String[] {LspDocumentUri.uriToPath(projectUri)}));
    }

    private static void onProjectsImported(List<String> projectUris) {
        if (projectUris == null || projectUris.isEmpty()) {
            return;
        }
        String[] paths = projectUris.stream()
            .map(LspDocumentUri::uriToPath)
            .toArray(String[]::new);
        setStatusBriefly(jEdit.getProperty("lsp.jdtls.event.projects-imported",
            new String[] {Integer.toString(paths.length), String.join(", ", paths)}));
    }

    private static void onProjectsDeleted(List<String> projectUris) {
        if (projectUris == null || projectUris.isEmpty()) {
            return;
        }
        setStatusBriefly(jEdit.getProperty("lsp.jdtls.event.projects-deleted",
            new String[] {Integer.toString(projectUris.size())}));
    }

    private static void onSourceInvalidated(Object data) {
        int count = 0;
        if (data instanceof Map<?, ?> map && map.containsKey("affectedRootPaths")) {
            Object roots = map.get("affectedRootPaths");
            if (roots instanceof Map<?, ?> rootMap) {
                count = rootMap.size();
            }
        }
        if (count > 0) {
            setStatusBriefly(jEdit.getProperty("lsp.jdtls.event.sources-attached",
                new String[] {Integer.toString(count)}));
        }
    }

    private static void showGradleCompatibilityIssue(
            JdtlsProtocol.GradleCompatibilityInfo info) {
        if (info == null || info.message == null) {
            return;
        }
        runOnEdt(() -> {
            View view = jEdit.getActiveView();
            JOptionPane.showMessageDialog(
                view,
                info.message,
                jEdit.getProperty("lsp.jdtls.gradle-compat.title"),
                JOptionPane.WARNING_MESSAGE);
        });
    }

    private static void showUpgradeGradleWrapper(
            JdtlsProtocol.UpgradeGradleWrapperInfo info) {
        if (info == null || info.message == null) {
            return;
        }
        runOnEdt(() -> {
            View view = jEdit.getActiveView();
            String upgradeLabel = jEdit.getProperty("lsp.jdtls.gradle-upgrade.action",
                new String[] {nullToEmpty(info.recommendedGradleVersion)});
            int choice = JOptionPane.showOptionDialog(
                view,
                info.message,
                jEdit.getProperty("lsp.jdtls.gradle-upgrade.title"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[] {upgradeLabel, jEdit.getProperty("common.cancel")},
                upgradeLabel);
            if (choice == 0 && info.projectUri != null) {
                requestGradleUpgrade(javaClient(), info.projectUri, info.recommendedGradleVersion);
            }
        });
    }

    private static void showPreviewFeatureIssues(Object data) {
        List<JdtlsProtocol.PreviewFeatureIssue> issues = new ArrayList<>();
        JsonElement element = GSON.toJsonTree(data);
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                JdtlsProtocol.PreviewFeatureIssue issue =
                    GSON.fromJson(item, JdtlsProtocol.PreviewFeatureIssue.class);
                if (issue != null && issue.message != null) {
                    issues.add(issue);
                }
            }
        }
        if (issues.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder();
        for (JdtlsProtocol.PreviewFeatureIssue issue : issues) {
            if (body.length() > 0) {
                body.append('\n');
            }
            if (issue.uri != null) {
                body.append(LspDocumentUri.uriToPath(issue.uri)).append(": ");
            }
            body.append(issue.message);
        }
        runOnEdt(() -> {
            View view = jEdit.getActiveView();
            JOptionPane.showMessageDialog(
                view,
                body.toString(),
                jEdit.getProperty("lsp.jdtls.preview-features.title"),
                JOptionPane.WARNING_MESSAGE);
        });
    }

    private static void showActionableOnEdt(JdtlsProtocol.ActionableNotification notification) {
        View view = jEdit.getActiveView();
        List<Command> commands = notification.commands;
        if (commands == null || commands.isEmpty()) {
            showMessage(notification.severity, notification.message, view);
            return;
        }

        String[] options = new String[commands.size()];
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            options[i] = command != null && command.getTitle() != null
                ? command.getTitle()
                : jEdit.getProperty("lsp.jdtls.action.run");
        }

        int choice = JOptionPane.showOptionDialog(
            view,
            notification.message,
            dialogTitle(notification.severity),
            JOptionPane.DEFAULT_OPTION,
            messageType(notification.severity),
            null,
            options,
            options[0]);

        if (choice < 0 || choice >= commands.size()) {
            return;
        }
        Command selected = commands.get(choice);
        if (selected == null || selected.getCommand() == null) {
            return;
        }
        executeClientCommand(javaClient(), selected);
    }

    private static void showMessage(MessageType severity, String message, View view) {
        JOptionPane.showMessageDialog(
            view,
            message,
            dialogTitle(severity),
            messageType(severity));
    }

    private static void showError(String message) {
        runOnEdt(() -> {
            View view = jEdit.getActiveView();
            JOptionPane.showMessageDialog(
                view,
                message != null ? message : jEdit.getProperty("lsp.jdtls.status.error-unknown"),
                jEdit.getProperty("lsp.jdtls.status.error-title"),
                JOptionPane.ERROR_MESSAGE);
        });
    }

    private static void requestGradleUpgrade(GenericLspClient client,
                                             String projectUri,
                                             String version) {
        if (client == null || projectUri == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            try {
                ExecuteCommandParams params = new ExecuteCommandParams(
                    "java.project.upgradeGradle",
                    List.of(projectUri, version));
                client.getServer().getWorkspaceService().executeCommand(params);
                setStatusBriefly(jEdit.getProperty("lsp.jdtls.gradle-upgrade.started"));
            } catch (Exception ex) {
                Log.log(Log.WARNING, JdtlsNotifications.class,
                    "Failed to upgrade Gradle wrapper", ex);
            }
        });
    }

    private static void executeClientCommand(GenericLspClient client, Command command) {
        if (client == null || command.getCommand() == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> {
            try {
                ExecuteCommandParams params = new ExecuteCommandParams(
                    command.getCommand(),
                    command.getArguments() != null ? command.getArguments() : List.of());
                client.getServer().getWorkspaceService().executeCommand(params);
            } catch (Exception ex) {
                Log.log(Log.WARNING, JdtlsNotifications.class,
                    "Failed to run jdtls command: " + command.getCommand(), ex);
            }
        });
    }

    private static GenericLspClient javaClient() {
        LspPlugin plugin = LspPlugin.getInstance();
        return plugin != null ? plugin.clients.get("java") : null;
    }

    private static String formatProgress(JdtlsProtocol.ProgressReport report) {
        StringBuilder message = new StringBuilder();
        if (report.task != null && !report.task.isBlank()) {
            message.append(report.task);
        }
        if (report.subTask != null && !report.subTask.isBlank()) {
            if (message.length() > 0) {
                message.append(" — ");
            }
            message.append(report.subTask);
        } else if (report.status != null && !report.status.isBlank()) {
            if (message.length() > 0) {
                message.append(" — ");
            }
            message.append(report.status);
        }
        if (report.totalWork > 0) {
            if (message.length() > 0) {
                message.append(' ');
            }
            message.append('(').append(report.workDone).append('/')
                .append(report.totalWork).append(')');
        }
        return message.toString();
    }

    private static void setPersistentStatus(String message) {
        runOnEdt(() -> forEachStatusBar(status -> status.setMessage(message)));
    }

    private static void setStatusBriefly(String message) {
        runOnEdt(() -> forEachStatusBar(status -> status.setMessageAndClear(message)));
    }

    private static void clearStartupStatus() {
        if (lastProgressMessage == null) {
            return;
        }
        lastProgressMessage = null;
        runOnEdt(() -> forEachStatusBar(status -> status.setMessage(null)));
    }

    private static void forEachStatusBar(java.util.function.Consumer<StatusBar> action) {
        View[] views = jEdit.getViews();
        if (views.length == 0) {
            return;
        }
        for (View view : views) {
            if (view != null && view.getStatus() != null) {
                action.accept(view.getStatus());
            }
        }
    }

    private static String stringValue(Object data) {
        if (data == null) {
            return null;
        }
        if (data instanceof String text) {
            return text;
        }
        return data.toString();
    }

    private static List<String> stringList(Object data) {
        if (data == null) {
            return List.of();
        }
        JsonElement element = GSON.toJsonTree(data);
        if (!element.isJsonArray()) {
            String single = stringValue(data);
            return single != null ? List.of(single) : List.of();
        }
        return GSON.fromJson(element, new TypeToken<List<String>>() {}.getType());
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String dialogTitle(MessageType severity) {
        if (severity == null) {
            return jEdit.getProperty("lsp.jdtls.dialog.title");
        }
        switch (severity) {
            case Error:
                return jEdit.getProperty("lsp.jdtls.dialog.error");
            case Warning:
                return jEdit.getProperty("lsp.jdtls.dialog.warning");
            case Info:
                return jEdit.getProperty("lsp.jdtls.dialog.info");
            case Log:
            default:
                return jEdit.getProperty("lsp.jdtls.dialog.title");
        }
    }

    private static int messageType(MessageType severity) {
        if (severity == null) {
            return JOptionPane.INFORMATION_MESSAGE;
        }
        switch (severity) {
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
