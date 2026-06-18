/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.util.List;
import java.util.Map;

final class RunConfigurationOverrides {

    final String vmOptions;
    final String additionalProperties;

    private RunConfigurationOverrides(String vmOptions, String additionalProperties) {
        this.vmOptions = vmOptions != null ? vmOptions : "";
        this.additionalProperties = additionalProperties != null ? additionalProperties : "";
    }

    static final RunConfigurationOverrides NONE = new RunConfigurationOverrides("", "");

    static RunConfigurationOverrides from(WorkspaceRunConfiguration configuration) {
        if (configuration == null) {
            return NONE;
        }
        return new RunConfigurationOverrides(
            configuration.vmOptions, configuration.additionalProperties);
    }

    void mergeVmOptions(Map<String, String> environment, String envKey, String projectOpts) {
        String merged = ShellCommands.mergeSpaceSeparated(projectOpts, vmOptions);
        if (!ShellCommands.isBlank(merged)) {
            environment.put(envKey, merged.trim());
        }
    }

    void appendSystemProperties(List<String> args) {
        ShellCommands.appendPropertyLines(args, additionalProperties);
    }

    void appendEnvironmentVariables(Map<String, String> environment) {
        ShellCommands.appendEnvironmentLines(environment, additionalProperties);
    }

    void appendVmOptionTokens(List<String> args) {
        ShellCommands.appendTokens(args, vmOptions);
    }
}
