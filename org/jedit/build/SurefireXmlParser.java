/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.gjt.sp.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class SurefireXmlParser {

    private SurefireXmlParser() {}

    static List<TestCaseResult> parseFile(File xmlFile, File projectRoot) {
        if (xmlFile == null || !xmlFile.isFile()) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(xmlFile);
            Element root = document.getDocumentElement();
            if (root == null) {
                return List.of();
            }
            String rootName = root.getTagName();
            if ("testsuite".equalsIgnoreCase(rootName)) {
                return parseSuite(root, projectRoot);
            }
            if ("testsuites".equalsIgnoreCase(rootName)) {
                List<TestCaseResult> cases = new ArrayList<>();
                NodeList suites = root.getElementsByTagName("testsuite");
                for (int i = 0; i < suites.getLength(); i++) {
                    Node node = suites.item(i);
                    if (node instanceof Element element) {
                        cases.addAll(parseSuite(element, projectRoot));
                    }
                }
                return cases;
            }
        } catch (Exception ex) {
            Log.log(Log.WARNING, SurefireXmlParser.class,
                "Failed to parse " + xmlFile.getAbsolutePath(), ex);
        }
        return List.of();
    }

    private static List<TestCaseResult> parseSuite(Element suite, File projectRoot) {
        List<TestCaseResult> cases = new ArrayList<>();
        NodeList children = suite.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            if (!"testcase".equalsIgnoreCase(element.getTagName())) {
                continue;
            }
            cases.add(parseTestCase(element, projectRoot));
        }
        return cases;
    }

    private static TestCaseResult parseTestCase(Element testcase, File projectRoot) {
        String className = attribute(testcase, "classname");
        String methodName = attribute(testcase, "name");
        double time = parseDouble(attribute(testcase, "time"));
        TestCaseStatus status = TestCaseStatus.PASSED;
        String message = "";
        Element failure = firstChild(testcase, "failure", "error", "skipped");
        if (failure != null) {
            String tag = failure.getTagName().toLowerCase();
            status = switch (tag) {
                case "skipped" -> TestCaseStatus.SKIPPED;
                case "error" -> TestCaseStatus.ERROR;
                default -> TestCaseStatus.FAILED;
            };
            message = firstNonBlank(attribute(failure, "message"), failure.getTextContent());
        }
        File source = TestSourceLocator.resolve(projectRoot, className, message);
        int line = TestSourceLocator.resolveLine(message);
        if (line <= 0) {
            line = TestMethodLocator.findMethodLine(source, methodName);
        }
        return new TestCaseResult(className, methodName, status, time, message, source, line);
    }

    private static Element firstChild(Element parent, String... names) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String tag = element.getTagName();
            for (String name : names) {
                if (name.equalsIgnoreCase(tag)) {
                    return element;
                }
            }
        }
        return null;
    }

    private static String attribute(Element element, String name) {
        if (element.hasAttribute(name)) {
            return element.getAttribute(name);
        }
        return "";
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
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
}
