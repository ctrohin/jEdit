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

import javax.swing.UIManager;

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

        String body = formatContents(hover.getContents(), foreground);
        if (body == null || body.isBlank()) {
            return null;
        }

        return "<html><body style='width: " + maxWidth + "px; color: "
            + colorHex(foreground) + ";'>" + body + "</body></html>";
    }

    private static String formatContents(
            Either<List<Either<String, MarkedString>>, MarkupContent> contents,
            Color foreground) {
        if (contents.isRight()) {
            return formatMarkup(contents.getRight(), foreground);
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
                html.append(markedStringToHtml(part.getRight(), foreground));
            }
        }
        return html.toString();
    }

    private static String formatMarkup(MarkupContent markup, Color foreground) {
        if (markup == null || markup.getValue() == null) {
            return null;
        }
        String value = markup.getValue();
        if (MarkupKind.MARKDOWN.equals(markup.getKind())) {
            return markdownToHtml(value, foreground);
        }
        return plainTextToHtml(value);
    }

    private static String markedStringToHtml(MarkedString marked, Color foreground) {
        if (marked == null || marked.getValue() == null) {
            return "";
        }
        if (marked.getLanguage() != null && !marked.getLanguage().isEmpty()) {
            return codeBlockHtml(marked.getValue(), foreground);
        }
        return plainTextToHtml(marked.getValue());
    }

    private static String plainTextToHtml(String text) {
        return escapeHtml(text).replace("\n", "<br>");
    }

    /**
     * Small markdown subset for typical LSP hover docs (signatures, code, emphasis).
     */
    private static String markdownToHtml(String markdown, Color foreground) {
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        boolean inCode = false;
        StringBuilder codeBlock = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCode) {
                    html.append(codeBlockHtml(codeBlock.toString(), foreground));
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

            html.append(inlineMarkdownToHtml(line, foreground)).append("<br>");
        }

        if (inCode && codeBlock.length() > 0) {
            html.append(codeBlockHtml(codeBlock.toString(), foreground));
        }

        return trimTrailingBreaks(html.toString());
    }

    private static String inlineMarkdownToHtml(String line, Color foreground) {
        String escaped = escapeHtml(line);
        String inlineCodeStyle = inlineCodeStyle(foreground);
        escaped = escaped.replaceAll("`([^`]+)`",
            "<code style='" + inlineCodeStyle + "'>$1</code>");
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        String italicStyle = italicStyle(foreground);
        escaped = escaped.replaceAll("\\*([^*]+)\\*",
            "<i style='" + italicStyle + "'>$1</i>");
        escaped = escaped.replaceAll("_([^_]+)_",
            "<i style='" + italicStyle + "'>$1</i>");
        return escaped;
    }

    private static String codeBlockHtml(String code, Color foreground) {
        return "<pre style='" + codeBlockStyle(foreground) + "'><code>"
            + escapeHtml(code) + "</code></pre>";
    }

    private static String codeBlockStyle(Color foreground) {
        Color background = codeBackground(foreground);
        Color codeForeground = codeForeground(foreground);
        return "margin:0 0 6px 0;padding:6px 8px;background:" + colorHex(background)
            + ";color:" + colorHex(codeForeground)
            + ";font-family:monospace;font-size:smaller;border:1px solid "
            + colorHex(codeBorder(background, foreground)) + ";";
    }

    private static String inlineCodeStyle(Color foreground) {
        Color background = codeBackground(foreground);
        Color codeForeground = codeForeground(foreground);
        return "background:" + colorHex(background) + ";color:" + colorHex(codeForeground)
            + ";font-family:monospace;padding:1px 3px;";
    }

    private static String italicStyle(Color foreground) {
        return "color:" + colorHex(italicForeground(foreground)) + ";";
    }

    private static Color codeBackground(Color foreground) {
        Color fromUi = UIManager.getColor("TextArea.background");
        if (fromUi != null && contrastsWith(fromUi, foreground)) {
            return fromUi;
        }
        return isDark(foreground)
            ? new Color(0x3c3f41)
            : new Color(0xf0f0f0);
    }

    private static Color codeForeground(Color foreground) {
        Color link = UIManager.getColor("Component.linkColor");
        if (link != null) {
            return link;
        }
        return isDark(foreground)
            ? new Color(0x9cdcfe)
            : new Color(0x0550ae);
    }

    private static Color italicForeground(Color foreground) {
        Color info = UIManager.getColor("Label.disabledForeground");
        if (info != null && contrastsWith(info, foreground)) {
            return info;
        }
        return isDark(foreground)
            ? new Color(0xa9b7c6)
            : new Color(0x5c6370);
    }

    private static Color codeBorder(Color background, Color foreground) {
        return isDark(foreground)
            ? blend(background, Color.WHITE, 0.18f)
            : blend(background, Color.BLACK, 0.12f);
    }

    private static Color blend(Color base, Color overlay, float ratio) {
        float inverse = 1f - ratio;
        return new Color(
            clampColor(base.getRed() * inverse + overlay.getRed() * ratio),
            clampColor(base.getGreen() * inverse + overlay.getGreen() * ratio),
            clampColor(base.getBlue() * inverse + overlay.getBlue() * ratio));
    }

    private static int clampColor(float channel) {
        return Math.max(0, Math.min(255, Math.round(channel)));
    }

    private static boolean isDark(Color color) {
        if (color == null) {
            return false;
        }
        double luminance = (0.299 * color.getRed())
            + (0.587 * color.getGreen())
            + (0.114 * color.getBlue());
        return luminance < 140;
    }

    private static boolean contrastsWith(Color a, Color b) {
        if (a == null || b == null) {
            return false;
        }
        int delta = Math.abs(a.getRed() - b.getRed())
            + Math.abs(a.getGreen() - b.getGreen())
            + Math.abs(a.getBlue() - b.getBlue());
        return delta > 30;
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
