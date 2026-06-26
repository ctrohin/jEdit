/*
 * TestGutter.java
 * :tabSize=4:indentSize=4:noTabs=false:
 *
 * Copyright (C) 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 */

package org.gjt.sp.jedit.textarea;

import java.awt.*;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.MouseInputAdapter;

import org.gjt.sp.util.Log;

/**
 * Test runner run icons displayed in a column to the right of the blame gutter.
 *
 * @since jEdit 6.0
 */
public class TestGutter extends JComponent {
    public static final int DEFAULT_LAYER = 0;
    public static final int DEFAULT_COLUMN_WIDTH = 16;

    private static final Dimension DISABLED_SIZE = new Dimension(0, 0);

    private final TextArea textArea;
    private final ExtensionManager extensionMgr;

    private boolean enabled;
    private int columnWidth;
    private Dimension gutterSize = new Dimension();

    private int borderWidth;
    private Border focusBorder;
    private Border noFocusBorder;

    private final MouseInputAdapter toolTipMouseHandler = new MouseInputAdapter() {
        private int toolTipInitialDelay;
        private int toolTipReshowDelay;

        @Override
        public void mouseEntered(MouseEvent e) {
            ToolTipManager ttm = ToolTipManager.sharedInstance();
            toolTipInitialDelay = ttm.getInitialDelay();
            toolTipReshowDelay = ttm.getReshowDelay();
            ttm.setInitialDelay(0);
            ttm.setReshowDelay(0);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            ToolTipManager ttm = ToolTipManager.sharedInstance();
            ttm.setInitialDelay(toolTipInitialDelay);
            ttm.setReshowDelay(toolTipReshowDelay);
        }
    };

    public TestGutter(TextArea textArea) {
        this.textArea = textArea;
        extensionMgr = new ExtensionManager();

        setAutoscrolls(true);
        setOpaque(true);
        setRequestFocusEnabled(false);
        addMouseListener(toolTipMouseHandler);
        addMouseMotionListener(toolTipMouseHandler);
        recalculateDimensions();
    }

    @Override
    public void paintComponent(Graphics _gfx) {
        Graphics2D gfx = (Graphics2D) _gfx;
        gfx.setRenderingHints(textArea.getPainter().renderingHints);

        Rectangle clip = gfx.getClipBounds();
        gfx.setColor(getBackground());
        gfx.fillRect(clip.x, clip.y, clip.width, clip.height);

        if (!enabled || textArea.getBuffer().isLoading()) {
            return;
        }

        int lineHeight = textArea.getPainter().getLineHeight();
        if (lineHeight == 0) {
            return;
        }

        int firstLine = clip.y / lineHeight;
        int lastLine = (clip.y + clip.height - 1) / lineHeight;

        if (lastLine - firstLine > textArea.getVisibleLines()) {
            Log.log(Log.ERROR, this, "BUG: firstLine=" + firstLine);
            Log.log(Log.ERROR, this, "     lastLine=" + lastLine);
            Log.log(Log.ERROR, this, "     visibleLines=" + textArea.getVisibleLines());
        }

        int y = clip.y - clip.y % lineHeight
            + textArea.getPainter().getLineExtraSpacing();
        extensionMgr.paintScreenLineRange(textArea, gfx,
            firstLine, lastLine, y, lineHeight,
            Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public void addExtension(TextAreaExtension extension) {
        extensionMgr.addExtension(DEFAULT_LAYER, extension);
        repaint();
    }

    public void removeExtension(TextAreaExtension extension) {
        extensionMgr.removeExtension(extension);
        repaint();
    }

    public TextAreaExtension[] getExtensions() {
        return extensionMgr.getExtensions();
    }

    @Override
    public String getToolTipText(MouseEvent evt) {
        if (!enabled || textArea.getBuffer().isLoading()) {
            return null;
        }
        return extensionMgr.getToolTipText(evt.getX(), evt.getY());
    }

    public void setBorder(int width, Color color1, Color color2, Color color3) {
        borderWidth = width;
        focusBorder = new CompoundBorder(
            new MatteBorder(0, 0, 0, width, color3),
            new MatteBorder(0, 0, 0, width, color1));
        noFocusBorder = new CompoundBorder(
            new MatteBorder(0, 0, 0, width, color3),
            new MatteBorder(0, 0, 0, width, color2));
        updateBorder();
    }

    public void updateBorder() {
        if (borderWidth <= 0) {
            setBorder((Border) null);
            return;
        }
        if (textArea.hasFocus()) {
            setBorder(focusBorder);
        } else {
            setBorder(noFocusBorder);
        }
    }

    @Override
    public void setBorder(Border border) {
        super.setBorder(border);
        recalculateDimensions();
        revalidate();
    }

    public boolean isTestEnabled() {
        return enabled;
    }

    public void setTestEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        recalculateDimensions();
        revalidate();
        textArea.revalidate();
        repaint();
    }

    public int getColumnWidth() {
        return columnWidth;
    }

    public void setColumnWidth(int width) {
        if (width < 0) {
            width = 0;
        }
        if (columnWidth == width) {
            return;
        }
        columnWidth = width;
        if (enabled) {
            recalculateDimensions();
            revalidate();
            textArea.revalidate();
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        if (!enabled) {
            return DISABLED_SIZE;
        }
        return gutterSize;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    private void recalculateDimensions() {
        Border border = getBorder();
        int borderInsets = 0;
        int borderHeight = 0;
        if (border != null) {
            Insets insets = border.getBorderInsets(this);
            borderInsets = insets.right;
            borderHeight = insets.top + insets.bottom;
        }
        gutterSize.width = enabled ? columnWidth + borderInsets : 0;
        gutterSize.height = borderHeight;
    }
}
