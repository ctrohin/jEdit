/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TestGutter;
import org.gjt.sp.jedit.textarea.TextAreaExtension;

import javax.swing.*;

/**
 * Run icons in a dedicated gutter column for discovered {@code @Test} and JS test methods.
 */
public final class TestGutterSupport implements EBComponent {

    private static TestGutterSupport instance;
    private static final Icon playIcon = IconManager.loadIcon("MatIcons.PLAY_ARROW:16:BLUE");

    private final Map<EditPane, TestGutterExtension> extensions = new WeakHashMap<>();

    public static void install() {
        getInstance();
    }

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
            } else if (EditPaneUpdate.BUFFER_CHANGED.equals(what)) {
                refreshEditPane(update.getEditPane());
            }
        } else if (message instanceof BufferUpdate bufferUpdate) {
            Object what = bufferUpdate.getWhat();
            if (BufferUpdate.LOADED.equals(what) || BufferUpdate.SAVED.equals(what)) {
                refreshBuffer(bufferUpdate.getBuffer());
            }
        }
    }

    private void install(EditPane editPane) {
        if (editPane == null || extensions.containsKey(editPane)) {
            return;
        }
        TestGutterExtension extension = new TestGutterExtension(editPane);
        TestGutter testGutter = editPane.getTextArea().getTestGutter();
        testGutter.addExtension(extension);
        testGutter.addMouseListener(extension.getMouseHandler());
        extensions.put(editPane, extension);
        extension.refresh();
    }

    private void uninstall(EditPane editPane) {
        TestGutterExtension extension = extensions.remove(editPane);
        if (extension == null || editPane == null) {
            return;
        }
        TestGutter testGutter = editPane.getTextArea().getTestGutter();
        testGutter.removeMouseListener(extension.getMouseHandler());
        testGutter.removeExtension(extension);
        updateTestGutterState(editPane, false);
    }

    private void refreshBuffer(Buffer buffer) {
        for (TestGutterExtension extension : extensions.values()) {
            if (extension.editPane.getBuffer() == buffer) {
                extension.refresh();
            }
        }
    }

    private void refreshEditPane(EditPane editPane) {
        TestGutterExtension extension = extensions.get(editPane);
        if (extension != null) {
            extension.refresh();
        }
    }

    private static void updateTestGutterState(EditPane editPane, boolean hasTests) {
        JEditTextArea textArea = editPane.getTextArea();
        TestGutter testGutter = textArea.getTestGutter();
        testGutter.setColumnWidth(hasTests ? TestGutter.DEFAULT_COLUMN_WIDTH : 0);
        testGutter.setTestEnabled(hasTests);
        textArea.syncGutterStripBorders();
    }

    private static final class TestGutterExtension extends TextAreaExtension {

        private final EditPane editPane;
        private final List<TestDiscovery.DiscoveredTest> tests = new ArrayList<>();

        private int physicalLineAtPoint(Point point) {
            JEditTextArea textArea = editPane.getTextArea();
            int lineHeight = textArea.getPainter().getLineHeight();
            if (lineHeight <= 0) {
                return -1;
            }
            int screenLine = point.y / lineHeight;
            if (screenLine < 0 || screenLine >= textArea.getVisibleLines()) {
                return -1;
            }
            return textArea.getPhysicalLineOfScreenLine(screenLine);
        }

        private final MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!editPane.getTextArea().getTestGutter().isTestEnabled()) {
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
                runTest(hit);
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
            boolean hasTests = !tests.isEmpty();
            updateTestGutterState(editPane, hasTests);
            editPane.getTextArea().getTestGutter().repaint();
        }

        @Override
        public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,
                                   int start, int end, int y) {
            TestDiscovery.DiscoveredTest test = testAtLine(physicalLine);
            if (test == null || playIcon == null) {
                return;
            }
            TestGutter testGutter = editPane.getTextArea().getTestGutter();
            int columnWidth = testGutter.getColumnWidth();
            int iconWidth = playIcon.getIconWidth();
            int iconHeight = playIcon.getIconHeight();
            int x = Math.max(2, (columnWidth - iconWidth) / 2);
            int height = editPane.getTextArea().getPainter().getLineHeight();
            int iconY = y + (height - iconHeight) / 2;
            playIcon.paintIcon(null, gfx, x, iconY);
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

        private void runTest(TestDiscovery.DiscoveredTest test) {
            File root = ProjectRoots.workspaceRoot();
            View view = editPane.getView();
            if (root == null || view == null) {
                return;
            }
            WorkspaceTestRunner.runTests(view, root,
                TestRunOptions.single(test.className, test.methodName));
        }
    }
}
