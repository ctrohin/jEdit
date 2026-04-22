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
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class FlutterUI {
    private final JComponent component;

    private FlutterUI(JComponent component) {
        this.component = component;
    }

    // --- NEW: CENTER WIDGET ---
    public static FlutterUI Center(Object child) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false); // Keeps background of parent
        panel.add(resolve(child));
        return new FlutterUI(panel);
    }

    // --- TEXT WIDGET ---
    public static TextBuilder Text(String data) { return new TextBuilder(data); }

    public static class TextBuilder {
        private final String data;
        private TextStyle style;
        private Runnable onTap;

        public TextBuilder(String data) { this.data = data; }
        public TextBuilder style(TextStyle style) { this.style = style; return this; }
        public TextBuilder onTap(Runnable onTap) { this.onTap = onTap; return this; }

        public FlutterUI build() {
            JLabel label = new JLabel(data);
            if (style != null) {
                if (style.font != null) {
                    label.setFont(style.font);
                }
                if (style.color != null) {
                    label.setForeground(style.color);
                }
                if (style.underline) label.setText("<html><u>" + data + "</u></html>");
            }
            if (onTap != null) {
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                label.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onTap.run(); }
                });
            }
            return new FlutterUI(label);
        }
    }

    // --- BUTTON WIDGET ---
    public static ButtonBuilder Button(String label) { return new ButtonBuilder(label); }

    public static class ButtonBuilder {
        private final String label;
        private Icon icon;
        private Runnable onPressed;

        public ButtonBuilder(String label) { this.label = label; }
        public ButtonBuilder icon(Icon icon) { this.icon = icon; return this; }
        public ButtonBuilder onPressed(Runnable onPressed) { this.onPressed = onPressed; return this; }

        public FlutterUI build() {
            JButton button = (icon != null) ? new JButton(label, icon) : new JButton(label);
            if (onPressed != null) button.addActionListener(e -> onPressed.run());
            return new FlutterUI(button);
        }
    }

    // --- LAYOUTS ---
    public static FlutterUI Column(List<FlutterUI> children) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        children.forEach(child -> panel.add(child.getComponent()));
        return new FlutterUI(panel);
    }

    public static FlutterUI Padding(int top, int left, int bottom, int right, Object child) {
        JComponent c = resolve(child);
        c.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        return new FlutterUI(c);
    }

    // Helper to turn Builders or FlutterUI into a real JComponent
    private static JComponent resolve(Object obj) {
        if (obj instanceof FlutterUI) return ((FlutterUI) obj).getComponent();
        if (obj instanceof TextBuilder) return ((TextBuilder) obj).build().getComponent();
        if (obj instanceof ButtonBuilder) return ((ButtonBuilder) obj).build().getComponent();
        if (obj instanceof BoxBuilder) return ((BoxBuilder) obj).build().getComponent();
        throw new IllegalArgumentException("Unknown UI type");
    }

    public static List<FlutterUI> children(Object... elements) {
        List<FlutterUI> list = new ArrayList<>();
        for (Object e : elements) {
            if (e instanceof FlutterUI) list.add((FlutterUI) e);
            else if (e instanceof TextBuilder) list.add(((TextBuilder) e).build());
            else if (e instanceof ButtonBuilder) list.add(((ButtonBuilder) e).build());
        }
        return list;
    }

    public JComponent getComponent() { return component; }

    public static class TextStyle {
        Font font = new JLabel().getFont();
        Color color = new JLabel().getForeground();
        boolean underline = false;
        public TextStyle font(Font f) { this.font = f; return this; }
        public TextStyle size(float s) { font = font.deriveFont(s); return this; }
        public TextStyle bold() { font = font.deriveFont(Font.BOLD); return this; }
        public TextStyle italic() { font = font.deriveFont(Font.ITALIC); return this; }
        public TextStyle color(Color c) { color = c; return this; }
        public TextStyle underline() { underline = true; return this; }
    }

    public static BoxBuilder Box() { return new BoxBuilder(); }

    public static class BoxBuilder {
        private Integer width, height;
        private Color backgroundColor;
        private Object child;
        private Border border;

        public BoxBuilder width(int w) { this.width = w; return this; }
        public BoxBuilder height(int h) { this.height = h; return this; }
        public BoxBuilder color(Color c) { this.backgroundColor = c; return this; }
        public BoxBuilder child(Object child) { this.child = child; return this; }

        public BoxBuilder border(Color color, int thickness) {
            this.border = BorderFactory.createLineBorder(color, thickness);
            return this;
        }

        public FlutterUI build() {
            JPanel panel = new JPanel(new BorderLayout());
            if (backgroundColor != null) {
                panel.setBackground(backgroundColor);
                panel.setOpaque(true);
            } else {
                panel.setOpaque(false);
            }

            if (border != null) panel.setBorder(border);
            if (child != null) panel.add(resolve(child));

            // Set dimensions if provided
            if (width != null || height != null) {
                Dimension d = new Dimension(
                    width != null ? width : panel.getPreferredSize().width,
                    height != null ? height : panel.getPreferredSize().height
                );
                panel.setPreferredSize(d);
                panel.setMinimumSize(d);
                panel.setMaximumSize(d);
            }

            return new FlutterUI(panel);
        }
    }
}