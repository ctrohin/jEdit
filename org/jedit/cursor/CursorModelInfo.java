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

package org.jedit.cursor;

import org.gjt.sp.jedit.jEdit;

final class CursorModelInfo {

    static CursorModelInfo accountDefault() {
        return new CursorModelInfo(null,
            jEdit.getProperty("cursor.model.default"),
            jEdit.getProperty("cursor.model.default.description"));
    }

    private final String id;
    private final String displayName;
    private final String description;

    CursorModelInfo(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    boolean isAccountDefault() {
        return id == null || id.isBlank();
    }

    static boolean isDuplicateAuto(CursorModelInfo model) {
        if (model == null || model.isAccountDefault()) {
            return true;
        }
        String autoLabel = jEdit.getProperty("cursor.model.default");
        if (model.displayName() != null
            && model.displayName().equalsIgnoreCase(autoLabel)) {
            return true;
        }
        String modelId = model.id();
        return modelId != null
            && (modelId.equalsIgnoreCase("default") || modelId.equalsIgnoreCase("auto"));
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : id;
    }
}
