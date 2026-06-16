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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

final class GitModels {

    private GitModels() {}

    enum ChangeKind {
        UNTRACKED,
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED,
        COPIED,
        CONFLICT,
        OTHER;

        Color foreground() {
            return GitColors.changeForeground(this);
        }
    }

    static final class FileChange {
        final char indexStatus;
        final char workTreeStatus;
        final String path;

        FileChange(char indexStatus, char workTreeStatus, String path) {
            this.indexStatus = indexStatus;
            this.workTreeStatus = workTreeStatus;
            this.path = path;
        }

        boolean isUntracked() {
            return indexStatus == '?' && workTreeStatus == '?';
        }

        boolean isStaged() {
            return indexStatus != ' ' && indexStatus != '?';
        }

        boolean hasWorkTreeChanges() {
            return workTreeStatus != ' ';
        }

        String statusLabel() {
            if (isUntracked()) {
                return "U";
            }
            if (isStaged() && hasWorkTreeChanges()) {
                return "" + indexStatus + workTreeStatus;
            }
            if (isStaged()) {
                return String.valueOf(indexStatus);
            }
            return String.valueOf(workTreeStatus);
        }

        ChangeKind kind() {
            if (isUntracked()) {
                return ChangeKind.UNTRACKED;
            }
            if (indexStatus == 'U' || workTreeStatus == 'U') {
                return ChangeKind.CONFLICT;
            }
            if (indexStatus == 'A' || workTreeStatus == 'A') {
                return ChangeKind.ADDED;
            }
            if (indexStatus == 'D' || workTreeStatus == 'D') {
                return ChangeKind.DELETED;
            }
            if (indexStatus == 'R' || workTreeStatus == 'R') {
                return ChangeKind.RENAMED;
            }
            if (indexStatus == 'C' || workTreeStatus == 'C') {
                return ChangeKind.COPIED;
            }
            if (indexStatus == 'M' || workTreeStatus == 'M') {
                return ChangeKind.MODIFIED;
            }
            return ChangeKind.OTHER;
        }

        String displayText() {
            return statusLabel() + "  " + path;
        }

        @Override
        public String toString() {
            return displayText();
        }
    }

    static final class Commit {
        final String hash;
        final String shortHash;
        final String author;
        final String date;
        final String subject;

        Commit(String hash, String shortHash, String author, String date, String subject) {
            this.hash = hash;
            this.shortHash = shortHash;
            this.author = author;
            this.date = date;
            this.subject = subject;
        }

        @Override
        public String toString() {
            return displayText();
        }

        String displayText() {
            return "[" + shortHash + "]  " + subject + "  (" + author + ", " + date + ")";
        }

        String displayHtml() {
            return "<html><b>[" + escapeHtml(shortHash) + "]</b>  "
                + escapeHtml(subject) + "  ("
                + escapeHtml(author) + ", " + escapeHtml(date) + ")</html>";
        }

        private static String escapeHtml(String text) {
            if (text == null || text.isEmpty()) {
                return "";
            }
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        }
    }

    static final class Branch {
        final String name;
        final boolean current;

        Branch(String name, boolean current) {
            this.name = name;
            this.current = current;
        }

        @Override
        public String toString() {
            return (current ? "* " : "  ") + name;
        }
    }

    static List<FileChange> parseStatus(String output) {
        List<FileChange> changes = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return changes;
        }
        for (String line : output.split("\n")) {
            if (line.length() < 4) {
                continue;
            }
            char index = line.charAt(0);
            char workTree = line.charAt(1);
            if (index == ' ' && workTree == ' ') {
                continue;
            }
            String path = line.substring(3).trim();
            int arrow = path.indexOf(" -> ");
            if (arrow >= 0) {
                path = path.substring(arrow + 4);
            }
            changes.add(new FileChange(index, workTree, path));
        }
        return changes;
    }

    static List<Commit> parseLog(String output) {
        List<Commit> commits = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return commits;
        }
        for (String line : output.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\u0001", -1);
            if (parts.length < 5) {
                continue;
            }
            commits.add(new Commit(parts[0], parts[1], parts[2], parts[3], parts[4]));
        }
        return commits;
    }

    static List<Branch> parseBranches(String output) {
        List<Branch> branches = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return branches;
        }
        for (String line : output.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            boolean current = line.startsWith("* ");
            String name = current ? line.substring(2).trim() : line.trim();
            if (!name.isEmpty()) {
                branches.add(new Branch(name, current));
            }
        }
        return branches;
    }
}
