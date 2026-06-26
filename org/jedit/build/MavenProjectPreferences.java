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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

final class MavenProjectPreferences {

    private static final String KEY_FOLDER = "project.folder";
    private static final String KEY_MAVEN_HOME = "maven.home";
    private static final String KEY_SETTINGS_FILE = "settings.file";
    private static final String KEY_LOCAL_REPO = "local.repository";
    private static final String KEY_JDK_HOME = "jdk.home";
    private static final String KEY_EXECUTABLE = "maven.executable";
    private static final String KEY_USE_WRAPPER = "use.wrapper";
    private static final String KEY_PROFILES = "active.profiles";
    private static final String KEY_OFFLINE = "offline";
    private static final String KEY_SKIP_TESTS = "skip.tests";
    private static final String KEY_MAVEN_OPTS = "maven.opts";
    private static final String KEY_ADDITIONAL_ARGS = "additional.args";

    private MavenProjectPreferences() {}

    static MavenProjectSettings load(File projectRoot) {
        MavenProjectSettings settings = new MavenProjectSettings();
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return settings;
        }
        File file = settingsFile(canonical);
        if (!file.isFile()) {
            return settings;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            Log.log(Log.WARNING, MavenProjectPreferences.class,
                "Could not read " + file, ex);
            return settings;
        }
        if (!canonical.equals(props.getProperty(KEY_FOLDER, "").trim())) {
            return settings;
        }
        settings.mavenHome = props.getProperty(KEY_MAVEN_HOME, "");
        settings.settingsFile = props.getProperty(KEY_SETTINGS_FILE, "");
        settings.localRepository = props.getProperty(KEY_LOCAL_REPO, "");
        settings.jdkHome = props.getProperty(KEY_JDK_HOME, "");
        settings.mavenExecutable = props.getProperty(KEY_EXECUTABLE, "");
        settings.useWrapper = Boolean.parseBoolean(
            props.getProperty(KEY_USE_WRAPPER, "true"));
        settings.activeProfiles = props.getProperty(KEY_PROFILES, "");
        settings.offline = Boolean.parseBoolean(props.getProperty(KEY_OFFLINE, "false"));
        settings.skipTests = Boolean.parseBoolean(props.getProperty(KEY_SKIP_TESTS, "false"));
        settings.mavenOpts = props.getProperty(KEY_MAVEN_OPTS, "");
        settings.additionalArgs = props.getProperty(KEY_ADDITIONAL_ARGS, "");
        return settings;
    }

    static void save(File projectRoot, MavenProjectSettings settings) throws IOException {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null || settings == null) {
            return;
        }
        File file = settingsFile(canonical);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        Properties props = new Properties();
        props.setProperty(KEY_FOLDER, canonical);
        put(props, KEY_MAVEN_HOME, settings.mavenHome);
        put(props, KEY_SETTINGS_FILE, settings.settingsFile);
        put(props, KEY_LOCAL_REPO, settings.localRepository);
        put(props, KEY_JDK_HOME, settings.jdkHome);
        put(props, KEY_EXECUTABLE, settings.mavenExecutable);
        props.setProperty(KEY_USE_WRAPPER, Boolean.toString(settings.useWrapper));
        put(props, KEY_PROFILES, settings.activeProfiles);
        props.setProperty(KEY_OFFLINE, Boolean.toString(settings.offline));
        props.setProperty(KEY_SKIP_TESTS, Boolean.toString(settings.skipTests));
        put(props, KEY_MAVEN_OPTS, settings.mavenOpts);
        put(props, KEY_ADDITIONAL_ARGS, settings.additionalArgs);
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "jEdit Maven project settings");
        }
    }

    static void reset(File projectRoot) {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return;
        }
        File file = settingsFile(canonical);
        if (file.isFile() && !file.delete()) {
            Log.log(Log.WARNING, MavenProjectPreferences.class,
                "Could not delete " + file);
        }
    }

    private static void put(Properties props, String key, String value) {
        if (value != null && !value.isBlank()) {
            props.setProperty(key, value.trim());
        }
    }

    private static String canonicalFolder(File projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        return MiscUtilities.resolveSymlinks(projectRoot.getAbsolutePath());
    }

    private static File settingsFile(String canonicalFolder) {
        String name = new File(canonicalFolder).getName()
            .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isEmpty()) {
            name = "project";
        }
        String id = Integer.toHexString(canonicalFolder.hashCode());
        return new File(new File(jEdit.getSettingsDirectory(), "maven-projects"),
            name + "-" + id + ".properties");
    }
}
