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

package org.jedit.build;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.gjt.sp.jedit.gui.RolloverButton;
import org.gjt.sp.jedit.icons.IconManager;
import org.gjt.sp.jedit.jEdit;

final class TerminalTab {

    final String tabKey;
    final JPanel panel;
    private final PtyTerminalSession session;
    private String title;
    private Runnable onTitleChanged;

    TerminalTab(String tabKey, File workingDir, Runnable onTitleChanged) throws IOException {
        this.tabKey = tabKey;
        this.onTitleChanged = onTitleChanged;
        title = TerminalShell.shellName();
        session = new PtyTerminalSession(workingDir, ignored -> {
            title = TerminalShell.shellName() + " (" + jEdit.getProperty("terminal.ended") + ")";
            notifyTitleChanged();
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton kill = new RolloverButton(
            IconManager.loadIcon("MatIcons.STOP:22"),
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

    void setTitleSuffix(String suffix) {
        title = session.baseTitle() + suffix;
        notifyTitleChanged();
    }

    void requestFocus() {
        session.widget().requestFocus();
    }

    private void notifyTitleChanged() {
        if (onTitleChanged != null) {
            onTitleChanged.run();
        }
    }
}
