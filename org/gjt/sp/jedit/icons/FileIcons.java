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
import jiconfont.IconFont;

import java.io.InputStream;
import java.net.URL;

public enum FileIcons implements IconCode {
    // WEB & LANGUAGES
    JS('\uE781'),
    JSX('\uE7BA'),
    TS('\uE749'),
    TSX('\uE7BA'),
    HTML('\uE736'),
    HTM('\uE736'),
    CSS('\uE749'),
    SASS('\uE603'),
    SCSS('\uE603'),
    LESS('\uE758'),
    STYL('\uE600'),
    PHP('\uE73D'),
    JAVA('\uE738'),
    JAR('\uE738'),
    CLASS('\uE738'),
    KOTLIN('\uE734'),
    KT('\uE734'),
    KTS('\uE734'),
    SWIFT('\uE755'),
    OBJECTIVEC('\uE779'),
    PY('\uE73C'),
    PYW('\uE73C'),
    RB('\uE739'),
    RUBY('\uE739'),
    C('\uE739'),
    H('\uE606'),
    CPP('\uE731'),
    HPP('\uE606'),
    CC('\uE731'),
    HH('\uE606'),
    CS('\uE732'),
    CSHARP('\uE732'),
    GO('\uE626'),
    RS('\uE7A8'),
    RUST('\uE7A8'),
    LUA('\uE620'),
    CLJ('\uE768'),
    CLJS('\uE768'),
    EDN('\uE768'),
    EX('\uE62D'),
    EXS('\uE62D'),
    BEAM('\uE7B1'),
    HS('\uE777'),
    LHS('\uE777'),
    SCALA('\uE706'),
    ML('\uE621'),
    MLI('\uE621'),
    F('\uE7AF'),
    F90('\uE7AF'),
    LISP('\uE701'),
    LSP('\uE701'),

    // DATA & CONFIG
    JSON('\uE60B'),
    XML('\uE619'),
    YAML('\uE601'),
    YML('\uE601'),
    SQL('\uE706'),
    DB('\uE706'),
    CSV('\uE60B'),
    TOML('\uE615'),
    INI('\uE615'),
    CONF('\uE615'),
    CFG('\uE615'),
    MD('\uE609'),
    MARKDOWN('\uE609'),
    TXT('\uF15C'),
    LOG('\uE615'),
    PDF('\uF1C1'),

    // SHELL & OS
    SH('\uE795'),
    BASH('\uE795'),
    ZSH('\uE795'),
    BAT('\uE795'),
    CMD('\uE795'),
    PS1('\uE70F'),
    PSM1('\uE70F'),
    PSD1('\uE70F'),
    EXE('\uE70F'),
    DLL('\uE70F'),
    BIN('\uE70F'),
    DMG('\uE70F'),
    ISO('\uF1C6'),
    APP('\uE70F'),

    // IMAGES & ASSETS
    PNG('\uE602'),
    JPG('\uE602'),
    JPEG('\uE602'),
    GIF('\uE602'),
    SVG('\uE728'),
    ICO('\uE602'),
    WEBP('\uE602'),
    TIFF('\uE602'),
    PSD('\uE7B8'),
    AI('\uE7B4'),
    FIG('\uE73A'),
    SKETCH('\uE73A'),

    // ARCHIVES
    ZIP('\uF1C6'),
    RAR('\uF1C6'),
    TAR('\uF1C6'),
    GZ('\uF1C6'),
    _7Z('\uF1C6'),
    BZ2('\uF1C6'),
    XZ('\uF1C6'),

    // TOOLING
    DOCKERFILE('\uE7B0'),
    DOCKER('\uE7B0'),
    GITIGNORE('\uE702'),
    GITATTRIBUTES('\uE702'),
    VAGRANT('\uE618'),

    // SYSTEM
    FOLDER('\uF115'),
    FOLDER_OPEN('\uF114'),
    FILE_DEFAULT('\uF15B');

    private final char unicode;

    FileIcons(final char unicode) { this.unicode = unicode; }

    @Override
    public char getUnicode() {
        return unicode;
    }

    @Override
    public String getFontFamily() {
        return "File Icons";
    }

    public static IconFont getIconFont() {
        return new IconFont() {
            public String getFontFamily() {
                return "File Icons";
            }

            public InputStream getFontInputStream() {
                try {
//                var is = MatIcons.class.getResourceAsStream("jeditresource:/org/gjt/sp/jedit/icons/MaterialIcons-Regular.ttf");
                    final var url = new URL("jeditresource:/org/gjt/sp/jedit/icons/file-icons.ttf");
                    final var is = url.openStream();
                    return is;
                }
                catch (Exception _) {
                    return null;
                }
            }
        };
    }
}