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

package org.jedit.build;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.OperatingSystem;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

final class BuildProcessRunner {

    private volatile Process process;
    private volatile boolean running;
    private volatile boolean userStopped;
    private volatile long activeRunId;

    boolean isRunning() {
        return running;
    }

    void stop() {
        userStopped = true;
        activeRunId++;
        running = false;
        Process active = process;
        if (active != null) {
            ThreadUtilities.runInBackground(() -> terminateProcessSync(active));
        }
    }

    void sendInput(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        Process active = process;
        if (active == null || !active.isAlive()) {
            return;
        }
        try {
            OutputStream stdin = active.getOutputStream();
            stdin.write(data);
            stdin.flush();
        } catch (IOException ex) {
            Log.log(Log.DEBUG, BuildProcessRunner.class, "Failed to write process input", ex);
        }
    }

    void run(File workingDir, List<String> command, Consumer<String> onLine,
             IntConsumer onFinished) {
        run(workingDir, command, null, onLine, onFinished);
    }

    void run(File workingDir, List<String> command,
             BiConsumer<String, Boolean> onLine, IntConsumer onFinished) {
        run(workingDir, command, null, onLine, onFinished);
    }

    void run(File workingDir, List<String> command, Map<String, String> environment,
             Consumer<String> onLine, IntConsumer onFinished) {
        run(workingDir, command, environment, (line, error) -> onLine.accept(line), onFinished);
    }

    void run(File workingDir, List<String> command, Map<String, String> environment,
             BiConsumer<String, Boolean> onLine, IntConsumer onFinished) {
        userStopped = false;
        long runId = ++activeRunId;
        running = true;
        ThreadUtilities.runInBackground(() -> {
            Process previous = process;
            if (previous != null) {
                terminateProcessSync(previous);
            }
            executeRun(runId, workingDir, command, environment, onLine, onFinished);
        });
    }

    private void executeRun(long runId, File workingDir, List<String> command,
                            Map<String, String> environment,
                            BiConsumer<String, Boolean> onLine, IntConsumer onFinished) {
        Process localProcess = null;
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
            localProcess = builder.start();
            final Process runningProcess = localProcess;
            process = runningProcess;
            Thread stdout = new Thread(
                () -> readLines(runId, runningProcess.getInputStream(), false, onLine),
                "build-stdout");
            Thread stderr = new Thread(
                () -> readLines(runId, runningProcess.getErrorStream(), true, onLine),
                "build-stderr");
            stdout.setDaemon(true);
            stderr.setDaemon(true);
            stdout.start();
            stderr.start();
            stdout.join();
            stderr.join();
            exitCode = localProcess.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            if (runId == activeRunId) {
                Log.log(Log.ERROR, BuildProcessRunner.class, "Build command failed", ex);
                String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                SwingUtilities.invokeLater(() -> onLine.accept("Error: " + message, true));
            }
        } finally {
            if (process == localProcess) {
                process = null;
            }
            if (runId != activeRunId) {
                return;
            }
            running = false;
            boolean stopped = userStopped;
            int code = exitCode;
            SwingUtilities.invokeLater(() -> {
                onLine.accept("--- exit code " + code + " ---", false);
                if (!stopped && onFinished != null) {
                    onFinished.accept(code);
                }
            });
        }
    }

    private static void terminateProcessSync(Process active) {
        destroyProcessTree(active);
        closeQuietly(active.getOutputStream());
        closeQuietly(active.getInputStream());
        closeQuietly(active.getErrorStream());
    }

    private static void destroyProcessTree(Process process) {
        if (process == null) {
            return;
        }
        long pid = process.pid();
        if (OperatingSystem.isWindows()) {
            try {
                Process taskkill = new ProcessBuilder(
                    "taskkill", "/F", "/T", "/PID", Long.toString(pid))
                    .redirectErrorStream(true)
                    .start();
                taskkill.waitFor(5, TimeUnit.SECONDS);
                return;
            } catch (Exception ex) {
                Log.log(Log.DEBUG, BuildProcessRunner.class, "taskkill failed", ex);
            }
        }
        try {
            ProcessHandle.of(pid).ifPresent(root -> {
                root.descendants().forEach(BuildProcessRunner::destroyHandle);
                destroyHandle(root);
            });
            process.descendants().forEach(BuildProcessRunner::destroyHandle);
        } catch (Exception ex) {
            Log.log(Log.DEBUG, BuildProcessRunner.class, "Process tree lookup failed", ex);
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private static void destroyHandle(ProcessHandle handle) {
        if (handle.isAlive()) {
            handle.destroyForcibly();
        }
    }

    private static void closeQuietly(Closeable stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }

    private void readLines(long runId, InputStream stream, boolean error,
                           BiConsumer<String, Boolean> onLine) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            stream, Charset.defaultCharset()))) {
            String line;
            while (runId == activeRunId && (line = reader.readLine()) != null) {
                String captured = line;
                SwingUtilities.invokeLater(() -> {
                    if (runId == activeRunId) {
                        onLine.accept(captured, error);
                    }
                });
            }
        } catch (IOException ex) {
            Log.log(Log.DEBUG, BuildProcessRunner.class, "Process output stream closed", ex);
        }
    }
}
