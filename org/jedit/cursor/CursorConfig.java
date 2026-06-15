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

package org.jedit.cursor;

import org.gjt.sp.jedit.jEdit;

final class CursorConfig {

    static final String API_KEY_PROPERTY = "cursor.api-key";
    static final String MODE_PROPERTY = "cursor.selected-mode";
    static final String MODEL_PROPERTY = "cursor.selected-model-id";

    private CursorConfig() {}

    static String apiKey() {
        String key = jEdit.getProperty(API_KEY_PROPERTY);
        return key == null || key.isBlank() ? null : key.trim();
    }

    static void setApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            jEdit.unsetProperty(API_KEY_PROPERTY);
        } else {
            jEdit.setProperty(API_KEY_PROPERTY, apiKey.trim());
        }
    }

    static void clearSession() {
        jEdit.unsetProperty(API_KEY_PROPERTY);
    }

    static String modelId() {
        String id = jEdit.getProperty(MODEL_PROPERTY);
        return id == null || id.isBlank() ? null : id.trim();
    }

    static void setModelId(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            jEdit.unsetProperty(MODEL_PROPERTY);
        } else {
            jEdit.setProperty(MODEL_PROPERTY, modelId.trim());
        }
    }
}
