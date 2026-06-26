/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ProjectModuleRoots {

    private ProjectModuleRoots() {}

    static List<File> listModuleDirectories(File projectRoot) {
        Set<File> modules = new LinkedHashSet<>();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return List.of();
        }
        modules.add(projectRoot);
        collectMavenModules(projectRoot, modules, 0);
        collectGradleModules(projectRoot, modules, 0);
        return new ArrayList<>(modules);
    }

    private static void collectMavenModules(File dir, Set<File> modules, int depth) {
        if (depth > 4) {
            return;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            if (new File(child, "pom.xml").isFile()) {
                modules.add(child);
            }
            collectMavenModules(child, modules, depth + 1);
        }
    }

    private static void collectGradleModules(File dir, Set<File> modules, int depth) {
        if (depth > 4) {
            return;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            if (ProjectRoots.findGradleBuild(child) != null) {
                modules.add(child);
            }
            collectGradleModules(child, modules, depth + 1);
        }
    }

    private static boolean shouldSkip(File dir) {
        String name = dir.getName();
        return name.startsWith(".")
            || name.equals("node_modules")
            || name.equals("target")
            || name.equals("build")
            || name.equals("out");
    }
}
