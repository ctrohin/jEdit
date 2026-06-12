/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.Location;

/**
 * One LSP symbol search session displayed in the Symbol Results view.
 */
public final class LspSymbolSearchResult {

    public enum Kind {
        REFERENCES("lsp-symbol-results.kind.references"),
        IMPLEMENTATION("lsp-symbol-results.kind.implementation"),
        TYPE_DEFINITION("lsp-symbol-results.kind.type-definition"),
        DECLARATION("lsp-symbol-results.kind.declaration"),
        DOCUMENT_SYMBOLS("lsp-symbol-results.kind.document-symbols"),
        WORKSPACE_SYMBOLS("lsp-symbol-results.kind.workspace-symbols"),
        CALL_HIERARCHY("lsp-symbol-results.kind.call-hierarchy");

        private final String propertyKey;

        Kind(String propertyKey) {
            this.propertyKey = propertyKey;
        }

        public String getPropertyKey() {
            return propertyKey;
        }
    }

    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final String id;
    private final Kind kind;
    private final String query;
    private final long timestamp;
    private final List<LspSymbolFileGroup> fileGroups;
    private final List<CallHierarchyItem> callHierarchyRoots;

    private LspSymbolSearchResult(String id, Kind kind, String query,
                                    List<LspSymbolFileGroup> fileGroups,
                                    List<CallHierarchyItem> callHierarchyRoots) {
        this.id = id;
        this.kind = kind;
        this.query = query != null ? query : "";
        this.timestamp = System.currentTimeMillis();
        this.fileGroups = List.copyOf(fileGroups);
        this.callHierarchyRoots = List.copyOf(callHierarchyRoots);
    }

    public static LspSymbolSearchResult forLocations(Kind kind, String query,
                                                     List<Location> locations) {
        return forHits(kind, query, LspLocations.toHits(locations));
    }

    public static LspSymbolSearchResult forHits(Kind kind, String query,
                                                List<LspSymbolHit> hits) {
        Map<String, List<LspSymbolHit>> byUri = new LinkedHashMap<>();
        for (LspSymbolHit hit : hits) {
            byUri.computeIfAbsent(hit.getUri(), ignored -> new ArrayList<>()).add(hit);
        }
        List<LspSymbolFileGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<LspSymbolHit>> entry : byUri.entrySet()) {
            groups.add(new LspSymbolFileGroup(
                entry.getKey(), LspSymbolHit.sortedCopy(entry.getValue())));
        }
        return new LspSymbolSearchResult(
            Long.toString(ID_GENERATOR.incrementAndGet()),
            kind,
            query,
            groups,
            List.of());
    }

    public static LspSymbolSearchResult forDocumentSymbols(String fileUri,
                                                           List<LspSymbolHit> roots) {
        return new LspSymbolSearchResult(
            Long.toString(ID_GENERATOR.incrementAndGet()),
            Kind.DOCUMENT_SYMBOLS,
            LspDocumentUri.uriToPath(fileUri),
            List.of(new LspSymbolFileGroup(fileUri, roots)),
            List.of());
    }

    public static LspSymbolSearchResult forCallHierarchy(String query,
                                                         List<CallHierarchyItem> roots) {
        return new LspSymbolSearchResult(
            Long.toString(ID_GENERATOR.incrementAndGet()),
            Kind.CALL_HIERARCHY,
            query,
            List.of(),
            roots);
    }

    public String getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getQuery() {
        return query;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<LspSymbolFileGroup> getFileGroups() {
        return fileGroups;
    }

    public List<CallHierarchyItem> getCallHierarchyRoots() {
        return callHierarchyRoots;
    }

    public int getHitCount() {
        if (kind == Kind.CALL_HIERARCHY) {
            return callHierarchyRoots.size();
        }
        int count = 0;
        for (LspSymbolFileGroup group : fileGroups) {
            count += countHits(group.getHits());
        }
        return count;
    }

    private static int countHits(List<LspSymbolHit> hits) {
        int count = hits.size();
        for (LspSymbolHit hit : hits) {
            count += countHits(hit.getChildren());
        }
        return count;
    }

    public int getFileCount() {
        return fileGroups.size();
    }

    public boolean isEmpty() {
        return getHitCount() == 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LspSymbolSearchResult other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
