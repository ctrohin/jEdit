/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.gui.RolloverToggleButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;

final class BuildOutputTab {

    final String taskKey;
    final LinkAwareTextArea output;
    final JPanel panel;
    final JScrollPane scrollPane;
    final BuildProcessRunner runner = new BuildProcessRunner();
    private final JLabel statusLabel;
    private final RolloverToggleButton pinButton;
    private boolean scrollPinned;
    private String title;
    private String status = "";

    BuildOutputTab(View view, String taskKey, String title, int maxLines,
                   Runnable onSettings) {
        this.taskKey = taskKey;
        this.title = title;
        output = new LinkAwareTextArea(view);
        output.setMaxLines(maxLines);
        scrollPane = new JScrollPane(output);
        output.configureScrolling(() -> scrollPinned, this::scrollToEnd);

        JPanel toolbar = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton clear = new RolloverButton(IconManager.loadIcon("MatIcons.CLEAR:22"), jEdit.getProperty("build-output.clear"));
        clear.addActionListener(e -> clearOutput());
        JButton stop = new RolloverButton(IconManager.loadIcon("MatIcons.STOP:22:RED"), jEdit.getProperty("build-output.stop"));
        stop.addActionListener(e -> stopRun());
        statusLabel = new JLabel(" ");
        left.add(clear);
        left.add(stop);
        left.add(statusLabel);
        toolbar.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        pinButton = new RolloverToggleButton(IconManager.loadIcon("MatIcons.KEEP_DOWN:22"));
        updatePinTooltip();
        pinButton.addActionListener(e -> {
            scrollPinned = pinButton.isSelected();
            updatePinTooltip();
            if (!scrollPinned) {
                scrollToEnd();
            }
        });
        JButton settings = new RolloverButton(IconManager.loadIcon("MatIcons.SETTINGS:22"), jEdit.getProperty("build-output.settings"));
        settings.addActionListener(e -> onSettings.run());
        right.add(pinButton);
        right.add(settings);
        toolbar.add(right, BorderLayout.EAST);

        panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title != null ? title : "";
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status != null ? status : "";
        statusLabel.setText(this.status.isEmpty() ? " " : this.status);
    }

    boolean isRunning() {
        return runner.isRunning();
    }

    void clearOutput() {
        output.clearOutput();
        if (!isRunning()) {
            setStatus("");
        }
    }

    void stopRun() {
        runner.stop();
        output.clearProcessInput();
        setStatus(jEdit.getProperty("build-output.stopped"));
    }

    void enableProcessInput() {
        output.setProcessInput(runner::sendInput, this::stopRun);
    }

    void disableProcessInput() {
        output.clearProcessInput();
    }

    private void scrollToEnd() {
        SwingUtilities.invokeLater(() -> {
            try {
                int length = output.getDocument().getLength();
                output.setCaretPosition(length);
                Rectangle rect = output.modelToView2D(length).getBounds();
                if (rect != null) {
                    output.scrollRectToVisible(rect);
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void updatePinTooltip() {
        pinButton.setToolTipText(jEdit.getProperty(scrollPinned
            ? "build-output.scroll-pin"
            : "build-output.scroll-unpin"));
    }
}
