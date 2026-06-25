/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.EnhancedTreeCellRenderer;
import org.jedit.build.BuildProblemsHub;
import org.jedit.build.TestProblemsExporter;

/**
 * Unified Problems view: LSP diagnostics, build errors, and test failures.
 */
public class LspProblemsView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "lsp-problems";

    private final View view;
    private final JLabel caption;
    private final JComboBox<String> filterCombo;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final Runnable problemsListener = this::refreshTree;

    public LspProblemsView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        JPanel north = new JPanel(new BorderLayout(4, 0));
        caption = new JLabel();
        north.add(caption, BorderLayout.CENTER);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        filterCombo = new JComboBox<>(new String[] {
            jEdit.getProperty("lsp-problems.filter.all"),
            jEdit.getProperty("lsp-problems.filter.errors"),
            jEdit.getProperty("lsp-problems.filter.warnings")
        });
        filterCombo.addActionListener(e -> applyFilter());
        filters.add(new JLabel(jEdit.getProperty("lsp-problems.filter.label")));
        filters.add(filterCombo);
        north.add(filters, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setCellRenderer(new ProblemTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    tree.setSelectionPath(path);
                }
                if (e.getClickCount() == 2) {
                    goToSelectedProblem();
                }
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    goToSelectedProblem();
                    e.consume();
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
        refreshTree();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ProblemsHub hub = ProblemsHub.getInstance();
        hub.addListener(problemsListener);
        LspDiagnosticsHub.getInstance().addListener(problemsListener);
        BuildProblemsHub.getInstance().addListener(problemsListener);
        TestProblemsExporter.addListener(problemsListener);
        refreshTree();
    }

    @Override
    public void removeNotify() {
        ProblemsHub hub = ProblemsHub.getInstance();
        hub.removeListener(problemsListener);
        LspDiagnosticsHub.getInstance().removeListener(problemsListener);
        BuildProblemsHub.getInstance().removeListener(problemsListener);
        TestProblemsExporter.removeListener(problemsListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    private void applyFilter() {
        ProblemsHub.SeverityFilter filter = switch (filterCombo.getSelectedIndex()) {
            case 1 -> ProblemsHub.SeverityFilter.ERRORS;
            case 2 -> ProblemsHub.SeverityFilter.WARNINGS;
            default -> ProblemsHub.SeverityFilter.ALL;
        };
        ProblemsHub.getInstance().setSeverityFilter(filter);
        refreshTree();
    }

    private void refreshTree() {
        rootNode.removeAllChildren();
        List<ProblemsHub.FileProblems> fileGroups = ProblemsHub.getInstance().getFileProblems();
        int problemCount = 0;
        for (ProblemsHub.FileProblems group : fileGroups) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                new FileNode(group.getUri()));
            for (UnifiedProblem problem : group.getProblems()) {
                fileNode.add(new DefaultMutableTreeNode(problem));
                problemCount++;
            }
            if (!fileNode.isLeaf()) {
                rootNode.add(fileNode);
            }
        }
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
        updateCaption(problemCount, fileGroups.size());
        updateDockNotifications();
    }

    private void updateDockNotifications() {
        DockableWindowManager manager = view.getDockableWindowManager();
        if (manager != null) {
            ProblemsHub hub = ProblemsHub.getInstance();
            manager.setDockableNotifications(NAME, hub.countErrors(), hub.countWarnings());
        }
    }

    private void updateCaption(int problemCount, int fileCount) {
        if (problemCount == 0) {
            caption.setText(jEdit.getProperty("lsp-problems.empty"));
            return;
        }
        caption.setText(jEdit.getProperty("lsp-problems.caption",
            new Object[] { Integer.valueOf(problemCount), Integer.valueOf(fileCount) }));
    }

    private void goToSelectedProblem() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) {
            return;
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof UnifiedProblem problem) {
            openProblem(problem);
        }
    }

    private void openProblem(UnifiedProblem problem) {
        String path = LspDocumentUri.uriToPath(problem.uri);
        if (path == null) {
            return;
        }

        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Buffer.CARET, Integer.valueOf(0));
        props.put(Buffer.CARET_POSITIONED, Boolean.TRUE);

        Buffer buffer = jEdit.openFile(view, null, path, false, props);
        if (buffer == null) {
            return;
        }

        int offset = problem.startOffset(buffer);
        buffer.setIntegerProperty(Buffer.CARET, offset);
        buffer.setBooleanProperty(Buffer.CARET_POSITIONED, true);
        buffer.unsetProperty(Buffer.SCROLL_VERT);

        if (view.getBuffer() == buffer) {
            view.getEditPane().loadCaretInfo();
            view.getTextArea().requestFocus();
        }

        view.toFront();
        view.requestFocus();
    }

    private static final class FileNode {
        private final String uri;
        private final String displayName;

        FileNode(String uri) {
            this.uri = uri;
            String path = LspDocumentUri.uriToPath(uri);
            this.displayName = new File(path != null ? path : uri).getName();
        }

        String getUri() {
            return uri;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final class ProblemTreeCellRenderer extends EnhancedTreeCellRenderer {
        @Override
        protected EnhancedTreeCellRenderer newInstance() {
            return new ProblemTreeCellRenderer();
        }

        @Override
        protected void configureTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean selected, boolean expanded,
                                                          boolean leaf, int row,
                                                          boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }
            Object userObject = node.getUserObject();
            if (userObject instanceof FileNode fileNode) {
                setText(fileNode.toString());
                setToolTipText(fileNode.getUri());
                setIcon(LspFileIcons.iconForUri(fileNode.getUri()));
                return;
            }
            if (userObject instanceof UnifiedProblem problem) {
                setIcon(null);
                setText(formatProblemHtml(problem));
                setToolTipText(problem.message);
            }
        }

        private String formatProblemHtml(UnifiedProblem problem) {
            String color = colorToHex(problem.severity.color());
            String rest = " [" + escapeHtml(problem.source.label()) + "] ("
                + problem.line + ":" + problem.column + ") "
                + escapeHtml(problem.message);
            return "<html><font color='" + color + "'>"
                + escapeHtml(problem.severity.label())
                + "</font>" + rest + "</html>";
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
