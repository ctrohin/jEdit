/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gjt.sp.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Parses newline-delimited JSON from {@code dart test --file-reporter=json:...}.
 */
final class DartTestReportParser {

    private DartTestReportParser() {}

    static List<TestCaseResult> parseFile(File jsonFile, File projectRoot) {
        if (jsonFile == null || !jsonFile.isFile()) {
            return List.of();
        }
        try {
            String text = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            return parseText(text, projectRoot);
        } catch (IOException ex) {
            Log.log(Log.WARNING, DartTestReportParser.class,
                "Failed to read " + jsonFile.getAbsolutePath(), ex);
            return List.of();
        }
    }

    static List<TestCaseResult> parseText(String text, File projectRoot) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<Integer, PendingTest> pending = new HashMap<>();
        Map<Integer, String> suitePaths = new HashMap<>();
        List<TestCaseResult> cases = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonElement element = JsonParser.parseString(line);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject event = element.getAsJsonObject();
                String type = stringValue(event, "type");
                switch (type) {
                    case "suite" -> {
                        JsonObject suite = event.getAsJsonObject("suite");
                        if (suite != null) {
                            int id = suite.get("id").getAsInt();
                            suitePaths.put(id, stringValue(suite, "path"));
                        }
                    }
                    case "testStart" -> {
                        JsonObject test = event.getAsJsonObject("test");
                        if (test != null) {
                            pending.put(test.get("id").getAsInt(), new PendingTest(test, suitePaths));
                        }
                    }
                    case "error" -> {
                        int testId = event.get("testID").getAsInt();
                        PendingTest test = pending.get(testId);
                        if (test != null) {
                            test.message = firstNonBlank(
                                stringValue(event, "error"),
                                stringValue(event, "stackTrace"));
                            test.failed = true;
                        }
                    }
                    case "testDone" -> {
                        int testId = event.get("testID").getAsInt();
                        PendingTest test = pending.remove(testId);
                        if (test == null || test.hidden(event)) {
                            continue;
                        }
                        cases.add(test.toResult(projectRoot, stringValue(event, "result"),
                            event.get("skipped").getAsBoolean()));
                    }
                    default -> { }
                }
            } catch (Exception ex) {
                Log.log(Log.DEBUG, DartTestReportParser.class, "Skipping JSON line: " + line, ex);
            }
        }
        return cases;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.getAsString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return "";
    }

    private static final class PendingTest {
        final String name;
        final int line;
        final File sourceFile;
        boolean failed;
        String message = "";

        PendingTest(JsonObject test, Map<Integer, String> suitePaths) {
            name = stringValue(test, "name");
            JsonElement lineElement = test.get("line");
            line = lineElement != null && !lineElement.isJsonNull()
                ? lineElement.getAsInt()
                : 0;
            sourceFile = resolveUrl(stringValue(test, "url"), suitePaths, test.get("suiteID"));
        }

        boolean hidden(JsonObject doneEvent) {
            JsonElement hidden = doneEvent.get("hidden");
            return hidden != null && !hidden.isJsonNull() && hidden.getAsBoolean();
        }

        TestCaseResult toResult(File projectRoot, String result, boolean skipped) {
            TestCaseStatus status = mapStatus(result, skipped);
            String className = deriveClassName(sourceFile, name);
            File source = sourceFile;
            if (source == null) {
                source = TestSourceLocator.resolve(projectRoot, className, message);
            }
            int resolvedLine = line > 0
                ? line
                : TestSourceLocator.resolveLine(message);
            if (resolvedLine <= 0) {
                resolvedLine = TestMethodLocator.findMethodLine(source, name);
            }
            return new TestCaseResult(className, name, status, 0, message, source, resolvedLine);
        }

        private static TestCaseStatus mapStatus(String result, boolean skipped) {
            if (skipped) {
                return TestCaseStatus.SKIPPED;
            }
            if (result == null) {
                return TestCaseStatus.ERROR;
            }
            return switch (result) {
                case "success" -> TestCaseStatus.PASSED;
                case "failure" -> TestCaseStatus.FAILED;
                default -> TestCaseStatus.ERROR;
            };
        }

        private static String deriveClassName(File sourceFile, String testName) {
            if (sourceFile != null) {
                String name = sourceFile.getName();
                if (name.endsWith("_test.dart")) {
                    return name.substring(0, name.length() - "_test.dart".length());
                }
                int dot = name.lastIndexOf('.');
                return dot > 0 ? name.substring(0, dot) : name;
            }
            return TestMethodLocator.shortTestName(testName);
        }

        private static File resolveUrl(String url, Map<Integer, String> suitePaths,
                                       JsonElement suiteIdElement) {
            File fromUrl = fileFromUrl(url);
            if (fromUrl != null) {
                return fromUrl;
            }
            if (suiteIdElement != null && !suiteIdElement.isJsonNull()) {
                String path = suitePaths.get(suiteIdElement.getAsInt());
                if (path != null && !path.isBlank()) {
                    File file = new File(path);
                    if (file.isFile()) {
                        return file;
                    }
                }
            }
            return null;
        }

        private static File fileFromUrl(String url) {
            if (url == null || url.isBlank()) {
                return null;
            }
            try {
                if (url.startsWith("file:")) {
                    return new File(URI.create(url));
                }
                File direct = new File(url);
                return direct.isFile() ? direct : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
