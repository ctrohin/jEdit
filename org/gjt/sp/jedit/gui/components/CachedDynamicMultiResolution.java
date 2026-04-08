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

import jiconfont.IconCode;
import jiconfont.icons.GoogleMaterialDesignIcons;
import jiconfont.swing.IconFontSwing;

import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class CachedDynamicMultiResolution extends BaseMultiResolutionImage {
    private final IconCode icon;
    private final Map<Float, Image> cache = new Hashtable<>();
    private final List<Image> variants = new ArrayList<>();

    public CachedDynamicMultiResolution(IconCode icon, final int baseSize) {
        super(IconFontSwing.buildImage(icon, baseSize, Color.gray));
        this.icon = icon;
    }

    @Override
    public Image getResolutionVariant(double destImageWidth, double destImageHeight) {
        float size = (float) Math.min(destImageHeight, destImageWidth);
        if (cache.containsKey(size)) {
            return cache.get(size);
        }
        var newIcon = IconFontSwing.buildImage(icon, size, Color.gray);
        cache.put(size, newIcon);
        return newIcon;
    }

    @Override
    public List<Image> getResolutionVariants() {
        return this.variants;
    }
}
