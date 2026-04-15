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

/** An interface for notifying MOVABLE dockable windows before their docking position is changed.
 *
 * @author Shlomy Reinstein
 * @version $Id$
 * @since jEdit 4.3pre11
 */

public interface DockableWindow {
	//{{{ Move notification
	/**
	 * Notifies a dockable window before its docking position is changed.
	 * @param newPosition The docking position to which the window is moving.
	 * @since jEdit 4.3pre11
	 */
	void move(String newPosition);
	//}}}
}
