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

package org.gjt.sp.jedit.gui.borders;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;


public class RoundedBorder extends AbstractBorder {
    private final Color lineColor;
    private final int thickness;
    private final int arcWidth;
    private final int arcHeight;

    public RoundedBorder(final Color color, final int thickness, final int arcWidth, final int arcHeight) {
        this.lineColor = color;
        this.thickness = thickness;
        this.arcWidth = arcWidth;
        this.arcHeight = arcHeight;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
    }

    public void paintBorder1(Component c, Graphics g, int x, int y, int w, int h) {
        // Step 1: Reset Transform
        AffineTransform at = null;
        Stroke oldStroke = null;
        boolean resetTransform = false;
        double scaleFactor = 1;

        int xtranslation = x;
        int ytranslation = y;
        int width = w;
        int height = h;

        if (g instanceof Graphics2D) {
            Graphics2D g2d = (Graphics2D) g;
            at = g2d.getTransform();
            oldStroke = g2d.getStroke();
            scaleFactor = Math.min(at.getScaleX(), at.getScaleY());

            // if m01 or m10 is non-zero, then there is a rotation or shear,
            // or if scale=1, skip resetting the transform in these cases.
            resetTransform = ((at.getShearX() == 0) && (at.getShearY() == 0))
                && ((at.getScaleX() > 1) || (at.getScaleY() > 1));

            if (resetTransform) {
                /* Deactivate the HiDPI scaling transform,
                 * so we can do paint operations in the device
                 * pixel coordinate system instead of the logical coordinate system.
                 */
                g2d.setTransform(new AffineTransform());
                double xx = at.getScaleX() * x + at.getTranslateX();
                double yy = at.getScaleY() * y + at.getTranslateY();
                xtranslation = clipRound(xx);
                ytranslation = clipRound(yy);
                width = clipRound(at.getScaleX() * w + xx) - xtranslation;
                height = clipRound(at.getScaleY() * h + yy) - ytranslation;
            }
        }

        g.translate(xtranslation, ytranslation);

        // Step 2: Call respective paintBorder with transformed values
        paintUnscaledBorder(c, g, width, height, scaleFactor);

        // Step 3: Restore previous stroke & transform
        g.translate(-xtranslation, -ytranslation);
        if (g instanceof Graphics2D) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setStroke(oldStroke);
            if (resetTransform) {
                g2d.setTransform(at);
            }
        }
    }

    private  void paintUnscaledBorder(Component c, Graphics g, int width, int height, double scaleFactor) {
        Graphics2D g2d = (Graphics2D) g;

        Color oldColor = g2d.getColor();
        g2d.setColor(this.lineColor);

        Shape outer;
        Shape inner;

        int offs = this.thickness * (int) scaleFactor;
        int size = offs + offs;
        float arc = .5f * offs;
        outer = new RoundRectangle2D.Float(0, 0, width, height, offs, offs);
        inner = new RoundRectangle2D.Float(offs, offs, width - size, height - size, arc, arc);

        Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        path.append(outer, false);
        path.append(inner, false);
        g2d.fill(path);

        g2d.setColor(oldColor);
    }

    public static int clipRound(final double coordinate) {
        final double newv = coordinate - 0.5;
        if (newv < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        if (newv > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(newv);
    }
}
