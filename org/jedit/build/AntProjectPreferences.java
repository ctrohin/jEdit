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

final class AntProjectPreferences {

    private static final String KEY_FOLDER = "project.folder";
    private static final String KEY_ANT_HOME = "ant.home";
    private static final String KEY_JDK_HOME = "jdk.home";
    private static final String KEY_BUILD_FILE = "build.file";
    private static final String KEY_WORKING_DIR = "working.directory";
    private static final String KEY_PROPERTY_FILE = "property.file";
    private static final String KEY_EXECUTABLE = "ant.executable";
    private static final String KEY_ANT_OPTS = "ant.opts";
    private static final String KEY_PROPERTIES = "properties";
    private static final String KEY_ADDITIONAL_ARGS = "additional.args";
    private static final String KEY_LOG_LEVEL = "log.level";

    private AntProjectPreferences() {}

    static AntProjectSettings load(File projectRoot) {
        AntProjectSettings settings = new AntProjectSettings();
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
            Log.log(Log.WARNING, AntProjectPreferences.class,
                "Could not read " + file, ex);
            return settings;
        }
        if (!canonical.equals(props.getProperty(KEY_FOLDER, "").trim())) {
            return settings;
        }
        settings.antHome = props.getProperty(KEY_ANT_HOME, "");
        settings.jdkHome = props.getProperty(KEY_JDK_HOME, "");
        settings.buildFile = props.getProperty(KEY_BUILD_FILE, "");
        settings.workingDirectory = props.getProperty(KEY_WORKING_DIR, "");
        settings.propertyFile = props.getProperty(KEY_PROPERTY_FILE, "");
        settings.antExecutable = props.getProperty(KEY_EXECUTABLE, "");
        settings.antOpts = props.getProperty(KEY_ANT_OPTS, "");
        settings.properties = props.getProperty(KEY_PROPERTIES, "");
        settings.additionalArgs = props.getProperty(KEY_ADDITIONAL_ARGS, "");
        settings.logLevel = props.getProperty(KEY_LOG_LEVEL, "default");
        return settings;
    }

    static void save(File projectRoot, AntProjectSettings settings) throws IOException {
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
        put(props, KEY_ANT_HOME, settings.antHome);
        put(props, KEY_JDK_HOME, settings.jdkHome);
        put(props, KEY_BUILD_FILE, settings.buildFile);
        put(props, KEY_WORKING_DIR, settings.workingDirectory);
        put(props, KEY_PROPERTY_FILE, settings.propertyFile);
        put(props, KEY_EXECUTABLE, settings.antExecutable);
        put(props, KEY_ANT_OPTS, settings.antOpts);
        put(props, KEY_PROPERTIES, settings.properties);
        put(props, KEY_ADDITIONAL_ARGS, settings.additionalArgs);
        put(props, KEY_LOG_LEVEL, settings.logLevel != null ? settings.logLevel : "default");
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "jEdit Ant project settings");
        }
    }

    static void reset(File projectRoot) {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return;
        }
        File file = settingsFile(canonical);
        if (file.isFile() && !file.delete()) {
            Log.log(Log.WARNING, AntProjectPreferences.class,
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
        return new File(new File(jEdit.getSettingsDirectory(), "ant-projects"),
            name + "-" + id + ".properties");
    }
}
