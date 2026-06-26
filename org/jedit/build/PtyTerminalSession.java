/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.jediterm.terminal.ui.JediTermWidget;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.jedit.View;

final class PtyTerminalSession {

    private final JediTermWidget widget;
    private final PtyProcess process;
    private final PtyTerminalConnector connector;
    private final File workingDir;
    private final Consumer<PtyTerminalSession> onSessionEnded;
    private boolean started;
    private boolean ended;

    PtyTerminalSession(View view, TerminalSessionConfig config,
                       Consumer<PtyTerminalSession> onSessionEnded)
        throws IOException
    {
        this.workingDir = config.workingDir;
        this.onSessionEnded = onSessionEnded;
        widget = new JediTermWidget(new LookAndFeelTerminalSettingsProvider());
        widget.addHyperlinkFilter(new TerminalHyperlinkFilter(
            view, ProjectRoots.workspaceRoot()));

        String[] command = TerminalShell.defaultCommand();
        Map<String, String> environment = config.environment;
        PtyProcessBuilder builder = new PtyProcessBuilder()
            .setCommand(command)
            .setEnvironment(environment);
        if (workingDir != null) {
            builder.setDirectory(workingDir.getAbsolutePath());
        }
        if (OperatingSystem.isWindows()) {
            builder.setUseWinConPty(true);
        }
        process = builder.start();
        List<String> commandLine = Arrays.asList(command);
        connector = new PtyTerminalConnector(process, StandardCharsets.UTF_8, commandLine);
        widget.setTtyConnector(connector);
        widget.addListener(ignored -> markEnded());
    }

    void start() {
        if (!started) {
            widget.start();
            started = true;
        }
    }

    void close() {
        if (!ended) {
            ended = true;
            widget.close();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    boolean isRunning() {
        return started && !ended && process.isAlive();
    }

    JediTermWidget widget() {
        return widget;
    }

    File workingDir() {
        return workingDir;
    }

    String baseTitle() {
        return TerminalShell.shellName();
    }

    private void markEnded() {
        if (ended) {
            return;
        }
        ended = true;
        if (onSessionEnded != null) {
            onSessionEnded.accept(this);
        }
    }
}
