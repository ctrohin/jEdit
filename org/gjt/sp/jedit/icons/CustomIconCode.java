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

package org.gjt.sp.jedit.icons;

import jiconfont.IconCode;

public enum CustomIconCode implements IconCode {
    HARD_DISK('\ue1db'),
    NOTE_ADD('\ue89c'),
    FILE_OPEN('\ue24d'),
    PLUGINS('\ue87b'),
    RECORD('\ue3fa'),
    STOP('\ue047'),
    FIND_MULTIPLE('\ue23e'),
    JUMP('\ue15a'),
    CHEVRON_DOWN('\ue313'),
    CHEVRON_LEFT('\ue314'),
    CHEVRON_RIGHT('\ue315'),
    CHEVRON_UP('\ue316'),
    FIRST('\ue045'),
    LAST('\ue044'),
    WINDOW('\ue8d8'),
    FILE_MANAGER('\ueb47'),
    SPLIT_VERTICAL('\ue22c'),
    SPLIT_HORIZONTAL('\ue233'),
    SPLIT_MERGE('\ue22f'),
    PROPERTIES('\ue241'),
    WRENCH('\ue869'),
    RESIZE_HORIZONTAL('\ue86f'),
    RESIZE_VERTICAL('\ue5d7'),
    ZOOM_IN('\ue8ff'),
    ZOOM_OUT('\ue900'),
    TARGET('\ue1b3'),
    CLOCK('\ue8ae');

    private final char character;

    CustomIconCode(char character) {
        this.character = character;
    }

    public char getUnicode() {
        return this.character;
    }

    public String getFontFamily() {
        return "Material Icons";
    }
}
