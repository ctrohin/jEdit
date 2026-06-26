/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.EnhancedDialog;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;
import static org.gjt.sp.jedit.gui.adapters.MouseAdapters.mouseClicked;

/**
 * Searches for text in all files under the project folder.
 */
public class ProjectFileSearchDialog extends EnhancedDialog implements ActionListener {

    private static final Map<View, ProjectFileSearchDialog> INSTANCES = new HashMap<>();

    private final View view;
    private final JTextField searchField;
    private final JComboBox<String> searchModeCombo;
    private final JComboBox<String> extensionModeCombo;
    private final JTextField extensionsField;
    private final JList<ProjectSearchMatch> resultsList;
    private final DefaultListModel<ProjectSearchMatch> resultsModel;
    private final JLabel statusLabel;
    private final JButton openInFindResultsButton;
    private final Timer searchTimer;
    private final AtomicInteger searchGeneration = new AtomicInteger();
    private volatile AtomicBoolean searchCancelled = new AtomicBoolean();

    private ProjectFileSearchDialog(View view) {
        super(view, jEdit.getProperty("project-file-search.title"), true);
        this.view = view;

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(content);

        JPanel top = new JPanel(new BorderLayout(6, 6));
        searchField = new JTextField();
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
                        openSelectedMatch();
                        e.consume();
                    }
                    default -> {
                    }
                }
            }
        });
        searchField.getDocument().addDocumentListener(
            new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }
            });
        top.add(searchField, BorderLayout.CENTER);

        searchModeCombo = new JComboBox<>(new String[] {
            jEdit.getProperty("project-file-search.mode.plain"),
            jEdit.getProperty("project-file-search.mode.regexp")
        });
        searchModeCombo.addActionListener(this);
        top.add(searchModeCombo, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        extensionModeCombo = new JComboBox<>(new String[] {
            jEdit.getProperty("project-file-search.extensions.exclude"),
            jEdit.getProperty("project-file-search.extensions.include")
        });
        extensionsField = new JTextField(18);
        extensionModeCombo.addActionListener(this);
        extensionsField.setToolTipText(jEdit.getProperty("project-file-search.extensions.tooltip"));
        extensionsField.getDocument().addDocumentListener(
            new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    scheduleSearch();
                }
            });
        filterPanel.add(extensionModeCombo);
        filterPanel.add(new JLabel(jEdit.getProperty("project-file-search.extensions.label")));
        filterPanel.add(extensionsField);
        center.add(filterPanel, BorderLayout.NORTH);

        resultsModel = new DefaultListModel<>();
        resultsList = new JList<>(resultsModel);
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            if (value != null) {
                label.setText(value.formatListHtml());
            }
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
        resultsList.addMouseListener(mouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openSelectedMatch();
            }
        }));
        JScrollPane scrollPane = new JScrollPane(resultsList);
        scrollPane.setPreferredSize(new Dimension(700, 360));
        center.add(scrollPane, BorderLayout.CENTER);
        content.add(top, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        openInFindResultsButton = new JButton(
            jEdit.getProperty("project-file-search.open-in-find-results"));
        openInFindResultsButton.addActionListener(this);
        openInFindResultsButton.setEnabled(false);
        buttons.add(openInFindResultsButton);
        JButton close = new JButton(jEdit.getProperty("common.close"));
        close.addActionListener(e -> cancel());
        buttons.add(close);
        bottom.add(buttons, BorderLayout.EAST);
        content.add(bottom, BorderLayout.SOUTH);

        searchTimer = new Timer(350, e -> runSearch());
        searchTimer.setRepeats(false);

        GUIUtilities.requestFocus(this, searchField);
        pack();
        setLocationRelativeTo(view);
    }

    public static void showDialog(View view) {
        if (view == null) {
            return;
        }
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            GUIUtilities.error(view, "project-file-search.no-project", null);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            ProjectFileSearchDialog dialog = INSTANCES.get(view);
            if (dialog == null) {
                dialog = new ProjectFileSearchDialog(view);
                INSTANCES.put(view, dialog);
            }
            String selection = view.getTextArea().getSelectedText();
            if (selection != null && !selection.isBlank()) {
                dialog.searchField.setText(selection);
                dialog.scheduleSearch();
            }
            dialog.setVisible(true);
            dialog.toFront();
            dialog.searchField.requestFocusInWindow();
        });
    }

    @Override
    public void ok() {
        openSelectedMatch();
    }

    @Override
    public void cancel() {
        searchCancelled.set(true);
        setVisible(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == searchModeCombo || source == extensionModeCombo) {
            scheduleSearch();
        } else if (source == openInFindResultsButton) {
            openInFindResults();
        }
    }

    private void scheduleSearch() {
        searchTimer.restart();
    }

    private void runSearch() {
        File root = ProjectRoots.workspaceRoot();
        if (root == null) {
            statusLabel.setText(jEdit.getProperty("project-file-search.no-project"));
            resultsModel.clear();
            openInFindResultsButton.setEnabled(false);
            return;
        }
        String query = searchField.getText();
        if (query == null || query.isBlank()) {
            resultsModel.clear();
            statusLabel.setText(" ");
            openInFindResultsButton.setEnabled(false);
            return;
        }
        searchCancelled.set(true);
        int generation = searchGeneration.incrementAndGet();
        searchCancelled = new AtomicBoolean();
        statusLabel.setText(jEdit.getProperty("project-file-search.searching"));
        resultsModel.clear();
        openInFindResultsButton.setEnabled(false);

        boolean regexp = searchModeCombo.getSelectedIndex() == 1;
        ProjectFileSearcher.ExtensionMode extensionMode =
            extensionModeCombo.getSelectedIndex() == 1
                ? ProjectFileSearcher.ExtensionMode.INCLUDE
                : ProjectFileSearcher.ExtensionMode.EXCLUDE;
        String extensions = extensionsField.getText();

        ThreadUtilities.runInBackground(() -> {
            List<ProjectSearchMatch> matches = ProjectFileSearcher.search(
                root, query, regexp, extensionMode, extensions);
            if (searchCancelled.get() || generation != searchGeneration.get()) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (generation != searchGeneration.get()) {
                    return;
                }
                resultsModel.clear();
                for (ProjectSearchMatch match : matches) {
                    resultsModel.addElement(match);
                }
                if (!matches.isEmpty()) {
                    resultsList.setSelectedIndex(0);
                }
                statusLabel.setText(jEdit.getProperty("project-file-search.status",
                    new Object[] { Integer.valueOf(matches.size()) }));
                openInFindResultsButton.setEnabled(!matches.isEmpty());
            });
        });
    }

    private void openSelectedMatch() {
        ProjectSearchMatch match = resultsList.getSelectedValue();
        if (match == null && resultsModel.getSize() > 0) {
            match = resultsModel.getElementAt(0);
        }
        if (match != null) {
            ProjectFindResults.openMatch(view, match);
            setVisible(false);
        }
    }

    private void moveSelection(int delta) {
        int size = resultsModel.getSize();
        if (size == 0) {
            return;
        }
        int index = resultsList.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.max(0, Math.min(size - 1, index + delta));
        }
        resultsList.setSelectedIndex(index);
        resultsList.ensureIndexIsVisible(index);
    }

    private void openInFindResults() {
        List<ProjectSearchMatch> matches = new ArrayList<>();
        for (int i = 0; i < resultsModel.getSize(); i++) {
            matches.add(resultsModel.getElementAt(i));
        }
        if (matches.isEmpty()) {
            return;
        }
        ProjectFindResults.publish(view, searchField.getText(), matches);
        setVisible(false);
    }
}
