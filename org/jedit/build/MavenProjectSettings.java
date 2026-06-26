/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
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

/**
 * Per-project Maven runner settings (similar to IntelliJ IDEA Maven import/runner options).
 */
final class MavenProjectSettings {

    String mavenHome = "";
    String settingsFile = "";
    String localRepository = "";
    String jdkHome = "";
    String mavenExecutable = "";
    boolean useWrapper = true;
    String activeProfiles = "";
    boolean offline;
    boolean skipTests;
    String mavenOpts = "";
    String additionalArgs = "";

    MavenProjectSettings copy() {
        MavenProjectSettings copy = new MavenProjectSettings();
        copy.mavenHome = mavenHome;
        copy.settingsFile = settingsFile;
        copy.localRepository = localRepository;
        copy.jdkHome = jdkHome;
        copy.mavenExecutable = mavenExecutable;
        copy.useWrapper = useWrapper;
        copy.activeProfiles = activeProfiles;
        copy.offline = offline;
        copy.skipTests = skipTests;
        copy.mavenOpts = mavenOpts;
        copy.additionalArgs = additionalArgs;
        return copy;
    }
}
