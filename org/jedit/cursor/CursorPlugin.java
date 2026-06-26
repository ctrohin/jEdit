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

import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.View;
import org.gjt.sp.util.Log;

/**
 * Built-in Cursor Cloud Agents integration.
 */
public class CursorPlugin extends EditPlugin {

    private static CursorPlugin instance;

    public CursorPlugin() {
        instance = this;
    }

    public static CursorPlugin getInstance() {
        return instance;
    }

    @Override
    public void start() {
        Log.log(Log.MESSAGE, this, "Cursor integration starting...");
    }

    @Override
    public void stop() {
        Log.log(Log.MESSAGE, this, "Cursor integration stopping...");
    }

    public static void showCursor(View view) {
        CursorView.show(view);
    }

    public static void login(View view) {
        CursorView cursorView = CursorView.show(view);
        cursorView.promptLogin();
    }

    public static void logout(View view) {
        CursorView cursorView = (CursorView) view.getDockableWindowManager()
            .getDockableWindow(CursorView.NAME);
        if (cursorView != null) {
            cursorView.promptLogout();
        } else {
            CursorConfig.clearSession();
        }
    }

    public static boolean isSignedIn() {
        return CursorConfig.apiKey() != null;
    }

    private static String workspaceCwd() {
        java.io.File root = CursorWorkspaceContext.workspaceRoot();
        if (root != null) {
            return root.getAbsolutePath();
        }
        String settings = org.gjt.sp.jedit.jEdit.getSettingsDirectory();
        if (settings != null && !settings.isBlank()) {
            return settings;
        }
        return System.getProperty("user.home", ".");
    }
}
