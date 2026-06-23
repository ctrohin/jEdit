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

import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AwtTransformers;

final class LookAndFeelColorPalette extends ColorPalette {

    private final com.jediterm.core.Color[] colors;

    LookAndFeelColorPalette() {
        Color[] awtColors = TerminalColors.ansiPalette();
        colors = new com.jediterm.core.Color[awtColors.length];
        for (int i = 0; i < awtColors.length; i++) {
            colors[i] = AwtTransformers.fromAwtColor(awtColors[i]);
        }
    }

    @Override
    protected com.jediterm.core.Color getForegroundByColorIndex(int index) {
        return colors[index];
    }

    @Override
    protected com.jediterm.core.Color getBackgroundByColorIndex(int index) {
        return colors[index];
    }
}
