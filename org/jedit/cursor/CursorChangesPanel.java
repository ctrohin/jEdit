/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;

final class CursorChangesPanel extends JPanel {

    private final View view;
    private final CursorConversation conversation;
    private final Runnable onChanged;

    private final JButton expandButton;
    private final JLabel summaryLabel;
    private final JButton undoAllButton;
    private final JButton reviewButton;
    private final DefaultListModel<CursorModifiedFile> fileModel;
    private final JList<CursorModifiedFile> fileList;
    private final JScrollPane fileScroll;
    private boolean expanded = true;

    CursorChangesPanel(View view, CursorConversation conversation, Runnable onChanged) {
        super(new BorderLayout(0, 4));
        this.view = view;
        this.conversation = conversation;
        this.onChanged = onChanged;

        expandButton = new JButton("▼");
        expandButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        expandButton.addActionListener(e -> toggleExpanded());

        summaryLabel = new JLabel();
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD));

        undoAllButton = new JButton(jEdit.getProperty("cursor.changes.undo-all"));
        undoAllButton.addActionListener(e -> undoAll());

        reviewButton = new JButton(jEdit.getProperty("cursor.changes.review"));
        reviewButton.addActionListener(e -> reviewSelected());

        fileModel = new DefaultListModel<>();
        fileList = new JList<>(fileModel);
        fileList.setVisibleRowCount(4);
        fileList.setCellRenderer(new FileCellRenderer());
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    reviewSelected();
                }
            }
        });

        fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(100, 88));

        JPanel header = new JPanel(new BorderLayout(4, 0));
        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        headerLeft.add(expandButton);
        headerLeft.add(summaryLabel);
        header.add(headerLeft, BorderLayout.WEST);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        headerRight.add(undoAllButton);
        headerRight.add(reviewButton);
        header.add(headerRight, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(fileScroll, BorderLayout.CENTER);
        refresh();
    }

    void refresh() {
        fileModel.clear();
        for (CursorModifiedFile file : conversation.modifiedFiles) {
            fileModel.addElement(file);
        }
        int count = conversation.modifiedFiles.size();
        summaryLabel.setText(jEdit.getProperty("cursor.changes.summary", new String[] {
            Integer.toString(count)
        }));
        boolean hasFiles = count > 0;
        setVisible(hasFiles);
        undoAllButton.setEnabled(hasFiles);
        reviewButton.setEnabled(hasFiles);
        fileList.setEnabled(hasFiles);
        if (hasFiles && fileList.getSelectedIndex() < 0) {
            fileList.setSelectedIndex(0);
        }
        revalidate();
        repaint();
    }

    private void toggleExpanded() {
        expanded = !expanded;
        expandButton.setText(expanded ? "▼" : "▶");
        fileScroll.setVisible(expanded);
        revalidate();
    }

    private void undoAll() {
        if (conversation.modifiedFiles.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(view,
            jEdit.getProperty("cursor.changes.undo-all.confirm"),
            jEdit.getProperty("cursor.changes.undo-all.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        File workspace = CursorWorkspaceContext.workspaceRoot();
        CursorWorkspaceChanges.undoAll(view, conversation, workspace);
        refresh();
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private void reviewSelected() {
        CursorModifiedFile selected = fileList.getSelectedValue();
        if (selected == null && !conversation.modifiedFiles.isEmpty()) {
            selected = conversation.modifiedFiles.iterator().next();
        }
        if (selected == null) {
            return;
        }
        File workspace = CursorWorkspaceContext.workspaceRoot();
        CursorWorkspaceChanges.reviewFile(view, conversation, workspace, selected, () -> {
            CursorWorkspaceChanges.syncRunChanges(conversation, workspace);
            SwingUtilities.invokeLater(this::refresh);
            if (onChanged != null) {
                onChanged.run();
            }
        });
    }

    private static final class FileCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
            if (value instanceof CursorModifiedFile file && component instanceof JLabel label) {
                label.setText(file.path);
                label.setToolTipText(file.local
                    ? jEdit.getProperty("cursor.changes.file.local")
                    : jEdit.getProperty("cursor.changes.file.remote"));
            }
            return component;
        }
    }
}
