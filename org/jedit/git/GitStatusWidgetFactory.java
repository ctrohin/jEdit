/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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

package org.jedit.git;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.statusbar.StatusBarEventType;
import org.gjt.sp.jedit.gui.statusbar.StatusWidgetFactory;
import org.gjt.sp.jedit.gui.statusbar.ToolTipLabel;
import org.gjt.sp.jedit.gui.statusbar.Widget;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.ProjectFolderClosed;
import org.gjt.sp.jedit.msg.ProjectFolderOpened;
import org.gjt.sp.util.ThreadUtilities;

import javax.swing.SwingUtilities;

public final class GitStatusWidgetFactory implements StatusWidgetFactory {

    @Override
    public Widget getWidget(View view) {
        return new GitHeadWidget(view);
    }

    private static final class GitHeadWidget implements Widget, EBComponent {

        private final View view;
        private final JLabel label;
        private final GitRunner runner = new GitRunner();
        private String lastText = "";
        private GitHeadState lastHead = GitHeadState.none();
        private int refreshGeneration;

        GitHeadWidget(View view) {
            this.view = view;
            label = new ToolTipLabel() {
                @Override
                public void addNotify() {
                    super.addNotify();
                    GitHeadWidget.this.refreshHead();
                    EditBus.addToBus(GitHeadWidget.this);
                }

                @Override
                public void removeNotify() {
                    EditBus.removeFromBus(GitHeadWidget.this);
                    super.removeNotify();
                }
            };
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 1) {
                        return;
                    }
                    File repoRoot = GitRepository.resolveRoot(view.getBuffer());
                    if (repoRoot == null) {
                        return;
                    }
                    GitRefMenu.show(label, view, repoRoot, runner, lastHead,
                        List.of(), GitHeadWidget.this::refreshHead);
                }
            });
        }

        @Override
        public JComponent getComponent() {
            return label;
        }

        @Override
        public void update() {
            refreshHead();
        }

        private void refreshHead() {
            File repoRoot = GitRepository.resolveRoot(view.getBuffer());
            if (repoRoot == null) {
                setTextIfChanged("");
                lastHead = GitHeadState.none();
                label.setToolTipText(jEdit.getProperty("git.head.tooltip.none"));
                return;
            }
            int generation = ++refreshGeneration;
            ThreadUtilities.runInBackground(() -> {
                GitRunner bgRunner = new GitRunner();
                GitRunner.Result version = bgRunner.run(repoRoot, "--version");
                GitHeadState head = version.success()
                    ? GitHeadState.query(repoRoot, bgRunner)
                    : GitHeadState.none();
                SwingUtilities.invokeLater(() -> {
                    if (generation != refreshGeneration) {
                        return;
                    }
                    if (!version.success()) {
                        setTextIfChanged("");
                        lastHead = GitHeadState.none();
                        label.setToolTipText(jEdit.getProperty("git.git-missing"));
                        return;
                    }
                    lastHead = head;
                    setTextIfChanged(head.statusText());
                    label.setToolTipText(head.tooltip());
                });
            });
        }

        private void setTextIfChanged(String text) {
            if (!text.equals(lastText)) {
                label.setText(text);
                lastText = text;
            }
        }

        @Override
        public boolean test(StatusBarEventType statusBarEventType) {
            return statusBarEventType == StatusBarEventType.Buffer;
        }

        @Override
        public void handleMessage(EBMessage message) {
            if (message instanceof GitHeadChanged
                || message instanceof ProjectFolderOpened
                || message instanceof ProjectFolderClosed) {
                refreshHead();
                return;
            }
            if (message instanceof BufferUpdate bufferUpdate
                && bufferUpdate.getWhat() == BufferUpdate.CREATED) {
                Buffer buffer = bufferUpdate.getBuffer();
                if (buffer != null && buffer.equals(view.getBuffer())) {
                    refreshHead();
                }
            }
        }
    }
}
