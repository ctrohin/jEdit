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

package org.jedit.build;

import java.awt.Color;
import java.awt.Font;

import com.jediterm.terminal.HyperlinkStyle;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AwtTransformers;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;

import org.gjt.sp.jedit.jEdit;

final class LookAndFeelTerminalSettingsProvider extends DefaultSettingsProvider {

  @Override
  public ColorPalette getTerminalColorPalette() {
    return new LookAndFeelColorPalette();
  }

  @Override
  public TextStyle getDefaultStyle() {
    return new TextStyle(
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.foreground()),
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.background()));
  }

  @Override
  public TextStyle getSelectionColor() {
    return new TextStyle(
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.selectionForeground()),
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.selectionBackground()));
  }

  @Override
  public TextStyle getFoundPatternColor() {
    Color foreground = TerminalColors.foreground();
    Color background = TerminalColors.searchHighlightBackground();
    return new TextStyle(
      AwtTransformers.fromAwtToTerminalColor(foreground),
      AwtTransformers.fromAwtToTerminalColor(background));
  }

  @Override
  public TextStyle getHyperlinkColor() {
    return new TextStyle(
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.linkColor()),
      AwtTransformers.fromAwtToTerminalColor(TerminalColors.background()));
  }

  @Override
  public HyperlinkStyle.HighlightMode getHyperlinkHighlightingMode() {
    return HyperlinkStyle.HighlightMode.HOVER;
  }

  @Override
  public Font getTerminalFont() {
    Font editorFont = jEdit.getFontProperty(
      "view.font",
      new Font(Font.MONOSPACED, Font.PLAIN, 12));
    if (editorFont != null && editorFont.getFamily() != null) {
      return editorFont;
    }
    return super.getTerminalFont();
  }

  @Override
  public float getTerminalFontSize() {
    int size = jEdit.getIntegerProperty("view.fontsize", 0);
    if (size > 0) {
      return size;
    }
    return super.getTerminalFontSize();
  }
}
