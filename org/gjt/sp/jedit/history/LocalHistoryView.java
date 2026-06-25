/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.history;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.jEdit;

/**
 * Lists local file history snapshots for the active buffer.
 */
public final class LocalHistoryView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "local-history";

    private final View view;
    private final JLabel caption;
    private final DefaultListModel<LocalHistory.Entry> model = new DefaultListModel<>();
    private final JList<LocalHistory.Entry> list;

    public LocalHistoryView(View view) {
        super(new BorderLayout(0, 4));
        this.view = view;
        caption = new JLabel(jEdit.getProperty("local-history.empty"));
        add(caption, BorderLayout.NORTH);
        list = new JList<>(model);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        refresh();
    }

    public static LocalHistoryView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        return (LocalHistoryView) dwm.getDockableWindow(NAME);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresh();
    }

    @Override
    public void focusOnDefaultComponent() {
        list.requestFocus();
    }

    void refresh() {
        model.clear();
        Buffer buffer = view.getBuffer();
        if (buffer == null || buffer.getPath() == null) {
            caption.setText(jEdit.getProperty("local-history.empty"));
            return;
        }
        List<LocalHistory.Entry> entries = LocalHistory.entriesFor(buffer.getPath());
        for (LocalHistory.Entry entry : entries) {
            model.addElement(entry);
        }
        caption.setText(jEdit.getProperty("local-history.caption",
            new Object[] {new File(buffer.getPath()).getName(), Integer.valueOf(entries.size())}));
    }

    private void openSelected() {
        LocalHistory.Entry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        jEdit.openFile(view, entry.file.toAbsolutePath().toString());
    }
}
