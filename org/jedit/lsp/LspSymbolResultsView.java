/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.Location;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.EnhancedTreeCellRenderer;

/**
 * Dockable view for LSP symbol search results (references, implementations,
 * call hierarchy, workspace symbols, etc.) with search history.
 */
public class LspSymbolResultsView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "lsp-symbol-results";

    private static final String INCOMING_LABEL_KEY = "lsp-symbol-results.incoming-calls";
    private static final String OUTGOING_LABEL_KEY = "lsp-symbol-results.outgoing-calls";
    private static final String LOADING_LABEL_KEY = "lsp-symbol-results.loading";

    private final View view;
    private final JLabel caption;
    private final JComboBox<HistoryEntry> historyCombo;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final Map<DefaultMutableTreeNode, LspCallHierarchyBranch> hierarchyBranches =
        new HashMap<>();
    private final Runnable resultsListener = this::refreshFromHub;
    private final Runnable structureListener = this::onCallHierarchyLoaded;
    private boolean updatingHistory;

    public LspSymbolResultsView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        JPanel top = new JPanel(new BorderLayout(4, 4));
        caption = new JLabel();
        top.add(caption, BorderLayout.NORTH);

        historyCombo = new JComboBox<>();
        historyCombo.addActionListener(e -> {
            if (updatingHistory) {
                return;
            }
            HistoryEntry entry = (HistoryEntry) historyCombo.getSelectedItem();
            if (entry != null) {
                LspSymbolSearchHub.getInstance().select(entry.resultId());
            }
        });
        top.add(historyCombo, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(0);
        tree.setCellRenderer(new SymbolTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    goToSelectedHit();
                }
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    goToSelectedHit();
                    e.consume();
                }
            }
        });
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                Object last = event.getPath().getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode node) {
                    DefaultMutableTreeNode hierarchyNode = hierarchyBranches.containsKey(node)
                        ? node
                        : findHierarchyBranchNode(node);
                    if (hierarchyNode != null) {
                        maybeLoadCallHierarchy(hierarchyNode);
                    }
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
        refreshFromHub();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        LspSymbolSearchHub.getInstance().addListener(resultsListener);
        LspSymbolSearchHub.getInstance().addStructureListener(structureListener);
        refreshFromHub();
    }

    @Override
    public void removeNotify() {
        LspSymbolSearchHub.getInstance().removeStructureListener(structureListener);
        LspSymbolSearchHub.getInstance().removeListener(resultsListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    private void refreshFromHub() {
        refreshHistoryCombo();
        rebuildTree(LspSymbolSearchHub.getInstance().getCurrent());
    }

    private void refreshHistoryCombo() {
        List<LspSymbolSearchResult> history = LspSymbolSearchHub.getInstance().getHistory();
        LspSymbolSearchResult current = LspSymbolSearchHub.getInstance().getCurrent();
        updatingHistory = true;
        try {
            DefaultComboBoxModel<HistoryEntry> model = new DefaultComboBoxModel<>();
            for (LspSymbolSearchResult result : history) {
                model.addElement(new HistoryEntry(result));
            }
            historyCombo.setModel(model);
            if (current != null) {
                for (int i = 0; i < model.getSize(); i++) {
                    HistoryEntry entry = model.getElementAt(i);
                    if (entry.resultId().equals(current.getId())) {
                        historyCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
            historyCombo.setEnabled(!history.isEmpty());
        } finally {
            updatingHistory = false;
        }
        updateCaption(current);
    }

    private void rebuildTree(LspSymbolSearchResult result) {
        hierarchyBranches.clear();
        rootNode.removeAllChildren();
        if (result == null) {
            treeModel.reload();
            updateCaption(null);
            return;
        }

        if (result.getKind() == LspSymbolSearchResult.Kind.CALL_HIERARCHY) {
            for (CallHierarchyItem item : result.getCallHierarchyRoots()) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                    LspLocations.callHierarchyItemHit(item));
                LspCallHierarchyBranch branch = new LspCallHierarchyBranch(item);
                hierarchyBranches.put(node, branch);
                node.add(new DefaultMutableTreeNode(
                    new GroupLabel(jEdit.getProperty(INCOMING_LABEL_KEY))));
                node.add(new DefaultMutableTreeNode(
                    new GroupLabel(jEdit.getProperty(OUTGOING_LABEL_KEY))));
                rootNode.add(node);
            }
        } else {
            for (LspSymbolFileGroup group : result.getFileGroups()) {
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                    new FileNode(group.getUri(), group.getDisplayName()));
                for (LspSymbolHit hit : group.getHits()) {
                    fileNode.add(buildHitNode(hit));
                }
                rootNode.add(fileNode);
            }
        }

        treeModel.reload();
        expandAll();
        updateCaption(result);
    }

    private DefaultMutableTreeNode buildHitNode(LspSymbolHit hit) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(hit);
        for (LspSymbolHit child : hit.getChildren()) {
            node.add(buildHitNode(child));
        }
        return node;
    }

    private void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) {
            tree.expandRow(i);
        }
    }

    private void updateCaption(LspSymbolSearchResult result) {
        if (result == null) {
            caption.setText(jEdit.getProperty("lsp-symbol-results.empty"));
            return;
        }
        String kind = jEdit.getProperty(result.getKind().getPropertyKey());
        caption.setText(jEdit.getProperty("lsp-symbol-results.caption",
            new Object[] {
                kind,
                result.getQuery(),
                Integer.valueOf(result.getHitCount()),
                Integer.valueOf(result.getFileCount())
            }));
    }

    private DefaultMutableTreeNode findHierarchyBranchNode(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode current = node;
        while (current != null) {
            if (hierarchyBranches.containsKey(current)) {
                return current;
            }
            if (!(current.getParent() instanceof DefaultMutableTreeNode parent)) {
                break;
            }
            current = parent;
        }
        return null;
    }

    private void maybeLoadCallHierarchy(DefaultMutableTreeNode node) {
        LspCallHierarchyBranch branch = hierarchyBranches.get(node);
        if (branch == null || branch.isLoaded() || branch.isLoading()) {
            return;
        }
        GenericLspClient client = LspPlugin.getExistingClientForBuffer(view.getBuffer());
        if (client == null) {
            return;
        }
        LspAsync.runOffEdt(() ->
            LspSymbolSearches.loadCallHierarchyChildren(view, client, branch.getItem(), branch));
    }

    void onCallHierarchyLoaded() {
        LspSymbolSearchResult current = LspSymbolSearchHub.getInstance().getCurrent();
        if (current == null || current.getKind() != LspSymbolSearchResult.Kind.CALL_HIERARCHY) {
            return;
        }
        for (Map.Entry<DefaultMutableTreeNode, LspCallHierarchyBranch> entry
            : hierarchyBranches.entrySet()) {
            LspCallHierarchyBranch branch = entry.getValue();
            if (!branch.isLoaded()) {
                continue;
            }
            DefaultMutableTreeNode root = entry.getKey();
            if (root.getChildCount() < 2) {
                continue;
            }
            updateCallGroup((DefaultMutableTreeNode) root.getChildAt(0), branch.getIncoming());
            updateCallGroup((DefaultMutableTreeNode) root.getChildAt(1), branch.getOutgoing());
        }
        treeModel.reload();
        expandAll();
    }

    private void updateCallGroup(DefaultMutableTreeNode groupNode, List<LspSymbolHit> hits) {
        groupNode.removeAllChildren();
        for (LspSymbolHit hit : hits) {
            groupNode.add(new DefaultMutableTreeNode(hit));
        }
    }

    private void goToSelectedHit() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode node)) {
            return;
        }
        Object userObject = node.getUserObject();
        if (userObject instanceof LspSymbolHit hit) {
            openHit(hit);
        } else if (userObject instanceof LspCallHierarchyBranch branch) {
            openHit(LspLocations.callHierarchyItemHit(branch.getItem()));
        }
    }

    private void openHit(LspSymbolHit hit) {
        String path = LspDocumentUri.uriToPath(hit.getUri());
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

        int offset = hit.getStartOffset(buffer);
        buffer.setIntegerProperty(Buffer.CARET, offset);
        buffer.setBooleanProperty(Buffer.CARET_POSITIONED, true);
        buffer.unsetProperty(Buffer.SCROLL_VERT);

        Location location = hit.toLocation();
        LspGoToDefinition.openLocation(view, location,
            LspNavigationHistory.capture(view));

        view.toFront();
        view.requestFocus();
    }

    private record HistoryEntry(String resultId, String label) {
        HistoryEntry(LspSymbolSearchResult result) {
            this(result.getId(), formatLabel(result));
        }

        private static String formatLabel(LspSymbolSearchResult result) {
            String kind = jEdit.getProperty(result.getKind().getPropertyKey());
            DateFormat format = SimpleDateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT);
            return kind + ": " + result.getQuery()
                + " (" + result.getHitCount() + ") — "
                + format.format(new Date(result.getTimestamp()));
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class FileNode {
        private final String uri;
        private final String displayName;

        FileNode(String uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final class GroupLabel {
        private final String text;

        GroupLabel(String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final class SymbolTreeCellRenderer extends EnhancedTreeCellRenderer {
        @Override
        protected EnhancedTreeCellRenderer newInstance() {
            return new SymbolTreeCellRenderer();
        }

        @Override
        protected void configureTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean selected,
                                                          boolean expanded,
                                                          boolean leaf, int row,
                                                          boolean hasFocus) {
            if (!(value instanceof DefaultMutableTreeNode node)) {
                return;
            }
            Object userObject = node.getUserObject();
            if (userObject instanceof FileNode fileNode) {
                setText(fileNode.toString());
                setToolTipText(fileNode.uri);
                setIcon(LspFileIcons.iconForUri(fileNode.uri));
                return;
            }
            if (userObject instanceof GroupLabel groupLabel) {
                setIcon(null);
                setText(groupLabel.toString());
                LspCallHierarchyBranch branch = hierarchyBranches.get(
                    findHierarchyBranchNode(node));
                if (branch != null && branch.isLoading()) {
                    setText(jEdit.getProperty(LOADING_LABEL_KEY));
                }
                return;
            }
            if (userObject instanceof LspSymbolHit hit) {
                setIcon(null);
                String html = hit.formatLineDisplayHtml();
                setText(html != null ? html : hit.formatDisplayText());
                setToolTipText(hit.getDetail());
            }
        }

    }
}
