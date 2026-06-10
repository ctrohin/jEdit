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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

final class BuildProcessRunner {

    private volatile Process process;
    private volatile boolean running;

    boolean isRunning() {
        return running;
    }

    void stop() {
        Process active = process;
        if (active != null && active.isAlive()) {
            active.destroyForcibly();
        }
        running = false;
    }

    void run(File workingDir, List<String> command, Consumer<String> onLine,
             Runnable onFinished) {
        run(workingDir, command, null, onLine, onFinished);
    }

    void run(File workingDir, List<String> command, Map<String, String> environment,
             Consumer<String> onLine, Runnable onFinished) {
        stop();
        running = true;
        ThreadUtilities.runInBackground(() -> {
            int exitCode = -1;
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                if (workingDir != null) {
                    builder.directory(workingDir);
                }
                if (environment != null && !environment.isEmpty()) {
                    builder.environment().putAll(environment);
                }
                builder.redirectErrorStream(true);
                process = builder.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), Charset.defaultCharset()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String captured = line;
                        SwingUtilities.invokeLater(() -> onLine.accept(captured));
                    }
                }
                exitCode = process.waitFor();
            } catch (Exception ex) {
                Log.log(Log.ERROR, BuildProcessRunner.class, "Build command failed", ex);
                String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                SwingUtilities.invokeLater(() -> onLine.accept("Error: " + message));
            } finally {
                process = null;
                running = false;
                int code = exitCode;
                SwingUtilities.invokeLater(() -> {
                    onLine.accept("--- exit code " + code + " ---");
                    if (onFinished != null) {
                        onFinished.run();
                    }
                });
            }
        });
    }
}
