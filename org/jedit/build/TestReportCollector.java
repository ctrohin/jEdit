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

    static File jeditTestReportsDir(File projectRoot) {
        return new File(projectRoot, ".jedit-test-reports");
    }

    static TestRunResult collect(File projectRoot, ProjectKind kind, String title, int exitCode) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return TestRunResult.empty(title, projectRoot, kind, exitCode);
        }
        List<TestCaseResult> cases = new ArrayList<>();
        for (File xmlFile : findReportFiles(projectRoot)) {
            cases.addAll(SurefireXmlParser.parseFile(xmlFile, projectRoot));
        }
        File jestJson = new File(jeditTestReportsDir(projectRoot), "jest-results.json");
        cases.addAll(JestReportParser.parseJsonFile(jestJson, projectRoot));
        File vitestXml = new File(jeditTestReportsDir(projectRoot), "junit.xml");
        cases.addAll(SurefireXmlParser.parseFile(vitestXml, projectRoot));
        addJsonReports(projectRoot, cases);
        return new TestRunResult(title, projectRoot, kind, exitCode, cases);
    }

    private static void addJsonReports(File projectRoot, List<TestCaseResult> cases) {
        File reports = jeditTestReportsDir(projectRoot);
        if (!reports.isDirectory()) {
            return;
        }
        File[] jsonFiles = reports.listFiles((d, name) -> name.endsWith(".json"));
        if (jsonFiles == null) {
            return;
        }
        for (File jsonFile : jsonFiles) {
            if ("jest-results.json".equals(jsonFile.getName())) {
                continue;
            }
            cases.addAll(JestReportParser.parseJsonFile(jsonFile, projectRoot));
        }
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
        addReportsFrom(jeditTestReportsDir(dir), files);
        addJestVitestReports(dir, files);
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

    private static void addJestVitestReports(File dir, Set<File> files) {
        File[] candidates = {
            new File(dir, "junit.xml"),
            new File(dir, "test-results" + File.separator + "junit.xml"),
            new File(dir, "coverage" + File.separator + "junit.xml")
        };
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                files.add(candidate);
            }
        }
    }

    private static void addReportsFrom(File reportDir, Set<File> files) {
        if (!reportDir.isDirectory()) {
            return;
        }
        File[] xmlFiles = reportDir.listFiles((d, name) ->
            (name.startsWith("TEST-") && name.endsWith(".xml"))
                || name.equals("junit.xml"));
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
