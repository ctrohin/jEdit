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

import java.awt.Component;
import java.io.File;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

final class GitRefMenu {

    private GitRefMenu() {}

    static void show(Component invoker, View view, File repoRoot, GitRunner runner,
                     Runnable onRepositoryChanged) {
        if (repoRoot == null) {
            return;
        }
        JPopupMenu menu = build(view, repoRoot, runner, onRepositoryChanged);
        menu.show(invoker, 0, invoker.getHeight());
    }

    private static JPopupMenu build(View view, File repoRoot, GitRunner runner,
                                    Runnable onRepositoryChanged) {
        JPopupMenu menu = new JPopupMenu();
        GitHeadState head = GitHeadState.query(repoRoot, runner);
        if (head.kind != GitHeadState.Kind.NONE) {
            JMenuItem current = new JMenuItem(jEdit.getProperty(
                "git.ref-menu.current", new String[] {head.menuLabel()}));
            current.setEnabled(false);
            menu.add(current);
            menu.addSeparator();
        }

        JMenu branchesMenu = new JMenu(jEdit.getProperty("git.ref-menu.branches"));
        branchesMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                populateBranches(branchesMenu, view, repoRoot, runner, head,
                    onRepositoryChanged);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        menu.add(branchesMenu);

        JMenu tagsMenu = new JMenu(jEdit.getProperty("git.ref-menu.tags"));
        tagsMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                populateTags(tagsMenu, view, repoRoot, runner, head, onRepositoryChanged);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        menu.add(tagsMenu);

        menu.addSeparator();
        JMenuItem createBranch = new JMenuItem(jEdit.getProperty("git.create-branch"));
        createBranch.addActionListener(e -> promptCreateBranch(
            view, repoRoot, runner, onRepositoryChanged));
        menu.add(createBranch);
        return menu;
    }

    private static void populateBranches(JMenu menu, View view, File repoRoot,
                                         GitRunner runner, GitHeadState head,
                                         Runnable onRepositoryChanged) {
        menu.removeAll();
        List<String> branches = GitHeadState.listBranches(repoRoot, runner);
        if (branches.isEmpty()) {
            JMenuItem empty = new JMenuItem(jEdit.getProperty("git.ref-menu.no-branches"));
            empty.setEnabled(false);
            menu.add(empty);
            return;
        }
        for (String branch : branches) {
            JMenuItem item = new JMenuItem(branch);
            if (head.kind == GitHeadState.Kind.BRANCH && head.isCurrentRef(branch)) {
                item.setEnabled(false);
            } else {
                item.addActionListener(e -> checkoutRef(view, repoRoot, runner, branch,
                    jEdit.getProperty("git.checkout-branch.confirm", new String[] {branch}),
                    onRepositoryChanged));
            }
            menu.add(item);
        }
    }

    private static void populateTags(JMenu menu, View view, File repoRoot,
                                       GitRunner runner, GitHeadState head,
                                       Runnable onRepositoryChanged) {
        menu.removeAll();
        List<String> tags = GitHeadState.listTags(repoRoot, runner);
        if (tags.isEmpty()) {
            JMenuItem empty = new JMenuItem(jEdit.getProperty("git.ref-menu.no-tags"));
            empty.setEnabled(false);
            menu.add(empty);
            return;
        }
        for (String tag : tags) {
            JMenuItem item = new JMenuItem(tag);
            if (head.kind == GitHeadState.Kind.TAG && head.isCurrentRef(tag)) {
                item.setEnabled(false);
            } else {
                item.addActionListener(e -> checkoutRef(view, repoRoot, runner, tag,
                    jEdit.getProperty("git.checkout-tag.confirm", new String[] {tag}),
                    onRepositoryChanged));
            }
            menu.add(item);
        }
    }

    private static void promptCreateBranch(View view, File repoRoot, GitRunner runner,
                                           Runnable onRepositoryChanged) {
        String name = JOptionPane.showInputDialog(view,
            jEdit.getProperty("git.create-branch.prompt"),
            jEdit.getProperty("git.create-branch"),
            JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        GitRunner.Result result = runner.run(repoRoot, "checkout", "-b", name.trim());
        if (!result.success()) {
            JOptionPane.showMessageDialog(view, result.output,
                jEdit.getProperty("git.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        notifyHeadChanged(onRepositoryChanged);
    }

    private static void checkoutRef(View view, File repoRoot, GitRunner runner, String ref,
                                    String confirmMessage, Runnable onRepositoryChanged) {
        if (JOptionPane.showConfirmDialog(view, confirmMessage,
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        GitRunner.Result result = runner.run(repoRoot, "checkout", ref);
        if (!result.success()) {
            JOptionPane.showMessageDialog(view, result.output,
                jEdit.getProperty("git.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        notifyHeadChanged(onRepositoryChanged);
    }

    private static void notifyHeadChanged(Runnable onRepositoryChanged) {
        if (onRepositoryChanged != null) {
            onRepositoryChanged.run();
        }
        EditBus.send(new GitHeadChanged());
    }
}
