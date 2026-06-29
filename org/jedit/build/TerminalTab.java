/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;

final class TerminalTab {

    final String tabKey;
    final JPanel panel;
    private final PtyTerminalSession session;
    private String title;
    private String customName = "";
    private Runnable onTitleChanged;

    TerminalTab(View view, String tabKey, TerminalSessionConfig config,
                Runnable onTitleChanged) throws IOException {
        this.tabKey = tabKey;
        this.onTitleChanged = onTitleChanged;
        title = buildTitle(config);
        session = new PtyTerminalSession(view, config, ignored -> {
            title = TerminalShell.shellName() + " (" + jEdit.getProperty("terminal.ended") + ")";
            notifyTitleChanged();
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton kill = new RolloverButton(
            IconManager.loadIcon("MatIcons.STOP:22:RED"),
            jEdit.getProperty("terminal.kill"));
        kill.addActionListener(e -> session.close());
        toolbar.add(kill);

        panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(session.widget(), BorderLayout.CENTER);
        session.start();
    }

    void close() {
        session.close();
    }

    boolean isRunning() {
        return session.isRunning();
    }

    String getTitle() {
        return title;
    }

    void setCustomName(String name) {
        customName = name != null ? name.trim() : "";
        title = customName.isEmpty() ? session.baseTitle() : customName;
        notifyTitleChanged();
    }

    void setTitleSuffix(String suffix) {
        String base = customName.isEmpty() ? session.baseTitle() : customName;
        title = base + suffix;
        notifyTitleChanged();
    }

    void requestFocus() {
        session.widget().requestFocus();
    }

    private static String buildTitle(TerminalSessionConfig config) {
        if (config.sessionName != null && !config.sessionName.isBlank()) {
            return config.sessionName.trim();
        }
        return TerminalShell.shellName();
    }

    private void notifyTitleChanged() {
        if (onTitleChanged != null) {
            onTitleChanged.run();
        }
    }
}
