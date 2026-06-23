/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.EnhancedTreeCellRenderer;

/**
 * Structured test results grouped by suite in a {@link JTree}.
 */
public final class TestResultsView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "test-results";

    private final View view;
    private final JLabel caption;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final Runnable resultsListener = this::refreshTree;
    private final ProjectFolderListener folderListener = new ProjectFolderListener(this::clearResults);

    public TestResultsView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        JPanel north = new JPanel(new BorderLayout(4, 0));
        caption = new JLabel();
        north.add(caption, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        JButton runTests = new JButton(jEdit.getProperty("test-results.run"));
        runTests.addActionListener(e -> runWorkspaceTests());
        buttons.add(runTests);
        north.add(buttons, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setCellRenderer(new TestTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    tree.setSelectionPath(path);
                }
                if (e.getClickCount() == 2) {
                    goToSelectedTest();
                }
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    goToSelectedTest();
                    e.consume();
                }
            }
        });
        add(new JScrollPane(tree), BorderLayout.CENTER);
        refreshTree();
    }

    public static TestResultsView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (TestResultsView) dwm.getDockableWindow(NAME);
    }

    private void runWorkspaceTests() {
        var root = ProjectRoots.workspaceRoot();
        if (root == null) {
            return;
        }
        WorkspaceTestRunner.runTests(view, root);
    }

    private void clearResults() {
        TestResultsHub.getInstance().clear();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        TestResultsHub.getInstance().addListener(resultsListener);
        EditBus.addToBus(folderListener);
        refreshTree();
    }

    @Override
    public void removeNotify() {
        TestResultsHub.getInstance().removeListener(resultsListener);
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    private void refreshTree() {
        rootNode.removeAllChildren();
        TestRunResult result = TestResultsHub.getInstance().getResult();
        for (String suiteName : result.suiteNames()) {
            DefaultMutableTreeNode suiteNode = new DefaultMutableTreeNode(
                new SuiteNode(suiteName));
            for (TestCaseResult testCase : result.casesForSuite(suiteName)) {
                suiteNode.add(new DefaultMutableTreeNode(testCase));
            }
            rootNode.add(suiteNode);
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        updateCaption(result);
        updateDockNotifications(result);
    }

    private void updateCaption(TestRunResult result) {
        if (result.totalCount() == 0) {
            if (result.exitCode >= 0) {
                caption.setText(jEdit.getProperty("test-results.no-reports",
                    new Object[] {Integer.valueOf(result.exitCode)}));
            } else {
                caption.setText(jEdit.getProperty("test-results.empty"));
            }
            return;
        }
        caption.setText(jEdit.getProperty("test-results.caption",
            new Object[] {
                Integer.valueOf(result.totalCount()),
                Integer.valueOf(result.count(TestCaseStatus.PASSED)),
                Integer.valueOf(result.count(TestCaseStatus.FAILED) + result.count(TestCaseStatus.ERROR)),
                Integer.valueOf(result.count(TestCaseStatus.SKIPPED)),
                Double.valueOf(result.totalTimeSeconds())
            }));
    }

    private void updateDockNotifications(TestRunResult result) {
        DockableWindowManager manager = view.getDockableWindowManager();
        if (manager != null) {
            int failures = result.count(TestCaseStatus.FAILED) + result.count(TestCaseStatus.ERROR);
            manager.setDockableNotifications(NAME, failures, 0);
        }
    }

    private void goToSelectedTest() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) {
            return;
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof TestCaseResult testCase) {
            openTestCase(testCase);
        }
    }

    private void openTestCase(TestCaseResult testCase) {
        if (!testCase.hasNavigationTarget()) {
            return;
        }
        FileLink link = new FileLink(
            0,
            0,
            testCase.sourceFile.getAbsolutePath(),
            Math.max(1, testCase.line),
            1);
        FileLinkNavigator.openLink(view, TestResultsHub.getInstance().getResult().projectRoot, link);
    }

    private static final class SuiteNode {
        private final String name;

        SuiteNode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final class TestTreeCellRenderer extends EnhancedTreeCellRenderer {
        @Override
        protected EnhancedTreeCellRenderer newInstance() {
            return new TestTreeCellRenderer();
        }

        @Override
        protected void configureTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean selected, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }
            Object userObject = node.getUserObject();
            if (userObject instanceof SuiteNode suiteNode) {
                setText(suiteNode.toString());
                setIcon(IconManager.loadIcon("MatIcons.FOLDER:16"));
                return;
            }
            if (userObject instanceof TestCaseResult testCase) {
                setIcon(null);
                setText(formatTestHtml(testCase));
                setToolTipText(testCase.message);
            }
        }

        private String formatTestHtml(TestCaseResult testCase) {
            String color = colorToHex(testCase.status.color());
            return "<html><font color='" + color + "'>"
                + escapeHtml(testCase.treeLabel())
                + "</font></html>";
        }
    }

    private static String colorToHex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
