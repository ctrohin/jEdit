/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.aiassist;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.input.AbstractInputHandler;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.EditorExiting;
import org.gjt.sp.jedit.msg.PositionChanging;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.jedit.textarea.TextArea;
import org.gjt.sp.util.ThreadUtilities;

final class AiInlineCompletionHub implements EBComponent {

    private static final int EMPTY_LINE_DELAY_MS = 400;

    private final AiInlineCompletionPopup popup = new AiInlineCompletionPopup();
    private final Timer idleTimer;
    private final BufferAdapter bufferAdapter = new BufferAdapter() {
        @Override
        public void contentInserted(JEditBuffer buffer, int startLine, int offset,
            int numLines, int length) {
            onUserActivity("buffer insert");
        }

        @Override
        public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
            int numLines, int length) {
            onUserActivity("buffer remove");
        }
    };
    private final CaretListener caretListener = new CaretListener() {
        @Override
        public void caretUpdate(CaretEvent e) {
            onUserActivity("caret move");
        }
    };
    private final FocusListener focusListener = new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
            AiAssistLog.debug("text area focus gained");
            refreshActiveTextArea();
        }

        @Override
        public void focusLost(FocusEvent e) {
            dismissSuggestion("focus lost");
        }
    };

    private final KeyListener tabInterceptor = new KeyListener() {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.isConsumed()) {
                return;
            }
            if (e.getKeyCode() == KeyEvent.VK_TAB && !e.isShiftDown() && acceptSuggestion(e)) {
                e.consume();
                return;
            }
            delegateKeyEvent(e);
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (e.isConsumed()) {
                return;
            }
            if (e.getKeyChar() == '\t' && !e.isShiftDown() && acceptSuggestion(e)) {
                e.consume();
                return;
            }
            delegateKeyEvent(e);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (!e.isConsumed()) {
                delegateKeyEvent(e);
            }
        }
    };

    private final AWTEventListener mouseMotionListener = this::handleMouseMotion;

    private volatile TextArea activeTextArea;
    private volatile JEditBuffer watchedBuffer;
    private volatile String currentSuggestion;
    private volatile AiInlineCompletionContext currentContext;
    private volatile int requestGeneration;
    private volatile boolean acceptingSuggestion;
    private volatile long lastActivityNanos;

    AiInlineCompletionHub() {
        idleTimer = new Timer(AiAssistConfig.idleDelayMs(), e -> onIdleTimer());
        idleTimer.setRepeats(false);
        lastActivityNanos = System.nanoTime();
    }

    void install() {
        AiAssistLog.message("inline assist hub installing (provider="
            + AiAssistConfig.provider()
            + ", enabled=" + AiAssistConfig.inlineEnabled()
            + ", idleMs=" + AiAssistConfig.idleDelayMs()
            + ", cursor=" + AiInlineCompletionService.isCursorAvailable()
            + ", copilot=" + AiInlineCompletionService.isCopilotAvailable() + ")");
        EditBus.addToBus(this);
        Toolkit.getDefaultToolkit().addAWTEventListener(
            mouseMotionListener, AWTEvent.MOUSE_MOTION_EVENT_MASK);
        refreshActiveTextArea();
    }

    void uninstall() {
        AiAssistLog.message("inline assist hub uninstalling");
        EditBus.removeFromBus(this);
        Toolkit.getDefaultToolkit().removeAWTEventListener(mouseMotionListener);
        idleTimer.stop();
        detachBufferListener();
        detachCaretListener();
        detachFocusListener();
        removeTabInterceptor();
        dismissSuggestion("uninstall");
        popup.dispose();
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof EditorExiting) {
            uninstall();
            return;
        }
        if (message instanceof ViewUpdate viewUpdate) {
            Object what = viewUpdate.getWhat();
            if (what == ViewUpdate.ACTIVATED || what == ViewUpdate.EDIT_PANE_CHANGED) {
                AiAssistLog.debug("view update: " + what);
                refreshActiveTextArea();
            }
            return;
        }
        if (message instanceof EditPaneUpdate editPaneUpdate) {
            Object what = editPaneUpdate.getWhat();
            if (what == EditPaneUpdate.BUFFER_CHANGED
                || what == EditPaneUpdate.BUFFER_CHANGING
                || what == EditPaneUpdate.CREATED
                || what == EditPaneUpdate.DESTROYED) {
                AiAssistLog.debug("edit pane update: " + what);
                refreshActiveTextArea();
                dismissSuggestion("edit pane " + what);
            }
            return;
        }
        if (message instanceof PositionChanging) {
            onUserActivity("position changing");
            return;
        }
        if (message instanceof BufferUpdate bufferUpdate
            && bufferUpdate.getWhat() == BufferUpdate.CLOSED) {
            if (bufferUpdate.getBuffer() == watchedBuffer) {
                detachBufferListener();
                dismissSuggestion("buffer closed");
            }
        }
    }

    private void refreshActiveTextArea() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshActiveTextArea);
            return;
        }
        View view = jEdit.getActiveView();
        TextArea textArea = view != null ? view.getTextArea() : null;
        if (textArea != activeTextArea) {
            removeTabInterceptor();
            detachCaretListener();
            detachFocusListener();
            activeTextArea = textArea;
            attachBufferListener(textArea != null ? textArea.getBuffer() : null);
            attachCaretListener(textArea);
            attachFocusListener(textArea);
            dismissSuggestion("active text area changed");
            AiAssistLog.message("active text area "
                + (textArea != null ? "attached" : "cleared")
                + (textArea != null && editBuffer(textArea) != null
                    ? " (" + editBuffer(textArea).getPath() + ")" : ""));
        }
        if (textArea != null) {
            scheduleSuggestion();
        }
    }

    private void attachBufferListener(JEditBuffer buffer) {
        if (watchedBuffer == buffer) {
            return;
        }
        detachBufferListener();
        watchedBuffer = buffer;
        if (buffer != null) {
            buffer.addBufferListener(bufferAdapter);
        }
    }

    private void detachBufferListener() {
        if (watchedBuffer != null) {
            watchedBuffer.removeBufferListener(bufferAdapter);
            watchedBuffer = null;
        }
    }

    private void attachCaretListener(TextArea textArea) {
        if (textArea != null) {
            textArea.addCaretListener(caretListener);
        }
    }

    private void detachCaretListener() {
        TextArea textArea = activeTextArea;
        if (textArea != null) {
            textArea.removeCaretListener(caretListener);
        }
    }

    private void attachFocusListener(TextArea textArea) {
        if (textArea != null) {
            textArea.addFocusListener(focusListener);
            textArea.getPainter().addFocusListener(focusListener);
        }
    }

    private void detachFocusListener() {
        TextArea textArea = activeTextArea;
        if (textArea != null) {
            textArea.removeFocusListener(focusListener);
            textArea.getPainter().removeFocusListener(focusListener);
        }
    }

    private void installTabInterceptor() {
        TextArea textArea = activeTextArea;
        if (textArea == null || !hasActiveSuggestion()) {
            return;
        }
        AbstractInputHandler<?> handler = textArea.getInputHandler();
        handler.setKeyEventInterceptor(tabInterceptor);
        AiAssistLog.message("tab interceptor installed on text area input handler");
    }

    private void removeTabInterceptor() {
        TextArea textArea = activeTextArea;
        if (textArea == null) {
            return;
        }
        AbstractInputHandler<?> handler = textArea.getInputHandler();
        if (handler.getKeyEventInterceptor() == tabInterceptor) {
            handler.setKeyEventInterceptor(null);
            AiAssistLog.debug("tab interceptor removed");
        }
    }

    private void delegateKeyEvent(KeyEvent e) {
        TextArea textArea = activeTextArea;
        if (textArea == null) {
            return;
        }
        AbstractInputHandler<?> handler = textArea.getInputHandler();
        if (handler.getKeyEventInterceptor() != tabInterceptor) {
            return;
        }
        handler.setKeyEventInterceptor(null);
        try {
            handler.processKeyEvent(e, 1, false);
        } finally {
            if (hasActiveSuggestion()) {
                handler.setKeyEventInterceptor(tabInterceptor);
            }
        }
    }

    private boolean hasActiveSuggestion() {
        String suggestion = currentSuggestion;
        return suggestion != null && !suggestion.isBlank() && currentContext != null;
    }

    private void handleMouseMotion(AWTEvent event) {
        if (!(event instanceof MouseEvent mouseEvent)) {
            return;
        }
        if (mouseEvent.getID() != MouseEvent.MOUSE_MOVED) {
            return;
        }
        TextArea textArea = activeTextArea;
        if (textArea == null || !isEventInTextArea(mouseEvent, textArea)) {
            return;
        }
        onUserActivity("mouse move in editor");
    }

    private static boolean isEventInTextArea(MouseEvent event, TextArea textArea) {
        Component source = event.getComponent();
        if (source == textArea || source == textArea.getPainter()) {
            return true;
        }
        Component walk = source;
        while (walk != null) {
            if (walk == textArea) {
                return true;
            }
            walk = walk.getParent();
        }
        return false;
    }

    private boolean acceptSuggestion(KeyEvent event) {
        if (event != null) {
            boolean tabPressed = event.getID() == KeyEvent.KEY_PRESSED
                && event.getKeyCode() == KeyEvent.VK_TAB && !event.isShiftDown();
            boolean tabTyped = event.getID() == KeyEvent.KEY_TYPED
                && event.getKeyChar() == '\t' && !event.isShiftDown();
            if (!tabPressed && !tabTyped) {
                return false;
            }
        }
        if (!hasActiveSuggestion()) {
            AiAssistLog.debug("tab accept skipped: no active suggestion");
            return false;
        }
        String suggestion = currentSuggestion;
        AiInlineCompletionContext context = currentContext;
        TextArea textArea = activeTextArea;
        if (textArea == null) {
            return false;
        }
        Buffer buffer = editBuffer(textArea);
        if (buffer == null || buffer.isClosed()) {
            AiAssistLog.message("tab accept failed: no buffer");
            return false;
        }
        int caret = textArea.getCaretPosition();
        if (caret != context.caret) {
            AiAssistLog.message("tab accept failed: caret moved from "
                + context.caret + " to " + caret);
            return false;
        }
        AiAssistLog.message("accepting inline suggestion (" + suggestion.length() + " chars)");
        removeTabInterceptor();
        acceptingSuggestion = true;
        try {
            buffer.insert(caret, suggestion);
        } finally {
            acceptingSuggestion = false;
        }
        dismissSuggestion("accepted");
        return true;
    }

    private void onUserActivity(String reason) {
        lastActivityNanos = System.nanoTime();
        AiAssistLog.debug("activity: " + reason);
        if (!acceptingSuggestion) {
            dismissSuggestion(reason);
        }
        scheduleSuggestion();
    }

    private void scheduleSuggestion() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::scheduleSuggestion);
            return;
        }
        if (!AiAssistConfig.inlineEnabled() || AiAssistConfig.provider() == AiAssistProvider.OFF) {
            AiAssistLog.debug("schedule skipped: inline disabled");
            idleTimer.stop();
            return;
        }
        TextArea textArea = activeTextArea;
        if (textArea == null) {
            AiAssistLog.debug("schedule skipped: no active text area");
            idleTimer.stop();
            return;
        }
        Buffer buffer = editBuffer(textArea);
        if (buffer == null) {
            AiAssistLog.debug("schedule skipped: no edit buffer");
            idleTimer.stop();
            return;
        }
        if (buffer.isClosed() || !buffer.isLoaded()) {
            AiAssistLog.debug("schedule skipped: buffer closed or not loaded");
            idleTimer.stop();
            return;
        }
        int delay = delayForCaret(textArea);
        idleTimer.setInitialDelay(delay);
        idleTimer.restart();
        AiAssistLog.debug("idle timer scheduled in " + delay + "ms at caret "
            + textArea.getCaretPosition());
    }

    private int delayForCaret(TextArea textArea) {
        Buffer buffer = editBuffer(textArea);
        if (buffer == null) {
            return AiAssistConfig.idleDelayMs();
        }
        View view = jEdit.getActiveView();
        AiInlineCompletionContext context = AiInlineCompletionContext.forBuffer(
            view, buffer, textArea.getCaretPosition());
        if (context != null && context.emptyLine) {
            return Math.min(EMPTY_LINE_DELAY_MS, AiAssistConfig.idleDelayMs());
        }
        return AiAssistConfig.idleDelayMs();
    }

    private void onIdleTimer() {
        AiAssistLog.message("idle timer fired");
        if (!AiAssistConfig.inlineEnabled() || AiAssistConfig.provider() == AiAssistProvider.OFF) {
            AiAssistLog.message("idle skipped: inline disabled");
            return;
        }
        TextArea textArea = activeTextArea;
        if (textArea == null) {
            AiAssistLog.message("idle skipped: no active text area");
            return;
        }
        Buffer buffer = editBuffer(textArea);
        if (buffer == null) {
            AiAssistLog.message("idle skipped: no edit buffer");
            return;
        }
        if (buffer.isClosed() || !buffer.isLoaded()) {
            AiAssistLog.message("idle skipped: buffer closed or not loaded");
            return;
        }
        long idleMs = (System.nanoTime() - lastActivityNanos) / 1_000_000L;
        int requiredIdle = delayForCaret(textArea);
        if (idleMs < requiredIdle - 50) {
            AiAssistLog.debug("idle not long enough (" + idleMs + "ms < " + requiredIdle + "ms)");
            scheduleSuggestion();
            return;
        }
        int caret = textArea.getCaretPosition();
        View view = jEdit.getActiveView();
        AiInlineCompletionContext context = AiInlineCompletionContext.forBuffer(view, buffer, caret);
        if (context == null) {
            AiAssistLog.message("idle skipped: could not build completion context");
            return;
        }
        if (!context.emptyLine && idleMs < AiAssistConfig.idleDelayMs() - 50) {
            AiAssistLog.debug("non-empty line idle not long enough (" + idleMs + "ms)");
            scheduleSuggestion();
            return;
        }
        if (!isProviderReady()) {
            AiAssistLog.message("idle skipped: no signed-in AI provider (provider="
                + AiAssistConfig.provider()
                + ", cursor=" + AiInlineCompletionService.isCursorAvailable()
                + ", copilot=" + AiInlineCompletionService.isCopilotAvailable() + ")");
            return;
        }
        int generation = ++requestGeneration;
        AiAssistLog.message("requesting inline suggestion #" + generation
            + " (emptyLine=" + context.emptyLine + ", caret=" + caret + ")");
        ThreadUtilities.runInBackground(() -> fetchAndShow(generation, textArea, context));
    }

    private static boolean isProviderReady() {
        AiAssistProvider provider = AiAssistConfig.provider();
        if (provider == AiAssistProvider.OFF) {
            return false;
        }
        if (provider == AiAssistProvider.CURSOR) {
            return AiInlineCompletionService.isCursorAvailable();
        }
        if (provider == AiAssistProvider.COPILOT) {
            return AiInlineCompletionService.isCopilotAvailable();
        }
        return AiInlineCompletionService.isCursorAvailable()
            || AiInlineCompletionService.isCopilotAvailable();
    }

    private void fetchAndShow(int generation, TextArea textArea,
            AiInlineCompletionContext context) {
        try {
            String suggestion = AiInlineCompletionService.fetchSuggestion(context);
            if (suggestion == null || suggestion.isBlank()) {
                AiAssistLog.message("request #" + generation + " returned no suggestion");
                return;
            }
            SwingUtilities.invokeLater(() -> showSuggestionIfValid(
                generation, textArea, context, suggestion));
        } catch (Exception e) {
            AiAssistLog.warning("request #" + generation + " failed", e);
        }
    }

    private void showSuggestionIfValid(int generation, TextArea textArea,
            AiInlineCompletionContext context, String suggestion) {
        if (generation != requestGeneration) {
            AiAssistLog.debug("request #" + generation + " stale (generation mismatch)");
            return;
        }
        if (textArea != activeTextArea) {
            AiAssistLog.message("request #" + generation + " stale (text area changed)");
            return;
        }
        Buffer buffer = editBuffer(textArea);
        if (buffer == null || buffer.isClosed()) {
            AiAssistLog.message("request #" + generation + " stale (buffer gone)");
            return;
        }
        if (textArea.getCaretPosition() != context.caret) {
            AiAssistLog.message("request #" + generation + " stale (caret moved from "
                + context.caret + " to " + textArea.getCaretPosition() + ")");
            return;
        }
        currentSuggestion = suggestion;
        currentContext = context;
        popup.show(textArea, suggestion);
        installTabInterceptor();
        AiAssistLog.message("showing inline suggestion #" + generation + " ("
            + suggestion.length() + " chars)");
    }

    private void dismissSuggestion(String reason) {
        if (currentSuggestion != null) {
            AiAssistLog.debug("dismissing suggestion: " + reason);
        }
        removeTabInterceptor();
        requestGeneration++;
        currentSuggestion = null;
        currentContext = null;
        popup.hide();
    }

    private static Buffer editBuffer(TextArea textArea) {
        if (textArea == null) {
            return null;
        }
        JEditBuffer buffer = textArea.getBuffer();
        return buffer instanceof Buffer editBuffer ? editBuffer : null;
    }
}
