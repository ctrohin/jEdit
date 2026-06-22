/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.ThreadUtilities;
import static org.gjt.sp.jedit.gui.adapters.MouseAdapters.mouseClicked;
/**
 * Quick-open style dialog for invoking jEdit actions.
 */
public class CommandPaletteDialog extends EnhancedDialog {

    private static final int FILTER_DEBOUNCE_MS = 120;

    private static final Map<View, CommandPaletteDialog> INSTANCES = new HashMap<>();

    private final View view;
    private final JTextField searchField;
    private final JList<CommandPaletteEntry> commandList;
    private final JLabel statusLabel;
    private final AtomicInteger filterGeneration = new AtomicInteger();
    private List<CommandPaletteEntry> allEntries = List.of();
    private Timer filterTimer;
    private volatile boolean loading;

    private CommandPaletteDialog(View view) {
        super(view, jEdit.getProperty("command-palette.title"), true);
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
                        invokeSelectedCommand();
                        e.consume();
                    }
                    default -> {
                    }
                }
            }
        });
        content.add(searchField, BorderLayout.NORTH);

        commandList = new JList<>();
        commandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel();
            if (value != null) {
                label.setText(value.formatListHtml());
                label.setToolTipText(value.getActionName());
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
        commandList.addMouseListener(mouseClicked(e -> {
            if (e.getClickCount() == 2) {
                invokeSelectedCommand();
            }
        }));
        commandList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    invokeSelectedCommand();
                    e.consume();
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(commandList);
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
        SwingUtilities.invokeLater(() -> {
            CommandPaletteDialog dialog = INSTANCES.get(view);
            if (dialog == null) {
                dialog = new CommandPaletteDialog(view);
                INSTANCES.put(view, dialog);
            }
            dialog.searchField.setText("");
            dialog.loadCommands();
            dialog.setVisible(true);
            dialog.toFront();
            dialog.searchField.requestFocusInWindow();
        });
    }

    @Override
    public void ok() {
        invokeSelectedCommand();
    }

    @Override
    public void cancel() {
        setVisible(false);
    }

    private void loadCommands() {
        loading = true;
        statusLabel.setText(jEdit.getProperty("command-palette.loading"));
        commandList.setListData(new CommandPaletteEntry[0]);
        ThreadUtilities.runInBackground(() -> {
            List<CommandPaletteEntry> entries = CommandPaletteIndex.build();
            SwingUtilities.invokeLater(() -> {
                allEntries = entries;
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
        List<CommandPaletteEntry> entries = allEntries;
        int generation = filterGeneration.incrementAndGet();
        ThreadUtilities.runInBackground(() -> {
            List<CommandPaletteEntry> matches = CommandPaletteIndex.filter(entries, query);
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

    private void applyMatches(List<CommandPaletteEntry> matches) {
        commandList.setListData(matches.toArray(new CommandPaletteEntry[0]));
        if (!matches.isEmpty()) {
            commandList.setSelectedIndex(0);
        }
        statusLabel.setText(jEdit.getProperty("command-palette.status",
            new Object[] { Integer.valueOf(matches.size()) }));
    }

    private void moveSelection(int delta) {
        int size = commandList.getModel().getSize();
        if (size == 0) {
            return;
        }
        int index = commandList.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.max(0, Math.min(size - 1, index + delta));
        }
        commandList.setSelectedIndex(index);
        commandList.ensureIndexIsVisible(index);
    }

    private void invokeSelectedCommand() {
        CommandPaletteEntry entry = commandList.getSelectedValue();
        if (entry == null && commandList.getModel().getSize() > 0) {
            entry = commandList.getModel().getElementAt(0);
        }
        if (entry == null) {
            return;
        }
        setVisible(false);
        view.getInputHandler().invokeAction(entry.getActionName());
    }
}
