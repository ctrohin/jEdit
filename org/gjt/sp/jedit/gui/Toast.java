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

package org.gjt.sp.jedit.gui;

import javax.swing.*;
import java.awt.*;

public class Toast {
    public static void showToast(String message, JComponent parent) {
        showToast(message, parent, 2000);
    }
    public static void showToast(String message, JComponent parent, int delay) {
        JWindow window = new JWindow();
        window.setLayout(new GridBagLayout());
        window.setBackground(new Color(0, 0, 0, 0)); // Transparent background

        JPanel panel = new JPanel();
        panel.setBackground(new Color(50, 50, 50, 200)); // Dark semi-transparent
        panel.add(new JLabel("<html><font color='white'>" + message + "</font></html>"));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        window.add(panel);
        window.pack();

        // Position at bottom center of your main frame
        window.setLocationRelativeTo(parent);

        window.setVisible(true);

        // Auto-hide after 2 seconds
        new Timer(delay, e -> window.dispose()).start();
    }
}
