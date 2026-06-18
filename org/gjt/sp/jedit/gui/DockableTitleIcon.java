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

package org.gjt.sp.jedit.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Dock button icon: dockable title plus an optional warning/error count badge.
 */
final class DockableTitleIcon implements Icon {

    private static final int BADGE_GAP = 3;
    private static final int BADGE_PAD = 4;
    private static final int BADGE_MIN = 12;
    private static final int BADGE_MAX = 22;

    private final PanelWindowContainer.RotatedTextIcon titleIcon;
    private final int rotate;
    private final String badgeText;
    private final Color badgeColor;
    private final int badgeWidth;
    private final int badgeHeight;

    static Icon create(int rotate, Font font, String title,
                       int errorCount, int warningCount) {
        int errors = Math.max(0, errorCount);
        int warnings = Math.max(0, warningCount);
        if (errors == 0 && warnings == 0) {
            return new PanelWindowContainer.RotatedTextIcon(rotate, font, title);
        }
        String badgeText;
        Color badgeColor;
        if (errors > 0) {
            badgeText = formatCount(errors);
            badgeColor = notificationErrorColor();
        } else {
            badgeText = formatCount(warnings);
            badgeColor = notificationWarningColor();
        }
        return new DockableTitleIcon(rotate, font, title, badgeText, badgeColor);
    }

    private DockableTitleIcon(int rotate, Font font, String title,
                              String badgeText, Color badgeColor) {
        this.rotate = rotate;
        this.titleIcon = new PanelWindowContainer.RotatedTextIcon(rotate, font, title);
        this.badgeText = badgeText;
        this.badgeColor = badgeColor;
        Font badgeFont = badgeFont(font);
        FontMetrics fm = metrics(badgeFont);
        int textWidth = fm.stringWidth(badgeText);
        badgeWidth = Math.max(BADGE_MIN, Math.min(BADGE_MAX, textWidth + BADGE_PAD));
        badgeHeight = Math.max(BADGE_MIN, fm.getHeight());
    }

    @Override
    public int getIconWidth() {
        if (badgeText == null) {
            return titleIcon.getIconWidth();
        }
        return switch (rotate) {
            case PanelWindowContainer.RotatedTextIcon.NONE ->
                titleIcon.getIconWidth() + BADGE_GAP + badgeWidth;
            case PanelWindowContainer.RotatedTextIcon.CW,
                 PanelWindowContainer.RotatedTextIcon.CCW ->
                Math.max(titleIcon.getIconWidth(), badgeWidth);
            default -> titleIcon.getIconWidth();
        };
    }

    @Override
    public int getIconHeight() {
        if (badgeText == null) {
            return titleIcon.getIconHeight();
        }
        return switch (rotate) {
            case PanelWindowContainer.RotatedTextIcon.NONE ->
                Math.max(titleIcon.getIconHeight(), badgeHeight);
            case PanelWindowContainer.RotatedTextIcon.CW,
                 PanelWindowContainer.RotatedTextIcon.CCW ->
                titleIcon.getIconHeight() + BADGE_GAP + badgeHeight;
            default -> titleIcon.getIconHeight();
        };
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        titleIcon.paintIcon(c, g, x, y);
        if (badgeText == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
            Font badgeFont = badgeFont(c.getFont());
            g2.setFont(badgeFont);
            FontMetrics fm = g2.getFontMetrics();
            int badgeX;
            int badgeY;
            switch (rotate) {
                case PanelWindowContainer.RotatedTextIcon.CW,
                     PanelWindowContainer.RotatedTextIcon.CCW -> {
                    badgeX = x + (titleIcon.getIconWidth() - badgeWidth) / 2;
                    badgeY = y + titleIcon.getIconHeight() + BADGE_GAP;
                }
                default -> {
                    badgeX = x + titleIcon.getIconWidth() + BADGE_GAP;
                    badgeY = y + (titleIcon.getIconHeight() - badgeHeight) / 2;
                }
            }
            g2.setColor(badgeColor);
            g2.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight,
                badgeHeight, badgeHeight);
            g2.setColor(Color.WHITE);
            int textX = badgeX + (badgeWidth - fm.stringWidth(badgeText)) / 2;
            int textY = badgeY + (badgeHeight + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(badgeText, textX, textY);
        } finally {
            g2.dispose();
        }
    }

    private static String formatCount(int count) {
        return count > 99 ? "99+" : Integer.toString(count);
    }

    private static Font badgeFont(Font base) {
        return base.deriveFont(Font.BOLD, Math.max(9f, base.getSize2D() - 3f));
    }

    private static FontMetrics metrics(Font font) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = scratch.getGraphics();
        try {
            return g.getFontMetrics(font);
        } finally {
            g.dispose();
        }
    }

    static Color notificationErrorColor() {
        Color error = UIManager.getColor("Component.error.focusedBorderColor");
        return error != null ? error : new Color(0xD32F2F);
    }

    static Color notificationWarningColor() {
        return new Color(0xF57C00);
    }
}
