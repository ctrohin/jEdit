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

package org.gjt.sp.jedit.themes;

import org.gjt.sp.jedit.jEdit;

public class ThemeUtils {
    private static final String[] THEME_STRING_PARAMETERS = {
        "view.style.comment1",
        "view.style.comment2",
        "view.style.comment3",
        "view.style.comment4",
        "view.style.digit",
        "view.style.foldLine.0",
        "view.style.foldLine.1",
        "view.style.foldLine.2",
        "view.style.foldLine.3",
        "view.style.function",
        "view.style.invalid",
        "view.style.keyword1",
        "view.style.keyword2",
        "view.style.keyword3",
        "view.style.keyword4",
        "view.style.label",
        "view.style.literal1",
        "view.style.literal2",
        "view.style.literal3",
        "view.style.literal4",
        "view.style.markup",
        "view.style.operator"
    };

    private static final String[] THEME_COLOR_PARAMETERS = {
        "view.status.background",
        "view.status.foreground",
        "view.bgColor",
        "view.fgColor",
        "view.lineHighlightColor",
        "view.caretColor",
        "view.selectionColor",
        "view.selectionFgColor",
        "view.multipleSelectionColor",
        "view.pageBreaksColor",
        "view.wrapGuideColor",
        "view.eolMarkerColor",
        "view.structureHighlightColor",
        "view.gutter.bgColor",
        "view.gutter.fgColor",
        "view.gutter.selectionAreaBgColor",
        "view.gutter.noFocusBorderColor",
        "view.gutter.focusBorderColor",
        "view.gutter.foldColor",
        "view.gutter.markerColor",
        "view.gutter.structureHighlightColor",
        "view.gutter.highlightColor",
        "view.gutter.currentLineColor"
    };
    public static void applyTheme(final String theme) {
        final String themePrefix = theme.equals("DEFAULT") ? "" : (theme + ".");
        for (final var param : THEME_STRING_PARAMETERS) {
            jEdit.setProperty(param, jEdit.getDefaultProperty(themePrefix + param));
        }
        for (final var param : THEME_COLOR_PARAMETERS) {
            jEdit.setColorProperty(param, jEdit.getDefaultColorProperty(themePrefix + param));
        }
    }
}
