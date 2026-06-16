/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gjt.sp.jedit.jEdit;

final class GitRepositoryLoader {

    enum State {
        NO_REPO,
        GIT_MISSING,
        OK
    }

    static final class Snapshot {
        final State state;
        final File repoRoot;
        final String captionText;
        final GitHeadState head;
        final List<GitModels.FileChange> changes;
        final List<GitModels.Commit> commits;
        final List<GitModels.Branch> branches;
        final String outputMessage;

        Snapshot(State state, File repoRoot, String captionText, GitHeadState head,
                 List<GitModels.FileChange> changes, List<GitModels.Commit> commits,
                 List<GitModels.Branch> branches, String outputMessage) {
            this.state = state;
            this.repoRoot = repoRoot;
            this.captionText = captionText;
            this.head = head;
            this.changes = changes;
            this.commits = commits;
            this.branches = branches;
            this.outputMessage = outputMessage;
        }

        static Snapshot noRepo() {
            return new Snapshot(
                State.NO_REPO,
                null,
                jEdit.getProperty("git.no-repo"),
                GitHeadState.none(),
                List.of(),
                List.of(),
                List.of(),
                jEdit.getProperty("git.no-repo"));
        }
    }

    private GitRepositoryLoader() {}

    static Snapshot load(File repoRoot) {
        if (repoRoot == null) {
            return Snapshot.noRepo();
        }
        GitRunner runner = new GitRunner();
        GitRunner.Result version = runner.run(repoRoot, "--version");
        if (!version.success()) {
            return new Snapshot(
                State.GIT_MISSING,
                repoRoot,
                jEdit.getProperty("git.git-missing"),
                GitHeadState.none(),
                List.of(),
                List.of(),
                List.of(),
                version.output);
        }
        GitHeadState head = GitHeadState.query(repoRoot, runner);
        List<GitModels.FileChange> changes = loadChanges(repoRoot, runner);
        List<GitModels.Commit> commits = loadLog(repoRoot, runner);
        List<GitModels.Branch> branches = loadBranches(repoRoot, runner);
        return new Snapshot(
            State.OK,
            repoRoot,
            jEdit.getProperty("git.caption", new String[] {repoRoot.getAbsolutePath()}),
            head,
            changes,
            commits,
            branches,
            null);
    }

    static List<GitModels.FileChange> loadChanges(File repoRoot, GitRunner runner) {
        List<GitModels.FileChange> changes = new ArrayList<>();
        GitRunner.Result result = runner.run(repoRoot, "status", "--porcelain", "-uall");
        if (result.success()) {
            changes.addAll(GitModels.parseStatus(result.output));
        }
        return changes;
    }

    static List<GitModels.Commit> loadLog(File repoRoot, GitRunner runner) {
        List<GitModels.Commit> commits = new ArrayList<>();
        GitRunner.Result result = runner.run(repoRoot, "log",
            "--max-count=200",
            "--date=short",
            "--pretty=format:%H%x01%h%x01%an%x01%ad%x01%s");
        if (result.success()) {
            commits.addAll(GitModels.parseLog(result.output));
        }
        return commits;
    }

    static List<GitModels.Branch> loadBranches(File repoRoot, GitRunner runner) {
        List<GitModels.Branch> branches = new ArrayList<>();
        GitRunner.Result result = runner.run(repoRoot, "branch", "--list");
        if (result.success()) {
            branches.addAll(GitModels.parseBranches(result.output));
        }
        return branches;
    }
}
