/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
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

final class ProjectPropertiesStorage {

    private static final String KEY_FOLDER = "project.folder";

    private ProjectPropertiesStorage() {}

    static Properties load(File projectRoot, String subdir) {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return null;
        }
        File file = settingsFile(canonical, subdir);
        if (!file.isFile()) {
            return null;
        }
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            Log.log(Log.WARNING, ProjectPropertiesStorage.class,
                "Could not read " + file, ex);
            return null;
        }
        if (!canonical.equals(props.getProperty(KEY_FOLDER, "").trim())) {
            return null;
        }
        return props;
    }

    static void save(File projectRoot, String subdir, Properties props) throws IOException {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return;
        }
        props.setProperty(KEY_FOLDER, canonical);
        File file = settingsFile(canonical, subdir);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            props.store(out, "jEdit project settings");
        }
    }

    static void reset(File projectRoot, String subdir) {
        String canonical = canonicalFolder(projectRoot);
        if (canonical == null) {
            return;
        }
        File file = settingsFile(canonical, subdir);
        if (file.isFile() && !file.delete()) {
            Log.log(Log.WARNING, ProjectPropertiesStorage.class,
                "Could not delete " + file);
        }
    }

    static void put(Properties props, String key, String value) {
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

    private static File settingsFile(String canonicalFolder, String subdir) {
        String name = new File(canonicalFolder).getName()
            .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isEmpty()) {
            name = "project";
        }
        String id = Integer.toHexString(canonicalFolder.hashCode());
        return new File(new File(jEdit.getSettingsDirectory(), subdir),
            name + "-" + id + ".properties");
    }
}
