/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp.buildconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Maven POM and Ant build.xml element trees for tag completion.
 */
final class BuildConfigXmlSchemas {

    private static final Map<String, List<String>> MAVEN_CHILDREN = new LinkedHashMap<>();
    private static final Map<String, List<String>> ANT_CHILDREN = new LinkedHashMap<>();

    static {
        put(MAVEN_CHILDREN, "project",
            "modelVersion", "groupId", "artifactId", "version", "packaging", "name",
            "description", "url", "inceptionYear", "organization", "licenses", "developers",
            "contributors", "mailingLists", "prerequisites", "modules", "properties",
            "dependencyManagement", "dependencies", "repositories", "pluginRepositories",
            "build", "reporting", "profiles", "distributionManagement", "scm", "issueManagement",
            "ciManagement");
        put(MAVEN_CHILDREN, "parent",
            "groupId", "artifactId", "version", "relativePath");
        put(MAVEN_CHILDREN, "dependencies", "dependency");
        put(MAVEN_CHILDREN, "dependency",
            "groupId", "artifactId", "version", "type", "classifier", "scope", "systemPath",
            "optional", "exclusions");
        put(MAVEN_CHILDREN, "dependencyManagement", "dependencies");
        put(MAVEN_CHILDREN, "exclusions", "exclusion");
        put(MAVEN_CHILDREN, "exclusion", "groupId", "artifactId");
        put(MAVEN_CHILDREN, "build", "defaultGoal", "sourceDirectory", "scriptSourceDirectory",
            "testSourceDirectory", "outputDirectory", "testOutputDirectory", "extensions",
            "resources", "testResources", "plugins", "pluginManagement");
        put(MAVEN_CHILDREN, "plugins", "plugin");
        put(MAVEN_CHILDREN, "plugin",
            "groupId", "artifactId", "version", "extensions", "executions", "configuration",
            "dependencies", "goals");
        put(MAVEN_CHILDREN, "executions", "execution");
        put(MAVEN_CHILDREN, "execution",
            "id", "phase", "goals", "configuration", "inherited");
        put(MAVEN_CHILDREN, "goals", "goal");
        put(MAVEN_CHILDREN, "profiles", "profile");
        put(MAVEN_CHILDREN, "profile",
            "id", "activation", "build", "dependencies", "dependencyManagement", "modules",
            "properties", "repositories", "pluginRepositories", "reporting");
        put(MAVEN_CHILDREN, "modules", "module");
        put(MAVEN_CHILDREN, "properties");
        put(MAVEN_CHILDREN, "repositories", "repository");
        put(MAVEN_CHILDREN, "repository",
            "id", "name", "url", "layout", "releases", "snapshots");
        put(MAVEN_CHILDREN, "pluginRepositories", "pluginRepository");

        put(ANT_CHILDREN, "project",
            "property", "path", "target", "import", "macrodef", "typedef", "taskdef",
            "presetdef", "extension-point", "dirname");
        put(ANT_CHILDREN, "target",
            "antcall", "ant", "apply", "available", "basename", "checksum", "chmod",
            "condition", "copy", "delete", "dirname", "echo", "exec", "fail", "get",
            "input", "jar", "java", "javac", "javadoc", "junit", "loadfile", "loadresource",
            "macrodef", "mkdir", "move", "parallel", "path", "property", "record",
            "replace", "sequential", "sleep", "subant", "tar", "tstamp", "untar", "unzip",
            "waitfor", "war", "xmlproperty", "xslt", "zip");
        put(ANT_CHILDREN, "path", "pathelement", "fileset", "dirset", "filelist");
        put(ANT_CHILDREN, "fileset", "include", "exclude", "patternset");
        put(ANT_CHILDREN, "javac", "classpath", "compilerarg", "src", "sourcepath");
        put(ANT_CHILDREN, "copy", "fileset", "file", "filterset", "mapper");
        put(ANT_CHILDREN, "mkdir");
        put(ANT_CHILDREN, "property", "name", "value", "location", "file", "resource");
    }

    private BuildConfigXmlSchemas() {}

    static List<String> xmlTagCandidates(BuildConfigLspSupport.Kind kind, String text,
                                         int offset) {
        if (kind != BuildConfigLspSupport.Kind.MAVEN && kind != BuildConfigLspSupport.Kind.ANT) {
            return List.of();
        }
        String before = text.substring(0, Math.min(offset, text.length()));
        int lineStart = before.lastIndexOf('\n') + 1;
        String linePrefix = before.substring(lineStart);
        boolean closing = linePrefix.matches(".*</\\s*\\S*$");
        boolean opening = linePrefix.matches(".*<\\s*[^/!?][^>]*$")
            || linePrefix.matches(".*<\\s*$");
        if (!opening && !closing) {
            return List.of();
        }

        String partial = "";
        if (closing) {
            int idx = linePrefix.lastIndexOf("</");
            partial = linePrefix.substring(idx + 2).trim();
        } else {
            int idx = linePrefix.lastIndexOf('<');
            partial = linePrefix.substring(idx + 1).trim();
        }

        String parent = parentElement(text, offset);
        Map<String, List<String>> schema = kind == BuildConfigLspSupport.Kind.MAVEN
            ? MAVEN_CHILDREN : ANT_CHILDREN;
        List<String> candidates = schema.getOrDefault(parent, List.of());
        if (candidates.isEmpty() && parent.isEmpty()) {
            candidates = List.of(kind == BuildConfigLspSupport.Kind.MAVEN ? "project" : "project");
        }
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (partial.isEmpty() || candidate.startsWith(partial)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private static String parentElement(String text, int offset) {
        Stack<String> stack = new Stack<>();
        int i = 0;
        while (i < offset && i < text.length()) {
            int lt = text.indexOf('<', i);
            if (lt < 0 || lt >= offset) {
                break;
            }
            if (lt + 1 < text.length() && text.charAt(lt + 1) == '/') {
                int gt = text.indexOf('>', lt);
                if (gt < 0 || gt >= offset) {
                    break;
                }
                String name = text.substring(lt + 2, gt).trim();
                int space = name.indexOf(' ');
                if (space >= 0) {
                    name = name.substring(0, space);
                }
                if (!stack.isEmpty() && stack.peek().equals(name)) {
                    stack.pop();
                }
                i = gt + 1;
                continue;
            }
            if (lt + 1 < text.length() && text.charAt(lt + 1) == '!') {
                i = lt + 1;
                continue;
            }
            int gt = text.indexOf('>', lt);
            if (gt < 0 || gt >= offset) {
                break;
            }
            String tag = text.substring(lt + 1, gt).trim();
            if (tag.endsWith("/")) {
                i = gt + 1;
                continue;
            }
            int space = tag.indexOf(' ');
            if (space >= 0) {
                tag = tag.substring(0, space);
            }
            if (!tag.isEmpty()) {
                stack.push(tag);
            }
            i = gt + 1;
        }
        return stack.isEmpty() ? "" : stack.peek();
    }

    private static void put(Map<String, List<String>> map, String parent, String... children) {
        map.put(parent, List.of(children));
    }
}
