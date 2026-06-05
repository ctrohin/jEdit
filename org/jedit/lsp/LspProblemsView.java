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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.Hashtable;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.EnhancedTreeCellRenderer;

/**
 * Dockable Problems view: LSP diagnostics grouped by file in a {@link JTree}.
 */
public class LspProblemsView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "lsp-problems";

    private final View view;
    private final JLabel caption;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final Runnable diagnosticsListener = this::refreshTree;

    public LspProblemsView(View view) {
        super(new BorderLayout());
        this.view = view;

        caption = new JLabel();
        add(caption, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setCellRenderer(new ProblemTreeCellRenderer());
        tree.addMouseListener(new MouseHandler());
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
        LspDiagnosticsHub.getInstance().addListener(diagnosticsListener);
        refreshTree();
    }

    @Override
    public void removeNotify() {
        LspDiagnosticsHub.getInstance().removeListener(diagnosticsListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    private void refreshTree() {
        rootNode.removeAllChildren();
        List<LspDiagnosticsHub.FileProblems> fileGroups =
            LspDiagnosticsHub.getInstance().getFileProblems();
        int problemCount = 0;
        for (LspDiagnosticsHub.FileProblems group : fileGroups) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                new FileNode(group.getUri()));
            for (LspDiagnosticProblem problem : group.getProblems()) {
                fileNode.add(new DefaultMutableTreeNode(problem));
                problemCount++;
            }
            rootNode.add(fileNode);
        }
        treeModel.reload();
        expandAll();
        updateCaption(problemCount, fileGroups.size());
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
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
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object userObject = ((DefaultMutableTreeNode) last).getUserObject();
        if (!(userObject instanceof LspDiagnosticProblem)) {
            return;
        }
        openProblem((LspDiagnosticProblem) userObject);
    }

    private void openProblem(LspDiagnosticProblem problem) {
        String path = uriToPath(problem.getUri());
        if (path == null) {
            return;
        }

        // Reserve caret via buffer props so EditPane.loadCaretInfo() uses the
        // problem location instead of buffer history when the file is first opened.
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(Buffer.CARET, Integer.valueOf(0));
        props.put(Buffer.CARET_POSITIONED, Boolean.TRUE);

        Buffer buffer = jEdit.openFile(view, null, path, false, props);
        if (buffer == null) {
            return;
        }

        int offset = offsetForProblem(buffer, problem);
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

    private static int offsetForProblem(Buffer buffer, LspDiagnosticProblem problem) {
        int line = problem.getLine();
        if (buffer.getLineCount() == 0) {
            return 0;
        }
        if (line < 0) {
            line = 0;
        } else if (line >= buffer.getLineCount()) {
            line = buffer.getLineCount() - 1;
        }

        int lineStart = buffer.getLineStartOffset(line);
        int character = problem.getCharacter();
        int lineLength = buffer.getLineLength(line);
        if (character < 0) {
            character = 0;
        } else if (character > lineLength) {
            character = lineLength;
        }
        return Math.min(lineStart + character, buffer.getLength());
    }

    private static String uriToPath(String uri) {
        try {
            URI parsed = URI.create(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return null;
            }
            return MiscUtilities.resolveSymlinks(new File(parsed).getPath());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static final class FileNode {
        private final String uri;
        private final String displayName;

        FileNode(String uri) {
            this.uri = uri;
            String path = uriToPath(uri);
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
                                                          boolean selected,
                                                          boolean expanded,
                                                          boolean leaf, int row,
                                                          boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode)) {
                return;
            }
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof FileNode fileNode) {
                setText(fileNode.toString());
                setToolTipText(fileNode.getUri());
                return;
            }
            if (userObject instanceof LspDiagnosticProblem problem) {
                setText(formatProblemHtml(problem));
                setToolTipText(problem.getMessage());
            }
        }

        private String formatProblemHtml(LspDiagnosticProblem problem) {
            LspDiagnosticProblem.Severity severity = problem.getSeverity();
            String color = colorToHex(severity.getColor());
            String rest = " (" + (problem.getLine() + 1) + ":"
                + (problem.getCharacter() + 1) + ") "
                + escapeHtml(problem.getMessage());
            return "<html><font color='" + color + "'>"
                + escapeHtml(severity.getLabel())
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

    private final class MouseHandler extends MouseAdapter {
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
    }
}
