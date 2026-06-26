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

package org.jedit.lsp;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageServer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

/**
 * Eclipse JDT Language Server (jdtls) launch and workspace configuration.
 */
final class JdtlsSupport {

    private static final String MODE = "java";

    private JdtlsSupport() {}

    static boolean isJavaMode(String modeName) {
        return MODE.equals(LspConfig.resolveModeName(modeName));
    }

    /**
     * Resolves the directory jdtls should treat as the Java project root
     * (Maven, Gradle, Ant {@code build.xml}, or Eclipse metadata).
     */
    static String resolveProjectRoot(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return workspaceRoot;
        }
        File dir = new File(MiscUtilities.resolveSymlinks(workspaceRoot));
        if (!dir.isDirectory()) {
            dir = dir.getParentFile();
        }
        while (dir != null) {
            if (isJavaProjectRoot(dir)) {
                return dir.getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        return new File(MiscUtilities.resolveSymlinks(workspaceRoot)).getAbsolutePath();
    }

    static String[] augmentCommand(String[] command, String projectRoot) {
        if (command == null || command.length == 0) {
            return command;
        }
        List<String> args = new ArrayList<>(Arrays.asList(command));
        if (!hasFlag(args, "-data")) {
            args.add("-data");
            args.add(workspaceDataDirectory(projectRoot));
        }
        return args.toArray(String[]::new);
    }

    static WorkspaceFolder buildWorkspaceFolder(String projectRoot) {
        String root = resolveProjectRoot(projectRoot);
        File rootFile = new File(root);
        String name = rootFile.getName();
        if (name.isEmpty()) {
            name = "project";
        }
        return new WorkspaceFolder(LspDocumentUri.pathToUri(root), name);
    }

    static Map<String, Object> buildInitializationOptions(String projectRoot) {
        WorkspaceFolder folder = buildWorkspaceFolder(projectRoot);
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("bundles", List.of());
        // jdtls expects a collection of folder URI strings, not LSP WorkspaceFolder objects.
        options.put("workspaceFolders", List.of(folder.getUri()));
        options.put("settings", buildSettings());
        return options;
    }

    static Object configurationForSection(String section) {
        Map<String, Object> settings = buildSettings();
        if (section == null || section.isBlank()) {
            return settings;
        }
        if ("java".equals(section)) {
            return settings.get("java");
        }
        return Map.of();
    }

    static void requestProjectConfigurationUpdate(GenericLspClient client, String projectUri) {
        if (client == null || projectUri == null || projectUri.isBlank()) {
            return;
        }
        try {
            client.notifyServer("java/projectConfigurationUpdate", Map.of("uri", projectUri));
        } catch (Exception ex) {
            Log.log(Log.WARNING, JdtlsSupport.class,
                "Failed to request jdtls project configuration update", ex);
        }
    }

    static void pushConfiguration(LanguageServer server) {
        if (server == null) {
            return;
        }
        try {
            DidChangeConfigurationParams params = new DidChangeConfigurationParams();
            params.setSettings(buildSettings());
            server.getWorkspaceService().didChangeConfiguration(params);
        } catch (Exception ex) {
            Log.log(Log.WARNING, JdtlsSupport.class,
                "Failed to push jdtls configuration", ex);
        }
    }

    private static Map<String, Object> buildSettings() {
        Map<String, Object> importGradle = new LinkedHashMap<>();
        importGradle.put("enabled", true);

        Map<String, Object> importMaven = new LinkedHashMap<>();
        importMaven.put("enabled", true);

        Map<String, Object> importSettings = new LinkedHashMap<>();
        importSettings.put("gradle", importGradle);
        importSettings.put("maven", importMaven);
        importSettings.put("generatesMetadataFilesAtProjectRoot", true);

        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("updateBuildConfiguration", "automatic");

        Map<String, Object> project = new LinkedHashMap<>();
        project.put("importOnFirstTimeStartup", "automatic");
        project.put("resourceFilters", List.of("node_modules", "\\.git"));

        Map<String, Object> java = new LinkedHashMap<>();
        java.put("import", importSettings);
        java.put("configuration", configuration);
        java.put("project", project);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("java", java);
        return settings;
    }

    private static String workspaceDataDirectory(String projectRoot) {
        String root = resolveProjectRoot(projectRoot);
        String settingsHome = jEdit.getSettingsDirectory();
        if (settingsHome == null) {
            settingsHome = System.getProperty("java.io.tmpdir");
        }
        String id = root.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (id.length() > 80) {
            id = Integer.toHexString(root.hashCode());
        }
        File dataDir = new File(settingsHome + File.separator + "lsp" + File.separator
            + "jdtls" + File.separator + id);
        if (!dataDir.mkdirs() && !dataDir.isDirectory()) {
            Log.log(Log.WARNING, JdtlsSupport.class,
                "Could not create jdtls data directory: " + dataDir);
        }
        return dataDir.getAbsolutePath();
    }

    private static boolean isJavaProjectRoot(File dir) {
        return isFile(dir, "pom.xml")
            || isFile(dir, "build.gradle")
            || isFile(dir, "build.gradle.kts")
            || isFile(dir, "settings.gradle")
            || isFile(dir, "settings.gradle.kts")
            || isFile(dir, "build.xml")
            || isFile(dir, ".project")
            || isFile(dir, "mvnw")
            || isFile(dir, "mvnw.cmd")
            || isFile(dir, "gradlew")
            || isFile(dir, "gradlew.bat");
    }

    private static boolean isFile(File dir, String name) {
        return new File(dir, name).isFile();
    }

    private static boolean hasFlag(List<String> args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
