/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class TestSourceLocator {

    private static final String[] TEST_SOURCE_PREFIXES = {
        "src" + File.separator + "test" + File.separator + "java" + File.separator,
        "src" + File.separator + "test" + File.separator + "kotlin" + File.separator,
        "test" + File.separator + "java" + File.separator,
        "test" + File.separator + "kotlin" + File.separator,
        "test" + File.separator
    };

    private TestSourceLocator() {}

    static File resolve(File projectRoot, String className, String failureMessage) {
        File fromMessage = resolveFromMessage(projectRoot, failureMessage);
        if (fromMessage != null) {
            return fromMessage;
        }
        File fromClass = resolveFromClassName(projectRoot, className);
        if (fromClass != null) {
            return fromClass;
        }
        return resolveDartOrPython(projectRoot, className);
    }

    static int resolveLine(String failureMessage) {
        if (failureMessage == null || failureMessage.isBlank()) {
            return 0;
        }
        for (String line : failureMessage.split("\\R")) {
            List<FileLink> links = FileLinkParser.parseLine(line, 0);
            if (!links.isEmpty()) {
                return links.get(0).line;
            }
        }
        return 0;
    }

    private static File resolveFromMessage(File projectRoot, String failureMessage) {
        if (failureMessage == null || failureMessage.isBlank()) {
            return null;
        }
        for (String line : failureMessage.split("\\R")) {
            List<FileLink> links = FileLinkParser.parseLine(line, 0);
            for (FileLink link : links) {
                File file = resolveFile(projectRoot, link.path);
                if (file != null) {
                    return file;
                }
            }
        }
        return null;
    }

    private static File resolveFromClassName(File projectRoot, String className) {
        if (projectRoot == null || className == null || className.isBlank()) {
            return null;
        }
        String relative = className.replace('.', File.separatorChar);
        for (String ext : new String[] {".java", ".kt"}) {
            File candidate = findTestFile(projectRoot, relative + ext);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static File resolveDartOrPython(File projectRoot, String className) {
        if (projectRoot == null || className == null || className.isBlank()) {
            return null;
        }
        String module = className.replace('.', File.separatorChar);
        File dart = findTestFile(projectRoot, module + "_test.dart");
        if (dart != null) {
            return dart;
        }
        dart = findTestFile(projectRoot, module + ".dart");
        if (dart != null) {
            return dart;
        }
        File python = findTestFile(projectRoot, module + ".py");
        if (python != null) {
            return python;
        }
        if (className.contains(".")) {
            String simple = className.substring(className.lastIndexOf('.') + 1);
            python = findTestFile(projectRoot, simple + ".py");
            if (python != null) {
                return python;
            }
        }
        return findUnderProject(projectRoot, className + "_test.dart", 0);
    }

    private static File findTestFile(File projectRoot, String relative) {
        for (String prefix : TEST_SOURCE_PREFIXES) {
            File candidate = new File(projectRoot, prefix + relative);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        File direct = new File(projectRoot, relative);
        if (direct.isFile()) {
            return direct;
        }
        return findUnderProject(projectRoot, relative, 0);
    }

    private static File findUnderProject(File dir, String relative, int depth) {
        if (depth > 8 || dir == null || !dir.isDirectory()) {
            return null;
        }
        for (String prefix : TEST_SOURCE_PREFIXES) {
            File candidate = new File(dir, prefix + relative);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        File direct = new File(dir, relative);
        if (direct.isFile()) {
            return direct;
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            File found = findUnderProject(child, relative, depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static File resolveFile(File projectRoot, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path.trim());
        if (file.isFile()) {
            return file;
        }
        if (projectRoot != null) {
            File relative = new File(projectRoot, path.trim());
            if (relative.isFile()) {
                return relative;
            }
        }
        return null;
    }

    private static boolean shouldSkip(File dir) {
        String name = dir.getName();
        return name.startsWith(".")
            || name.equals("node_modules")
            || name.equals("target")
            || name.equals("build")
            || name.equals("out")
            || name.equals(".dart_tool");
    }
}
