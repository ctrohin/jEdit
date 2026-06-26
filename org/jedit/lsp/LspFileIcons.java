/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.io.File;

import javax.swing.Icon;

import org.gjt.sp.jedit.icons.WorkspaceFileIcons;

/**
 * File-type icons for LSP tree views, matching the workspace tree and buffer tabs.
 */
final class LspFileIcons {

    private LspFileIcons() {}

    static Icon iconForUri(String uri) {
        String path = LspDocumentUri.uriToPath(uri);
        return WorkspaceFileIcons.getIcon(path != null ? new File(path) : null);
    }
}
