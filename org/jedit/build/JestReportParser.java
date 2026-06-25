/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.gjt.sp.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class JestReportParser {

    private JestReportParser() {}

    static List<TestCaseResult> parseJsonFile(File jsonFile, File projectRoot) {
        if (jsonFile == null || !jsonFile.isFile()) {
            return List.of();
        }
        try (Reader reader = new FileReader(jsonFile)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return List.of();
            }
            return parseJsonObject(root.getAsJsonObject(), projectRoot);
        } catch (Exception ex) {
            Log.log(Log.WARNING, JestReportParser.class,
                "Failed to parse " + jsonFile.getAbsolutePath(), ex);
            return List.of();
        }
    }

    private static List<TestCaseResult> parseJsonObject(JsonObject root, File projectRoot) {
        List<TestCaseResult> cases = new ArrayList<>();
        JsonArray testResults = root.getAsJsonArray("testResults");
        if (testResults == null) {
            return cases;
        }
        for (JsonElement suiteElement : testResults) {
            if (!suiteElement.isJsonObject()) {
                continue;
            }
            JsonObject suite = suiteElement.getAsJsonObject();
            String suiteFile = stringValue(suite, "name");
            JsonArray assertions = suite.getAsJsonArray("assertionResults");
            if (assertions == null) {
                continue;
            }
            for (JsonElement assertionElement : assertions) {
                if (!assertionElement.isJsonObject()) {
                    continue;
                }
                JsonObject assertion = assertionElement.getAsJsonObject();
                cases.add(parseAssertion(assertion, suiteFile, projectRoot));
            }
        }
        return cases;
    }

    private static TestCaseResult parseAssertion(JsonObject assertion, String suiteFile,
                                                 File projectRoot) {
        String title = stringValue(assertion, "title");
        String fullName = stringValue(assertion, "fullName");
        String statusText = stringValue(assertion, "status");
        TestCaseStatus status = mapStatus(statusText);
        String message = "";
        JsonArray failureMessages = assertion.getAsJsonArray("failureMessages");
        if (failureMessages != null && !failureMessages.isEmpty()) {
            message = failureMessages.get(0).getAsString();
        }
        double time = assertion.has("duration")
            ? assertion.get("duration").getAsDouble() / 1000.0
            : 0;
        String className = deriveClassName(suiteFile, fullName, title);
        File source = TestSourceLocator.resolve(projectRoot, className, message);
        if (source == null && suiteFile != null && !suiteFile.isBlank()) {
            source = resolveFile(projectRoot, suiteFile);
        }
        int line = TestSourceLocator.resolveLine(message);
        if (line <= 0) {
            line = TestMethodLocator.findMethodLine(source, title);
        }
        return new TestCaseResult(className, title, status, time, message, source, line);
    }

    private static TestCaseStatus mapStatus(String status) {
        if (status == null) {
            return TestCaseStatus.ERROR;
        }
        return switch (status.toLowerCase()) {
            case "passed" -> TestCaseStatus.PASSED;
            case "pending", "skipped", "todo" -> TestCaseStatus.SKIPPED;
            case "failed" -> TestCaseStatus.FAILED;
            default -> TestCaseStatus.ERROR;
        };
    }

    private static String deriveClassName(String suiteFile, String fullName, String title) {
        if (suiteFile != null && !suiteFile.isBlank()) {
            String name = new File(suiteFile).getName();
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        if (fullName != null && !fullName.isBlank()) {
            int space = fullName.lastIndexOf(' ');
            return space > 0 ? fullName.substring(0, space) : fullName;
        }
        return title != null ? title : "";
    }

    private static File resolveFile(File projectRoot, String path) {
        File file = new File(path);
        if (file.isFile()) {
            return file;
        }
        if (projectRoot != null) {
            File relative = new File(projectRoot, path);
            if (relative.isFile()) {
                return relative;
            }
        }
        return null;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }
}
