/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.gjt.sp.jedit.jEdit;

final class CopilotBridgeInstaller {

    private static final String RESOURCE_PREFIX = "/org/jedit/copilot/bridge/";
    private static final String PACKAGE_JSON = "package.json";
    private static final String BRIDGE_SCRIPT = "bridge.mjs";
    private static final String GHOST_LSP_SCRIPT = "ghost-lsp.mjs";
    private static final String SDK_MARKER = "node_modules/@github/copilot-sdk/package.json";
    private static final String LS_MARKER = "node_modules/@github/copilot-language-server/package.json";

    private CopilotBridgeInstaller() {}

    static Path bridgeDirectory() {
        String settings = jEdit.getSettingsDirectory();
        Path root = settings == null || settings.isBlank()
            ? Path.of("copilot-bridge")
            : Path.of(settings, "copilot", "bridge");
        return root.toAbsolutePath().normalize();
    }

    static void ensureInstalled() throws IOException {
        Path bridgeDir = bridgeDirectory();
        Files.createDirectories(bridgeDir);
        copyResource(PACKAGE_JSON, bridgeDir.resolve(PACKAGE_JSON), true);
        copyResource(BRIDGE_SCRIPT, bridgeDir.resolve(BRIDGE_SCRIPT), true);
        copyResource(GHOST_LSP_SCRIPT, bridgeDir.resolve(GHOST_LSP_SCRIPT), true);
        if (!Files.isRegularFile(bridgeDir.resolve(SDK_MARKER))
            || !Files.isRegularFile(bridgeDir.resolve(LS_MARKER))) {
            installDependencies(bridgeDir);
        }
    }

    private static void copyResource(String name, Path target, boolean always) throws IOException {
        try (InputStream in = CopilotBridgeInstaller.class.getResourceAsStream(RESOURCE_PREFIX + name)) {
            if (in == null) {
                throw new IOException("Missing bridge resource: " + name);
            }
            if (!always && Files.exists(target)) {
                return;
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void installDependencies(Path bridgeDir) throws IOException {
        String npm = npmExecutable();
        List<String> command = new ArrayList<>();
        command.add(npm);
        command.add("install");
        command.add("--omit=dev");
        command.add("--no-fund");
        command.add("--no-audit");
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(bridgeDir.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException(jEdit.getProperty("copilot.error.bridge-install-timeout"));
            }
            if (process.exitValue() != 0) {
                String message = output.isBlank()
                    ? jEdit.getProperty("copilot.error.bridge-install-failed")
                    : output.trim();
                throw new IOException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(jEdit.getProperty("copilot.error.bridge-install-interrupted"), e);
        }
    }

    private static String npmExecutable() {
        String configured = jEdit.getProperty(CopilotConfig.NPM_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return isWindows() ? "npm.cmd" : "npm";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }
}
