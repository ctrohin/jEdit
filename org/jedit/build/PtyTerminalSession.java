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

final class PtyTerminalSession {

    private final JediTermWidget widget;
    private final PtyProcess process;
    private final PtyTerminalConnector connector;
    private final File workingDir;
    private final Consumer<PtyTerminalSession> onSessionEnded;
    private boolean started;
    private boolean ended;

    PtyTerminalSession(File workingDir, Consumer<PtyTerminalSession> onSessionEnded)
        throws IOException
    {
        this.workingDir = workingDir;
        this.onSessionEnded = onSessionEnded;
        widget = new JediTermWidget(new LookAndFeelTerminalSettingsProvider());

        String[] command = TerminalShell.defaultCommand();
        Map<String, String> environment = TerminalShell.environment();
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
