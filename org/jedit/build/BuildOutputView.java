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
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.gui.DockableWindowManager;
import org.gjt.sp.jedit.jEdit;

/**
 * Build output console for Ant/Maven tasks.
 */
public final class BuildOutputView extends JPanel implements DefaultFocusComponent {

    public static final String NAME = "build-output";

    private final View view;
    private final LinkAwareTextArea output;
    private final JLabel statusLabel;
    private final BuildProcessRunner runner = new BuildProcessRunner();
    private final ProjectFolderListener folderListener =
        new ProjectFolderListener(this::updateProjectRoot);

    public BuildOutputView(View view) {
        super(new BorderLayout());
        this.view = view;

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton clear = new JButton(jEdit.getProperty("build-output.clear"));
        clear.addActionListener(e -> clearOutput());
        JButton stop = new JButton(jEdit.getProperty("build-output.stop"));
        stop.addActionListener(e -> stopBuild());
        toolbar.add(clear);
        toolbar.add(stop);
        statusLabel = new JLabel(" ");
        toolbar.add(statusLabel);
        add(toolbar, BorderLayout.NORTH);

        output = new LinkAwareTextArea(view);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    public static BuildOutputView show(View view) {
        DockableWindowManager dwm = view.getDockableWindowManager();
        dwm.addDockableWindow(NAME);
        BuildOutputView panel = (BuildOutputView) dwm.getDockableWindow(NAME);
        if (panel != null) {
            panel.prepareForBuild();
        }
        return panel;
    }

    void prepareForBuild() {
        updateProjectRoot();
        statusLabel.setText(jEdit.getProperty("build-output.running"));
    }

    private void updateProjectRoot() {
        output.setProjectRoot(ProjectRoots.workspaceRoot());
    }

    void clearOutput() {
        output.clearOutput();
        statusLabel.setText(" ");
    }

    void stopBuild() {
        runner.stop();
        statusLabel.setText(jEdit.getProperty("build-output.stopped"));
    }

    void appendLine(String line) {
        output.appendLine(line);
    }

    void runBuild(File workingDir, List<String> command) {
        runBuild(workingDir, command, null);
    }

    void runBuild(File workingDir, List<String> command, Map<String, String> environment) {
        prepareForBuild();
        output.clearOutput();
        output.appendLine("$ " + String.join(" ", command));
        runner.run(workingDir, command, environment, this::appendLine, () ->
            statusLabel.setText(jEdit.getProperty("build-output.finished")));
    }

    boolean isRunning() {
        return runner.isRunning();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EditBus.addToBus(folderListener);
        updateProjectRoot();
    }

    @Override
    public void removeNotify() {
        EditBus.removeFromBus(folderListener);
        super.removeNotify();
    }

    @Override
    public void focusOnDefaultComponent() {
        output.requestFocus();
    }
}
