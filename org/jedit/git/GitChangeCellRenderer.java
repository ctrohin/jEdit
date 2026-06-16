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

package org.jedit.git;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.UIManager;

final class GitChangeCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (!(value instanceof GitModels.FileChange change)) {
            return this;
        }
        setText(change.displayText());
        GitModels.ChangeKind kind = change.kind();
        if (isSelected) {
            setOpaque(true);
            return this;
        }
        Color foreground = GitColors.changeForeground(kind);
        Color background = GitColors.changeBackground(kind);
        if (foreground != null) {
            setForeground(foreground);
        } else {
            setForeground(UIManager.getColor("List.foreground"));
        }
        if (background != null) {
            setBackground(background);
            setOpaque(true);
        } else {
            setOpaque(false);
        }
        Font base = list.getFont();
        if (base != null) {
            int style = base.getStyle();
            if (change.isStaged() || kind == GitModels.ChangeKind.CONFLICT) {
                style |= Font.BOLD;
            }
            setFont(base.deriveFont(style));
        }
        return this;
    }
}
