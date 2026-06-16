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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.Mode;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.BufferListener;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.syntax.ModeProvider;
import org.gjt.sp.jedit.textarea.JEditEmbeddedTextArea;
import org.gjt.sp.util.ThreadUtilities;

final class GitDiffDialog extends EnhancedDialog {

    private final View view;
    private final File repoRoot;
    private final GitModels.FileChange change;
    private final GitRunner runner;
    private final Runnable onRepositoryChanged;
    private final GitDiffLoader.Sides sides;
    private final JEditEmbeddedTextArea leftArea;
    private final JEditEmbeddedTextArea rightArea;
    private final GitDiffHighlight leftHighlight;
    private final GitDiffHighlight rightHighlight;
    private final JButton saveButton;
    private final JButton revertChangeButton;
    private final JButton revertAllButton;
    private final JLabel changeCaption;
    private GitLineDiff.Result diffResult = new GitLineDiff.Result(0, 0);
    private int currentChange;
    private boolean dirty;
    private boolean syncingScroll;
    private boolean refreshingDiff;

    static void show(View view, File repoRoot, GitModels.FileChange change,
                     GitRunner runner, Runnable onRepositoryChanged) {
        ThreadUtilities.runInBackground(() -> {
            GitRunner loaderRunner = new GitRunner();
            GitDiffLoader.Sides sides = GitDiffLoader.load(repoRoot, change, loaderRunner);
            SwingUtilities.invokeLater(() -> {
                GitDiffDialog dialog = new GitDiffDialog(
                    view, repoRoot, change, runner, onRepositoryChanged, sides);
                dialog.setVisible(true);
            });
        });
    }

    private GitDiffDialog(View view, File repoRoot, GitModels.FileChange change,
                          GitRunner runner, Runnable onRepositoryChanged,
                          GitDiffLoader.Sides sides) {
        super(view, jEdit.getProperty("git.diff-viewer.title",
            new String[] {change.path}), false);
        this.view = view;
        this.repoRoot = repoRoot;
        this.change = change;
        this.runner = runner;
        this.onRepositoryChanged = onRepositoryChanged;
        this.sides = sides;

        setEnterEnabled(false);
        GUIUtilities.loadGeometry(this, "git-diff-viewer");

        leftArea = createEditor(false);
        rightArea = createEditor(sides.rightEditable);
        leftHighlight = new GitDiffHighlight(leftArea, true);
        rightHighlight = new GitDiffHighlight(rightArea, false);
        GitDiffHighlight.install(leftArea, leftHighlight);
        GitDiffHighlight.install(rightArea, rightHighlight);

        setEditorText(leftArea, sides.leftText);
        setEditorText(rightArea, sides.rightText);
        applyMode(leftArea, change.path);
        applyMode(rightArea, change.path);

        if (sides.rightEditable) {
            BufferListener dirtyListener = new BufferAdapter() {
                @Override
                public void contentInserted(JEditBuffer buffer, int startLine, int offset,
                                            int numLines, int length) {
                    markDirty();
                }

                @Override
                public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
                                           int numLines, int length) {
                    markDirty();
                }
            };
            rightArea.getBuffer().addBufferListener(dirtyListener);
        }

        leftArea.setPreferredSize(new Dimension(480, 520));
        rightArea.setPreferredSize(new Dimension(480, 520));

        JPanel leftPanel = buildSidePanel(sides.leftLabel, leftArea);
        JPanel rightPanel = buildSidePanel(sides.rightLabel, rightArea);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.5);
        split.setOneTouchExpandable(true);

        changeCaption = new JLabel(" ");
        changeCaption.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

        saveButton = new JButton(jEdit.getProperty("git.diff-viewer.save"));
        saveButton.setEnabled(sides.canSave);
        saveButton.addActionListener(e -> saveWorkingCopy());

        revertChangeButton = new JButton(jEdit.getProperty("git.diff-viewer.revert-change"));
        revertChangeButton.setEnabled(sides.rightEditable);
        revertChangeButton.addActionListener(e -> revertCurrentChange());

        revertAllButton = new JButton(jEdit.getProperty("git.diff-viewer.revert-all"));
        revertAllButton.setEnabled(sides.rightEditable);
        revertAllButton.addActionListener(e -> revertAllChanges());

        JButton discardFileButton = new JButton(jEdit.getProperty("git.diff-viewer.discard-file"));
        discardFileButton.setEnabled(sides.canRevert);
        discardFileButton.addActionListener(e -> discardFileFromGit());

        JButton previousChangeButton = new RolloverButton(
            IconManager.loadIcon("MatIcons.KEYBOARD_ARROW_UP:22"),
            jEdit.getProperty("git.diff-viewer.previous-change"));
        previousChangeButton.addActionListener(e -> goToChange(-1));

        JButton nextChangeButton = new RolloverButton(
            IconManager.loadIcon("MatIcons.KEYBOARD_ARROW_DOWN:22"),
            jEdit.getProperty("git.diff-viewer.next-change"));
        nextChangeButton.addActionListener(e -> goToChange(1));

        JButton closeButton = new JButton(jEdit.getProperty("common.close"));
        closeButton.addActionListener(e -> requestClose());

        JPanel buttons = new JPanel(new BorderLayout(0, 4));
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        navButtons.add(previousChangeButton);
        navButtons.add(nextChangeButton);
        navButtons.add(changeCaption);
        buttons.add(navButtons, BorderLayout.WEST);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionButtons.add(revertChangeButton);
        actionButtons.add(revertAllButton);
        actionButtons.add(discardFileButton);
        actionButtons.add(saveButton);
        actionButtons.add(closeButton);
        buttons.add(actionButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(split, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        pack();
        setLocationRelativeTo(view);

        SwingUtilities.invokeLater(() -> {
            setupScrollSync();
            refreshDiffHighlights();
            goToChange(0);
        });
    }

    @Override
    public void ok() {
        requestClose();
    }

    @Override
    public void cancel() {
        requestClose();
    }

    private void requestClose() {
        if (dirty && sides.canSave) {
            int choice = JOptionPane.showConfirmDialog(this,
                jEdit.getProperty("git.diff-viewer.unsaved"),
                jEdit.getProperty("git.diff-viewer.title",
                    new String[] {change.path}),
                JOptionPane.YES_NO_CANCEL_OPTION);
            if (choice == JOptionPane.CANCEL_OPTION) {
                return;
            }
            if (choice == JOptionPane.YES_OPTION && !saveWorkingCopy()) {
                return;
            }
        }
        GUIUtilities.saveGeometry(this, "git-diff-viewer");
        dispose();
    }

    private static JPanel buildSidePanel(String label, JEditEmbeddedTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JLabel header = new JLabel(label);
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        panel.add(header, BorderLayout.NORTH);
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    private static JEditEmbeddedTextArea createEditor(boolean editable) {
        JEditEmbeddedTextArea area = new JEditEmbeddedTextArea();
        area.getPainter().setLineHighlightEnabled(false);
        area.getBuffer().setReadOnly(!editable);
        return area;
    }

    private static void setEditorText(JEditEmbeddedTextArea area, String text) {
        JEditBuffer buffer = area.getBuffer();
        buffer.beginCompoundEdit();
        if (buffer.getLength() > 0) {
            buffer.remove(0, buffer.getLength());
        }
        if (text != null && !text.isEmpty()) {
            buffer.insert(0, text);
        }
        buffer.endCompoundEdit();
        buffer.setDirty(false);
    }

    private static void applyMode(JEditEmbeddedTextArea area, String path) {
        JEditBuffer buffer = area.getBuffer();
        String firstLine = buffer.getLineCount() > 0 ? buffer.getLineText(0) : null;
        Mode mode = ModeProvider.instance.getModeForFile(path, null, firstLine);
        if (mode == null) {
            mode = ModeProvider.instance.getMode("text");
        }
        buffer.setMode(mode);
    }

    private void markDirty() {
        if (refreshingDiff) {
            return;
        }
        dirty = true;
        saveButton.setEnabled(sides.canSave);
        refreshDiffHighlights();
    }

    private void refreshDiffHighlights() {
        refreshingDiff = true;
        try {
            String leftText = leftArea.getBuffer().getText(0, leftArea.getBuffer().getLength());
            String rightText = rightArea.getBuffer().getText(0, rightArea.getBuffer().getLength());
            diffResult = GitLineDiff.compute(leftText, rightText);
            leftHighlight.setDiff(diffResult);
            rightHighlight.setDiff(diffResult);
            updateChangeCaption();
            boolean hasChanges = !diffResult.changes.isEmpty();
            if (revertChangeButton != null) {
                revertChangeButton.setEnabled(sides.rightEditable && hasChanges);
            }
            if (revertAllButton != null) {
                revertAllButton.setEnabled(sides.rightEditable && hasChanges);
            }
            if (currentChange >= diffResult.changes.size()) {
                currentChange = Math.max(0, diffResult.changes.size() - 1);
            }
        } finally {
            refreshingDiff = false;
        }
    }

    private void updateChangeCaption() {
        if (changeCaption == null) {
            return;
        }
        int total = diffResult.changes.size();
        if (total == 0) {
            changeCaption.setText(jEdit.getProperty("git.diff-viewer.no-changes"));
            return;
        }
        changeCaption.setText(jEdit.getProperty("git.diff-viewer.change-caption",
            new String[] {
                String.valueOf(currentChange + 1),
                String.valueOf(total)
            }));
    }

    private void setupScrollSync() {
        JScrollBar leftVertical = GitDiffHighlight.findVerticalScrollBar(leftArea);
        JScrollBar rightVertical = GitDiffHighlight.findVerticalScrollBar(rightArea);
        if (leftVertical != null && rightVertical != null) {
            linkVerticalScroll(leftVertical, rightVertical, true);
            linkVerticalScroll(rightVertical, leftVertical, false);
        }
    }

    private void linkVerticalScroll(JScrollBar source, JScrollBar target, boolean leftToRight) {
        source.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                if (syncingScroll) {
                    return;
                }
                syncingScroll = true;
                try {
                    int sourceLine = source.getValue();
                    int mapped = leftToRight
                        ? GitLineDiff.mapLeftToRight(diffResult, sourceLine)
                        : GitLineDiff.mapRightToLeft(diffResult, sourceLine);
                    target.setValue(mapped);
                } finally {
                    syncingScroll = false;
                }
            }
        });
    }

    private void goToChange(int direction) {
        if (diffResult.changes.isEmpty()) {
            updateChangeCaption();
            return;
        }
        if (direction == 0) {
            currentChange = 0;
        } else {
            currentChange += direction;
            if (currentChange < 0) {
                currentChange = diffResult.changes.size() - 1;
            } else if (currentChange >= diffResult.changes.size()) {
                currentChange = 0;
            }
        }
        GitLineDiff.Change changeRegion = diffResult.changes.get(currentChange);
        navigateToCurrentChange();
    }

    private void navigateToCurrentChange() {
        if (diffResult.changes.isEmpty()) {
            updateChangeCaption();
            return;
        }
        GitLineDiff.Change changeRegion = diffResult.changes.get(currentChange);
        if (changeRegion.leftStart >= 0) {
            goToLine(leftArea, changeRegion.leftStart, false);
        }
        int rightLine;
        if (changeRegion.rightStart >= 0 && changeRegion.rightEnd >= changeRegion.rightStart) {
            rightLine = changeRegion.rightStart;
        } else if (changeRegion.isDelete()) {
            rightLine = changeRegion.rightStart;
        } else if (changeRegion.leftStart >= 0) {
            rightLine = GitLineDiff.mapLeftToRight(diffResult, changeRegion.leftStart);
        } else {
            rightLine = 0;
        }
        goToLine(rightArea, rightLine, sides.rightEditable);
        updateChangeCaption();
    }

    private static void goToLine(JEditEmbeddedTextArea area, int line, boolean focus) {
        JEditBuffer buffer = area.getBuffer();
        if (buffer.getLineCount() == 0) {
            return;
        }
        line = Math.max(0, Math.min(line, buffer.getLineCount() - 1));
        area.setCaretPosition(buffer.getLineStartOffset(line));
        area.goToStartOfLine(false);
        area.scrollToCaret(true);
        if (focus) {
            area.requestFocusInWindow();
        }
    }

    private void revertCurrentChange() {
        if (!sides.rightEditable || diffResult.changes.isEmpty()) {
            return;
        }
        revertChange(diffResult.changes.get(currentChange));
    }

    private void revertAllChanges() {
        if (!sides.rightEditable) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("git.diff-viewer.revert-all.confirm"),
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        String leftText = leftArea.getBuffer().getText(0, leftArea.getBuffer().getLength());
        setEditorText(rightArea, leftText);
        dirty = true;
        saveButton.setEnabled(sides.canSave);
        refreshDiffHighlights();
    }

    private void revertChange(GitLineDiff.Change changeRegion) {
        JEditBuffer left = leftArea.getBuffer();
        JEditBuffer right = rightArea.getBuffer();
        boolean wasReadOnly = right.isReadOnly();
        right.setReadOnly(false);
        right.beginCompoundEdit();
        try {
            if (changeRegion.isInsert()) {
                removeLines(right, changeRegion.rightStart, changeRegion.rightEnd);
            } else if (changeRegion.isDelete()) {
                insertLines(right, changeRegion.rightStart, left,
                    changeRegion.leftStart, changeRegion.leftEnd);
            } else if (changeRegion.isReplace()) {
                removeLines(right, changeRegion.rightStart, changeRegion.rightEnd);
                insertLines(right, changeRegion.rightStart, left,
                    changeRegion.leftStart, changeRegion.leftEnd);
            }
        } finally {
            right.endCompoundEdit();
            right.setReadOnly(wasReadOnly);
        }
        dirty = true;
        saveButton.setEnabled(sides.canSave);
        refreshDiffHighlights();
        if (!diffResult.changes.isEmpty()) {
            currentChange = Math.min(currentChange, diffResult.changes.size() - 1);
            navigateToCurrentChange();
        }
    }

    private static void removeLines(JEditBuffer buffer, int startLine, int endLine) {
        if (startLine < 0 || endLine < startLine || buffer.getLineCount() == 0) {
            return;
        }
        startLine = Math.min(startLine, buffer.getLineCount() - 1);
        endLine = Math.min(endLine, buffer.getLineCount() - 1);
        int start = buffer.getLineStartOffset(startLine);
        int end = buffer.getLineEndOffset(endLine);
        if (endLine < buffer.getLineCount() - 1) {
            end++;
        }
        buffer.remove(start, end - start);
    }

    private static void insertLines(JEditBuffer target, int atLine, JEditBuffer source,
                                    int startLine, int endLine) {
        if (startLine < 0 || endLine < startLine) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int line = startLine; line <= endLine; line++) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(source.getLineText(line));
        }
        if (text.length() == 0) {
            return;
        }
        int offset;
        if (target.getLineCount() == 0) {
            offset = 0;
        } else if (atLine >= target.getLineCount()) {
            offset = target.getLength();
            if (offset > 0 && target.getText(offset - 1, 1).charAt(0) != '\n') {
                text.insert(0, '\n');
            }
        } else {
            offset = target.getLineStartOffset(atLine);
            if (offset > 0) {
                text.insert(0, '\n');
            }
        }
        target.insert(offset, text.toString());
    }

    private boolean saveWorkingCopy() {
        if (!sides.canSave || sides.workingFile == null) {
            return true;
        }
        try {
            String text = rightArea.getBuffer().getText(0, rightArea.getBuffer().getLength());
            Files.writeString(sides.workingFile.toPath(), text, Charset.defaultCharset());
            Buffer buffer = jEdit.getBufferManager()
                .getBuffer(sides.workingFile.getAbsolutePath()).orElse(null);
            if (buffer != null) {
                buffer.load(view, true);
            }
            dirty = false;
            refreshDiffHighlights();
            if (onRepositoryChanged != null) {
                onRepositoryChanged.run();
            }
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                jEdit.getProperty("git.diff-viewer.save-failed",
                    new String[] {ex.getMessage() != null ? ex.getMessage() : ex.toString()}),
                jEdit.getProperty("git.title"),
                JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void discardFileFromGit() {
        if (!sides.canRevert) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
            jEdit.getProperty("git.discard.confirm"),
            jEdit.getProperty("git.title"),
            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        File root = repoRoot;
        String path = change.path;
        boolean untracked = change.isUntracked();
        ThreadUtilities.runInBackground(() -> {
            GitRunner bgRunner = new GitRunner();
            if (untracked) {
                bgRunner.run(root, "clean", "-fd", "--", path);
            } else {
                bgRunner.run(root, "restore", "--", path);
            }
            SwingUtilities.invokeLater(() -> {
                dirty = false;
                if (onRepositoryChanged != null) {
                    onRepositoryChanged.run();
                }
                GUIUtilities.saveGeometry(this, "git-diff-viewer");
                dispose();
            });
        });
    }
}
