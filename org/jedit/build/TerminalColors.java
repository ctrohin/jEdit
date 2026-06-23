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

import java.awt.Color;

import javax.swing.UIManager;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.syntax.SyntaxStyle;
import org.gjt.sp.util.SyntaxUtilities;

final class TerminalColors {

    private TerminalColors() {}

    static Color background() {
        return firstColor(
            "TextArea.background",
            () -> jEdit.getColorProperty("view.bgColor", Color.WHITE));
    }

    static Color foreground() {
        return firstColor(
            "TextArea.foreground",
            () -> jEdit.getColorProperty("view.fgColor", Color.BLACK));
    }

    static Color selectionBackground() {
        return firstColor(
            "TextArea.selectionBackground",
            () -> jEdit.getColorProperty("view.selectionColor", new Color(0xCC, 0xCC, 0xFF)));
    }

    static Color selectionForeground() {
        return firstColor(
            "TextArea.selectionForeground",
            () -> jEdit.getColorProperty("view.selectionFgColor", foreground()));
    }

    static Color linkColor() {
        return firstColor(
            "Component.linkColor",
            () -> styleColor("function", new Color(0x00, 0x66, 0xCC)));
    }

    static Color searchHighlightBackground() {
        return firstColor(
            "TextArea.matchHighlightBackground",
            () -> jEdit.getColorProperty("view.structureHighlightColor", new Color(0xFF, 0xFF, 0x00, 0x80)));
    }

    static Color[] ansiPalette() {
        Color bg = background();
        Color fg = foreground();
        boolean dark = isDark(bg);

        Color black = dark ? darken(bg, 0.15f) : new Color(0x1E, 0x1E, 0x1E);
        Color red = styleColor("invalid", new Color(0xCC, 0x00, 0x00));
        Color green = styleColor("literal1", new Color(0x00, 0xAA, 0x00));
        Color yellow = styleColor("digit", new Color(0xAA, 0xAA, 0x00));
        Color blue = styleColor("keyword1", new Color(0x00, 0x00, 0xCC));
        Color magenta = styleColor("label", styleColor("keyword2", new Color(0xAA, 0x00, 0xAA)));
        Color cyan = styleColor("function", styleColor("markup", new Color(0x00, 0xAA, 0xAA)));
        Color white = styleColor("comment1", dark ? brighten(fg, 0.15f) : darken(fg, 0.35f));

        Color[] palette = {
            black, red, green, yellow, blue, magenta, cyan, white
        };
        Color[] bright = new Color[16];
        System.arraycopy(palette, 0, bright, 0, 8);
        for (int i = 0; i < 8; i++) {
            bright[8 + i] = brighten(palette[i], dark ? 0.35f : 0.25f);
        }
        return bright;
    }

    private static Color styleColor(String token, Color fallback) {
        String family = jEdit.getProperty("view.font");
        int size = jEdit.getIntegerProperty("view.fontsize", 12);
        SyntaxStyle style = SyntaxUtilities.parseStyle(
            jEdit.getProperty("view.style." + token),
            family,
            size,
            true);
        Color color = style.getForegroundColor();
        return color != null ? color : fallback;
    }

    private static Color firstColor(String uiKey, ColorSupplier fallback) {
        Color color = UIManager.getColor(uiKey);
        return color != null ? color : fallback.get();
    }

    private static boolean isDark(Color color) {
        double luminance = (0.299 * color.getRed()
            + 0.587 * color.getGreen()
            + 0.114 * color.getBlue()) / 255.0;
        return luminance < 0.5;
    }

    private static Color brighten(Color color, float amount) {
        return new Color(
            clamp(color.getRed() + Math.round((255 - color.getRed()) * amount)),
            clamp(color.getGreen() + Math.round((255 - color.getGreen()) * amount)),
            clamp(color.getBlue() + Math.round((255 - color.getBlue()) * amount)));
    }

    private static Color darken(Color color, float amount) {
        return new Color(
            clamp(Math.round(color.getRed() * (1.0f - amount))),
            clamp(Math.round(color.getGreen() * (1.0f - amount))),
            clamp(Math.round(color.getBlue() * (1.0f - amount))));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @FunctionalInterface
    private interface ColorSupplier {
        Color get();
    }
}
