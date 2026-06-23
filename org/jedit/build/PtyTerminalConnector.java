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

package org.jedit.build;

import java.nio.charset.Charset;
import java.util.List;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

final class PtyTerminalConnector extends ProcessTtyConnector {

    private final PtyProcess ptyProcess;

    PtyTerminalConnector(PtyProcess process, Charset charset, List<String> commandLine) {
        super(process, charset, commandLine);
        this.ptyProcess = process;
    }

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public void resize(TermSize termSize) {
        if (ptyProcess.isAlive()) {
            ptyProcess.setWinSize(new WinSize(termSize.getColumns(), termSize.getRows()));
        }
        super.resize(termSize);
    }
}
