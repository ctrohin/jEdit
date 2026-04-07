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

package org.gjt.sp.jedit.gui.components;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.renderer.SVGRenderingHints;
import com.github.weisj.jsvg.view.ViewBox;
import org.gjt.sp.jedit.GUIUtilities;

import javax.swing.*;
import java.awt.*;

public class SVGIcon implements Icon {
    private final SVGDocument doc;
    private final int width;
    private final int height;
    public SVGIcon(final SVGDocument document, final int width, final int height) {
        this.doc = document;
        this.width = width;
        this.height = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        var g2d = (Graphics2D) g;
        var scaleFactor = GUIUtilities.getScaleFactor(g);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(SVGRenderingHints.KEY_IMAGE_ANTIALIASING, SVGRenderingHints.VALUE_IMAGE_ANTIALIASING_ON);
        var scaledWidth = (int) (width * scaleFactor);
        var scaledHeight = (int) (height * scaleFactor);
        var rX = 0;
        var rY = 0;
        var dif = c.getWidth() - scaledWidth;
        if (dif > 0) {
            rX = dif / 2;
            rY = dif / 2;
        }
        doc.render(null, g2d, new ViewBox(rX, rY, scaledWidth, scaledHeight));
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }
}
