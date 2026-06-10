/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
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

package org.jedit.build;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.gjt.sp.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads lifecycle plugin goals declared in {@code pom.xml} (and local parent POMs).
 */
final class MavenPomFile {

    final File file;
    private final List<String> customGoals;
    private final List<String> profileIds;

    private MavenPomFile(File file, List<String> customGoals, List<String> profileIds) {
        this.file = file;
        this.customGoals = customGoals;
        this.profileIds = profileIds;
    }

    List<String> customGoals() {
        return customGoals;
    }

    List<String> profileIds() {
        return profileIds;
    }

    static MavenPomFile parse(File pom) {
        if (pom == null || !pom.isFile()) {
            return null;
        }
        try {
            Set<String> goals = new LinkedHashSet<>();
            Set<String> profiles = new LinkedHashSet<>();
            Set<File> visited = new HashSet<>();
            collectFromPomChain(pom, goals, profiles, visited);
            return new MavenPomFile(pom, new ArrayList<>(goals), new ArrayList<>(profiles));
        } catch (Exception ex) {
            Log.log(Log.WARNING, MavenPomFile.class,
                "Failed to parse " + pom.getAbsolutePath(), ex);
            return null;
        }
    }

    private static void collectFromPomChain(File pom, Set<String> goals, Set<String> profiles,
                                            Set<File> visited) throws Exception {
        if (!visited.add(pom)) {
            return;
        }
        Document doc = parseDocument(pom);
        Element project = doc.getDocumentElement();
        if (project == null || !"project".equals(project.getTagName())) {
            return;
        }
        collectPluginGoals(project, goals);
        collectProfileIds(project, profiles);
        File parentPom = resolveLocalParentPom(project, pom.getParentFile());
        if (parentPom != null) {
            collectFromPomChain(parentPom, goals, profiles, visited);
        }
    }

    private static void collectProfileIds(Element project, Set<String> profiles) {
        Element profilesEl = childElement(project, "profiles");
        if (profilesEl == null) {
            return;
        }
        for (Element profile : childElements(profilesEl, "profile")) {
            String id = textChild(profile, "id");
            if (id != null && !id.isBlank()) {
                profiles.add(id);
            }
        }
    }

    private static Document parseDocument(File pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        return factory.newDocumentBuilder().parse(pom);
    }

    private static File resolveLocalParentPom(Element project, File pomDir) {
        Element parent = childElement(project, "parent");
        if (parent == null || pomDir == null) {
            return null;
        }
        Element relativePathEl = childElement(parent, "relativePath");
        String relativePath = relativePathEl != null
            ? relativePathEl.getTextContent().trim()
            : "../pom.xml";
        if (relativePath.isEmpty()) {
            return null;
        }
        File parentPom = new File(pomDir, relativePath);
        return parentPom.isFile() ? parentPom : null;
    }

    private static void collectPluginGoals(Element project, Set<String> goals) {
        Element build = childElement(project, "build");
        if (build != null) {
            collectPluginGoalsFromBuild(build, goals);
        }
        Element profiles = childElement(project, "profiles");
        if (profiles != null) {
            for (Element profile : childElements(profiles, "profile")) {
                Element profileBuild = childElement(profile, "build");
                if (profileBuild != null) {
                    collectPluginGoalsFromBuild(profileBuild, goals);
                }
            }
        }
    }

    private static void collectPluginGoalsFromBuild(Element build, Set<String> goals) {
        Element plugins = childElement(build, "plugins");
        if (plugins == null) {
            return;
        }
        for (Element plugin : childElements(plugins, "plugin")) {
            String artifactId = textChild(plugin, "artifactId");
            if (artifactId == null || artifactId.isBlank()) {
                continue;
            }
            String prefix = pluginPrefix(artifactId);
            collectGoalsFromElement(childElement(plugin, "goals"), prefix, goals);
            for (Element execution : childElements(plugin, "execution")) {
                collectGoalsFromElement(childElement(execution, "goals"), prefix, goals);
            }
        }
    }

    private static void collectGoalsFromElement(Element goalsEl, String prefix, Set<String> goals) {
        if (goalsEl == null) {
            return;
        }
        for (Element goal : childElements(goalsEl, "goal")) {
            String goalName = goal.getTextContent().trim();
            if (!goalName.isEmpty()) {
                goals.add(prefix + ":" + goalName);
            }
        }
    }

    /**
     * Maven CLI prefix from a plugin {@code artifactId}.
     */
    static String pluginPrefix(String artifactId) {
        if (artifactId.startsWith("maven-") && artifactId.endsWith("-plugin")) {
            return artifactId.substring("maven-".length(), artifactId.length() - "-plugin".length());
        }
        if (artifactId.endsWith("-maven-plugin")) {
            return artifactId.substring(0, artifactId.length() - "-maven-plugin".length());
        }
        return artifactId;
    }

    private static String textChild(Element parent, String name) {
        Element child = childElement(parent, name);
        return child != null ? child.getTextContent().trim() : null;
    }

    private static Element childElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }
}
