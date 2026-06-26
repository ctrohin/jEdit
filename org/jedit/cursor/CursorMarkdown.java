/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.cursor;

import java.awt.Color;

import javax.swing.UIManager;

import org.gjt.sp.jedit.jEdit;

public final class CursorMarkdown {

    private CursorMarkdown() {}

    static String documentHtml(String markdown) {
        Color foreground = textForeground();
        String body = markdownToHtml(markdown == null ? "" : markdown, foreground);
        return "<html><body style='margin:0;padding:0;color:" + colorHex(foreground)
            + ";font-family:sans-serif;font-size:13px;line-height:1.45;'>"
            + body + "</body></html>";
    }

    static String plainHtml(String text) {
        Color foreground = textForeground();
        String escaped = escapeHtml(text == null ? "" : text).replace("\n", "<br>");
        return "<html><body style='margin:0;padding:0;color:" + colorHex(foreground)
            + ";font-family:sans-serif;font-size:13px;line-height:1.45;'>"
            + escaped + "</body></html>";
    }

    static Color textForeground() {
        Color color = jEdit.getColorProperty("view.fgColor");
        return color != null ? color : UIManager.getColor("Label.foreground");
    }

    static Color textBackground() {
        Color color = jEdit.getColorProperty("view.bgColor");
        return color != null ? color : UIManager.getColor("Panel.background");
    }

    static Color userBubbleBackground(Color foreground) {
        return isDark(foreground)
            ? new Color(0x2b4a63)
            : new Color(0xe8f1ff);
    }

    static Color userBubbleForeground(Color foreground) {
        return isDark(foreground) ? new Color(0xf2f6fa) : new Color(0x1f2937);
    }

    static Color metaForeground(Color foreground) {
        Color disabled = UIManager.getColor("Label.disabledForeground");
        if (disabled != null) {
            return disabled;
        }
        return isDark(foreground) ? new Color(0x9aa7b3) : new Color(0x6b7280);
    }

    static Color errorForeground() {
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        return error != null ? error : new Color(0xc62828);
    }

    static Color errorBackground(Color foreground) {
        return isDark(foreground) ? new Color(0x3f2224) : new Color(0xffebee);
    }

    private static String markdownToHtml(String markdown, Color foreground) {
        StringBuilder html = new StringBuilder();
        String[] lines = markdown.split("\n", -1);
        boolean inCode = false;
        boolean inList = false;
        StringBuilder codeBlock = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("```")) {
                closeList(html, inList);
                inList = false;
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
                closeList(html, inList);
                inList = false;
                html.append("<br>");
                continue;
            }

            if (line.startsWith("### ")) {
                closeList(html, inList);
                inList = false;
                html.append("<div style='font-weight:600;font-size:14px;margin:8px 0 4px 0;'>")
                    .append(inlineMarkdownToHtml(line.substring(4), foreground))
                    .append("</div>");
                continue;
            }
            if (line.startsWith("## ")) {
                closeList(html, inList);
                inList = false;
                html.append("<div style='font-weight:600;font-size:15px;margin:10px 0 4px 0;'>")
                    .append(inlineMarkdownToHtml(line.substring(3), foreground))
                    .append("</div>");
                continue;
            }
            if (line.startsWith("# ")) {
                closeList(html, inList);
                inList = false;
                html.append("<div style='font-weight:600;font-size:16px;margin:12px 0 6px 0;'>")
                    .append(inlineMarkdownToHtml(line.substring(2), foreground))
                    .append("</div>");
                continue;
            }

            if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) {
                    html.append("<ul style='margin:4px 0 8px 18px;padding:0;'>");
                    inList = true;
                }
                html.append("<li>")
                    .append(inlineMarkdownToHtml(line.substring(2), foreground))
                    .append("</li>");
                continue;
            }

            closeList(html, inList);
            inList = false;
            html.append(inlineMarkdownToHtml(line, foreground)).append("<br>");
        }

        closeList(html, inList);
        if (inCode && codeBlock.length() > 0) {
            html.append(codeBlockHtml(codeBlock.toString(), foreground));
        }
        return trimTrailingBreaks(html.toString());
    }

    private static void closeList(StringBuilder html, boolean inList) {
        if (inList) {
            html.append("</ul>");
        }
    }

    private static String inlineMarkdownToHtml(String line, Color foreground) {
        String escaped = escapeHtml(line);
        String inlineCodeStyle = inlineCodeStyle(foreground);
        escaped = escaped.replaceAll("`([^`]+)`",
            "<code style='" + inlineCodeStyle + "'>$1</code>");
        escaped = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        String italicStyle = "color:" + colorHex(metaForeground(foreground)) + ";";
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
        return "margin:6px 0;padding:10px 12px;background:" + colorHex(background)
            + ";color:" + colorHex(codeForeground)
            + ";font-family:monospace;font-size:12px;white-space:pre-wrap;border-radius:6px;border:1px solid "
            + colorHex(codeBorder(background, foreground)) + ";";
    }

    private static String inlineCodeStyle(Color foreground) {
        Color background = codeBackground(foreground);
        Color codeForeground = codeForeground(foreground);
        return "background:" + colorHex(background) + ";color:" + colorHex(codeForeground)
            + ";font-family:monospace;padding:1px 4px;border-radius:3px;";
    }

    private static Color codeBackground(Color foreground) {
        Color fromUi = UIManager.getColor("TextArea.background");
        if (fromUi != null && contrastsWith(fromUi, foreground)) {
            return fromUi;
        }
        return isDark(foreground) ? new Color(0x1e1f22) : new Color(0xf5f5f5);
    }

    private static Color codeForeground(Color foreground) {
        Color link = UIManager.getColor("Component.linkColor");
        if (link != null) {
            return link;
        }
        return isDark(foreground) ? new Color(0x9cdcfe) : new Color(0x0550ae);
    }

    private static Color codeBorder(Color background, Color foreground) {
        return isDark(foreground)
            ? background.brighter()
            : background.darker();
    }

    private static boolean endsWithBlock(StringBuilder html) {
        String text = html.toString();
        return text.endsWith("</pre>") || text.endsWith("</ul>") || text.endsWith("</div>");
    }

    private static String trimTrailingBreaks(String html) {
        while (html.endsWith("<br>")) {
            html = html.substring(0, html.length() - 4);
        }
        return html;
    }

    private static String escapeHtml(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String colorHex(Color color) {
        if (color == null) {
            return "#000000";
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static boolean isDark(Color color) {
        if (color == null) {
            return false;
        }
        double luminance = (0.299 * color.getRed()) + (0.587 * color.getGreen())
            + (0.114 * color.getBlue());
        return luminance < 128;
    }

    private static boolean contrastsWith(Color a, Color b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.getRed() - b.getRed()) > 16
            || Math.abs(a.getGreen() - b.getGreen()) > 16
            || Math.abs(a.getBlue() - b.getBlue()) > 16;
    }
}
