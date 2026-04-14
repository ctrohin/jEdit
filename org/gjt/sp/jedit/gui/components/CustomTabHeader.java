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

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.gui.callbacks.IndexCallback;
import org.gjt.sp.jedit.gui.layout.WrapLayout;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;

public class CustomTabHeader extends JPanel {
    private final SingleSelectionModel selectionModel;
    private final ArrayList<CustomTab> tabs = new ArrayList<>();
    private final IndexCallback onParentSelect;
    private final IndexCallback onTabClose;

    public CustomTabHeader(IndexCallback onSelect, IndexCallback onTabClose) {
        this.selectionModel = new DefaultSingleSelectionModel();
        this.onParentSelect = onSelect;
        this.onTabClose = onTabClose;
        setLayout(new WrapLayout(FlowLayout.LEFT, 4, 2));

        // Repaint when selection changes
        selectionModel.addChangeListener(e -> {
            revalidate();
            repaint();
        });
    }

    public void addTab(String title, boolean selected) {
        int index = tabs.size();
        final var tab = new CustomTab(
            title,
            (t) -> selectParentIndex(tabs.indexOf(t))
        );
        tab.setOnClose(() -> {
            closeBuffer(tabs.indexOf(tab));
//            remove(tab);
//            tabs.remove(tab);
//            revalidate();
        });
        tab.setSelected(selected);
        tabs.add(tab);

        add(tab);

        if (index == 0) selectionModel.setSelectedIndex(0);
        revalidate();
    }


    public void removeTabAt(int index) {
        tabs.remove(index);
        remove(index);
        revalidate();
    }

    public void setTabs(Buffer[] buffers, Buffer selectedBuffer) {
        removeAll();
        tabs.clear();

        for (Buffer buffer : buffers) {
            addTab(buffer.getName(), buffer == selectedBuffer);
        }
        revalidate();
    }

    public void addChangeListener(ChangeListener l) {
        selectionModel.addChangeListener(l);
    }

    public int getSelectedIndex() {
        return selectionModel.getSelectedIndex();
    }

    public void selectParentIndex(final int index) {
        onParentSelect.call(index);
    }

    public void closeBuffer(final int index) {
        onTabClose.call(index);
    }

    public void setSelectedIndex(int index) {
        selectionModel.setSelectedIndex(index);
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setSelected(i == index);
        }
    }
}
