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

package org.jedit.git;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

final class GitDiffLoader {

    static final class Sides {
        final String leftLabel;
        final String rightLabel;
        final String leftText;
        final String rightText;
        final File workingFile;
        final boolean rightEditable;
        final boolean canRevert;
        final boolean canSave;

        Sides(String leftLabel, String rightLabel, String leftText, String rightText,
              File workingFile, boolean rightEditable, boolean canRevert, boolean canSave) {
            this.leftLabel = leftLabel;
            this.rightLabel = rightLabel;
            this.leftText = leftText;
            this.rightText = rightText;
            this.workingFile = workingFile;
            this.rightEditable = rightEditable;
            this.canRevert = canRevert;
            this.canSave = canSave;
        }
    }

    private GitDiffLoader() {}

    static Sides load(File repoRoot, GitModels.FileChange change, GitRunner runner) {
        String gitPath = toGitPath(change.path);
        File workingFile = resolvePath(repoRoot, change.path);
        boolean fileExists = workingFile.isFile();

        String headText = readGitBlob(repoRoot, runner, "HEAD:" + gitPath);
        String indexText = readGitBlob(repoRoot, runner, ":" + gitPath);
        String workingText = fileExists ? readFile(workingFile) : "";

        if (change.isUntracked()) {
            return new Sides(
                jEditLabel("git.diff-viewer.left.untracked"),
                jEditLabel("git.diff-viewer.right.working"),
                "",
                workingText,
                workingFile,
                true,
                true,
                fileExists);
        }

        if (change.kind() == GitModels.ChangeKind.DELETED) {
            return new Sides(
                jEditLabel("git.diff-viewer.left.head"),
                jEditLabel("git.diff-viewer.right.deleted"),
                headText,
                "",
                null,
                false,
                true,
                false);
        }

        if (change.isStaged() && !change.hasWorkTreeChanges()) {
            return new Sides(
                jEditLabel("git.diff-viewer.left.head"),
                jEditLabel("git.diff-viewer.right.staged"),
                headText,
                indexText,
                workingFile,
                fileExists,
                true,
                fileExists);
        }

        return new Sides(
            jEditLabel("git.diff-viewer.left.head"),
            jEditLabel("git.diff-viewer.right.working"),
            headText,
            workingText,
            workingFile,
            fileExists,
            true,
            fileExists);
    }

    private static String readGitBlob(File repoRoot, GitRunner runner, String spec) {
        GitRunner.Result result = runner.run(repoRoot, "show", spec);
        return result.success() ? result.output : "";
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath(), Charset.defaultCharset());
        } catch (Exception ex) {
            return "";
        }
    }

    static File resolvePath(File repoRoot, String relativePath) {
        if (repoRoot == null || relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return new File(repoRoot, relativePath.replace('/', File.separatorChar));
    }

    private static String toGitPath(String path) {
        return path.replace(File.separatorChar, '/');
    }

    private static String jEditLabel(String key) {
        return org.gjt.sp.jedit.jEdit.getProperty(key);
    }
}
