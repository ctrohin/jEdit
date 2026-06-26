/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Hashtable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.eclipse.lsp4j.Location;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.buffer.BufferAdapter;
import org.gjt.sp.jedit.buffer.JEditBuffer;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.ViewUpdate;
import org.gjt.sp.util.EnhancedTreeCellRenderer;
import org.gjt.sp.util.GenericGUIUtilities;

/**
 * Dockable Structure view showing the LSP document symbol tree for the
 * active buffer.
 */
public class LspStructureView extends JPanel
    implements DefaultFocusComponent, ActionListener {

    public static final String NAME = "lsp-structure";

    private final View view;
    private final JLabel caption;
    private final DefaultMutableTreeNode rootNode;
    private final DefaultTreeModel treeModel;
    private final JTree tree;
    private final Runnable structureListener = this::refreshTree;
    private final BufferAdapter bufferListener = new BufferAdapter() {
        @Override
        public void contentInserted(JEditBuffer buffer, int startLine, int offset,
                                    int numLines, int length) {
            onBufferContentChanged((Buffer) buffer);
        }

        @Override
        public void contentRemoved(JEditBuffer buffer, int startLine, int offset,
                                   int numLines, int length) {
            onBufferContentChanged((Buffer) buffer);
        }
    };
    private Buffer trackedBuffer;
    private LspStructureHub.StructureSnapshot lastRenderedSnapshot;
    private Timer caretSyncTimer;
    private Timer bufferSwitchTimer;

    public LspStructureView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;

        Box toolBar = new Box(BoxLayout.X_AXIS);
        toolBar.add(new JLabel(GenericGUIUtilities.prettifyMenuLabel(
            jEdit.getProperty("lsp-structure.label"))));
        toolBar.add(Box.createGlue());

        RolloverButton refresh = new RolloverButton(
            IconManager.loadIcon("MatIcons.REFRESH:22"));
        refresh.setToolTipText(jEdit.getProperty("lsp-structure.refresh"));
        refresh.setActionCommand("refresh");
        refresh.addActionListener(this);
        toolBar.add(refresh);
        add(toolBar, BorderLayout.NORTH);

        caption = new JLabel();
        add(caption, BorderLayout.SOUTH);

        rootNode = new DefaultMutableTreeNode();
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(18);
        tree.setCellRenderer(new StructureTreeCellRenderer());
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    goToSelectedSymbol();
                }
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    goToSelectedSymbol();
                    e.consume();
                }
            }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
        refreshTree();
    }

    public static void show(View view) {
        if (view == null) {
            return;
        }
        view.getDockableWindowManager().showDockableWindow(NAME);
        LspStructureHub.getInstance().requestRefresh(view.getBuffer());
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("refresh".equals(e.getActionCommand())) {
            LspStructureHub.getInstance().requestRefresh(view.getBuffer());
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(this);
        LspStructureHub.getInstance().addListener(structureListener);
        trackBuffer(view.getBuffer());
        LspStructureHub.getInstance().requestRefresh(view.getBuffer());
    }

    @Override
    public void removeNotify() {
        LspStructureHub.getInstance().removeListener(structureListener);
        EditBus.removeFromBus(this);
        trackBuffer(null);
        if (caretSyncTimer != null) {
            caretSyncTimer.stop();
            caretSyncTimer = null;
        }
        if (bufferSwitchTimer != null) {
            bufferSwitchTimer.stop();
            bufferSwitchTimer = null;
        }
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    @EBHandler
    public void handleEditPaneUpdate(EditPaneUpdate message) {
        if (message.getEditPane().getView().equals(view)
            && EditPaneUpdate.BUFFER_CHANGED.equals(message.getWhat())) {
            onActiveBufferChanged();
        }
    }

    @EBHandler
    public void handleViewUpdate(ViewUpdate message) {
        if (message.getView().equals(view)
            && ViewUpdate.EDIT_PANE_CHANGED.equals(message.getWhat())) {
            onActiveBufferChanged();
        }
    }

    @EBHandler
    public void handleBufferUpdate(BufferUpdate message) {
        if (message.getBuffer().equals(view.getBuffer())
            && BufferUpdate.LOADED.equals(message.getWhat())) {
            LspStructureHub.getInstance().requestRefresh(view.getBuffer());
        }
    }

    @EBHandler
    public void handleEditPanePosition(EditPaneUpdate message) {
        if (message.getEditPane().getView().equals(view)
            && EditPaneUpdate.POSITION_CHANGING.equals(message.getWhat())) {
            scheduleCaretSync();
        }
    }

    private void onActiveBufferChanged() {
        lastRenderedSnapshot = null;
        trackBuffer(view.getBuffer());
        javax.swing.SwingUtilities.invokeLater(this::refreshTree);
        if (bufferSwitchTimer == null) {
            bufferSwitchTimer = new Timer(200, e ->
                LspStructureHub.getInstance().requestRefresh(view.getBuffer()));
            bufferSwitchTimer.setRepeats(false);
        }
        bufferSwitchTimer.restart();
    }

    private void trackBuffer(Buffer buffer) {
        if (trackedBuffer == buffer) {
            return;
        }
        if (trackedBuffer != null) {
            trackedBuffer.removeBufferListener(bufferListener);
        }
        trackedBuffer = buffer;
        if (trackedBuffer != null) {
            trackedBuffer.addBufferListener(bufferListener);
        }
    }

    private void onBufferContentChanged(Buffer buffer) {
        if (buffer.equals(view.getBuffer())) {
            LspStructureHub.getInstance().requestRefresh(buffer);
        }
    }

    private void refreshTree() {
        Buffer buffer = view.getBuffer();
        LspStructureHub.StructureSnapshot snapshot =
            LspStructureHub.getInstance().getSnapshot(buffer);

        if (snapshot.getState() == LspStructureHub.StructureSnapshot.State.LOADING
            && lastRenderedSnapshot != null
            && lastRenderedSnapshot.getState()
                == LspStructureHub.StructureSnapshot.State.READY
            && buffer != null
            && snapshot.getUri() != null
            && snapshot.getUri().equals(lastRenderedSnapshot.getUri())) {
            updateCaption(buffer, snapshot);
            return;
        }

        if (snapshot.equals(lastRenderedSnapshot)) {
            return;
        }

        rootNode.removeAllChildren();
        if (snapshot.getState() == LspStructureHub.StructureSnapshot.State.READY) {
            for (LspSymbolHit hit : snapshot.getSymbols()) {
                rootNode.add(buildSymbolNode(hit));
            }
        }
        treeModel.reload();
        javax.swing.SwingUtilities.invokeLater(this::expandStructureNodes);
        lastRenderedSnapshot = snapshot;
        updateCaption(buffer, snapshot);
        scheduleCaretSync();
    }

    private DefaultMutableTreeNode buildSymbolNode(LspSymbolHit hit) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(hit);
        for (LspSymbolHit child : hit.getChildren()) {
            node.add(buildSymbolNode(child));
        }
        return node;
    }

    private void expandStructureNodes() {
        expandNodesWithChildren(rootNode);
    }

    private void expandNodesWithChildren(DefaultMutableTreeNode parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObject = child.getUserObject();
            if (userObject instanceof LspSymbolHit hit && hit.hasChildren()) {
                tree.expandPath(new TreePath(child.getPath()));
                expandNodesWithChildren(child);
            }
        }
    }

    private void scheduleCaretSync() {
        if (caretSyncTimer == null) {
            caretSyncTimer = new Timer(120, e -> syncCaretSelection());
            caretSyncTimer.setRepeats(false);
        }
        caretSyncTimer.restart();
    }

    private void updateCaption(Buffer buffer, LspStructureHub.StructureSnapshot snapshot) {
        String fileName = buffer != null ? buffer.getName() : "";
        switch (snapshot.getState()) {
            case LOADING -> caption.setText(jEdit.getProperty("lsp-structure.loading"));
            case READY -> caption.setText(jEdit.getProperty("lsp-structure.caption",
                new Object[] {
                    fileName,
                    Integer.valueOf(snapshot.getSymbolCount())
                }));
            case UNAVAILABLE -> caption.setText(jEdit.getProperty(snapshot.getMessageKey()));
            default -> caption.setText(jEdit.getProperty("lsp-structure.empty"));
        }
    }

    private void syncCaretSelection() {
        Buffer buffer = view.getBuffer();
        if (buffer == null || rootNode.getChildCount() == 0) {
            return;
        }
        int caret = view.getTextArea().getCaretPosition();
        TreePath bestPath = findBestSymbolPath(rootNode, buffer, caret);
        if (bestPath == null) {
            return;
        }
        TreePath current = tree.getSelectionPath();
        if (bestPath.equals(current)) {
            return;
        }
        tree.setSelectionPath(bestPath);
        tree.scrollPathToVisible(bestPath);
    }

    private TreePath findBestSymbolPath(DefaultMutableTreeNode parent, Buffer buffer,
                                        int offset) {
        TreePath best = null;
        int bestDepth = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            Object userObject = child.getUserObject();
            if (!(userObject instanceof LspSymbolHit hit) || !hit.containsOffset(buffer, offset)) {
                continue;
            }
            TreePath childPath = new TreePath(child.getPath());
            int depth = child.getPath().length;
            if (depth > bestDepth) {
                best = childPath;
                bestDepth = depth;
            }
            TreePath nested = findBestSymbolPath(child, buffer, offset);
            if (nested != null && nested.getPathCount() > bestDepth) {
                best = nested;
                bestDepth = nested.getPathCount();
            }
        }
        return best;
    }

    private void goToSelectedSymbol() {
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

    private final class StructureTreeCellRenderer extends EnhancedTreeCellRenderer {
        @Override
        protected EnhancedTreeCellRenderer newInstance() {
            return new StructureTreeCellRenderer();
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
            if (userObject instanceof LspSymbolHit hit) {
                setIcon(LspStructureIcons.iconFor(hit.getKind()));
                setText(hit.getName());
                String tooltip = hit.getDetail();
                if (tooltip == null || tooltip.isBlank()) {
                    tooltip = hit.getLabel();
                }
                setToolTipText(tooltip);
            }
        }
    }
}
