/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.util.Map;

import com.jediterm.terminal.model.hyperlinks.HyperlinkFilter;
import com.jediterm.terminal.model.hyperlinks.LinkInfo;
import com.jediterm.terminal.model.hyperlinks.LinkResult;
import com.jediterm.terminal.model.hyperlinks.LinkResultItem;

import org.gjt.sp.jedit.View;

import java.util.ArrayList;
import java.util.List;

final class TerminalHyperlinkFilter implements HyperlinkFilter {

    private final View view;
    private final File projectRoot;

    TerminalHyperlinkFilter(View view, File projectRoot) {
        this.view = view;
        this.projectRoot = projectRoot;
    }

    @Override
    public LinkResult apply(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        var links = FileLinkParser.parseLine(line, 0);
        if (links.isEmpty()) {
            return null;
        }
        List<LinkResultItem> items = new ArrayList<>();
        for (FileLink link : links) {
            int start = Math.max(0, link.start - 0);
            int end = Math.min(line.length(), link.end);
            if (start >= end) {
                continue;
            }
            FileLink captured = link;
            items.add(new LinkResultItem(start, end, new LinkInfo(() ->
                FileLinkNavigator.openLink(view, projectRoot, captured))));
        }
        return items.isEmpty() ? null : new LinkResult(items);
    }
}
