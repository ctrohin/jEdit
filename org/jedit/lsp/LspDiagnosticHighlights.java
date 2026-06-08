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

package org.jedit.lsp;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaPainter;

/**
 * Installs {@link LspDiagnosticHighlight} on text areas and repaints when diagnostics change.
 */
final class LspDiagnosticHighlights implements EBComponent {

    private static final LspDiagnosticHighlights INSTANCE = new LspDiagnosticHighlights();

    private final Map<JEditTextArea, LspDiagnosticHighlight> extensions =
        new IdentityHashMap<>();
    private final Map<JEditTextArea, LspDiagnosticTooltip> tooltips =
        new IdentityHashMap<>();
    private final Runnable diagnosticsListener = this::repaintAll;

    private LspDiagnosticHighlights() {}

    static void install() {
        LspDiagnosticsHub.getInstance().addListener(INSTANCE.diagnosticsListener);
        EditBus.addToBus(INSTANCE);
        INSTANCE.attachAllTextAreas();
    }

    static void uninstall() {
        LspDiagnosticsHub.getInstance().removeListener(INSTANCE.diagnosticsListener);
        EditBus.removeFromBus(INSTANCE);
        INSTANCE.detachAllTextAreas();
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (!(message instanceof EditPaneUpdate update)) {
            return;
        }
        JEditTextArea textArea = update.getEditPane().getTextArea();
        if (update.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
            LspDiagnosticTooltip tooltip = tooltips.get(textArea);
            if (tooltip != null) {
                tooltip.hide();
            }
        }
        if (update.getWhat() == EditPaneUpdate.CREATED
            || update.getWhat() == EditPaneUpdate.BUFFER_CHANGED) {
            ensureHighlight(textArea);
            repaintTextArea(textArea);
        }
    }

    private void attachAllTextAreas() {
        for (View view : jEdit.getViews()) {
            for (EditPane editPane : view.getEditPanes()) {
                ensureHighlight(editPane.getTextArea());
            }
        }
    }

    private void detachAllTextAreas() {
        for (Map.Entry<JEditTextArea, LspDiagnosticHighlight> entry : extensions.entrySet()) {
            entry.getKey().getPainter().removeExtension(entry.getValue());
        }
        extensions.clear();
        for (LspDiagnosticTooltip tooltip : tooltips.values()) {
            tooltip.dispose();
        }
        tooltips.clear();
    }

    private void ensureHighlight(JEditTextArea textArea) {
        if (!extensions.containsKey(textArea)) {
            LspDiagnosticHighlight highlight = new LspDiagnosticHighlight(textArea);
            textArea.getPainter().addExtension(
                TextAreaPainter.BELOW_MOST_EXTENSIONS_LAYER, highlight);
            extensions.put(textArea, highlight);
        }
        if (!tooltips.containsKey(textArea)) {
            tooltips.put(textArea, new LspDiagnosticTooltip(textArea));
        }
    }

    private void repaintAll() {
        for (View view : jEdit.getViews()) {
            for (EditPane editPane : view.getEditPanes()) {
                JEditTextArea textArea = editPane.getTextArea();
                ensureHighlight(textArea);
                repaintTextArea(textArea);
            }
        }
    }

    private void repaintTextArea(JEditTextArea textArea) {
        if (!(textArea.getBuffer() instanceof Buffer buffer)) {
            textArea.getPainter().repaint();
            return;
        }

        List<LspDiagnosticProblem> problems =
            LspDiagnosticsHub.getInstance().getProblemsForBuffer(buffer);
        if (problems.isEmpty()) {
            int visibleLines = textArea.getVisibleLines();
            if (visibleLines > 0) {
                textArea.invalidateScreenLineRange(0, visibleLines - 1);
            } else {
                textArea.getPainter().repaint();
            }
            return;
        }

        int minLine = Integer.MAX_VALUE;
        int maxLine = 0;
        for (LspDiagnosticProblem problem : problems) {
            minLine = Math.min(minLine, problem.getLine());
            maxLine = Math.max(maxLine, problem.getEndLine());
        }
        if (minLine == Integer.MAX_VALUE) {
            textArea.getPainter().repaint();
            return;
        }
        if (maxLine >= buffer.getLineCount()) {
            maxLine = Math.max(0, buffer.getLineCount() - 1);
        }
        textArea.invalidateLineRange(minLine, maxLine);
    }
}
