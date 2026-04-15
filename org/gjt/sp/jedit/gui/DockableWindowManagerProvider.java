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

import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.View.ViewConfig;
import org.gjt.sp.jedit.gui.DockableWindowManager.DockingLayout;

/** jEdit's classic dockable window manager, turned into a "provider" service.
   *
   *  @author Shlomy Reinstein
   *  @since jEdit 4.3pre16
   */
public class DockableWindowManagerProvider implements DockingFrameworkProvider
{
	@Override
	public DockableWindowManager create(View view, DockableWindowFactory instance, ViewConfig config)
	{
		return new DockableWindowManagerImpl(view, instance, config);
	}

	@Override
	public DockingLayout createDockingLayout()
	{
		return new DockableWindowManagerImpl.DockableWindowConfig();
	}
}
