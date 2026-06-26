/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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

package org.gjt.sp.jedit.gui;

import com.formdev.flatlaf.extras.components.FlatTree;
import com.formdev.flatlaf.util.SystemFileChooser;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.layout.WrapLayout;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.icons.WorkspaceFileIcons;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.jedit.search.DirectoryListSet;
import org.gjt.sp.jedit.search.SearchAndReplace;
import org.gjt.sp.jedit.search.SearchDialog;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;
import org.jedit.build.ProjectFolderListener;
import org.jedit.build.WorkspaceProjectRunner;
import org.jedit.build.WorkspaceTestRunner;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class WorkspaceTreeView extends JPanel implements DefaultFocusComponent, DockableWindow {
    public static final String NAME = "workspace";
    public static final String FOLDER_KEY = "workspace.folder";
    public static final String RECENTS_KEY = "workspace.recents";
    public static final int MAX_RECENTS = 20;

    private final View view;
    private FlatTree tree;
    private final JComponent toolbar;
    private final JComponent treeView;
    private final ArrayList<String> recentFolders = new ArrayList<>();

    private volatile String currentWorkspace;
    private boolean opened = false;
    private JButton runProjectButton;
    private JButton runTestsButton;
    private JButton runConfigMenuButton;
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateRunButtons);

    /**
     * Shows the project-folder chooser for the given view (from the File menu, etc.).
     */
    public static void openProjectFolder(View view) {
        if (view == null) {
            view = jEdit.getActiveView();
        }
        if (view == null) {
            return;
        }
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        JComponent comp = dwm.getDockableWindow(NAME);
        if (comp instanceof WorkspaceTreeView workspace) {
            workspace.chooseWorkspace();
        }
    }

    public WorkspaceTreeView(View view) {
        super(new BorderLayout());
        this.view = view;
        WorkspaceOpenFiles.ensureRegistered();
        toolbar = createToolbar();
        treeView = createTree();
        loadFolder(jEdit.getProperty(FOLDER_KEY), false);
        loadRecents();
    }

    private void loadRecents() {
        final var recentsStoredValue = jEdit.getProperty(RECENTS_KEY);
        Optional.ofNullable(recentsStoredValue)
            .map(r -> r.split(">"))
            .ifPresent(recents -> recentFolders.addAll(Arrays.asList(recents)));
    }

    private void saveRecents(final String newAddition) {
        if (newAddition == null) {
            return;
        }
        while (recentFolders.remove(newAddition));
        recentFolders.addFirst(newAddition);
        if (recentFolders.size() > MAX_RECENTS) {
            recentFolders.removeLast();
        }
        final var recentsStr = String.join(">", recentFolders);
        jEdit.setProperty(RECENTS_KEY, String.join(">", recentFolders));
    }
    private void loadLayout() {
        removeAll();
        if (currentWorkspace != null) {
            opened = true;
            add(BorderLayout.NORTH, toolbar);
            add(BorderLayout.CENTER, treeView);
        }
        else {
            opened = false;
            add(BorderLayout.CENTER, createNoFolderComponent());
        }
        repaint();
        revalidate();
        updateRunButtons();
    }

    private void searchInFolder(final File file) {
        SearchAndReplace.setSearchFileSet(new DirectoryListSet(
            file.isDirectory() ? file.getAbsolutePath() : file.getParentFile().getAbsolutePath(), "*",true));
        SearchDialog.showSearchDialog(view,null,SearchDialog.DIRECTORY);
    }

    private JComponent createNoFolderComponent() {
        final JPanel panel = new JPanel(new BorderLayout());
        final ArrayList<FlutterUI> uiColumn = new ArrayList<>(List.of(
            FlutterUI.Text("No project folder selected.").build(),
            FlutterUI.Button("Open project folder").onPressed(this::chooseWorkspace).build()
        ));

        if (!recentFolders.isEmpty()) {
            uiColumn.add(FlutterUI.Box().height(20).build());
            uiColumn.add(FlutterUI.Text("Recents").style(new FlutterUI.TextStyle().bold()).build());
            for (final var recentItem : recentFolders) {
                uiColumn.add(
                    FlutterUI.Text(recentItem)
                        .style(new FlutterUI.TextStyle()
                            .italic()
                        )
                        .onTap(() -> loadFolder(recentItem, true))
                        .build()
                );
            }
        }

        final var comp = FlutterUI.Padding(10, 10, 10, 10,
            FlutterUI.Column(uiColumn)
        ).getComponent();
        panel.add(comp, BorderLayout.NORTH);
        return panel;
    }

    private JComponent createTree() {
        tree = new FlatTree();
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                                   boolean sel, boolean exp, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, exp, leaf, row, hasFocus);
                if (value instanceof FileNode) {
                    File f = (File) ((FileNode) value).getUserObject();
                    setIcon(getCachedIcon(f));
                }
                return this;
            }
        });

        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                FileNode node = (FileNode) event.getPath().getLastPathComponent();
                expandNode(node);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMouse(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouse(e);
            }

            private void handleMouse(MouseEvent e) {
                int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                if (row == -1) return;
                
                if (e.isPopupTrigger()) {
                    tree.setSelectionRow(row);
                    FileNode node = (FileNode) tree.getPathForRow(row).getLastPathComponent();
                    showPopupMenu(e, node);
                } else if (e.getID() == MouseEvent.MOUSE_RELEASED && e.getClickCount() == 2) {
                    FileNode node = (FileNode) tree.getPathForRow(row).getLastPathComponent();
                    openFile((File) node.getUserObject());
                }
            }
        });

        return new JScrollPane(tree);
    }

    private void expandNode(FileNode node) {
        if (node.isLoaded() || node.isLoading()) {
            return;
        }

        node.setLoading(true);
        File directory = (File) node.getUserObject();
        ThreadUtilities.runInBackground(() -> {
            File[] children = getSortedChildren(directory);
            SwingUtilities.invokeLater(() -> finishExpandNode(node, children));
        });
    }

    private void finishExpandNode(FileNode node, File[] children) {
        if (node.isLoaded()) {
            node.setLoading(false);
            return;
        }
        node.removeAllChildren();
        addChildNodes(node, children, false);
        node.setLoaded(true);
        node.setLoading(false);
        if (tree.getModel() != null) {
            ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
        }
    }

    private void openFile(final File file) {
        if (file.isDirectory()) {
            TreePath path = findPath(file);
            if (path != null) {
                if (tree.isExpanded(path)) {
                    tree.collapsePath(path);
                } else {
                    tree.expandPath(path);
                }
            }
        } else {
            jEdit.openFile(view, file.getAbsolutePath());
        }
    }

    public void locateFile() {
        if (view.getBuffer() == null) {
            return;
        }

        String path = view.getBuffer().getPath();
        if (path == null) {
            return;
        }

        File target = new File(path);
        if (!target.exists()) {
            return;
        }

        if (currentWorkspace == null
            || !target.getAbsolutePath().startsWith(new File(currentWorkspace).getAbsolutePath())) {
            Toast.showToast("File is not within current workspace.", this, 3000);
            return;
        }

        ArrayList<File> breadcrumbs = new ArrayList<>();
        File current = target;
        while (current != null) {
            breadcrumbs.add(current);
            if (current.getAbsolutePath().equals(new File(currentWorkspace).getAbsolutePath())) {
                break;
            }
            current = current.getParentFile();
        }
        Collections.reverse(breadcrumbs);

        if (breadcrumbs.isEmpty() || !(tree.getModel().getRoot() instanceof FileNode)) {
            return;
        }

        final String workspace = currentWorkspace;
        final List<File> trail = List.copyOf(breadcrumbs);
        ThreadUtilities.runInBackground(() -> {
            List<File[]> prefetched = prefetchBreadcrumbChildren(trail);
            if (prefetched == null) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (!workspace.equals(currentWorkspace)) {
                    return;
                }
                applyLocatedFile(trail, prefetched);
            });
        });
    }

    private List<File[]> prefetchBreadcrumbChildren(List<File> breadcrumbs) {
        if (breadcrumbs.isEmpty()) {
            return null;
        }
        List<File[]> levels = new ArrayList<>();
        File dir = breadcrumbs.get(0);
        for (int i = 1; i < breadcrumbs.size(); i++) {
            File[] children = getSortedChildren(dir);
            levels.add(children);
            File crumb = breadcrumbs.get(i);
            File next = null;
            for (File child : children) {
                if (child.equals(crumb)) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            if (next.isDirectory() && i < breadcrumbs.size() - 1) {
                dir = next;
            }
        }
        return levels;
    }

    private void applyLocatedFile(List<File> breadcrumbs, List<File[]> prefetched) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        if (!(model.getRoot() instanceof FileNode node)) {
            return;
        }

        TreePath treePath = new TreePath(node);
        for (int i = 1; i < breadcrumbs.size(); i++) {
            File crumb = breadcrumbs.get(i);
            File[] children = prefetched.get(i - 1);

            if (!node.isLoaded()) {
                node.removeAllChildren();
                addChildNodes(node, children, false);
                node.setLoaded(true);
                node.setLoading(false);
            }

            FileNode childNode = null;
            for (int j = 0; j < node.getChildCount(); j++) {
                if (node.getChildAt(j) instanceof FileNode child
                    && child.getUserObject().equals(crumb)) {
                    childNode = child;
                    break;
                }
            }
            if (childNode == null) {
                return;
            }
            node = childNode;
            treePath = treePath.pathByAddingChild(node);
        }

        model.nodeStructureChanged((FileNode) model.getRoot());
        tree.setSelectionPath(treePath);
        tree.scrollPathToVisible(treePath);
    }

    private TreePath findPath(File file) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        FileNode root = (FileNode) model.getRoot();
        return findPath(new TreePath(root), file);
    }

    private TreePath findPath(TreePath parent, File file) {
        FileNode node = (FileNode) parent.getLastPathComponent();
        if (node.getUserObject().equals(file)) {
            return parent;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            Object childObj = node.getChildAt(i);
            if (childObj instanceof FileNode child) {
                TreePath path = parent.pathByAddingChild(child);
                TreePath result = findPath(path, file);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private void showPopupMenu(MouseEvent e, FileNode node) {
        File file = (File) node.getUserObject();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem open = new JMenuItem(file.isDirectory() ? "Expand/collapse" : "Open");
        open.addActionListener(ev -> openFile(file));
        menu.add(open);
        if (file.isDirectory()) {
            JMenuItem search = new JMenuItem("Search in folder");
            search.addActionListener(ev -> searchInFolder(file));
            menu.add(search);
        }
        if (node.isWorkspaceRoot()) {
            menu.addSeparator();
            JMenuItem removeRoot = new JMenuItem(jEdit.getProperty("workspace.remove-folder"));
            removeRoot.addActionListener(ev -> removeWorkspaceRoot(file));
            removeRoot.setEnabled(!file.getAbsolutePath().equals(currentWorkspace));
            menu.add(removeRoot);
        }
        menu.addSeparator();

        if (file.isDirectory()) {
            JMenuItem newFolder = new JMenuItem("New folder");
            newFolder.addActionListener(ev -> createNewFolder(file));
            menu.add(newFolder);

            JMenuItem newFile = new JMenuItem("New file");
            newFile.addActionListener(ev -> createNewFile(file));
            menu.add(newFile);

            if (!node.isDeletableNode()) {
                menu.addSeparator();
            }
        }

        if (!node.isDeletableNode()) {
            JMenuItem rename = new JMenuItem("Rename");
            rename.addActionListener(ev -> renameFile(file));
            menu.add(rename);

            JMenuItem move = new JMenuItem("Move");
            move.addActionListener(ev -> moveFile(file));
            menu.add(move);

            menu.addSeparator();

            JMenuItem delete = new JMenuItem("Delete");
            delete.addActionListener(ev -> deleteFile(file));
            menu.add(delete);
        }

        menu.show(tree, e.getX(), e.getY());
    }

    private void createNewFolder(File parent) {
        String name = JOptionPane.showInputDialog(view, "Folder name:");
        if (name != null && !name.isEmpty()) {
            File newDir = new File(parent, name);
            if (newDir.mkdir()) {
                refreshNodeForFile(parent);
            }
        }
    }

    private void createNewFile(File parent) {
        String name = JOptionPane.showInputDialog(view, "File name:");
        if (name != null && !name.isEmpty()) {
            File newFile = new File(parent, name);
            try {
                if (newFile.createNewFile()) {
                    refreshNodeForFile(parent);
                    jEdit.openFile(view, newFile.getAbsolutePath());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(view, "Error creating file: " + ex.getMessage());
            }
        }
    }

    private void renameFile(File file) {
        String newName = JOptionPane.showInputDialog(view, "New name:", file.getName());
        if (newName != null && !newName.isEmpty()) {
            File newFile = new File(file.getParentFile(), newName);
            if (file.renameTo(newFile)) {
                refreshNodeForFile(file.getParentFile());
            }
        }
    }

    private void moveFile(File file) {
        SystemFileChooser chooser = new SystemFileChooser(currentWorkspace);
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showDialog(view, "Move Here") == SystemFileChooser.APPROVE_OPTION) {
            File targetDir = chooser.getSelectedFile();
            File newFile = new File(targetDir, file.getName());
            if (file.renameTo(newFile)) {
                refreshNodeForFile(file.getParentFile());
                refreshNodeForFile(targetDir);
            }
        }
    }

    private void deleteFile(File file) {
        int confirm = JOptionPane.showConfirmDialog(view, 
            "Delete " + file.getName() + "? This operation cannot be undone!", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (file.delete()) {
                refreshNodeForFile(file.getParentFile());
            } else {
                JOptionPane.showMessageDialog(view, "Could not delete file. Is it a non-empty folder?");
            }
        }
    }

    private void refreshNodeForFile(File file) {
        loadFolder(currentWorkspace, false);
    }

    private final Map<String, Icon> iconCache = new Hashtable<>();
    private Icon getCachedIcon(final File file) {
        String ext = file.isDirectory() ? "__DIRECTORY__" : getFileExtension(file);
        if (iconCache.containsKey(ext)) {
            return iconCache.get(ext);
        }
        final Icon icon = WorkspaceFileIcons.getIcon(file);
        iconCache.put(ext, icon);
        return icon;
    }

    public String getFileExtension(File file) {
        if (file == null || !file.exists()) {
            return "";
        }

        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private JComponent createToolbar() {
        JPanel panelWest = new JPanel(new WrapLayout(WrapLayout.LEFT, 2, 2));

        JButton reload = new RolloverButton(IconManager.loadIcon("MatIcons.REFRESH:22"), "Reload");
        reload.addActionListener(e -> loadFolder(currentWorkspace, false));
        panelWest.add(reload);

        JButton openFolder = new RolloverButton(IconManager.loadIcon("MatIcons.FOLDER_OPEN:22"), "Open folder");
        openFolder.addActionListener(e -> chooseWorkspace());
        panelWest.add(openFolder);

        JButton addFolder = new RolloverButton(IconManager.loadIcon("MatIcons.CREATE_NEW_FOLDER:22"),
            jEdit.getProperty("workspace.add-folder"));
        addFolder.addActionListener(e -> addWorkspaceFolder());
        panelWest.add(addFolder);

        JButton locate = new RolloverButton(IconManager.loadIcon("MatIcons.TARGET:22"), "Locate current file");
        locate.addActionListener(e -> locateFile());
        panelWest.add(locate);

        JButton recentsSelector = new RolloverButton(IconManager.loadIcon("MatIcons.FOLDER_SPECIAL:22"), "Recent folders");
        recentsSelector.addActionListener(this::showRecents);
        panelWest.add(recentsSelector);

        panelWest.add(Box.createHorizontalStrut(12));
        JPanel runPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        runProjectButton = new RolloverButton(
            IconManager.loadIcon("MatIcons.PLAY_ARROW:22"),
            jEdit.getProperty("workspace-run.run"));
        runProjectButton.addActionListener(e -> runCurrentProject());
        runPanel.add(runProjectButton);

        runTestsButton = new RolloverButton(
            IconManager.loadIcon("MatIcons.SCIENCE:22"),
            jEdit.getProperty("workspace-test.run"));
        runTestsButton.addActionListener(e -> runCurrentTests());
        runPanel.add(runTestsButton);

        runConfigMenuButton = new RolloverButton(
            IconManager.loadIcon("MatIcons.ARROW_DROP_DOWN:22"),
            jEdit.getProperty("workspace-run.configurations"));
        runConfigMenuButton.addActionListener(this::showRunConfigurationsMenu);
        runPanel.add(runConfigMenuButton);
        panelWest.add(runPanel);

        panelWest.add(Box.createHorizontalStrut(20));
        JButton close = new RolloverButton(IconManager.loadIcon("MatIcons.CLOSE:22"), "Close project folder");
        close.addActionListener(e -> {
            loadFolder(null, false);
        });
        JPanel panelEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        panelEast.add(close);

        JPanel rp = new JPanel(new BorderLayout());
        rp.add(panelWest, BorderLayout.WEST);
        rp.add(panelEast, BorderLayout.EAST);
        return rp;
    }

    private void showRecents(final ActionEvent e) {
        Log.log(Log.ERROR, this, "Show recents called: " + recentFolders);
        final var menu = new JPopupMenu("Switch to folder");
        for (String recents : recentFolders) {
            final var f = new File(recents);
            if (f.exists() && f.isDirectory()) {
                final var menuItem = new JMenuItem(f.getAbsolutePath());
                menuItem.addActionListener(l -> loadFolder(f.getAbsolutePath(), true));
                menu.add(menuItem);
            }
        }
        menu.show((JComponent)e.getSource(), 0, 0);
    }

    private void chooseWorkspace() {
        final var chooser = new SystemFileChooser(currentWorkspace);
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        final var choice = chooser.showOpenDialog(this);
        if (choice == SystemFileChooser.APPROVE_OPTION) {
            loadFolder(chooser.getSelectedFile().getAbsolutePath(), true);
        }
    }

    private void loadFolder(final String folder, final boolean saveToRecents) {
        final String previousWorkspace = currentWorkspace;
        if (!Objects.equals(folder, previousWorkspace) && previousWorkspace != null) {
            WorkspaceOpenFiles.save(previousWorkspace);
        }

        final var willBeOpened = Objects.nonNull(folder);
        var emitEvent = !Objects.equals(folder, currentWorkspace);
        currentWorkspace = folder;
        if (saveToRecents) {
            saveRecents(currentWorkspace);
        }
        if (folder == null) {
            jEdit.unsetProperty(FOLDER_KEY);
        } else {
            jEdit.setProperty(FOLDER_KEY, folder);
        }
        if (emitEvent) {
            final var event = Optional.ofNullable(currentWorkspace)
                .map(ws -> new ProjectFolderOpened(this, ws))
                .map(EBMessage.class::cast)
                .orElseGet(() -> new ProjectFolderClosed(this));
            EditBus.send(event);
        }
        if (willBeOpened != opened) {
            loadLayout();
        }
        if (folder == null) {
            return;
        }

        File rootFile = new File(folder);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            return;
        }

        rebuildWorkspaceTree();

        if (folder != null && !Objects.equals(folder, previousWorkspace)) {
            WorkspaceOpenFiles.restore(view, folder);
        }
        updateRunButtons();
    }

    private void rebuildWorkspaceTree() {
        List<File> roots = org.gjt.sp.jedit.project.ProjectRoots.workspaceRoots();
        if (roots.isEmpty()) {
            return;
        }
        if (roots.size() == 1) {
            loadSingleRoot(roots.get(0));
            return;
        }
        DefaultMutableTreeNode synthetic = new DefaultMutableTreeNode("workspace-roots");
        for (File rootFile : roots) {
            FileNode root = new FileNode(rootFile);
            root.setWorkspaceRoot(true);
            root.add(new DefaultMutableTreeNode("Loading..."));
            synthetic.add(root);
        }
        tree.setModel(new DefaultTreeModel(synthetic));
        tree.setRootVisible(false);
        for (int i = 0; i < synthetic.getChildCount(); i++) {
            expandNode((FileNode) synthetic.getChildAt(i));
        }
    }

    private void loadSingleRoot(File rootFile) {
        FileNode root = new FileNode(rootFile);
        root.setWorkspaceRoot(true);
        root.add(new DefaultMutableTreeNode("Loading..."));
        tree.setModel(new DefaultTreeModel(root));
        ThreadUtilities.runInBackground(() -> {
            File[] children = getSortedChildren(rootFile);
            SwingUtilities.invokeLater(() -> {
                if (!rootFile.getAbsolutePath().equals(currentWorkspace)) {
                    return;
                }
                root.removeAllChildren();
                addChildNodes(root, children, false);
                root.setLoaded(true);
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(root);
            });
        });
    }

    private void addWorkspaceFolder() {
        final var chooser = new SystemFileChooser(currentWorkspace);
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION) {
            return;
        }
        File selected = chooser.getSelectedFile();
        if (selected == null || !selected.isDirectory()) {
            return;
        }
        if (currentWorkspace != null && selected.getAbsolutePath().equals(currentWorkspace)) {
            return;
        }
        org.gjt.sp.jedit.project.ProjectRoots.addExtraRoot(selected);
        rebuildWorkspaceTree();
    }

    private void removeWorkspaceRoot(File folder) {
        if (folder == null) {
            return;
        }
        if (currentWorkspace != null && folder.getAbsolutePath().equals(currentWorkspace)) {
            JOptionPane.showMessageDialog(view,
                jEdit.getProperty("workspace.remove-primary-root.denied"),
                jEdit.getProperty("workspace.title"),
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        org.gjt.sp.jedit.project.ProjectRoots.removeExtraRoot(folder);
        rebuildWorkspaceTree();
    }

    private void updateRunButtons() {
        if (runProjectButton == null || runConfigMenuButton == null) {
            return;
        }
        File root = currentWorkspace != null ? new File(currentWorkspace) : null;
        boolean canRun = WorkspaceProjectRunner.canRun(root);
        runProjectButton.setEnabled(canRun);
        runConfigMenuButton.setEnabled(canRun);
        if (canRun && root != null) {
            var cfg = WorkspaceProjectRunner.resolveDefaultConfiguration(root);
            if (cfg != null) {
                runProjectButton.setToolTipText(jEdit.getProperty(
                    "workspace-run.run.tooltip.named",
                    new String[] {WorkspaceProjectRunner.configurationLabel(cfg)}));
            } else {
                runProjectButton.setToolTipText(jEdit.getProperty("workspace-run.run"));
            }
        } else {
            runProjectButton.setToolTipText(jEdit.getProperty("workspace-run.run"));
        }
        if (runTestsButton != null) {
            boolean canTest = WorkspaceTestRunner.canRunTests(root);
            runTestsButton.setEnabled(canTest);
            runTestsButton.setToolTipText(canTest
                ? jEdit.getProperty("workspace-test.run")
                : jEdit.getProperty("workspace-test.run-disabled"));
        }
    }

    private void runCurrentProject() {
        if (currentWorkspace == null) {
            return;
        }
        File root = new File(currentWorkspace);
        if (!WorkspaceProjectRunner.canRun(root)) {
            return;
        }
        WorkspaceProjectRunner.runProject(view, root);
    }

    private void runCurrentTests() {
        if (currentWorkspace == null) {
            return;
        }
        File root = new File(currentWorkspace);
        if (!WorkspaceTestRunner.canRunTests(root)) {
            return;
        }
        WorkspaceTestRunner.runTests(view, root);
    }

    private void showRunConfigurationsMenu(ActionEvent event) {
        if (currentWorkspace == null) {
            return;
        }
        File root = new File(currentWorkspace);
        if (!WorkspaceProjectRunner.canRun(root)) {
            return;
        }
        WorkspaceProjectRunner.showRunConfigurationMenu(event, view, root, this::updateRunButtons);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        updateRunButtons();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }


    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    @Override
    public void move(String newPosition) {}


    private static class FileNode extends DefaultMutableTreeNode {
        private boolean loaded = false;
        private boolean loading = false;
        private boolean workspaceRoot = false;

        public FileNode(File file) {
            super(file);
        }

        boolean isWorkspaceRoot() {
            return workspaceRoot;
        }

        void setWorkspaceRoot(boolean workspaceRoot) {
            this.workspaceRoot = workspaceRoot;
        }

        boolean isWorkspaceTreeRoot() {
            return workspaceRoot;
        }

        boolean isDeletableNode() {
            if (workspaceRoot) {
                return false;
            }
            Object parent = getParent();
            return parent != null
                && (!(parent instanceof DefaultMutableTreeNode node)
                    || !"workspace-roots".equals(node.getUserObject()));
        }

        public boolean isLoaded() {
            return loaded;
        }

        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }

        public boolean isLoading() {
            return loading;
        }

        public void setLoading(boolean loading) {
            this.loading = loading;
        }

        @Override
        public String toString() {
            File file = (File) getUserObject();
            String name = file.getName();
            return (name.isEmpty()) ? file.getPath() : name;
        }

        @Override
        public boolean isLeaf() {
            return !((File) getUserObject()).isDirectory();
        }
    }

    public File[] getSortedChildren(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return new File[0];

        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) {
                return -1;
            } else if (!f1.isDirectory() && f2.isDirectory()) {
                return 1;
            }
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        return files;
    }

    private void addChildren(FileNode node, boolean recursive) {
        File file = (File) node.getUserObject();
        addChildNodes(node, getSortedChildren(file), recursive);
    }

    private void addChildNodes(FileNode node, File[] children, boolean recursive) {
        if (children != null) {
            for (File child : children) {
                FileNode childNode = new FileNode(child);
                node.add(childNode);

                if (child.isDirectory()) {
                    if (recursive) {
                        addChildren(childNode, true);
                        childNode.setLoaded(true);
                    } else {
                        // Add a dummy node to make it expandable
                        childNode.add(new DefaultMutableTreeNode("Loading..."));
                        childNode.setLoaded(false);
                    }
                }
            }
        }
    }
}
