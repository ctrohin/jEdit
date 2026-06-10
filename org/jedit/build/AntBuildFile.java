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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.gjt.sp.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class AntBuildFile {

    final File file;
    final String defaultTarget;
    final List<String> targets;

    private AntBuildFile(File file, String defaultTarget, List<String> targets) {
        this.file = file;
        this.defaultTarget = defaultTarget;
        this.targets = targets;
    }

    static AntBuildFile parse(File buildXml) {
        if (buildXml == null || !buildXml.isFile()) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            Document doc = factory.newDocumentBuilder().parse(buildXml);
            Element project = doc.getDocumentElement();
            if (project == null || !"project".equals(project.getTagName())) {
                return null;
            }
            String defaultTarget = project.getAttribute("default");
            Set<String> names = new LinkedHashSet<>();
            if (defaultTarget != null && !defaultTarget.isBlank()) {
                names.add(defaultTarget);
            }
            NodeList targetNodes = project.getElementsByTagName("target");
            for (int i = 0; i < targetNodes.getLength(); i++) {
                if (!(targetNodes.item(i) instanceof Element target)) {
                    continue;
                }
                String name = target.getAttribute("name");
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return new AntBuildFile(buildXml, defaultTarget, new ArrayList<>(names));
        } catch (Exception ex) {
            Log.log(Log.WARNING, AntBuildFile.class,
                "Failed to parse " + buildXml.getAbsolutePath(), ex);
            return null;
        }
    }
}
