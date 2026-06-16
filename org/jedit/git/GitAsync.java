/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.io.File;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.gjt.sp.util.ThreadUtilities;

final class GitAsync {

    private GitAsync() {}

    static void run(File repoRoot, Consumer<GitRunner> task) {
        if (repoRoot == null) {
            return;
        }
        ThreadUtilities.runInBackground(() -> task.accept(new GitRunner()));
    }

    static void runCommand(File repoRoot, String[] args, Consumer<GitRunner.Result> onResult) {
        run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, args);
            if (onResult != null) {
                SwingUtilities.invokeLater(() -> onResult.accept(result));
            }
        });
    }
}
