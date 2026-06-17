/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Builds a parent/child tree from a flat list of document symbols.
 */
final class LspStructureTreeBuilder {

    private LspStructureTreeBuilder() {}

    static List<LspSymbolHit> build(List<FlatSymbol> flat) {
        if (flat == null || flat.isEmpty()) {
            return List.of();
        }
        List<MutableNode> nodes = new ArrayList<>(flat.size());
        for (FlatSymbol symbol : flat) {
            nodes.add(new MutableNode(symbol));
        }
        nodes.sort(NestingOrder.COMPARATOR);

        List<MutableNode> roots = nestByRange(nodes);
        attachByContainerName(roots);
        return freeze(roots);
    }

    private static List<MutableNode> nestByRange(List<MutableNode> sorted) {
        List<MutableNode> roots = new ArrayList<>();
        Deque<MutableNode> stack = new ArrayDeque<>();
        for (MutableNode node : sorted) {
            while (!stack.isEmpty() && !stack.peek().rangeContains(node)) {
                stack.pop();
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                MutableNode parent = stack.peek();
                parent.children.add(node);
                node.parent = parent;
            }
            stack.push(node);
        }
        return roots;
    }

    private static void attachByContainerName(List<MutableNode> roots) {
        Map<String, List<MutableNode>> byName = new HashMap<>();
        indexNames(roots, byName);

        List<MutableNode> pending = new ArrayList<>();
        collectNodes(roots, pending);
        for (MutableNode node : pending) {
            String containerName = node.symbol.containerName();
            if (containerName == null || containerName.isBlank()) {
                continue;
            }
            if (node.parent != null) {
                continue;
            }
            MutableNode parent = findContainerParent(byName, containerName, node);
            if (parent != null && parent != node) {
                roots.remove(node);
                parent.children.add(node);
                node.parent = parent;
            }
        }
    }

    private static void indexNames(List<MutableNode> nodes, Map<String, List<MutableNode>> byName) {
        for (MutableNode node : nodes) {
            if (isContainerKind(node.symbol.kind())) {
                byName.computeIfAbsent(node.symbol.name(), k -> new ArrayList<>()).add(node);
                String simple = simpleName(node.symbol.name());
                if (!simple.equals(node.symbol.name())) {
                    byName.computeIfAbsent(simple, k -> new ArrayList<>()).add(node);
                }
            }
            indexNames(node.children, byName);
        }
    }

    private static void collectNodes(List<MutableNode> nodes, List<MutableNode> out) {
        for (MutableNode node : nodes) {
            out.add(node);
            collectNodes(node.children, out);
        }
    }

    private static MutableNode findContainerParent(Map<String, List<MutableNode>> byName,
                                                   String containerName, MutableNode child) {
        MutableNode match = bestMatch(byName.get(containerName), child);
        if (match != null) {
            return match;
        }
        String simple = simpleName(containerName);
        if (!simple.equals(containerName)) {
            match = bestMatch(byName.get(simple), child);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static MutableNode bestMatch(List<MutableNode> candidates, MutableNode child) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        MutableNode best = null;
        int bestSpan = Integer.MAX_VALUE;
        for (MutableNode candidate : candidates) {
            if (candidate == child) {
                continue;
            }
            if (!candidate.rangeContains(child)) {
                continue;
            }
            int span = candidate.spanLength();
            if (span < bestSpan) {
                best = candidate;
                bestSpan = span;
            }
        }
        if (best != null) {
            return best;
        }
        return candidates.get(0);
    }

    private static boolean isContainerKind(SymbolKind kind) {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case Class, Interface, Struct, Enum, Namespace, Module, Package, Object -> true;
            default -> false;
        };
    }

    private static String simpleName(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static List<LspSymbolHit> freeze(List<MutableNode> nodes) {
        List<LspSymbolHit> hits = new ArrayList<>(nodes.size());
        for (MutableNode node : nodes) {
            hits.add(node.toHit());
        }
        return hits;
    }

    record FlatSymbol(String uri, Range range, SymbolKind kind, String name,
                      String detail, String containerName) {

        int line() {
            Position start = range != null ? range.getStart() : null;
            return start != null ? start.getLine() : 0;
        }

        int character() {
            Position start = range != null ? range.getStart() : null;
            return start != null ? start.getCharacter() : 0;
        }

        int endLine() {
            Position end = range != null ? range.getEnd() : null;
            return end != null ? end.getLine() : line();
        }

        int endCharacter() {
            Position end = range != null ? range.getEnd() : null;
            return end != null ? end.getCharacter() : character();
        }
    }

    private static final class MutableNode {
        private final FlatSymbol symbol;
        private final List<MutableNode> children = new ArrayList<>();
        private MutableNode parent;

        MutableNode(FlatSymbol symbol) {
            this.symbol = symbol;
        }

        boolean rangeContains(MutableNode other) {
            if (other == this) {
                return false;
            }
            if (other.symbol.line() < symbol.line()) {
                return false;
            }
            if (other.symbol.endLine() > symbol.endLine()) {
                return false;
            }
            if (other.symbol.line() == symbol.line()
                && other.symbol.character() < symbol.character()) {
                return false;
            }
            if (other.symbol.endLine() == symbol.endLine()
                && other.symbol.endCharacter() > symbol.endCharacter()) {
                return false;
            }
            return !(other.symbol.line() == symbol.line()
                && other.symbol.character() == symbol.character()
                && other.symbol.endLine() == symbol.endLine()
                && other.symbol.endCharacter() == symbol.endCharacter());
        }

        int spanLength() {
            int lineSpan = symbol.endLine() - symbol.line();
            return lineSpan * 10_000 + (symbol.endCharacter() - symbol.character());
        }

        LspSymbolHit toHit() {
            List<LspSymbolHit> childHits = freeze(children);
            return new LspSymbolHit(
                symbol.uri(),
                symbol.range(),
                symbol.kind(),
                symbol.name(),
                symbol.detail(),
                symbol.containerName(),
                childHits);
        }
    }

    private static final class NestingOrder {
        private static final Comparator<MutableNode> COMPARATOR = (a, b) -> {
            int byLine = Integer.compare(a.symbol.line(), b.symbol.line());
            if (byLine != 0) {
                return byLine;
            }
            int byChar = Integer.compare(a.symbol.character(), b.symbol.character());
            if (byChar != 0) {
                return byChar;
            }
            return Integer.compare(b.spanLength(), a.spanLength());
        };

        private NestingOrder() {}
    }
}
