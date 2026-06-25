/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Map;
import java.util.WeakHashMap;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

/**
 * Highlights merge conflict marker lines in open buffers.
 */
public final class GitMergeConflictSupport implements EBComponent {

    private static final Color CONFLICT_COLOR = new Color(0xFFCDD2);

    private static GitMergeConflictSupport instance;
    private final Map<EditPane, ConflictExtension> extensions = new WeakHashMap<>();

    public static void install() {
        if (instance == null) {
            instance = new GitMergeConflictSupport();
            EditBus.addToBus(instance);
            for (org.gjt.sp.jedit.View view : org.gjt.sp.jedit.jEdit.getViewManager().getViews()) {
                for (EditPane editPane : view.getEditPanes()) {
                    instance.installEditPane(editPane);
                }
            }
        }
    }

    public static void uninstall() {
        if (instance != null) {
            EditBus.removeFromBus(instance);
            instance = null;
        }
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof EditPaneUpdate update) {
            if (EditPaneUpdate.CREATED.equals(update.getWhat())) {
                installEditPane(update.getEditPane());
            } else if (EditPaneUpdate.DESTROYED.equals(update.getWhat())) {
                extensions.remove(update.getEditPane());
            }
        } else if (message instanceof BufferUpdate bufferUpdate) {
            Object what = bufferUpdate.getWhat();
            if (BufferUpdate.LOADED.equals(what) || BufferUpdate.SAVED.equals(what)) {
                repaintPanes(bufferUpdate.getBuffer());
            }
        }
    }

    private void installEditPane(EditPane editPane) {
        if (editPane == null || extensions.containsKey(editPane)) {
            return;
        }
        ConflictExtension extension = new ConflictExtension(editPane);
        editPane.getTextArea().getPainter().addExtension(
            TextAreaPainter.LINE_BACKGROUND_LAYER, extension);
        extensions.put(editPane, extension);
    }

    private void repaintPanes(Buffer buffer) {
        for (Map.Entry<EditPane, ConflictExtension> entry : extensions.entrySet()) {
            if (entry.getKey().getBuffer() == buffer) {
                entry.getKey().getTextArea().repaint();
            }
        }
    }

    private static boolean isConflictLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        return trimmed.startsWith("<<<<<<<")
            || trimmed.startsWith("=======")
            || trimmed.startsWith(">>>>>>>");
    }

    private static final class ConflictExtension extends TextAreaExtension {
        private final EditPane editPane;

        ConflictExtension(EditPane editPane) {
            this.editPane = editPane;
        }

        @Override
        public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,
                                   int start, int end, int y) {
            JEditTextArea textArea = editPane.getTextArea();
            Buffer buffer = editPane.getBuffer();
            if (buffer == null || physicalLine < 0 || physicalLine >= buffer.getLineCount()) {
                return;
            }
            String line = buffer.getLineText(physicalLine);
            if (!isConflictLine(line)) {
                return;
            }
            gfx.setColor(CONFLICT_COLOR);
            gfx.fillRect(0, y, textArea.getPainter().getWidth(),
                textArea.getPainter().getFontMetrics().getHeight());
        }
    }
}
