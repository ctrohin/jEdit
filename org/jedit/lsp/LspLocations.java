/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;

/**
 * Converts LSP location and symbol types into {@link LspSymbolHit} lists.
 */
final class LspLocations {

    private LspLocations() {}

    static List<Location> fromDefinition(
        Either<List<? extends Location>, List<? extends LocationLink>> result) {
        List<Location> locations = new ArrayList<>();
        if (result == null) {
            return locations;
        }
        if (result.isLeft()) {
            List<? extends Location> list = result.getLeft();
            if (list != null) {
                locations.addAll(list);
            }
        } else {
            List<? extends LocationLink> links = result.getRight();
            if (links != null) {
                for (LocationLink link : links) {
                    if (link == null) {
                        continue;
                    }
                    Location location = new Location();
                    location.setUri(link.getTargetUri());
                    location.setRange(link.getTargetRange());
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    static List<Location> fromLocationList(List<? extends Location> list) {
        List<Location> locations = new ArrayList<>();
        if (list != null) {
            for (Location location : list) {
                if (location != null && location.getUri() != null) {
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    static List<LspSymbolHit> toHits(List<Location> locations) {
        List<LspSymbolHit> hits = new ArrayList<>();
        for (Location location : locations) {
            if (location == null || location.getUri() == null) {
                continue;
            }
            hits.add(LspSymbolHit.fromLocation(location, describeLocation(location)));
        }
        return hits;
    }

    static List<LspSymbolHit> fromDocumentSymbols(String fileUri,
        List<Either<SymbolInformation, DocumentSymbol>> symbols) {
        List<LspSymbolHit> hits = new ArrayList<>();
        if (symbols == null) {
            return hits;
        }
        for (Either<SymbolInformation, DocumentSymbol> either : symbols) {
            if (either == null) {
                continue;
            }
            if (either.isLeft()) {
                SymbolInformation info = either.getLeft();
                if (info != null && info.getLocation() != null) {
                    hits.add(symbolInformationHit(info));
                }
            } else {
                DocumentSymbol symbol = either.getRight();
                if (symbol != null) {
                    hits.add(documentSymbolHit(symbol, fileUri));
                }
            }
        }
        return hits;
    }

    static List<LspSymbolHit> fromIncomingCalls(List<CallHierarchyIncomingCall> calls) {
        List<LspSymbolHit> hits = new ArrayList<>();
        if (calls == null) {
            return hits;
        }
        for (CallHierarchyIncomingCall call : calls) {
            if (call == null) {
                continue;
            }
            CallHierarchyItem from = call.getFrom();
            if (from == null) {
                continue;
            }
            for (Range range : call.getFromRanges()) {
                hits.add(callHierarchyHit(from, range, "caller"));
            }
        }
        return hits;
    }

    static List<LspSymbolHit> fromOutgoingCalls(List<CallHierarchyOutgoingCall> calls) {
        List<LspSymbolHit> hits = new ArrayList<>();
        if (calls == null) {
            return hits;
        }
        for (CallHierarchyOutgoingCall call : calls) {
            if (call == null) {
                continue;
            }
            CallHierarchyItem to = call.getTo();
            if (to == null) {
                continue;
            }
            for (Range range : call.getFromRanges()) {
                hits.add(callHierarchyHit(to, range, "callee"));
            }
        }
        return hits;
    }

    static LspSymbolHit callHierarchyItemHit(CallHierarchyItem item) {
        Range range = selectionRange(item);
        String label = item.getName();
        if (item.getKind() != null) {
            label = formatKind(item.getKind()) + " " + label;
        }
        return new LspSymbolHit(
            item.getUri(),
            range,
            label,
            item.getDetail(),
            List.of());
    }

    private static LspSymbolHit callHierarchyHit(CallHierarchyItem item, Range range, String role) {
        String label = item.getName();
        if (item.getKind() != null) {
            label = formatKind(item.getKind()) + " " + label;
        }
        String detail = role;
        if (item.getDetail() != null && !item.getDetail().isBlank()) {
            detail = role + " — " + item.getDetail();
        }
        return new LspSymbolHit(item.getUri(), range, label, detail, List.of());
    }

    private static LspSymbolHit documentSymbolHit(DocumentSymbol symbol, String uri) {
        String symbolUri = uri;
        if (symbolUri == null) {
            symbolUri = "urn:document-symbol";
        }
        List<LspSymbolHit> children = new ArrayList<>();
        if (symbol.getChildren() != null) {
            for (DocumentSymbol child : symbol.getChildren()) {
                children.add(documentSymbolHit(child, symbolUri));
            }
        }
        String label = symbol.getName();
        if (symbol.getKind() != null) {
            label = formatKind(symbol.getKind()) + " " + label;
        }
        return new LspSymbolHit(
            symbolUri,
            symbol.getRange(),
            label,
            symbol.getDetail(),
            children);
    }

    static LspSymbolHit symbolInformationHit(SymbolInformation info) {
        String label = info.getName();
        if (info.getKind() != null) {
            label = formatKind(info.getKind()) + " " + label;
        }
        Location location = info.getLocation();
        return new LspSymbolHit(
            location.getUri(),
            location.getRange(),
            label,
            info.getContainerName(),
            List.of());
    }

    static LspSymbolHit workspaceSymbolHit(WorkspaceSymbol symbol) {
        String label = symbol.getName();
        if (symbol.getKind() != null) {
            label = formatKind(symbol.getKind()) + " " + label;
        }
        Either<Location, WorkspaceSymbolLocation> location = symbol.getLocation();
        String uri;
        Range range;
        if (location == null) {
            return new LspSymbolHit("urn:workspace-symbol", null, label,
                symbol.getContainerName(), List.of());
        }
        if (location.isLeft()) {
            Location left = location.getLeft();
            uri = left.getUri();
            range = left.getRange();
        } else {
            WorkspaceSymbolLocation right = location.getRight();
            uri = right.getUri();
            range = new Range(new Position(0, 0), new Position(0, 0));
        }
        return new LspSymbolHit(uri, range, label, symbol.getContainerName(), List.of());
    }

    static Range selectionRange(CallHierarchyItem item) {
        if (item.getRange() != null) {
            return item.getRange();
        }
        if (item.getSelectionRange() != null) {
            return item.getSelectionRange();
        }
        return new Range(new Position(0, 0), new Position(0, 0));
    }

    static String describeLocation(Location location) {
        String path = LspDocumentUri.uriToPath(location.getUri());
        Buffer buffer = path != null ? jEdit.getBuffer(path) : null;
        if (buffer != null && location.getRange() != null) {
            Position start = location.getRange().getStart();
            if (start != null) {
                try {
                    int lineStart = buffer.getLineStartOffset(start.getLine());
                    int lineEnd = buffer.getLineEndOffset(start.getLine()) - 1;
                    int from = Math.min(lineStart + start.getCharacter(), lineEnd);
                    int to = lineEnd;
                    if (location.getRange().getEnd() != null) {
                        int endOffset = buffer.getLineStartOffset(
                            location.getRange().getEnd().getLine())
                            + location.getRange().getEnd().getCharacter();
                        to = Math.min(endOffset, lineEnd);
                    }
                    String lineText = buffer.getText(from, Math.max(0, to - from)).trim();
                    if (!lineText.isEmpty()) {
                        return lineText;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (location.getRange() != null && location.getRange().getStart() != null) {
            Position start = location.getRange().getStart();
            return "line " + (start.getLine() + 1);
        }
        return path != null ? path : location.getUri();
    }

    private static String formatKind(SymbolKind kind) {
        if (kind == null) {
            return "";
        }
        String name = kind.name();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }
}
