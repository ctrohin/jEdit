/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.List;

import org.eclipse.lsp4j.CallHierarchyItem;

/**
 * Tree node state for lazy-loaded call hierarchy branches.
 */
public final class LspCallHierarchyBranch {

    private final CallHierarchyItem item;
    private boolean loaded;
    private boolean loading;
    private List<LspSymbolHit> incoming = List.of();
    private List<LspSymbolHit> outgoing = List.of();

    public LspCallHierarchyBranch(CallHierarchyItem item) {
        this.item = item;
    }

    public CallHierarchyItem getItem() {
        return item;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isLoading() {
        return loading;
    }

    void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }

    void setLoading(boolean loading) {
        this.loading = loading;
    }

    public List<LspSymbolHit> getIncoming() {
        return incoming;
    }

    public List<LspSymbolHit> getOutgoing() {
        return outgoing;
    }

    void setIncoming(List<LspSymbolHit> incoming) {
        this.incoming = incoming;
    }

    void setOutgoing(List<LspSymbolHit> outgoing) {
        this.outgoing = outgoing;
    }
}
