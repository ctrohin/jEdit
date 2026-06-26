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

import java.util.List;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.MessageType;

import com.google.gson.annotations.SerializedName;

/**
 * Data types for Eclipse JDT LS protocol extensions (not part of standard LSP).
 */
final class JdtlsProtocol {

    private JdtlsProtocol() {}

    static final class StatusReport {
        @SerializedName("type")
        String type;

        @SerializedName("message")
        String message;
    }

    static final class EventNotification {
        @SerializedName("eventType")
        int eventType;

        @SerializedName("data")
        Object data;
    }

    static final class ProgressReport {
        @SerializedName("id")
        String id;

        @SerializedName("task")
        String task;

        @SerializedName("subTask")
        String subTask;

        @SerializedName("status")
        String status;

        @SerializedName("totalWork")
        int totalWork;

        @SerializedName("workDone")
        int workDone;

        @SerializedName("complete")
        boolean complete;
    }

    static final class ActionableNotification {
        @SerializedName("severity")
        MessageType severity;

        @SerializedName("message")
        String message;

        @SerializedName("data")
        Object data;

        @SerializedName("commands")
        List<Command> commands;
    }

    static final class GradleCompatibilityInfo {
        @SerializedName("projectUri")
        String projectUri;

        @SerializedName("message")
        String message;

        @SerializedName("highestJavaVersion")
        String highestJavaVersion;

        @SerializedName("recommendedGradleVersion")
        String recommendedGradleVersion;
    }

    static final class UpgradeGradleWrapperInfo {
        @SerializedName("projectUri")
        String projectUri;

        @SerializedName("message")
        String message;

        @SerializedName("recommendedGradleVersion")
        String recommendedGradleVersion;
    }

    static final class PreviewFeatureIssue {
        @SerializedName("uri")
        String uri;

        @SerializedName("message")
        String message;
    }

    enum EventType {
        ClasspathUpdated(100),
        ProjectsImported(200),
        ProjectsDeleted(210),
        IncompatibleGradleJdkIssue(300),
        UpgradeGradleWrapper(400),
        SourceInvalidated(500),
        PreviewFeaturesNotAllowed(600);

        private final int code;

        EventType(int code) {
            this.code = code;
        }

        static EventType fromCode(int code) {
            for (EventType type : values()) {
                if (type.code == code) {
                    return type;
                }
            }
            return null;
        }
    }

    enum ServiceStatus {
        Starting,
        Started,
        Message,
        Error,
        ServiceReady,
        ProjectStatus
    }
}
