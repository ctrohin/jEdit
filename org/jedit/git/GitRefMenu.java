/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Component;
import java.io.File;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;

final class GitRefMenu {

    private GitRefMenu() {}

    static void show(Component invoker, View view, File repoRoot, GitRunner runner,
                     Runnable onRepositoryChanged) {
        if (repoRoot == null) {
            return;
        }
        JPopupMenu menu = build(view, repoRoot, onRepositoryChanged);
        menu.show(invoker, 0, invoker.getHeight());
    }

    private static JPopupMenu build(View view, File repoRoot, Runnable onRepositoryChanged) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem loadingItem = new JMenuItem(jEdit.getProperty("git.loading"));
        loadingItem.setEnabled(false);
        menu.add(loadingItem);
        JSeparator loadingSeparator = new JSeparator();
        menu.add(loadingSeparator);

        ThreadUtilities.runInBackground(() -> {
            GitRunner bgRunner = new GitRunner();
            GitHeadState head = GitHeadState.query(repoRoot, bgRunner);
            SwingUtilities.invokeLater(() -> {
                menu.remove(loadingItem);
                menu.remove(loadingSeparator);
                if (head.kind != GitHeadState.Kind.NONE) {
                    JMenuItem currentItem = new JMenuItem(jEdit.getProperty(
                        "git.ref-menu.current", new String[] {head.menuLabel()}));
                    currentItem.setEnabled(false);
                    menu.insert(currentItem, 0);
                    menu.insert(new JSeparator(), 1);
                }
            });
        });

        JMenu branchesMenu = new JMenu(jEdit.getProperty("git.ref-menu.branches"));
        branchesMenu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                populateBranchesAsync(branchesMenu, view, repoRoot, onRepositoryChanged);
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
                populateTagsAsync(tagsMenu, view, repoRoot, onRepositoryChanged);
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
        createBranch.addActionListener(e -> promptCreateBranch(view, repoRoot, onRepositoryChanged));
        menu.add(createBranch);
        return menu;
    }

    private static void populateBranchesAsync(JMenu menu, View view, File repoRoot,
                                              Runnable onRepositoryChanged) {
        menu.removeAll();
        JMenuItem loading = new JMenuItem(jEdit.getProperty("git.loading"));
        loading.setEnabled(false);
        menu.add(loading);

        ThreadUtilities.runInBackground(() -> {
            GitRunner runner = new GitRunner();
            GitHeadState head = GitHeadState.query(repoRoot, runner);
            List<String> branches = GitHeadState.listBranches(repoRoot, runner);
            SwingUtilities.invokeLater(() -> populateBranches(menu, view, repoRoot, head,
                branches, onRepositoryChanged));
        });
    }

    private static void populateBranches(JMenu menu, View view, File repoRoot,
                                       GitHeadState head, List<String> branches,
                                       Runnable onRepositoryChanged) {
        menu.removeAll();
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
                item.addActionListener(e -> checkoutRef(view, repoRoot, branch,
                    jEdit.getProperty("git.checkout-branch.confirm", new String[] {branch}),
                    onRepositoryChanged));
            }
            menu.add(item);
        }
    }

    private static void populateTagsAsync(JMenu menu, View view, File repoRoot,
                                          Runnable onRepositoryChanged) {
        menu.removeAll();
        JMenuItem loading = new JMenuItem(jEdit.getProperty("git.loading"));
        loading.setEnabled(false);
        menu.add(loading);

        ThreadUtilities.runInBackground(() -> {
            GitRunner runner = new GitRunner();
            GitHeadState head = GitHeadState.query(repoRoot, runner);
            List<String> tags = GitHeadState.listTags(repoRoot, runner);
            SwingUtilities.invokeLater(() -> populateTags(menu, view, repoRoot, head,
                tags, onRepositoryChanged));
        });
    }

    private static void populateTags(JMenu menu, View view, File repoRoot,
                                   GitHeadState head, List<String> tags,
                                   Runnable onRepositoryChanged) {
        menu.removeAll();
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
                item.addActionListener(e -> checkoutRef(view, repoRoot, tag,
                    jEdit.getProperty("git.checkout-tag.confirm", new String[] {tag}),
                    onRepositoryChanged));
            }
            menu.add(item);
        }
    }

    private static void promptCreateBranch(View view, File repoRoot,
                                           Runnable onRepositoryChanged) {
        String name = JOptionPane.showInputDialog(view,
            jEdit.getProperty("git.create-branch.prompt"),
            jEdit.getProperty("git.create-branch"),
            JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        String branchName = name.trim();
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "checkout", "-b", branchName);
            SwingUtilities.invokeLater(() -> {
                if (!result.success()) {
                    JOptionPane.showMessageDialog(view, result.output,
                        jEdit.getProperty("git.title"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                notifyHeadChanged(onRepositoryChanged);
            });
        });
    }

    private static void checkoutRef(View view, File repoRoot, String ref,
                                    String confirmMessage, Runnable onRepositoryChanged) {
        if (JOptionPane.showConfirmDialog(view, confirmMessage,
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "checkout", ref);
            SwingUtilities.invokeLater(() -> {
                if (!result.success()) {
                    JOptionPane.showMessageDialog(view, result.output,
                        jEdit.getProperty("git.title"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                notifyHeadChanged(onRepositoryChanged);
            });
        });
    }

    private static void notifyHeadChanged(Runnable onRepositoryChanged) {
        if (onRepositoryChanged != null) {
            onRepositoryChanged.run();
        }
        EditBus.send(new GitHeadChanged());
    }
}
