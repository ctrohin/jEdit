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
import java.util.ArrayList;
import java.util.List;

import org.gjt.sp.jedit.jEdit;

final class GitHeadState {

    enum Kind {
        BRANCH,
        TAG,
        DETACHED,
        NONE
    }

    final Kind kind;
    final String label;
    final String checkoutRef;

    private GitHeadState(Kind kind, String label, String checkoutRef) {
        this.kind = kind;
        this.label = label;
        this.checkoutRef = checkoutRef;
    }

    static GitHeadState none() {
        return new GitHeadState(Kind.NONE, "", "");
    }

    static GitHeadState query(File repoRoot, GitRunner runner) {
        if (repoRoot == null) {
            return none();
        }
        GitRunner.Result branchResult = runner.run(repoRoot, "branch", "--show-current");
        String branch = branchResult.success() ? branchResult.output.trim() : "";
        if (!branch.isEmpty()) {
            return new GitHeadState(Kind.BRANCH, branch, branch);
        }
        GitRunner.Result tagsAtHead = runner.run(repoRoot, "tag", "--points-at", "HEAD");
        if (tagsAtHead.success() && !tagsAtHead.output.isBlank()) {
            String tag = tagsAtHead.output.split("\n")[0].trim();
            if (!tag.isEmpty()) {
                return new GitHeadState(Kind.TAG, tag, tag);
            }
        }
        GitRunner.Result hashResult = runner.run(repoRoot, "rev-parse", "--short", "HEAD");
        String hash = hashResult.success() ? hashResult.output.trim() : "?";
        return new GitHeadState(Kind.DETACHED, hash, hash);
    }

    static List<String> listBranches(File repoRoot, GitRunner runner) {
        List<String> branches = new ArrayList<>();
        GitRunner.Result result = runner.run(repoRoot, "branch", "--list");
        if (!result.success()) {
            return branches;
        }
        for (GitModels.Branch branch : GitModels.parseBranches(result.output)) {
            branches.add(branch.name);
        }
        return branches;
    }

    static List<String> listTags(File repoRoot, GitRunner runner) {
        List<String> tags = new ArrayList<>();
        GitRunner.Result result = runner.run(repoRoot, "tag", "--list", "--sort=-creatordate");
        if (!result.success()) {
            return tags;
        }
        for (String line : result.output.split("\n")) {
            if (!line.isBlank()) {
                tags.add(line.trim());
            }
        }
        return tags;
    }

    String statusText() {
        if (kind == Kind.NONE) {
            return "";
        }
        return jEdit.getProperty("git.head.status", new String[] {menuLabel()});
    }

    String menuLabel() {
        if (kind == Kind.NONE) {
            return "";
        }
        return switch (kind) {
            case BRANCH -> label;
            case TAG -> jEdit.getProperty("git.head.tag-label", new String[] {label});
            case DETACHED -> jEdit.getProperty("git.head.detached-label", new String[] {label});
            case NONE -> "";
        };
    }

    String tooltip() {
        if (kind == Kind.NONE) {
            return jEdit.getProperty("git.head.tooltip.none");
        }
        return switch (kind) {
            case BRANCH -> jEdit.getProperty("git.head.tooltip.branch", new String[] {label});
            case TAG -> jEdit.getProperty("git.head.tooltip.tag", new String[] {label});
            case DETACHED -> jEdit.getProperty("git.head.tooltip.detached", new String[] {label});
            case NONE -> "";
        };
    }

    boolean isCurrentRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        return ref.equals(label) || ref.equals(checkoutRef);
    }
}
