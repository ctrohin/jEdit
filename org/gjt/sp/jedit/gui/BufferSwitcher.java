/*
 * BufferSwitcher.java - Status bar
 * Copyright (C) 2000, 2004 Slava Pestov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 *
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

//{{{ Imports
import javax.swing.event.*;
import javax.accessibility.Accessible;

import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;

import java.awt.*;
import java.io.File;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.formdev.flatlaf.extras.components.FlatTabbedPane;
import io.vavr.control.Try;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.EditBus.EBHandler;
import org.gjt.sp.jedit.icons.WorkspaceFileIcons;
import org.gjt.sp.jedit.bufferset.BufferSet;
import org.gjt.sp.jedit.bufferset.BufferSetManager;
import org.gjt.sp.jedit.gui.components.CustomTabHeader;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.util.Log;
import org.gjt.sp.util.ThreadUtilities;
import org.jedit.git.GitBufferTabLabels;
import org.jedit.git.GitBufferTabStatus;

import static org.gjt.sp.util.StandardUtilities.castUnchecked;
//}}}

/** BufferSwitcher class
   @version $Id$
*/
public class BufferSwitcher extends JComboBox<Buffer>
{
	// private members
	private final EditPane editPane;
	private boolean updating;
	// item that was selected before popup menu was opened
	private Object itemSelectedBefore;
	public static final DataFlavor BufferDataFlavor = new DataFlavor(BufferTransferableData.class, DataFlavor.javaJVMLocalObjectMimeType);

	// actual colors will be set in constructor, here are just fallback values
	static Color defaultColor   = Color.BLACK;
	static Color defaultBGColor = Color.LIGHT_GRAY;
//	private final CustomTabHeader tabs;
	private final FlatTabbedPane tabs;
	private JPanel fullPane;
	private boolean tabsListenerEnabled = true;

	public BufferSwitcher(final EditPane editPane)
	{
		this.editPane = editPane;
		tabs = new FlatTabbedPane();
		tabs.setHasFullBorder(true);
		tabs.setScrollButtonsPlacement(FlatTabbedPane.ScrollButtonsPlacement.trailing);
		tabs.setShowTabSeparators(true);
		tabs.setShowContentSeparators(true);
		tabs.setTabsClosable(true);
		tabs.setTabLayoutPolicy(FlatTabbedPane.SCROLL_TAB_LAYOUT);
		tabs.setTabCloseCallback((pane, idx) -> {
			jEdit.closeBuffer(editPane, getSortedBuffers()[idx]);
		});
		tabs.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				showTabPopupMenu(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showTabPopupMenu(e);
			}
		});
		tabs.addChangeListener(e -> {
			if (!tabsListenerEnabled) {
				return;
			}
			// Get the index of the newly selected tab
			int selectedIndex = tabs.getSelectedIndex();
			var sortedBuffers = getSortedBuffers();
			if (selectedIndex != -1 && selectedIndex < sortedBuffers.length) {
				editPane.setBuffer(sortedBuffers[selectedIndex]);
			}
		});
		setTransferHandler(new ComboBoxTransferHandler(this));
		setRenderer(new BufferCellRenderer());
		setMaximumRowCount(jEdit.getIntegerProperty("bufferSwitcher.maxRowCount", 10));
		addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(
				PopupMenuEvent e)
			{
				itemSelectedBefore = getSelectedItem();
			}

			@Override
			public void popupMenuWillBecomeInvisible(
				PopupMenuEvent e)
			{
				if (!updating)
				{
					Buffer buffer = (Buffer)getSelectedItem();
					if(buffer != null)
						editPane.setBuffer(buffer);
				}
				editPane.getTextArea().requestFocus();
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
				setSelectedItem(itemSelectedBefore);
			}
		});

		addItemListener(evt ->
		{
			if (evt.getStateChange() == ItemEvent.SELECTED)
			{
				Buffer buffer = (Buffer) evt.getItem();
				updateStyle(buffer);
				var selectedIndex = getSelectedIndex();
				if (selectedIndex != -1 && selectedIndex < tabs.getTabCount()) {
					tabs.setSelectedIndex(selectedIndex);
				}
			}
		});
		defaultColor   = getForeground();
		defaultBGColor = getBackground();

		GitBufferTabStatus.getInstance().addListener(this::updateTabTitles);
		updateStyle(editPane.getBuffer());
	}

	public void updateLayout() {
		if (fullPane == null) {
			return;
		}
		if (jEdit.getBooleanProperty("view.showBufferSwitcher")) {
			fullPane.add(this, BorderLayout.CENTER);
		} else {
			fullPane.remove(this);
		}
		if (jEdit.getBooleanProperty("view.showBufferTabs")) {
			fullPane.add(tabs, BorderLayout.NORTH);
		} else {
			fullPane.remove(tabs);
		}
	}
	public JComponent getFullPane() {
		if (fullPane != null) {
			return fullPane;
		}
		fullPane = new JPanel(new BorderLayout());
		if (jEdit.getBooleanProperty("view.showBufferSwitcher")) {
			fullPane.add(this, BorderLayout.CENTER);
		}
		if (jEdit.getBooleanProperty("view.showBufferTabs")) {
			fullPane.add(tabs, BorderLayout.NORTH);
		}
		return fullPane;
	}

	static void updateStyle(JComponent target, boolean isBackup, String path)
	{
		String styleName = isBackup ? "backup" : "normal";

		switch (styleName)
		{
			case "backup":
				target.setForeground(new Color(230,207,93));
				break;

			case "normal":
			default:
				target.setForeground(defaultColor);
				break;
		}

		target.setToolTipText(path != null ? makeToolTipText(path, isBackup) : null);
	}

	static String makeToolTipText(String path, boolean isBackup)
	{
		String text = path;

		if (isBackup) text = String.format("(backup file?) %s", text);

		return text;
	}

	public void updateStyle(Buffer buffer)
	{
		String path = buffer.getPath();
		boolean isBackup = buffer.isBackup();

		updateStyle(this, isBackup, path);
	}

	public void updateBufferList()
	{
		// if the buffer count becomes 0, then it is guaranteed to
		// become 1 very soon, so don't do anything in that case.
		final BufferSet bufferSet = editPane.getBufferSet();
		if(bufferSet.size() == 0)
			return;

		Runnable runnable = () ->
		{
			updating = true;
			setMaximumRowCount(jEdit.getIntegerProperty("bufferSwitcher.maxRowCount",10));
			Buffer[] buffers = getSortedBuffers();
			setModel(new DefaultComboBoxModel<>(buffers));
			buildTabs(buffers);
			GitBufferTabStatus.getInstance().requestRefresh(buffers);
			// FIXME: editPane.getBuffer() returns wrong buffer (old buffer) after last non-untitled buffer close.
			// When the only non-untitled (last) buffer is closed a new untitled buffer is added to BufferSet
			// directly from BufferSetManager (@see BufferSetManager.bufferRemoved() and BufferSetManager.addBuffer())
			// This triggers EditPane.bufferAdded() -> bufferSwitcher.updateBufferList() bypassing setting EditPane's
			// buffer object reference to a new created untitled buffer.
			// This is why here editPane.getBuffer() returns wrong previous already closed buffer in that case.
			setSelectedItem(editPane.getBuffer());
			addDnD();
			updating = false;
		};
		ThreadUtilities.runInDispatchThread(runnable);
	}


	private void buildTabs(final Buffer[] buffers) {
		tabsListenerEnabled = false;
		tabs.removeAll();
		for (Buffer buffer : buffers) {
			tabs.addTab(GitBufferTabLabels.formatTabTitle(buffer), tabIcon(buffer), null,
				GitBufferTabLabels.formatTabToolTip(buffer));
		}
		var idx = 0;
		for (int i = 0; i < buffers.length; i++) {
			if (buffers[i] == editPane.getBuffer()) {
				idx = i;
				break;
			}
		}
		tabsListenerEnabled = true;
		tabs.setSelectedIndex(idx);
	}

	public void updateTabTitles() {
		Runnable runnable = () ->
		{
			if (tabs.getTabCount() == 0) {
				return;
			}
			Buffer[] buffers = getSortedBuffers();
			int count = Math.min(buffers.length, tabs.getTabCount());
			for (int i = 0; i < count; i++) {
				Buffer buffer = buffers[i];
				tabs.setTitleAt(i, GitBufferTabLabels.formatTabTitle(buffer));
				tabs.setToolTipTextAt(i, GitBufferTabLabels.formatTabToolTip(buffer));
			}
		};
		ThreadUtilities.runInDispatchThread(runnable);
	}

	private static Icon tabIcon(Buffer buffer) {
		String path = buffer.getPath();
		File file = (path != null && !path.isEmpty())
			? new File(path)
			: new File(buffer.getName());
		return WorkspaceFileIcons.getIcon(file);
	}

	private Buffer[] getSortedBuffers() {
		final BufferSet bufferSet = editPane.getBufferSet();
		Buffer[] buffers = bufferSet.getAllBuffers();
		if (jEdit.getBooleanProperty("bufferswitcher.sortBuffers", true))
		{
			Arrays.sort(buffers, (a, b) ->
			{
				if (jEdit.getBooleanProperty("bufferswitcher.sortByName", true))
					return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
				else
					return a.getPath().toLowerCase().compareTo(b.getPath().toLowerCase());
			});
		}
		return buffers;
	}

	private void showTabPopupMenu(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		int tabIndex = tabs.indexAtLocation(e.getX(), e.getY());
		if (tabIndex < 0) {
			return;
		}
		Buffer[] buffers = getSortedBuffers();
		if (tabIndex >= buffers.length) {
			return;
		}
		tabs.setSelectedIndex(tabIndex);
		editPane.setBuffer(buffers[tabIndex]);
		buildTabPopupMenu(tabIndex, buffers.length).show(tabs, e.getX(), e.getY());
	}

	private JPopupMenu buildTabPopupMenu(int tabIndex, int tabCount) {
		JPopupMenu menu = new JPopupMenu();
		menu.add(createTabMenuItem("buffer-tabs.close", () -> closeBufferAtTab(tabIndex)));
		menu.add(createTabMenuItem("buffer-tabs.close-all", this::closeAllTabs));
		menu.addSeparator();
		JMenuItem closeRight = createTabMenuItem("buffer-tabs.close-right",
			() -> closeTabsToRight(tabIndex));
		closeRight.setEnabled(tabIndex < tabCount - 1);
		menu.add(closeRight);
		JMenuItem closeLeft = createTabMenuItem("buffer-tabs.close-left",
			() -> closeTabsToLeft(tabIndex));
		closeLeft.setEnabled(tabIndex > 0);
		menu.add(closeLeft);
		JMenuItem closeOthers = createTabMenuItem("buffer-tabs.close-others",
			() -> closeOtherTabs(tabIndex));
		closeOthers.setEnabled(tabCount > 1);
		menu.add(closeOthers);
		menu.addSeparator();
		menu.add(createTabMenuItem("buffer-tabs.close-unmodified", this::closeUnmodifiedTabs));
		return menu;
	}

	private JMenuItem createTabMenuItem(String labelProperty, Runnable action) {
		JMenuItem item = new JMenuItem(jEdit.getProperty(labelProperty));
		item.addActionListener(evt -> action.run());
		return item;
	}

	private void closeBufferAtTab(int tabIndex) {
		Buffer[] buffers = getSortedBuffers();
		if (tabIndex >= 0 && tabIndex < buffers.length) {
			jEdit.closeBuffer(editPane, buffers[tabIndex]);
		}
	}

	private void closeAllTabs() {
		if (!confirmCloseAll("closeall")) {
			return;
		}
		closeBuffers(List.of(getSortedBuffers()));
	}

	private void closeTabsToRight(int tabIndex) {
		Buffer[] buffers = getSortedBuffers();
		List<Buffer> toClose = new ArrayList<>();
		for (int i = tabIndex + 1; i < buffers.length; i++) {
			toClose.add(buffers[i]);
		}
		closeBuffers(toClose);
	}

	private void closeTabsToLeft(int tabIndex) {
		Buffer[] buffers = getSortedBuffers();
		List<Buffer> toClose = new ArrayList<>();
		for (int i = 0; i < tabIndex; i++) {
			toClose.add(buffers[i]);
		}
		closeBuffers(toClose);
	}

	private void closeOtherTabs(int tabIndex) {
		if (!confirmCloseAll("closeothers")) {
			return;
		}
		Buffer[] buffers = getSortedBuffers();
		if (tabIndex < 0 || tabIndex >= buffers.length) {
			return;
		}
		Buffer keep = buffers[tabIndex];
		List<Buffer> toClose = new ArrayList<>();
		for (Buffer buffer : buffers) {
			if (buffer != keep) {
				toClose.add(buffer);
			}
		}
		closeBuffers(toClose);
	}

	private void closeUnmodifiedTabs() {
		List<Buffer> toClose = new ArrayList<>();
		for (Buffer buffer : getSortedBuffers()) {
			if (!buffer.isDirty()) {
				toClose.add(buffer);
			}
		}
		closeBuffers(toClose);
	}

	private boolean confirmCloseAll(String messageProperty) {
		if (!jEdit.getBooleanProperty("closeAllConfirm")) {
			return true;
		}
		int answer = GUIUtilities.confirm(
			editPane.getView(),
			messageProperty,
			null,
			JOptionPane.YES_NO_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		return answer == JOptionPane.YES_OPTION;
	}

	private void closeBuffers(List<Buffer> buffers) {
		for (Buffer buffer : buffers) {
			jEdit.closeBuffer(editPane, buffer);
		}
	}

	@EBHandler
	public void handlePropertiesChanged(PropertiesChanged msg)
	{
		updateBufferList();
	}

	static class BufferCellRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(
			JList list, Object value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			Buffer buffer = (Buffer)value;
			var isBackup = buffer != null && buffer.isBackup();
			var path = buffer == null ? null : buffer.getPath();
			var icon = buffer == null ? null : buffer.getIcon();

			setIcon(icon);
			updateStyle(this, isBackup, path);
			return this;
		}
	}

	private void addDnD()
	{
		Optional.of(getUI())
			.filter(BasicComboBoxUI.class::isInstance)
			.map(ui -> ui.getAccessibleChild(null, 0))
			.filter(BasicComboPopup.class::isInstance)
			.map(BasicComboPopup.class::cast)
			.map(BasicComboPopup::getList)
			.ifPresent(list -> {
				list.setDragEnabled(true);
				list.setDropMode(DropMode.INSERT);
				list.setTransferHandler(new BufferSwitcherTransferHandler());
			});
	}

	private static class ComboBoxTransferHandler extends TransferHandler
	{
		private final JComboBox<?> comboBox;

		ComboBoxTransferHandler(JComboBox<?> comboBox)
		{
			this.comboBox = comboBox;
		}

		@Override
		public boolean canImport(TransferHandler.TransferSupport info)
		{
			// we only import Strings
			if (!info.isDataFlavorSupported(BufferSwitcher.BufferDataFlavor))
			{
				return false;
			}
			if (!comboBox.isPopupVisible())
			{
				comboBox.showPopup();
			}
			return false;
		}
	}

	private class BufferSwitcherTransferable implements Transferable
	{
		private final DataFlavor[] supportedDataFlavor = { BufferSwitcher.BufferDataFlavor };
		private final Buffer buffer;
		private final JComponent source;

		BufferSwitcherTransferable(Buffer buffer, JComponent source)
		{
			this.buffer = buffer;
			this.source = source;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return Arrays.copyOf(supportedDataFlavor, supportedDataFlavor.length);
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return BufferSwitcher.BufferDataFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor)
				throws UnsupportedFlavorException, IOException
		{
			if (!isDataFlavorSupported(flavor))
				throw new UnsupportedFlavorException(flavor);
			return new BufferTransferableData(buffer, source);
		}
	}

	private record BufferTransferableData(Buffer buffer, JComponent source) {
	}

	private class BufferSwitcherTransferHandler extends TransferHandler
	{
		@Override
		public boolean canImport(TransferSupport support)
		{

			if (!support.isDataFlavorSupported(BufferSwitcher.BufferDataFlavor))
			{
				return false;
			}

			JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
			if (dl.getIndex() == -1)
			{
				return false;
			}

			Transferable t = support.getTransferable();
			BufferTransferableData data;
			try
			{
				data = (BufferTransferableData) t
						.getTransferData(BufferSwitcher.BufferDataFlavor);
			}
			catch (UnsupportedFlavorException | IOException e)
			{
				return false;
			}
			JComponent target = (JComponent) support.getComponent();
			EditPane sourceEditPane = (EditPane) GUIUtilities.getComponentParent(
					data.source(), EditPane.class);
			EditPane targetEditPane = (EditPane) GUIUtilities.getComponentParent(
					target, EditPane.class);
			BufferSet.Scope scope = jEdit.getBufferSetManager().getScope();
			View sourceView = sourceEditPane.getView();
			View targetView = targetEditPane.getView();
			switch (scope)
			{
				case editpane:
				{
					return sourceEditPane != targetEditPane;
				}
				case view:
				{
					return sourceView != targetView;
				}
				case global:
					return false;
			}

			return false;
		}

		@Override
		public boolean importData(TransferSupport support)
		{
			if (!support.isDrop())
			{
				return false;
			}

			Transferable t = support.getTransferable();
			BufferTransferableData data;
			try
			{
				data = (BufferTransferableData) t
						.getTransferData(BufferSwitcher.BufferDataFlavor);
			}
			catch (UnsupportedFlavorException | IOException e)
			{
				return false;
			}
			JComponent target = (JComponent) support.getComponent();
			EditPane targetEditPane = (EditPane) GUIUtilities.getComponentParent(
					target, EditPane.class);

			Buffer buffer = data.buffer();

			View view = targetEditPane.getView();

			BufferSetManager bufferSetManager = jEdit.getBufferSetManager();
			if (buffer != null)
			{
				bufferSetManager.addBuffer(targetEditPane, buffer);
				targetEditPane.setBuffer(buffer);
			}
			view.toFront();
			view.requestFocus();
			targetEditPane.requestFocus();

			return true;
		}

		@Override
		public int getSourceActions(JComponent c)
		{
			return COPY_OR_MOVE;
		}

		@Override
		public void exportDone(JComponent c, Transferable t, int action)
		{
			if (action == MOVE)
			{
				Try.of(() -> t.getTransferData(BufferSwitcher.BufferDataFlavor))
					.map(BufferTransferableData.class::cast)
					.map(BufferTransferableData::buffer)
					.toJavaOptional()
					.ifPresent(buffer -> {
						EditPane editPane = (EditPane) GUIUtilities.getComponentParent(c,
							EditPane.class);
						BufferSetManager bufferSetManager = jEdit.getBufferSetManager();
						bufferSetManager.removeBuffer(editPane, buffer);
					});

			}
		}

		@Override
		public Transferable createTransferable(JComponent c)
		{
			return Optional.of(c)
				.map(JList.class::cast)
				.map(JList::getSelectedValue)
				.map(Buffer.class::cast)
				.map(b -> new BufferSwitcherTransferable(b, c))
				.orElse(null);
		}
	}
}

// :noTabs=false:
