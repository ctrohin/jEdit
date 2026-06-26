/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;

import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

final class GitCommitUI {

    private GitCommitUI() {}

    static void showCommit(View view, File repoRoot, GitBlameSupport.BlameLine blameLine) {
        if (view == null || repoRoot == null || blameLine == null || blameLine.isEmpty()) {
            return;
        }
        showCommit(view, repoRoot, blameLine.toCommit());
    }

    static void showCommit(View view, File repoRoot, GitModels.Commit commit) {
        if (view == null || repoRoot == null || commit == null) {
            return;
        }
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "show", "--stat", commit.hash);
            String text = result.output;
            if (text == null || text.isBlank()) {
                text = jEdit.getProperty("git.diff.empty-body",
                    new String[] {"commit " + commit.shortHash});
            }
            String finalText = text;
            SwingUtilities.invokeLater(() -> GitCommitDialog.show(view, commit, finalText));
        });
    }

    static void copyCommitId(GitBlameSupport.BlameLine blameLine) {
        if (blameLine == null || blameLine.isEmpty()) {
            return;
        }
        String hash = blameLine.hash;
        StringSelection selection = new StringSelection(hash);
        Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(selection, selection);
    }

    static void selectCommitInHistory(View view, String hash) {
        if (view == null || hash == null || hash.isBlank()) {
            return;
        }
        GitView gitView = GitView.show(view);
        gitView.selectCommitInHistory(hash);
    }
}
