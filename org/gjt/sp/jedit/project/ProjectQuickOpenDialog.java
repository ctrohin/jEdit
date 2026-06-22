/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;
import static org.gjt.sp.jedit.gui.adapters.MouseAdapters.mouseClicked;

/**
 * Quick-open dialog for files in the project folder.
 */
public class ProjectQuickOpenDialog extends EnhancedDialog {

    private static final int FILTER_DEBOUNCE_MS = 120;

    private static final Map<View, ProjectQuickOpenDialog> INSTANCES = new HashMap<>();

    private final View view;
    private final JTextField searchField;
    private final JList<String> fileList;
    private final JLabel statusLabel;
    private final AtomicInteger filterGeneration = new AtomicInteger();
    private List<String> allPaths = List.of();
    private String cachedRootPath;
    private Timer filterTimer;
    private volatile boolean loading;

    private ProjectQuickOpenDialog(View view) {
        super(view, jEdit.getProperty("project-quick-open.title"), true);
        this.view = view;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleFilter();
            }
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        moveSelection(1);
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        moveSelection(-1);
                        e.consume();
                    }
                    case KeyEvent.VK_ENTER -> {
                        openSelectedFile();
                        e.consume();
                    }
                    default -> {
                    }
                }
            }
        });
        content.add(searchField, BorderLayout.NORTH);

        fileList = new JList<>();
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(formatPathLabel(value));
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        fileList.addMouseListener(mouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelectedFile();
            }
        }));
        fileList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    openSelectedFile();
                    e.consume();
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(fileList);
        scrollPane.setPreferredSize(new Dimension(640, 360));
        content.add(scrollPane, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        content.add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(view);
    }

    public static void showDialog(View view) {
        if (view == null) {
            return;
        }
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            GUIUtilities.error(view, "project-quick-open.no-project", null);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ProjectQuickOpenDialog dialog = INSTANCES.get(view);
            if (dialog == null) {
                dialog = new ProjectQuickOpenDialog(view);
                INSTANCES.put(view, dialog);
            }
            dialog.searchField.setText("");
            dialog.loadFiles(root);
            dialog.setVisible(true);
            dialog.toFront();
            dialog.searchField.requestFocusInWindow();
        });
    }

    @Override
    public void ok() {
        openSelectedFile();
    }

    @Override
    public void cancel() {
        setVisible(false);
    }

    private void loadFiles(File root) {
        loading = true;
        cachedRootPath = root.getAbsolutePath();
        statusLabel.setText(jEdit.getProperty("project-quick-open.loading"));
        fileList.setListData(new String[0]);
        ThreadUtilities.runInBackground(() -> {
            List<String> paths = ProjectFileSearcher.listProjectFiles(root);
            SwingUtilities.invokeLater(() -> {
                allPaths = paths;
                loading = false;
                scheduleFilter();
            });
        });
    }

    private void scheduleFilter() {
        if (loading) {
            return;
        }
        if (filterTimer == null) {
            filterTimer = new Timer(FILTER_DEBOUNCE_MS, e -> runFilter());
            filterTimer.setRepeats(false);
        }
        filterTimer.restart();
    }

    private void runFilter() {
        if (loading) {
            return;
        }
        String query = searchField.getText();
        List<String> paths = allPaths;
        int generation = filterGeneration.incrementAndGet();
        ThreadUtilities.runInBackground(() -> {
            List<String> matches = ProjectFileSearcher.filterQuickOpenPaths(paths, query);
            if (generation != filterGeneration.get()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (generation != filterGeneration.get()) {
                    return;
                }
                applyMatches(matches);
            });
        });
    }

    private void applyMatches(List<String> matches) {
        fileList.setListData(matches.toArray(new String[0]));
        if (!matches.isEmpty()) {
            fileList.setSelectedIndex(0);
        }
        statusLabel.setText(jEdit.getProperty("project-quick-open.status",
            new Object[] { Integer.valueOf(matches.size()) }));
    }

    private void moveSelection(int delta) {
        int size = fileList.getModel().getSize();
        if (size == 0) {
            return;
        }
        int index = fileList.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.max(0, Math.min(size - 1, index + delta));
        }
        fileList.setSelectedIndex(index);
        fileList.ensureIndexIsVisible(index);
    }

    private void openSelectedFile() {
        String path = fileList.getSelectedValue();
        if (path == null && fileList.getModel().getSize() > 0) {
            path = fileList.getModel().getElementAt(0);
        }
        if (path != null) {
            ProjectFindResults.openFile(view, path);
            setVisible(false);
        }
    }

    private String formatPathLabel(String path) {
        if (cachedRootPath == null || path == null) {
            return path;
        }
        if (path.startsWith(cachedRootPath)) {
            String rel = path.substring(cachedRootPath.length());
            if (rel.startsWith(File.separator)) {
                rel = rel.substring(1);
            }
            if (!rel.isEmpty()) {
                return rel;
            }
        }
        return path;
    }
}
