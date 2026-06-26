/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Symbol hits grouped under one file in the results tree.
 */
public final class LspSymbolFileGroup {

    private final String uri;
    private final String displayName;
    private final List<LspSymbolHit> hits;

    public LspSymbolFileGroup(String uri, List<LspSymbolHit> hits) {
        this.uri = Objects.requireNonNull(uri, "uri");
        this.hits = List.copyOf(hits);
        String path = LspDocumentUri.uriToPath(uri);
        this.displayName = path != null
            ? new File(path).getName()
            : uri;
    }

    public String getUri() {
        return uri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<LspSymbolHit> getHits() {
        return hits;
    }

    @Override
    public String toString() {
        return displayName + " (" + hits.size() + ")";
    }
}
