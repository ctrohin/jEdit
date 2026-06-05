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

package org.jedit.lsp;

import java.io.File;
import java.net.URI;

import org.gjt.sp.jedit.MiscUtilities;

/**
 * Maps LSP {@code file:} document URIs to local paths (and back).
 */
final class LspDocumentUri {

    private LspDocumentUri() {}

    static String pathToUri(String path) {
        if (path == null) {
            return null;
        }
        return new File(MiscUtilities.resolveSymlinks(path)).toURI().toString();
    }

    static String uriToPath(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            URI parsed = URI.create(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return null;
            }
            return MiscUtilities.resolveSymlinks(new File(parsed).getPath());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean urisReferToSameFile(String uri1, String uri2) {
        if (uri1 == null || uri2 == null) {
            return false;
        }
        if (uri1.equals(uri2)) {
            return true;
        }
        String path1 = uriToPath(uri1);
        String path2 = uriToPath(uri2);
        return path1 != null && path2 != null && MiscUtilities.pathsEqual(path1, path2);
    }
}
