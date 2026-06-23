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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class TestReportCollector {

    private TestReportCollector() {}

    static TestRunResult collect(File projectRoot, ProjectKind kind, String title, int exitCode) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return TestRunResult.empty(title, projectRoot, kind, exitCode);
        }
        List<TestCaseResult> cases = new ArrayList<>();
        for (File xmlFile : findReportFiles(projectRoot)) {
            cases.addAll(SurefireXmlParser.parseFile(xmlFile, projectRoot));
        }
        return new TestRunResult(title, projectRoot, kind, exitCode, cases);
    }

    private static List<File> findReportFiles(File root) {
        Set<File> files = new LinkedHashSet<>();
        collectReportFiles(root, files, 0);
        return new ArrayList<>(files);
    }

    private static void collectReportFiles(File dir, Set<File> files, int depth) {
        if (depth > 10 || dir == null || !dir.isDirectory()) {
            return;
        }
        addReportsFrom(new File(dir, "target" + File.separator + "surefire-reports"), files);
        addReportsFrom(new File(dir, "build" + File.separator + "test-results" + File.separator + "test"), files);
        addReportsFrom(new File(dir, "build" + File.separator + "test" + File.separator + "raw-reports"), files);
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (shouldSkip(child)) {
                continue;
            }
            collectReportFiles(child, files, depth + 1);
        }
    }

    private static void addReportsFrom(File reportDir, Set<File> files) {
        if (!reportDir.isDirectory()) {
            return;
        }
        File[] xmlFiles = reportDir.listFiles((d, name) ->
            name.startsWith("TEST-") && name.endsWith(".xml"));
        if (xmlFiles == null) {
            return;
        }
        for (File xmlFile : xmlFiles) {
            files.add(xmlFile);
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
