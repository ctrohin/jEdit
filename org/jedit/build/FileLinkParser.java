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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FileLinkParser {

    // javac/ant/gcc: path:line or path:line:column
    private static final Pattern COLON_LOCATION = Pattern.compile(
        "((?:/[A-Za-z]:/)?(?:[A-Za-z]:)?[/\\w ./\\\\$()\\-]+?\\.[A-Za-z0-9]+):(\\d+)(?::(\\d+))?");

    // Maven compiler: path:[line,col], optional [ERROR] prefix; /C:/ drive paths
    private static final Pattern BRACKET_LOCATION = Pattern.compile(
        "(?:\\[(?:ERROR|WARNING|INFO)\\]\\s+)?"
        + "((?:/[A-Za-z]:/)?(?:[A-Za-z]:)?[/\\w ./\\\\$()\\-]+?\\.[A-Za-z0-9]+)"
        + ":\\[(\\d+),(\\d+)\\]");

    // stack traces: (File.java:42)
    private static final Pattern PAREN_LOCATION = Pattern.compile(
        "\\(([\\w ./\\\\$\\-]+?\\.[A-Za-z0-9]+):(\\d+)\\)");

    private FileLinkParser() {}

    static List<FileLink> parseLine(String line, int lineStartOffset) {
        List<FileLink> links = new ArrayList<>();
        addMatches(line, lineStartOffset, BRACKET_LOCATION, links, 1, 2, 3);
        addMatches(line, lineStartOffset, COLON_LOCATION, links, 1, 2, 3);
        addMatches(line, lineStartOffset, PAREN_LOCATION, links, 1, 2, -1);
        return links;
    }

    private static void addMatches(String line, int lineStartOffset, Pattern pattern,
                                   List<FileLink> links, int pathGroup, int lineGroup,
                                   int columnGroup) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            String path = matcher.group(pathGroup).trim();
            int lineNumber = Integer.parseInt(matcher.group(lineGroup));
            int column = 1;
            if (columnGroup > 0 && matcher.group(columnGroup) != null) {
                column = Integer.parseInt(matcher.group(columnGroup));
            }
            links.add(new FileLink(
                lineStartOffset + matcher.start(),
                lineStartOffset + matcher.end(),
                path,
                lineNumber,
                column));
        }
    }
}
