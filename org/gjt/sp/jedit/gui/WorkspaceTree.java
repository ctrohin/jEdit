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
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.layout.WrapLayout;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Map;

public class WorkspaceTree extends JPanel implements DefaultFocusComponent, DockableWindow {
    public static final String NAME = "workspace";
    public static final String FOLDER_KEY = "workspace.folder";
    private final View view;
    private JButton reload;
    private JButton openFolder;
    private FlatTree tree;

    private static String currentWorkspace;

    public WorkspaceTree(View view) {
        super(new BorderLayout());
        this.view = view;
        add(BorderLayout.NORTH, createToolbar());
        add(BorderLayout.CENTER, createTree());
        loadFolder(jEdit.getProperty(FOLDER_KEY));
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
                if (node.isLoaded()) {
                    return;
                }

                node.removeAllChildren();
                addChildren(node, false);
                node.setLoaded(true);
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(node);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {}
        });

        return new JScrollPane(tree);
    }

    private final Map<String, Icon> iconCache = new Hashtable<>();
    private Icon getCachedIcon(final File file) {
        String ext = file.isDirectory() ? "__DIRECTORY__" : getFileExtension(file);
        if (iconCache.containsKey(ext)) {
            return iconCache.get(ext);
        }
        final var icon = FileSystemView.getFileSystemView().getSystemIcon(file);
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

        reload = new RolloverButton(IconManager.loadIcon("MatIcons.REFRESH:22"));
        reload.addActionListener(e -> loadFolder(currentWorkspace));
        panel.add(reload);

        openFolder = new RolloverButton(IconManager.loadIcon("MatIcons.FOLDER_OPEN:22"));
        openFolder.addActionListener(e -> chooseWorkspace());
        panel.add(openFolder);
        return panel;
    }

    private void chooseWorkspace() {
        final var chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        final var choice = chooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            loadFolder(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void loadFolder(final String folder) {
        if (folder == null) {
            return;
        }
        currentWorkspace = folder;
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
