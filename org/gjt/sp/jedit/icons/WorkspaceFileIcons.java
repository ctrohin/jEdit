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

package org.gjt.sp.jedit.icons;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.Icon;

/**
 * Drawn file-type icons for the workspace tree: a colored circle with a
 * language glyph, plus a dedicated folder icon.
 */
public final class WorkspaceFileIcons {

    public static final int DEFAULT_SIZE = 16;

    private static final Map<String, LanguageKind> EXTENSIONS = buildExtensionMap();
    private static final Map<String, Icon> CACHE = new HashMap<>();

    private WorkspaceFileIcons() {}

    public static Icon getIcon(File file) {
        return getIcon(file, DEFAULT_SIZE);
    }

    public static Icon getIcon(File file, int size) {
        if (file == null) {
            return cached("default:" + size, () -> new CircleLanguageIcon(LanguageKind.GENERIC, size, ""));
        }
        if (file.isDirectory()) {
            return cached("folder:" + size, () -> new WorkspaceFolderIcon(size));
        }
        String extension = extensionOf(file.getName());
        LanguageKind kind = EXTENSIONS.get(extension);
        if (kind != null) {
            return cached(kind.name() + ":" + size,
                () -> new CircleLanguageIcon(kind, size, null));
        }
        return cached("generic:" + extension + ":" + size,
            () -> new CircleLanguageIcon(LanguageKind.GENERIC, size, extension));
    }

    /**
     * Circle icon with a single letter, matching the workspace tree style.
     */
    public static Icon createLetterIcon(String letter, Color background, Color foreground) {
        return createLetterIcon(letter, background, foreground, DEFAULT_SIZE);
    }

    public static Icon createLetterIcon(String letter, Color background, Color foreground,
                                        int size) {
        String glyph = letter == null || letter.isEmpty() ? "?" : letter;
        if (glyph.length() > 1) {
            glyph = glyph.substring(0, 1);
        }
        String key = "letter:" + glyph.toLowerCase(Locale.ROOT) + ':'
            + background.getRGB() + ':' + foreground.getRGB() + ':' + size;
        String finalGlyph = glyph;
        return cached(key, () -> new LetterCircleIcon(finalGlyph, background, foreground, size));
    }

    public static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.indexOf('.');
        while (dot >= 0 && dot < lower.length() - 1) {
            String ext = lower.substring(dot + 1);
            if (EXTENSIONS.containsKey(ext)) {
                return ext;
            }
            dot = lower.indexOf('.', dot + 1);
        }
        int lastDot = lower.lastIndexOf('.');
        if (lastDot > 0 && lastDot < lower.length() - 1) {
            return lower.substring(lastDot + 1);
        }
        return "";
    }

    private static Icon cached(String key, java.util.function.Supplier<Icon> factory) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, k -> factory.get());
        }
    }

    private static Map<String, LanguageKind> buildExtensionMap() {
        Map<String, LanguageKind> map = new HashMap<>();
        register(map, LanguageKind.PYTHON,
            "py", "pyw", "sc", "jy");
        register(map, LanguageKind.JAVA, "java");
        register(map, LanguageKind.C, "c", "h");
        register(map, LanguageKind.CPP, "cc", "cpp", "cxx", "hh", "hpp");
        register(map, LanguageKind.JAVASCRIPT, "js", "jsx");
        register(map, LanguageKind.TYPESCRIPT, "ts", "tsx");
        register(map, LanguageKind.DART, "dart");
        register(map, LanguageKind.RUST, "rs");
        register(map, LanguageKind.KOTLIN, "kt", "kts", "ks", "jetl");
        register(map, LanguageKind.GO, "go");
        register(map, LanguageKind.RUBY, "rb", "rbw");
        register(map, LanguageKind.SCALA, "scala", "sbt");
        register(map, LanguageKind.SWIFT, "swift");
        register(map, LanguageKind.PHP, "php", "php3", "php4", "phtml", "inc");
        register(map, LanguageKind.PERL, "pl", "pm", "ph", "pod");
        register(map, LanguageKind.CSHARP, "cs");
        register(map, LanguageKind.CSS, "css");
        register(map, LanguageKind.SCSS, "scss");
        register(map, LanguageKind.HTML, "html", "htm", "hta");
        register(map, LanguageKind.XML, "xml", "xhtml", "xsd", "docbook", "qrc", "ui");
        register(map, LanguageKind.JSON, "json");
        register(map, LanguageKind.YAML, "yaml", "yml");
        register(map, LanguageKind.MARKDOWN, "md");
        register(map, LanguageKind.SHELL, "sh", "bash", "csh");
        register(map, LanguageKind.SQL, "sql", "pls");
        register(map, LanguageKind.LATEX, "tex", "sty", "ltx");
        register(map, LanguageKind.CLOJURE, "clj");
        register(map, LanguageKind.HASKELL, "hs", "lhs");
        register(map, LanguageKind.LUA, "lua");
        register(map, LanguageKind.R_LANG, "r");
        register(map, LanguageKind.ASM, "asm");
        return Collections.unmodifiableMap(map);
    }

    private static void register(Map<String, LanguageKind> map, LanguageKind kind, String... extensions) {
        for (String extension : extensions) {
            map.put(extension, kind);
        }
    }

    enum GlyphStyle {
        TEXT,
        BRACES,
        ANGLES,
        DOLLAR,
        HASH
    }

    enum LanguageKind {
        PYTHON("Py", GlyphStyle.TEXT, new Color(0x37, 0x76, 0xAB), Color.WHITE),
        JAVA("Ja", GlyphStyle.TEXT, new Color(0xE7, 0x6F, 0x00), Color.WHITE),
        C("C", GlyphStyle.TEXT, new Color(0x00, 0x59, 0x9C), Color.WHITE),
        CPP("++", GlyphStyle.TEXT, new Color(0x00, 0x44, 0x82), Color.WHITE),
        JAVASCRIPT("JS", GlyphStyle.TEXT, new Color(0xF7, 0xDF, 0x1E), new Color(0x33, 0x33, 0x33)),
        TYPESCRIPT("TS", GlyphStyle.TEXT, new Color(0x31, 0x78, 0xC6), Color.WHITE),
        DART("Dt", GlyphStyle.TEXT, new Color(0x01, 0x75, 0xC2), Color.WHITE),
        RUST("Rs", GlyphStyle.TEXT, new Color(0xDE, 0xA5, 0x84), new Color(0x33, 0x22, 0x11)),
        KOTLIN("Kt", GlyphStyle.TEXT, new Color(0x7F, 0x52, 0xFF), Color.WHITE),
        GO("Go", GlyphStyle.TEXT, new Color(0x00, 0xAD, 0xD8), Color.WHITE),
        RUBY("Rb", GlyphStyle.TEXT, new Color(0xCC, 0x34, 0x2D), Color.WHITE),
        SCALA("Sc", GlyphStyle.TEXT, new Color(0xDC, 0x32, 0x2F), Color.WHITE),
        SWIFT("Sw", GlyphStyle.TEXT, new Color(0xF0, 0x51, 0x38), Color.WHITE),
        PHP("Ph", GlyphStyle.TEXT, new Color(0x77, 0x7B, 0xB4), Color.WHITE),
        PERL("Pl", GlyphStyle.TEXT, new Color(0x39, 0x45, 0x7E), Color.WHITE),
        CSHARP("#", GlyphStyle.HASH, new Color(0x68, 0x21, 0x7A), Color.WHITE),
        CSS("{}", GlyphStyle.BRACES, new Color(0x26, 0x4D, 0xE4), Color.WHITE),
        SCSS("Ss", GlyphStyle.TEXT, new Color(0xCD, 0x67, 0x99), Color.WHITE),
        HTML("<>", GlyphStyle.ANGLES, new Color(0xE3, 0x4F, 0x26), Color.WHITE),
        XML("<>", GlyphStyle.ANGLES, new Color(0xFF, 0x66, 0x00), Color.WHITE),
        JSON("{}", GlyphStyle.BRACES, new Color(0x33, 0x33, 0x33), new Color(0xF5, 0xF5, 0xF5)),
        YAML("Ym", GlyphStyle.TEXT, new Color(0xCB, 0x17, 0x1E), Color.WHITE),
        MARKDOWN("Md", GlyphStyle.TEXT, new Color(0x08, 0x3F, 0xA1), Color.WHITE),
        SHELL("$", GlyphStyle.DOLLAR, new Color(0x4E, 0xAA, 0x25), Color.WHITE),
        SQL("SQ", GlyphStyle.TEXT, new Color(0x33, 0x67, 0x91), Color.WHITE),
        LATEX("Tx", GlyphStyle.TEXT, new Color(0x00, 0x80, 0x80), Color.WHITE),
        CLOJURE("Cl", GlyphStyle.TEXT, new Color(0x58, 0x81, 0xD8), Color.WHITE),
        HASKELL("Hs", GlyphStyle.TEXT, new Color(0x5D, 0x4F, 0x85), Color.WHITE),
        LUA("Lu", GlyphStyle.TEXT, new Color(0x00, 0x00, 0x80), Color.WHITE),
        R_LANG("R", GlyphStyle.TEXT, new Color(0x27, 0x6D, 0xC3), Color.WHITE),
        ASM("As", GlyphStyle.TEXT, new Color(0x6E, 0x4C, 0x13), Color.WHITE),
        GENERIC("", GlyphStyle.TEXT, new Color(0x9E, 0x9E, 0x9E), Color.WHITE);

        private final String label;
        private final GlyphStyle style;
        private final Color background;
        private final Color foreground;

        LanguageKind(String label, GlyphStyle style, Color background, Color foreground) {
            this.label = label;
            this.style = style;
            this.background = background;
            this.foreground = foreground;
        }
    }

    static final class CircleLanguageIcon implements Icon {
        private final LanguageKind kind;
        private final String fallbackLabel;
        private final int size;

        CircleLanguageIcon(LanguageKind kind, int size, String extensionFallback) {
            this.kind = kind;
            this.size = size;
            if (kind == LanguageKind.GENERIC && extensionFallback != null && !extensionFallback.isEmpty()) {
                String ext = extensionFallback.toUpperCase(Locale.ROOT);
                this.fallbackLabel = ext.length() <= 2 ? ext : ext.substring(0, 2);
            } else if (kind == LanguageKind.CPP) {
                this.fallbackLabel = "C+";
            } else {
                this.fallbackLabel = kind.label;
            }
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int pad = Math.max(1, size / 10);
                int diameter = size - pad * 2;
                g2.setColor(kind.background);
                g2.fillOval(x + pad, y + pad, diameter, diameter);

                g2.setColor(kind.foreground);
                paintGlyph(g2, x, y, size, pad, diameter);
            } finally {
                g2.dispose();
            }
        }

        private void paintGlyph(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter) {
            switch (kind.style) {
                case BRACES -> paintBraces(g2, x, y, iconSize, pad, diameter);
                case ANGLES -> paintAngles(g2, x, y, iconSize, pad, diameter);
                case DOLLAR -> paintDollar(g2, x, y, iconSize, pad, diameter);
                case HASH -> paintHash(g2, x, y, iconSize, pad, diameter);
                default -> paintText(g2, x, y, iconSize, pad, diameter, fallbackLabel);
            }
        }

        private void paintText(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter, String text) {
            float fontSize = iconSize * (text.length() > 2 ? 0.34f : 0.42f);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
            var fm = g2.getFontMetrics();
            int textX = x + pad + (diameter - fm.stringWidth(text)) / 2;
            int textY = y + pad + (diameter + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(text, textX, textY);
        }

        private void paintBraces(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter) {
            float fontSize = iconSize * 0.5f;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
            paintText(g2, x, y, iconSize, pad, diameter, "{}");
        }

        private void paintAngles(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter) {
            float fontSize = iconSize * 0.42f;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
            paintText(g2, x, y, iconSize, pad, diameter, "<>");
        }

        private void paintDollar(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter) {
            float fontSize = iconSize * 0.55f;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
            paintText(g2, x, y, iconSize, pad, diameter, "$");
        }

        private void paintHash(Graphics2D g2, int x, int y, int iconSize, int pad, int diameter) {
            float fontSize = iconSize * 0.55f;
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
            paintText(g2, x, y, iconSize, pad, diameter, "#");
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static final class LetterCircleIcon implements Icon {
        private final String letter;
        private final Color background;
        private final Color foreground;
        private final int size;

        LetterCircleIcon(String letter, Color background, Color foreground, int size) {
            this.letter = letter;
            this.background = background;
            this.foreground = foreground;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int pad = Math.max(1, size / 10);
                int diameter = size - pad * 2;
                g2.setColor(background);
                g2.fillOval(x + pad, y + pad, diameter, diameter);

                g2.setColor(foreground);
                float fontSize = size * 0.55f;
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, fontSize));
                var fm = g2.getFontMetrics();
                int textX = x + pad + (diameter - fm.stringWidth(letter)) / 2;
                int textY = y + pad + (diameter + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(letter, textX, textY);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static final class WorkspaceFolderIcon implements Icon {
        private static final Color FOLDER = new Color(0xFF, 0xC1, 0x07);
        private static final Color FOLDER_TAB = new Color(0xFF, 0xD5, 0x4F);
        private static final Color OUTLINE = new Color(0xF9, 0xA8, 0x25);

        private final int size;

        WorkspaceFolderIcon(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int bodyX = x + 1;
                int bodyY = y + Math.max(3, size / 4);
                int bodyW = size - 2;
                int bodyH = size - bodyY + y - 1;
                int tabW = Math.max(5, size / 2);
                int tabH = Math.max(3, size / 5);

                g2.setColor(FOLDER_TAB);
                g2.fillRoundRect(bodyX, y + 1, tabW, tabH, 2, 2);
                g2.setColor(FOLDER);
                g2.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 3, 3);

                g2.setStroke(new BasicStroke(Math.max(1f, size / 14f)));
                g2.setColor(OUTLINE);
                Path2D outline = new Path2D.Float();
                outline.moveTo(bodyX, bodyY);
                outline.lineTo(bodyX, y + 1 + tabH);
                outline.lineTo(bodyX + tabW, y + 1 + tabH);
                outline.lineTo(bodyX + tabW, y + 1);
                outline.lineTo(bodyX + tabW - 1, y + 1);
                outline.lineTo(bodyX, bodyY);
                g2.draw(outline);
                g2.drawRoundRect(bodyX, bodyY, bodyW, bodyH, 3, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
