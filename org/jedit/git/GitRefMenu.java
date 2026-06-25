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
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;

final class GitRefMenu {

    private static final String HEAD_APPLIED_KEY = "git.ref-menu.head-applied";

    private GitRefMenu() {}

    static void show(Component invoker, View view, File repoRoot, GitRunner runner,
                     Runnable onRepositoryChanged) {
        show(invoker, view, repoRoot, runner, null, List.of(), onRepositoryChanged);
    }

    static void show(Component invoker, View view, File repoRoot, GitRunner runner,
                     GitHeadState cachedHead, List<String> cachedBranches,
                     Runnable onRepositoryChanged) {
        if (repoRoot == null) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem loadingItem = new JMenuItem(jEdit.getProperty("git.loading"));
        loadingItem.setEnabled(false);
        menu.add(loadingItem);

        JMenu branchesMenu = new JMenu(jEdit.getProperty("git.ref-menu.branches"));
        menu.add(branchesMenu);

        JMenu tagsMenu = new JMenu(jEdit.getProperty("git.ref-menu.tags"));
        menu.add(tagsMenu);

        menu.addSeparator();
        JMenu stashMenu = new JMenu(jEdit.getProperty("git.ref-menu.stash"));
        menu.add(stashMenu);
        JMenuItem stashPush = new JMenuItem(jEdit.getProperty("git.stash.push"));
        stashPush.addActionListener(e -> stashPush(view, repoRoot, onRepositoryChanged));
        stashMenu.add(stashPush);
        JMenuItem stashLoading = new JMenuItem(jEdit.getProperty("git.loading"));
        stashLoading.setEnabled(false);
        stashMenu.add(stashLoading);

        JMenuItem createBranch = new JMenuItem(jEdit.getProperty("git.create-branch"));
        createBranch.addActionListener(e ->
            promptCreateBranch(view, repoRoot, onRepositoryChanged));
        menu.add(createBranch);

        if (cachedHead != null && cachedHead.kind != GitHeadState.Kind.NONE) {
            applyHead(menu, loadingItem, cachedHead);
        }
        if (cachedBranches != null && !cachedBranches.isEmpty()) {
            GitHeadState head = cachedHead != null ? cachedHead : GitHeadState.none();
            populateBranches(branchesMenu, view, repoRoot, head, cachedBranches,
                onRepositoryChanged);
        } else {
            JMenuItem branchLoading = new JMenuItem(jEdit.getProperty("git.loading"));
            branchLoading.setEnabled(false);
            branchesMenu.add(branchLoading);
        }

        JMenuItem tagLoading = new JMenuItem(jEdit.getProperty("git.loading"));
        tagLoading.setEnabled(false);
        tagsMenu.add(tagLoading);

        menu.show(invoker, 0, invoker.getHeight());

        ThreadUtilities.runInBackground(() -> {
            GitRunner bgRunner = new GitRunner();
            GitHeadState head = GitHeadState.query(repoRoot, bgRunner);
            List<String> branches = GitHeadState.listBranches(repoRoot, bgRunner);
            List<String> tags = GitHeadState.listTags(repoRoot, bgRunner);
            SwingUtilities.invokeLater(() -> {
                if (!menu.isVisible()) {
                    return;
                }
                applyHead(menu, loadingItem, head);
                populateBranches(branchesMenu, view, repoRoot, head, branches,
                    onRepositoryChanged);
                populateTags(tagsMenu, view, repoRoot, head, tags, onRepositoryChanged);
                populateStashes(stashMenu, stashLoading, view, repoRoot, onRepositoryChanged);
            });
        });
    }

    private static void populateStashes(JMenu menu, JMenuItem loadingItem, View view,
                                      File repoRoot, Runnable onRepositoryChanged) {
        if (loadingItem.getParent() == menu) {
            menu.remove(loadingItem);
        }
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "stash", "list");
            List<String> entries = parseStashList(result.output);
            SwingUtilities.invokeLater(() -> {
                if (!menu.isVisible()) {
                    return;
                }
                while (menu.getItemCount() > 1) {
                    menu.remove(1);
                }
                if (entries.isEmpty()) {
                    JMenuItem empty = new JMenuItem(jEdit.getProperty("git.ref-menu.no-stashes"));
                    empty.setEnabled(false);
                    menu.add(empty);
                    return;
                }
                for (String entry : entries) {
                    JMenuItem item = new JMenuItem(entry);
                    item.addActionListener(e -> stashPop(view, repoRoot, entry, onRepositoryChanged));
                    menu.add(item);
                }
            });
        });
    }

    private static List<String> parseStashList(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                entries.add(line.trim());
            }
        }
        return entries;
    }

    private static void stashPush(View view, File repoRoot, Runnable onRepositoryChanged) {
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "stash", "push", "-m", "jEdit stash");
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

    private static void stashPop(View view, File repoRoot, String entry,
                                 Runnable onRepositoryChanged) {
        String ref = entry.contains(":") ? entry.substring(0, entry.indexOf(':')).trim() : entry;
        if (JOptionPane.showConfirmDialog(view,
            jEdit.getProperty("git.stash.pop.confirm", new String[] {entry}),
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        GitAsync.run(repoRoot, runner -> {
            GitRunner.Result result = runner.run(repoRoot, "stash", "pop", ref);
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

    private static void applyHead(JPopupMenu menu, JMenuItem loadingItem, GitHeadState head) {
        if (loadingItem.getParent() == menu) {
            menu.remove(loadingItem);
        }
        if (head.kind == GitHeadState.Kind.NONE) {
            return;
        }
        if (Boolean.TRUE.equals(menu.getClientProperty(HEAD_APPLIED_KEY))) {
            return;
        }
        menu.putClientProperty(HEAD_APPLIED_KEY, Boolean.TRUE);
        JMenuItem currentItem = new JMenuItem(jEdit.getProperty(
            "git.ref-menu.current", new String[] {head.menuLabel()}));
        currentItem.setEnabled(false);
        menu.insert(currentItem, 0);
        menu.insert(new JSeparator(), 1);
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
