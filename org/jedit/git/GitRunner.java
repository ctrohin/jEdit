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

package org.jedit.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;

final class GitRunner {

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output != null ? output : "";
        }

        boolean success() {
            return exitCode == 0;
        }
    }

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

    Result run(File workingDir, String... args) {
        List<String> command = command(args);
        StringBuilder output = new StringBuilder();
        int exitCode = -1;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (workingDir != null) {
                builder.directory(workingDir);
            }
            builder.redirectErrorStream(true);
            Process proc = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                proc.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() > 0) {
                        output.append('\n');
                    }
                    output.append(line);
                }
            }
            exitCode = proc.waitFor();
        } catch (Exception ex) {
            Log.log(Log.ERROR, GitRunner.class, "Git command failed", ex);
            String message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append("Error: ").append(message);
        }
        return new Result(exitCode, output.toString());
    }

    void runAsync(File workingDir, Consumer<String> onLine, Runnable onFinished,
                  String... args) {
        stop();
        running = true;
        List<String> command = command(args);
        ThreadUtilities.runInBackground(() -> {
            int exitCode = -1;
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                if (workingDir != null) {
                    builder.directory(workingDir);
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
                Log.log(Log.ERROR, GitRunner.class, "Git command failed", ex);
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

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add(GitRepository.executable());
        command.addAll(Arrays.asList(args));
        return command;
    }
}
