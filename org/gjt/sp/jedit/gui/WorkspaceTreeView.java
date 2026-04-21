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

package org.gjt.sp.jedit.gui;

import com.formdev.flatlaf.extras.components.FlatTree;
import com.formdev.flatlaf.util.SystemFileChooser;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.layout.WrapLayout;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.jedit.search.DirectoryListSet;
import org.gjt.sp.jedit.search.SearchAndReplace;
import org.gjt.sp.jedit.search.SearchDialog;
import org.gjt.sp.util.Log;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;

public class WorkspaceTreeView extends JPanel implements DefaultFocusComponent, DockableWindow {
    public static final String NAME = "workspace";
    public static final String FOLDER_KEY = "workspace.folder";
    private final View view;
    private FlatTree tree;
    private final JComponent emptyViewComponent;
    private final JComponent toolbar;
    private final JComponent treeView;

    private volatile String currentWorkspace;
    private boolean opened = false;

    public WorkspaceTreeView(View view) {
        super(new BorderLayout());
        this.view = view;
        toolbar = createToolbar();
        treeView = createTree();
        emptyViewComponent = createNoFolderComponent();
        loadFolder(jEdit.getProperty(FOLDER_KEY));
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
            add(BorderLayout.CENTER, emptyViewComponent);
        }
        repaint();
        revalidate();
    }

    private void searchInFolder(final File file) {
        SearchAndReplace.setSearchFileSet(new DirectoryListSet(
            file.isDirectory() ? file.getAbsolutePath() : file.getParentFile().getAbsolutePath(), "*",true));
        SearchDialog.showSearchDialog(view,null,SearchDialog.DIRECTORY);
    }

    private JComponent createNoFolderComponent() {
        final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        final JPanel grid = new JPanel(new GridLayout(2, 1, 5, 5));
        grid.add(new JLabel("No project folder selected."));
        JButton btn = new JButton("Open project folder");
        btn.addActionListener(e -> chooseWorkspace());
        grid.add(btn);
        panel.add(grid);
        return panel;
    }

    private JComponent createTree() {
        tree = new FlatTree();
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value,
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
        if (node.isLoaded()) {
            return;
        }

        node.removeAllChildren();
        addChildren(node, false);
        node.setLoaded(true);
        ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
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
        if (view.getBuffer() == null) return;
        
        String path = view.getBuffer().getPath();
        if (path == null) return;

        File target = new File(path);
        if (!target.exists()) return;

        // Verify if it's within current workspace
        if (currentWorkspace == null || !target.getAbsolutePath().startsWith(new File(currentWorkspace).getAbsolutePath())) {
            Toast.showToast("File is not within current workspace.", this, 3000);
            return;
        }

        // Build list of files from root to target
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

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        FileNode node = (FileNode) model.getRoot();
        TreePath treePath = new TreePath(node);

        for (int i = 1; i < breadcrumbs.size(); i++) {
            File crumb = breadcrumbs.get(i);
            expandNode(node);
            
            boolean found = false;
            for (int j = 0; j < node.getChildCount(); j++) {
                FileNode child = (FileNode) node.getChildAt(j);
                if (child.getUserObject().equals(crumb)) {
                    node = child;
                    treePath = treePath.pathByAddingChild(node);
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }

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
        open.addActionListener(_ -> openFile(file));
        menu.add(open);
        if (file.isDirectory()) {
            JMenuItem search = new JMenuItem("Search in folder");
            search.addActionListener(_ -> searchInFolder(file));
            menu.add(search);
        }
        menu.addSeparator();

        if (file.isDirectory()) {
            JMenuItem newFolder = new JMenuItem("New folder");
            newFolder.addActionListener(_ -> createNewFolder(file));
            menu.add(newFolder);

            JMenuItem newFile = new JMenuItem("New file");
            newFile.addActionListener(_ -> createNewFile(file));
            menu.add(newFile);

            if (!node.isRoot()) {
                menu.addSeparator();
            }
        }

        if (!node.isRoot()) {
            JMenuItem rename = new JMenuItem("Rename");
            rename.addActionListener(_ -> renameFile(file));
            menu.add(rename);

            JMenuItem move = new JMenuItem("Move");
            move.addActionListener(_ -> moveFile(file));
            menu.add(move);

            menu.addSeparator();

            JMenuItem delete = new JMenuItem("Delete");
            delete.addActionListener(_ -> deleteFile(file));
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
        loadFolder(currentWorkspace);
    }

    private final Map<String, Icon> iconCache = new Hashtable<>();
    private Icon getCachedIcon(final File file) {
        String ext = file.isDirectory() ? "__DIRECTORY__" : getFileExtension(file);
        if (iconCache.containsKey(ext)) {
            return iconCache.get(ext);
        }
        final var icon = FileSystemView.getFileSystemView().getSystemIcon(file);
//        final var icon = IconManager.loadIcon("FileIcons." + (file.isDirectory() ? "FOLDER"  : ext.toUpperCase()) + ":10");
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
        JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 2, 2));

        JButton reload = new RolloverButton(IconManager.loadIcon("MatIcons.REFRESH:22"), "Reload");
        reload.addActionListener(e -> loadFolder(currentWorkspace));
        panel.add(reload);

        JButton openFolder = new RolloverButton(IconManager.loadIcon("MatIcons.FOLDER_OPEN:22"), "Open folder");
        openFolder.addActionListener(e -> chooseWorkspace());
        panel.add(openFolder);

        JButton locate = new RolloverButton(IconManager.loadIcon("MatIcons.TARGET:22"), "Locate current file");
        locate.addActionListener(e -> locateFile());
        panel.add(locate);

        JButton close = new RolloverButton(IconManager.loadIcon("MatIcons.CLOSE:22"), "Close project folder");
        close.addActionListener(e -> {
            loadFolder(null);
        });
        panel.add(close);
        return panel;
    }

    private void chooseWorkspace() {
        final var chooser = new SystemFileChooser(currentWorkspace);
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        final var choice = chooser.showOpenDialog(this);
        if (choice == SystemFileChooser.APPROVE_OPTION) {
            loadFolder(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadFolder(final String folder) {
        final var willBeOpened = Objects.nonNull(folder);
        var emitEvent = !Objects.equals(folder, currentWorkspace);
        currentWorkspace = folder;
        if (emitEvent) {
            final var event = Optional.ofNullable(currentWorkspace)
                .map(ws -> new ProjectFolderOpened(this, ws))
                .map(EBMessage.class::cast)
                .orElseGet(() -> new ProjectFolderClosed(this));
            new Thread(() -> {
                Log.log(Log.ERROR, this, "event emitted");
                EditBus.send(event);
            }).start();
        }
        if (willBeOpened != opened) {
            loadLayout();
        }
        if (folder == null) {
            return;
        }
        jEdit.setProperty(FOLDER_KEY, folder);
        
        File rootFile = new File(folder);
        if (!rootFile.exists() || !rootFile.isDirectory()) {
            return;
        }

        FileNode root = new FileNode(rootFile);
        addChildren(root, false);
        root.setLoaded(true);
        tree.setModel(new DefaultTreeModel(root));
    }


    @Override
    public void focusOnDefaultComponent() {
        tree.requestFocus();
    }

    @Override
    public void move(String newPosition) {}


    private static class FileNode extends DefaultMutableTreeNode {
        private boolean loaded = false;

        public FileNode(File file) {
            super(file);
        }

        public boolean isLoaded() {
            return loaded;
        }

        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
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
        File[] children = getSortedChildren(file);

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
