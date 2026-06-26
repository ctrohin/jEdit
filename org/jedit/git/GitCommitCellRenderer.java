/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

final class GitCommitCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof GitModels.Commit commit) {
            setText(commit.displayHtml());
        }
        return this;
    }
}
