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

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.util.Log;

/**
 * LSP {@code textDocument/hover} requests and hover content formatting.
 */
final class LspHover {

    private static final AtomicInteger REQUEST_GENERATION = new AtomicInteger(0);

    private LspHover() {}

    static void requestHover(GenericLspClient client, Buffer buffer, Position position,
                             Consumer<Hover> onResult) {
        if (client == null || client.getServer() == null || buffer == null
            || position == null) {
            onResult.accept(null);
            return;
        }

        int generation = REQUEST_GENERATION.incrementAndGet();
        String documentUri = LspDocumentUri.pathToUri(buffer.getPath());

        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(documentUri));
        params.setPosition(position);

        client.whenReady().thenCompose(ignored ->
            client.getServer().getTextDocumentService().hover(params))
            .whenComplete((hover, ex) -> {
                if (generation != REQUEST_GENERATION.get()) {
                    return;
                }
                if (ex != null) {
                    Log.log(Log.DEBUG, LspHover.class, "Error requesting LSP hover", ex);
                    onResult.accept(null);
                    return;
                }
                onResult.accept(hover);
            });
    }

    static void cancelPendingRequests() {
        REQUEST_GENERATION.incrementAndGet();
    }

    static String hoverToHtml(Hover hover, int maxWidth, Color foreground) {
        if (hover == null || hover.getContents() == null) {
            return null;
        }

        String body = formatContents(hover.getContents());
        if (body == null || body.isBlank()) {
            return null;
        }

        return "<html><body style='width: " + maxWidth + "px; color: "
            + colorHex(foreground) + ";'>" + body + "</body></html>";
    }

    private static String formatContents(
            Either<List<Either<String, MarkedString>>, MarkupContent> contents) {
        if (contents.isRight()) {
            return formatMarkup(contents.getRight());
        }

        List<Either<String, MarkedString>> parts = contents.getLeft();
        if (parts == null || parts.isEmpty()) {
            return null;
        }

        StringBuilder html = new StringBuilder();
        for (Either<String, MarkedString> part : parts) {
            if (part == null) {
                continue;
            }
            if (html.length() > 0) {
                html.append("<br><br>");
            }
            if (part.isLeft()) {
                html.append(plainTextToHtml(part.getLeft()));
            } else {
                html.append(markedStringToHtml(part.getRight()));
            }
        }
        return html.toString();
    }

    private static String formatMarkup(MarkupContent markup) {
        if (markup == null || markup.getValue() == null) {
            return null;
        }
        String value = markup.getValue();
        if (MarkupKind.MARKDOWN.equals(markup.getKind())) {
            return markdownToHtml(value);
        }
        return plainTextToHtml(value);
    }

    private static String markedStringToHtml(MarkedString marked) {
        if (marked == null || marked.getValue() == null) {
            return "";
        }
        String value = escapeHtml(marked.getValue());
        if (marked.getLanguage() != null && !marked.getLanguage().isEmpty()) {
            return "<pre style='margin:0 0 6px 0;'><code>" + value + "</code></pre>";
        }
        return plainTextToHtml(marked.getValue());
    }

    private static String plainTextToHtml(String text) {
        return escapeHtml(text).replace("\n", "<br>");
    }

    /**
     * Small markdown subset for typical LSP hover docs (signatures, code, emphasis).
     */
    private static String markdownToHtml(String markdown) {
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        boolean inCode = false;
        StringBuilder codeBlock = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCode) {
                    html.append("<pre style='margin:0 0 6px 0;'><code>")
                        .append(escapeHtml(codeBlock.toString()))
                        .append("</code></pre>");
                    codeBlock.setLength(0);
                    inCode = false;
                } else {
                    if (html.length() > 0 && !endsWithBlock(html)) {
                        html.append("<br>");
                    }
                    inCode = true;
                }
                continue;
            }

            if (inCode) {
                if (codeBlock.length() > 0) {
                    codeBlock.append('\n');
                }
                codeBlock.append(line);
                continue;
            }

            if (line.isBlank()) {
                html.append("<br>");
                continue;
            }

            html.append(inlineMarkdownToHtml(line)).append("<br>");
        }

        if (inCode && codeBlock.length() > 0) {
            html.append("<pre style='margin:0 0 6px 0;'><code>")
                .append(escapeHtml(codeBlock.toString()))
                .append("</code></pre>");
        }

        return trimTrailingBreaks(html.toString());
    }

    private static String inlineMarkdownToHtml(String line) {
        String escaped = escapeHtml(line);
        escaped = escaped.replaceAll("`([^`]+)`", "<code>$1</code>");
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        escaped = escaped.replaceAll("_([^_]+)_", "<i>$1</i>");
        return escaped;
    }

    private static boolean endsWithBlock(StringBuilder html) {
        String text = html.toString();
        return text.endsWith("</pre>") || text.endsWith("<br>");
    }

    private static String trimTrailingBreaks(String html) {
        while (html.endsWith("<br>")) {
            html = html.substring(0, html.length() - 4);
        }
        return html;
    }

    static String colorHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
