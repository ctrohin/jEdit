/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.textarea.Gutter;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;

/**
 * Run/debug icons in the gutter for discovered {@code @Test} and JS test methods.
 */
public final class TestGutterSupport implements EBComponent {

    private static TestGutterSupport instance;

    private final Map<EditPane, TestGutterExtension> extensions = new WeakHashMap<>();

    public static TestGutterSupport getInstance() {
        if (instance == null) {
            instance = new TestGutterSupport();
            EditBus.addToBus(instance);
        }
        return instance;
    }

    private TestGutterSupport() {
        for (View view : jEdit.getViewManager().getViews()) {
            for (EditPane editPane : view.getEditPanes()) {
                install(editPane);
            }
        }
    }

    @Override
    public void handleMessage(EBMessage message) {
        if (message instanceof EditPaneUpdate update) {
            Object what = update.getWhat();
            if (EditPaneUpdate.CREATED.equals(what)) {
                install(update.getEditPane());
            } else if (EditPaneUpdate.DESTROYED.equals(what)) {
                uninstall(update.getEditPane());
            }
        } else if (message instanceof BufferUpdate bufferUpdate) {
            Object what = bufferUpdate.getWhat();
            if (BufferUpdate.LOADED.equals(what) || BufferUpdate.SAVED.equals(what)) {
                refreshBuffer(bufferUpdate.getBuffer());
            }
        } else if (message instanceof EditPaneUpdate paneUpdate
            && EditPaneUpdate.BUFFER_CHANGED.equals(paneUpdate.getWhat())) {
            refreshBuffer(paneUpdate.getEditPane().getBuffer());
        }
    }

    private void install(EditPane editPane) {
        if (editPane == null || extensions.containsKey(editPane)) {
            return;
        }
        JEditTextArea textArea = editPane.getTextArea();
        TestGutterExtension extension = new TestGutterExtension(editPane);
        Gutter gutter = textArea.getGutter();
        gutter.addExtension(extension);
        gutter.addMouseListener(extension.getMouseHandler());
        extensions.put(editPane, extension);
        extension.refresh();
    }

    private void uninstall(EditPane editPane) {
        TestGutterExtension extension = extensions.remove(editPane);
        if (extension == null || editPane == null) {
            return;
        }
        JEditTextArea textArea = editPane.getTextArea();
        Gutter gutter = textArea.getGutter();
        gutter.removeExtension(extension);
        gutter.removeMouseListener(extension.getMouseHandler());
    }

    private void refreshBuffer(Buffer buffer) {
        for (TestGutterExtension extension : extensions.values()) {
            if (extension.editPane.getBuffer() == buffer) {
                extension.refresh();
            }
        }
    }

    private static final class TestGutterExtension extends TextAreaExtension {

        private static final int ICON_SIZE = 10;

        private final EditPane editPane;
        private final List<TestDiscovery.DiscoveredTest> tests = new ArrayList<>();
        private int physicalLineAtPoint(Point point) {
            JEditTextArea textArea = editPane.getTextArea();
            int screenLine = point.y / textArea.getPainter().getLineHeight();
            if (screenLine < 0) {
                return -1;
            }
            return textArea.getPhysicalLineOfScreenLine(screenLine);
        }

        private final MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 1 || e.getX() > ICON_SIZE + 6) {
                    return;
                }
                int line = physicalLineAtPoint(e.getPoint());
                if (line < 0) {
                    return;
                }
                TestDiscovery.DiscoveredTest hit = testAtLine(line);
                if (hit == null) {
                    return;
                }
                runTest(hit, e.isShiftDown());
                e.consume();
            }
        };

        TestGutterExtension(EditPane editPane) {
            this.editPane = editPane;
        }

        MouseAdapter getMouseHandler() {
            return mouseHandler;
        }

        void refresh() {
            tests.clear();
            tests.addAll(TestDiscovery.discoverBuffer(editPane.getBuffer()));
            editPane.getTextArea().getGutter().repaint();
        }

        @Override
        public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,
                                   int start, int end, int y) {
            TestDiscovery.DiscoveredTest test = testAtLine(physicalLine);
            if (test == null) {
                return;
            }
            int x = 2;
            int height = editPane.getTextArea().getPainter().getLineHeight();
            int cy = y + (height / 2);
            gfx.setColor(TestCaseStatus.DISCOVERED.color());
            int[] xs = {x, x + ICON_SIZE, x + (ICON_SIZE / 2)};
            int[] ys = {cy - 4, cy - 4, cy + 3};
            gfx.fillPolygon(xs, ys, 3);
        }

        @Override
        public String getToolTipText(int x, int y) {
            int line = physicalLineAtPoint(new Point(x, y));
            TestDiscovery.DiscoveredTest test = testAtLine(line);
            if (test == null) {
                return null;
            }
            return jEdit.getProperty("test-gutter.tooltip",
                new Object[] {test.methodName});
        }

        private TestDiscovery.DiscoveredTest testAtLine(int physicalLine) {
            for (TestDiscovery.DiscoveredTest test : tests) {
                if (test.line - 1 == physicalLine) {
                    return test;
                }
            }
            return null;
        }

        private void runTest(TestDiscovery.DiscoveredTest test, boolean debug) {
            File root = ProjectRoots.workspaceRoot();
            View view = editPane.getView();
            if (root == null || view == null) {
                return;
            }
            TestRunOptions options = debug
                ? TestRunOptions.single(test.className, test.methodName, true)
                : TestRunOptions.single(test.className, test.methodName);
            WorkspaceTestRunner.runTests(view, root, options);
        }
    }
}
