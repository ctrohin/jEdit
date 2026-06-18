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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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

    void run(File workingDir, List<String> command,
             BiConsumer<String, Boolean> onLine, Runnable onFinished) {
        run(workingDir, command, null, onLine, onFinished);
    }

    void run(File workingDir, List<String> command, Map<String, String> environment,
             Consumer<String> onLine, Runnable onFinished) {
        run(workingDir, command, environment, (line, error) -> onLine.accept(line), onFinished);
    }

    void run(File workingDir, List<String> command, Map<String, String> environment,
             BiConsumer<String, Boolean> onLine, Runnable onFinished) {
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
                builder.redirectErrorStream(false);
                process = builder.start();
                Thread stdout = new Thread(
                    () -> readLines(process.getInputStream(), false, onLine),
                    "build-stdout");
                Thread stderr = new Thread(
                    () -> readLines(process.getErrorStream(), true, onLine),
                    "build-stderr");
                stdout.start();
                stderr.start();
                stdout.join();
                stderr.join();
                exitCode = process.waitFor();
            } catch (Exception ex) {
                Log.log(Log.ERROR, BuildProcessRunner.class, "Build command failed", ex);
                String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                SwingUtilities.invokeLater(() -> onLine.accept("Error: " + message, true));
            } finally {
                process = null;
                running = false;
                int code = exitCode;
                SwingUtilities.invokeLater(() -> {
                    onLine.accept("--- exit code " + code + " ---", false);
                    if (onFinished != null) {
                        onFinished.run();
                    }
                });
            }
        });
    }

    private static void readLines(InputStream stream, boolean error,
                                  BiConsumer<String, Boolean> onLine) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            stream, Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String captured = line;
                SwingUtilities.invokeLater(() -> onLine.accept(captured, error));
            }
        } catch (Exception ex) {
            Log.log(Log.ERROR, BuildProcessRunner.class, "Failed to read process output", ex);
        }
    }
}
