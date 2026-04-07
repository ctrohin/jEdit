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

package org.gjt.sp.jedit.gui.components;

import org.gjt.sp.jedit.gui.borders.RoundedBorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class CustomTab extends JPanel {
    private final JButton close;
    private final JLabel label;
    public CustomTab(String str, CustomTabInterfaces.OnSelectionChanged selectAction) {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        label = new JLabel(str);
        add(label);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
            selectAction.setSelected(CustomTab.this);
            }
        });
        close = new JButton("x");
        close.setMargin(new Insets(1,4,1,4));
        add(close);
    }

    public void setOnClose(CustomTabInterfaces.OnClose closeAction) {
        close.addActionListener(e -> closeAction.close());
    }

    public void setSelected(final boolean selected) {
        setBackground(getTabBackground(selected));
        close.setBackground(getTabBackground(selected));
        setBorder(getBorder(selected));
        label.setFont(label.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
        repaint();
    }

    private Color getJListSelectionBackground() {
        return UIManager.getColor("ComboBox.selectionBackground");
    }

    private Color getLabelBackground() {
        return UIManager.getColor("Label.background");
    }

    private Color getTextAreaBackground() {
        return UIManager.getColor("TextArea.background");
    }

    private Color getTextAreaDisabledBackground() {
        return getLabelBackground().darker();
    }

    private Color getTabBackground(final boolean selected) {
        return selected ? getTextAreaBackground() : getLabelBackground();
    }

    private Border getBorder(final boolean selected) {
        if (selected) {
            return new LineBorder(getJListSelectionBackground(), 2, false);
        }
        return new CompoundBorder(
            new LineBorder(getTextAreaDisabledBackground(), 1, false),
            new LineBorder(getTabBackground(false), 1, false));
    }
}