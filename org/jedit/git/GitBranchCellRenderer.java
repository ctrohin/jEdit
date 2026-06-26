/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.git;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.UIManager;

final class GitBranchCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!(value instanceof GitModels.Branch branch)) {
            return this;
        }
        setText(branch.toString());
        if (isSelected) {
            return this;
        }
        if (branch.current) {
            setForeground(GitColors.currentBranchForeground());
            Font base = list.getFont();
            if (base != null) {
                setFont(base.deriveFont(Font.BOLD));
            }
            Color tint = GitColors.changeBackground(GitModels.ChangeKind.ADDED);
            if (tint != null) {
                setBackground(tint);
                setOpaque(true);
            }
        } else {
            setForeground(UIManager.getColor("List.foreground"));
            setOpaque(false);
        }
        return this;
    }
}
