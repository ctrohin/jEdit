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
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.layout.WrapLayout;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
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
    private JButton newFile;
    private JButton newFolder;
    private JButton sortBy;
    private FlatTree tree;
    private JPanel pleaseWait;
    private JPanel noWorkpaceLoaded;

    private static String currentWorkspace;

    public WorkspaceTree(View view) {
        super(new BorderLayout());
        add(BorderLayout.NORTH, createToolbar());
        add(BorderLayout.CENTER, createTree());
        this.view = view;
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
                    // Use system icons
                    setIcon(getCachedIcon(f));
                }
                return this;
            }
        });
        return new JScrollPane(tree);
    }

    private final Map<String, Icon> iconCache = new Hashtable<>();
    private Icon getCachedIcon(final File file) {
        String ext = getFileExtension(file);
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

        // Check if dot exists and is not the first character
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }

        return ""; // No extension found
    }

    private JComponent createToolbar() {
        JPanel panel = new JPanel(new WrapLayout(WrapLayout.LEFT, 2, 2));

        /*
        		EnhancedButton b = new EnhancedButton(icon,toolTip,name,context);
		b.setPreferredSize(new Dimension(32,32));

        * */

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
        jEdit.setProperty(FOLDER_KEY, folder);
        tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Please wait...")));
        new Thread(() -> {
            final var root = new FileNode(new File(folder));
            addChildren(root);
            SwingUtilities.invokeLater(() -> {
                tree.setModel(new DefaultTreeModel(root));
            });
        }).start();
    }


    @Override
    public void focusOnDefaultComponent() {

    }

    @Override
    public void move(String newPosition) {
    }


    /**
     * Custom Node class to handle File objects and display names correctly
     */
    private static class FileNode extends DefaultMutableTreeNode {
        public FileNode(File file) {
            super(file);
        }

        @Override
        public String toString() {
            File file = (File) getUserObject();
            String name = file.getName();
            // Handle root directory cases where name might be empty
            return (name.isEmpty()) ? file.getPath() : name;
        }

        public boolean isDirectory() {
            return ((File) getUserObject()).isDirectory();
        }
    }

    public File[] getSortedChildren(File directory) {
        File[] files = directory.listFiles();

        if (files == null) return new File[0];

        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                // 1. Sort by Directory vs File
                if (f1.isDirectory() && !f2.isDirectory()) {
                    return -1; // f1 comes first
                } else if (!f1.isDirectory() && f2.isDirectory()) {
                    return 1; // f2 comes first
                }

                // 2. If both are same type, sort alphabetically (case-insensitive)
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });

        return files;
    }
    /**
     * Populates a node with its file system children
     */
    private void addChildren(FileNode node) {
        File file = (File) node.getUserObject();
        File[] children = getSortedChildren(file);

        if (children != null) {
            for (File child : children) {
                FileNode childNode = new FileNode(child);
                node.add(childNode);
                // If it's a directory, we could recurse here,
                // but for large systems, use a TreeWillExpandListener instead.
                if (child.isDirectory()) {
                    addChildren(childNode);
                }
            }
        }
    }
}
