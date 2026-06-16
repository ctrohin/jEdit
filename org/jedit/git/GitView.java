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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.Hashtable;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
/**
 * Git integration dockable: status, staging, commit, diff, log, branches, pull/push.
 */
public final class GitView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "git";

    private final View view;
    private final JLabel caption;
    private final JButton refButton;
    private final DefaultListModel<GitModels.FileChange> changeModel = new DefaultListModel<>();
    private final JList<GitModels.FileChange> changeList;
    private final DefaultListModel<GitModels.Commit> logModel = new DefaultListModel<>();
    private final JList<GitModels.Commit> logList;
    private final DefaultListModel<GitModels.Branch> branchModel = new DefaultListModel<>();
    private final JList<GitModels.Branch> branchList;
    private final JTextArea commitMessage;
    private final JTextArea output;
    private final GitRunner runner = new GitRunner();
    private final GitFolderListener folderListener = new GitFolderListener(this::refreshRepository);
    private File repoRoot;

    public GitView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        caption = new JLabel(" ");
        add(caption, BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton refreshButton = new JButton(jEdit.getProperty("git.refresh"));
        refreshButton.addActionListener(e -> refreshRepository());
        refButton = new JButton(jEdit.getProperty("git.branch.menu"));
        refButton.addActionListener(e -> {
            if (repoRoot != null) {
                GitRefMenu.show(refButton, view, repoRoot, runner, this::refreshRepository);
            }
        });
        JButton fetchButton = actionButton("MatIcons.FETCH:22", "git.fetch", () -> runGitAsync("fetch"));
        JButton pullButton = actionButton("MatIcons.PULL:22","git.pull", () -> runGitAsync("pull"));
        JButton pushButton = actionButton("MatIcons.PUSH:22","git.push", () -> runGitAsync("push"));
        toolbar.add(refreshButton);
        toolbar.add(refButton);
        toolbar.add(fetchButton);
        toolbar.add(pullButton);
        toolbar.add(pushButton);

        changeList = new JList<>(changeModel);
        changeList.setCellRenderer(new GitChangeCellRenderer());
        changeList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        changeList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openDiffViewer();
                }
            }
        });
        JPanel changesPanel = new JPanel(new BorderLayout(0, 4));
        JPanel changeButtons = buttonRow(
            actionButton("MatIcons.ADD:22","git.stage", this::stageSelected),
            actionButton("MatIcons.REMOVE:22", "git.unstage", this::unstageSelected),
            actionButton("MatIcons.PLAYLIST_ADD:22", "git.stage-all", this::stageAll),
            actionButton("MatIcons.REPLY_ALL:22", "git.unstage-all", this::unstageAll),
            actionButton("MatIcons.UNDO:22", "git.discard", this::discardSelected),
            actionButton("MatIcons.OPEN_IN_NEW:22", "git.open", this::openSelectedFile),
            actionButton("MatIcons.DIFF:22", "git.diff", this::diffSelected));
        changesPanel.add(changeButtons, BorderLayout.NORTH);
        changesPanel.add(new JScrollPane(changeList), BorderLayout.CENTER);

        commitMessage = new JTextArea(4, 40);
        commitMessage.setLineWrap(true);
        commitMessage.setWrapStyleWord(true);
        JPanel commitPanel = new JPanel(new BorderLayout(0, 4));
        commitPanel.add(new JScrollPane(commitMessage), BorderLayout.CENTER);
        JPanel commitButtons = buttonRow(
            simpleActionButton("git.commit", this::commitChanges),
            simpleActionButton("git.commit-all", this::commitAll));
        commitPanel.add(commitButtons, BorderLayout.SOUTH);
        changesPanel.add(commitPanel, BorderLayout.SOUTH);

        logList = new JList<>(logModel);
        logList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        logList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showSelectedCommit();
                }
            }
        });
        JPanel logPanel = new JPanel(new BorderLayout(0, 4));
        logPanel.add(buttonRow(
            actionButton("MatIcons.REMOVE_RED_EYE:22", "git.show-commit", this::showSelectedCommit),
            actionButton("MatIcons.COMPARE_ARROWS:22", "git.diff-commit", this::diffSelectedCommit),
            actionButton("MatIcons.FILE_DOWNLOAD:22","git.checkout-commit", this::checkoutSelectedCommit)),
            BorderLayout.NORTH);
        logPanel.add(new JScrollPane(logList), BorderLayout.CENTER);

        branchList = new JList<>(branchModel);
        branchList.setCellRenderer(new GitBranchCellRenderer());
        branchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        branchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    checkoutSelectedBranch();
                }
            }
        });
        JPanel branchesPanel = new JPanel(new BorderLayout(0, 4));
        JPanel branchControls = new JPanel(new BorderLayout(0, 4));
        branchControls.add(buttonRow(
            actionButton("MatIcons.KEYBOARD_ARROW_DOWN:22", "git.checkout-branch", this::checkoutSelectedBranch),
            actionButton("MatIcons.DELETE:22","git.delete-branch", this::deleteSelectedBranch)),
            BorderLayout.NORTH);
        JPanel createBranchRow = new JPanel(new BorderLayout(4, 0));
        JTextField newBranchField = new JTextField();
        createBranchRow.add(newBranchField, BorderLayout.CENTER);
        createBranchRow.add(actionButton("MatIcons.ADD:22","git.create-branch",
            () -> createBranch(newBranchField.getText())), BorderLayout.EAST);
        branchControls.add(createBranchRow, BorderLayout.SOUTH);
        branchesPanel.add(branchControls, BorderLayout.NORTH);
        branchesPanel.add(new JScrollPane(branchList), BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(jEdit.getProperty("git.tab.changes"), changesPanel);
        tabs.addTab(jEdit.getProperty("git.tab.history"), logPanel);
        tabs.addTab(jEdit.getProperty("git.tab.branches"), branchesPanel);

        output = new JTextArea();
        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, output.getFont().getSize()));
        JPanel outputPanel = new JPanel(new BorderLayout(0, 4));
        outputPanel.add(buttonRow(
            actionButton("MatIcons.REMOVE:22", "git.output.clear", () -> output.setText("")),
            actionButton("MatIcons.STOP:22", "git.output.stop", runner::stop)),
            BorderLayout.NORTH);
        outputPanel.add(new JScrollPane(output), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, outputPanel);
        split.setResizeWeight(0.65);
        split.setOneTouchExpandable(true);

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.add(toolbar, BorderLayout.NORTH);
        center.add(split, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        refreshRepository();
    }

    public static GitView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (GitView) dwm.getDockableWindow(NAME);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        refreshRepository();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        runner.stop();
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        changeList.requestFocusInWindow();
    }

    private static JButton simpleActionButton(String propertyKey, Runnable action) {
        JButton button = new JButton(jEdit.getProperty(propertyKey));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JButton actionButton(String iconName, String propertyKey, Runnable action) {
        JButton button = new RolloverButton(IconManager.loadIcon(iconName), jEdit.getProperty(propertyKey));
        button.addActionListener(e -> action.run());
        return button;
    }

    private static JPanel buttonRow(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (JButton button : buttons) {
            panel.add(button);
        }
        return panel;
    }

    private void refreshRepository() {
        repoRoot = GitRepository.resolveRoot(view.getBuffer());
        changeModel.clear();
        logModel.clear();
        branchModel.clear();
        setControlsEnabled(false);
        updateRefButton();

        if (repoRoot == null) {
            caption.setText(jEdit.getProperty("git.no-repo"));
            appendOutput(jEdit.getProperty("git.no-repo"));
            return;
        }

        GitRunner.Result version = runner.run(repoRoot, "--version");
        if (!version.success()) {
            caption.setText(jEdit.getProperty("git.git-missing"));
            appendOutput(version.output);
            return;
        }

        caption.setText(jEdit.getProperty("git.caption", new String[] {repoRoot.getAbsolutePath()}));
        setControlsEnabled(true);
        updateRefButton();
        reloadChanges();
        reloadLog();
        reloadBranches();
    }

    private void setControlsEnabled(boolean enabled) {
        refButton.setEnabled(enabled);
        changeList.setEnabled(enabled);
        logList.setEnabled(enabled);
        branchList.setEnabled(enabled);
        commitMessage.setEnabled(enabled);
        output.setEnabled(enabled);
    }

    private void updateRefButton() {
        if (repoRoot == null) {
            refButton.setText(jEdit.getProperty("git.branch.menu"));
            refButton.setToolTipText(jEdit.getProperty("git.head.tooltip.none"));
            return;
        }
        GitHeadState head = GitHeadState.query(repoRoot, runner);
        refButton.setText(head.statusText().isBlank()
            ? jEdit.getProperty("git.branch.menu")
            : head.statusText());
        refButton.setToolTipText(head.tooltip());
    }

    private void reloadChanges() {
        changeModel.clear();
        GitRunner.Result result = runner.run(repoRoot, "status", "--porcelain", "-uall");
        if (!result.success()) {
            appendOutput(result.output);
            return;
        }
        for (GitModels.FileChange change : GitModels.parseStatus(result.output)) {
            changeModel.addElement(change);
        }
    }

    private void reloadLog() {
        logModel.clear();
        GitRunner.Result result = runner.run(repoRoot, "log",
            "--max-count=200",
            "--date=short",
            "--pretty=format:%H%x01%h%x01%an%x01%ad%x01%s");
        if (!result.success()) {
            appendOutput(result.output);
            return;
        }
        for (GitModels.Commit commit : GitModels.parseLog(result.output)) {
            logModel.addElement(commit);
        }
    }

    private void reloadBranches() {
        branchModel.clear();
        GitRunner.Result result = runner.run(repoRoot, "branch", "--list");
        if (!result.success()) {
            appendOutput(result.output);
            return;
        }
        for (GitModels.Branch branch : GitModels.parseBranches(result.output)) {
            branchModel.addElement(branch);
        }
    }

    private void runGitAndRefresh(String... args) {
        GitRunner.Result result = runner.run(repoRoot, args);
        appendOutput("$ git " + String.join(" ", args) + "\n" + result.output);
        if (result.success()) {
            refreshRepository();
            EditBus.send(new GitHeadChanged());
        }
    }

    private void stageSelected() {
        List<GitModels.FileChange> selected = changeList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        for (GitModels.FileChange change : selected) {
            if (change.isUntracked() || change.hasWorkTreeChanges() || change.isStaged()) {
                runGit("add", "--", change.path);
            }
        }
        reloadChanges();
    }

    private void unstageSelected() {
        List<GitModels.FileChange> selected = changeList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        for (GitModels.FileChange change : selected) {
            runGit("restore", "--staged", "--", change.path);
        }
        reloadChanges();
    }

    private void stageAll() {
        runGitAndRefresh("add", "-A");
    }

    private void unstageAll() {
        runGitAndRefresh("restore", "--staged", ".");
    }

    private void discardSelected() {
        List<GitModels.FileChange> selected = changeList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        if (!confirm(jEdit.getProperty("git.discard.confirm"))) {
            return;
        }
        for (GitModels.FileChange change : selected) {
            if (change.isUntracked()) {
                runGit("clean", "-fd", "--", change.path);
            } else {
                runGit("restore", "--", change.path);
            }
        }
        reloadChanges();
    }

    private void openSelectedFile() {
        GitModels.FileChange change = changeList.getSelectedValue();
        if (change == null || repoRoot == null) {
            return;
        }
        File file = GitRepository.resolveFile(repoRoot, change.path);
        if (file == null) {
            JOptionPane.showMessageDialog(view,
                jEdit.getProperty("git.file-missing", new String[] {change.path}),
                jEdit.getProperty("git.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        Hashtable<String, Object> props = new Hashtable<>();
        jEdit.openFile(view, null, file.getAbsolutePath(), false, props);
    }

    private void diffSelected() {
        openDiffViewer();
    }

    private void openDiffViewer() {
        GitModels.FileChange change = changeList.getSelectedValue();
        if (change == null || repoRoot == null) {
            JOptionPane.showMessageDialog(view,
                jEdit.getProperty("git.diff.no-selection"),
                jEdit.getProperty("git.title"),
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        GitDiffDialog.show(view, repoRoot, change, runner, this::reloadChanges);
    }

    private void commitChanges() {
        String message = commitMessage.getText().trim();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(view,
                jEdit.getProperty("git.commit.empty-message"),
                jEdit.getProperty("git.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        GitRunner.Result result = runner.run(repoRoot, "commit", "-m", message);
        appendOutput(result.output);
        if (result.success()) {
            commitMessage.setText("");
            refreshRepository();
        }
    }

    private void commitAll() {
        stageAll();
        commitChanges();
    }

    private void showSelectedCommit() {
        GitModels.Commit commit = logList.getSelectedValue();
        if (commit == null || repoRoot == null) {
            return;
        }
        GitRunner.Result result = runner.run(repoRoot, "show", "--stat", commit.hash);
        openDiffBuffer("commit " + commit.shortHash, result.output);
    }

    private void diffSelectedCommit() {
        GitModels.Commit commit = logList.getSelectedValue();
        if (commit == null || repoRoot == null) {
            return;
        }
        GitRunner.Result result = runner.run(repoRoot, "show", commit.hash);
        openDiffBuffer("commit " + commit.shortHash, result.output);
    }

    private void checkoutSelectedCommit() {
        GitModels.Commit commit = logList.getSelectedValue();
        if (commit == null || repoRoot == null) {
            return;
        }
        if (!confirm(jEdit.getProperty("git.checkout-commit.confirm",
            new String[] {commit.shortHash}))) {
            return;
        }
        runGitAndRefresh("checkout", commit.hash);
    }

    private void checkoutSelectedBranch() {
        GitModels.Branch branch = branchList.getSelectedValue();
        if (branch == null || repoRoot == null) {
            return;
        }
        if (!confirm(jEdit.getProperty("git.checkout-branch.confirm",
            new String[] {branch.name}))) {
            return;
        }
        runGitAndRefresh("checkout", branch.name);
    }

    private void createBranch(String name) {
        if (name == null || name.isBlank() || repoRoot == null) {
            return;
        }
        runGitAndRefresh("checkout", "-b", name.trim());
    }

    private void deleteSelectedBranch() {
        GitModels.Branch branch = branchList.getSelectedValue();
        if (branch == null || repoRoot == null || branch.current) {
            return;
        }
        if (!confirm(jEdit.getProperty("git.delete-branch.confirm",
            new String[] {branch.name}))) {
            return;
        }
        runGitAndRefresh("branch", "-d", branch.name);
    }

    private void runGit(String... args) {
        GitRunner.Result result = runner.run(repoRoot, args);
        appendOutput("$ git " + String.join(" ", args) + "\n" + result.output);
    }

    private void runGitAsync(String... args) {
        appendOutput("$ git " + String.join(" ", args));
        runner.runAsync(repoRoot, line -> appendOutput(line), () -> {
            refreshRepository();
            EditBus.send(new GitHeadChanged());
        }, args);
    }

    private void openDiffBuffer(String title, String text) {
        if (text == null || text.isBlank()) {
            text = jEdit.getProperty("git.diff.empty-body",
                new String[] {title != null ? title : ""});
        }
        String finalText = text;
        String bufferName = "git-diff-" + sanitizeBufferName(title)
            + "-" + System.currentTimeMillis();
        SwingUtilities.invokeLater(() -> {
            Hashtable<String, Object> props = new Hashtable<>();
            Buffer buffer = jEdit.openTemporary(view, null, bufferName, true, props, true);
            if (buffer == null) {
                JOptionPane.showMessageDialog(view,
                    jEdit.getProperty("git.diff.open-failed"),
                    jEdit.getProperty("git.title"),
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
            buffer.beginCompoundEdit();
            if (buffer.getLength() > 0) {
                buffer.remove(0, buffer.getLength());
            }
            buffer.insert(0, finalText);
            buffer.endCompoundEdit();
            buffer.setProperty("folding", "none");
            jEdit.commitTemporary(buffer);
            view.setBuffer(buffer);
        });
    }

    private static String sanitizeBufferName(String title) {
        if (title == null || title.isBlank()) {
            return "untitled";
        }
        String safe = title.replace('\\', '/');
        int slash = safe.lastIndexOf('/');
        if (slash >= 0) {
            safe = safe.substring(slash + 1);
        }
        return safe.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private void appendOutput(String line) {
        SwingUtilities.invokeLater(() -> {
            if (output.getText().length() > 0) {
                output.append("\n");
            }
            output.append(line);
            output.setCaretPosition(output.getDocument().getLength());
        });
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(view, message,
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
}
