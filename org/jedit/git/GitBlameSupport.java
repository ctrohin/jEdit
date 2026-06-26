/*

 * jEdit - Programmer's Text Editor

 * :tabSize=8:indentSize=8:noTabs=false:

 * :folding=explicit:collapseFolds=1:

 *

 * Copyright © 2026 jEdit contributors

 */



package org.jedit.git;



import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.EBComponent;
import org.gjt.sp.jedit.EBMessage;
import org.gjt.sp.jedit.EditBus;
import org.gjt.sp.jedit.EditPane;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.msg.EditPaneUpdate;
import org.gjt.sp.jedit.msg.PropertiesChanged;
import org.gjt.sp.jedit.options.GutterOptionPane;
import org.gjt.sp.jedit.textarea.BlameGutter;
import org.gjt.sp.jedit.textarea.Gutter;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.gjt.sp.jedit.textarea.TextAreaExtension;
import org.gjt.sp.jedit.textarea.TextAreaMouseHandler;
import org.gjt.sp.util.ThreadUtilities;



/**

 * Optional git blame annotations in a dedicated gutter column.

 */

public final class GitBlameSupport implements EBComponent {



    private static final String ENABLED_PROPERTY = "git.blame.enabled";

    private static final int HORIZONTAL_PADDING = 4;

    private static GitBlameSupport instance;

    private final Map<Buffer, BlameCache> caches = new WeakHashMap<>();

    private final Map<EditPane, BlameExtension> extensions = new WeakHashMap<>();



    public static void install() {

        if (instance == null) {

            instance = new GitBlameSupport();

            EditBus.addToBus(instance);

            for (View view : jEdit.getViewManager().getViews()) {

                for (EditPane editPane : view.getEditPanes()) {

                    instance.installEditPane(editPane);

                }

            }

        }

    }



    public static void uninstall() {

        if (instance != null) {

            for (EditPane editPane : new ArrayList<>(instance.extensions.keySet())) {

                instance.uninstallEditPane(editPane);

            }

            EditBus.removeFromBus(instance);

            instance = null;

        }

    }



    public static boolean isEnabled() {

        return jEdit.getBooleanProperty(ENABLED_PROPERTY);

    }



    public static void toggle() {

        setEnabled(!isEnabled());

    }



    public static void setEnabled(boolean enabled) {

        jEdit.setBooleanProperty(ENABLED_PROPERTY, enabled);

        if (instance != null) {

            instance.applyToAllEditPanes();

        }

        if (enabled && !GutterOptionPane.isGutterEnabled()) {

            jEdit.setBooleanProperty("view.gutter.enabled", true);

            jEdit.propertiesChanged();

        }

    }



    public static void applyBlameGutterState(EditPane editPane) {

        if (instance == null || editPane == null) {

            return;

        }

        instance.updateBlameGutterForEditPane(editPane);

    }



    private void updateBlameGutterForEditPane(EditPane editPane) {

        BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();

        blameGutter.setColumnWidth(computeBlameColumnWidth(blameGutter,

            cacheFor(editPane.getBuffer())));

        blameGutter.setBlameEnabled(isEnabled());

    }



    private static void syncGutterBorders(JEditTextArea textArea) {

        if (textArea == null) {

            return;

        }

        textArea.syncGutterStripBorders();

    }



    @Override

    public void handleMessage(EBMessage message) {

        if (message instanceof EditPaneUpdate update) {

            if (EditPaneUpdate.CREATED.equals(update.getWhat())) {

                installEditPane(update.getEditPane());

            } else if (EditPaneUpdate.DESTROYED.equals(update.getWhat())) {

                uninstallEditPane(update.getEditPane());

            } else if (EditPaneUpdate.BUFFER_CHANGED.equals(update.getWhat())) {

                updateBlameGutterForEditPane(update.getEditPane());

            }

        } else if (message instanceof BufferUpdate bufferUpdate) {

            Object what = bufferUpdate.getWhat();

            if (BufferUpdate.CLOSED.equals(what)) {

                caches.remove(bufferUpdate.getBuffer());

            } else if (isEnabled()

                && (BufferUpdate.LOADED.equals(what)

                || BufferUpdate.SAVED.equals(what))) {

                invalidateBuffer(bufferUpdate.getBuffer());

            }

        } else if (message instanceof PropertiesChanged) {

            applyToAllEditPanes();

        }

    }



    private void installEditPane(EditPane editPane) {

        if (editPane == null || extensions.containsKey(editPane)) {

            return;

        }

        BlameExtension extension = new BlameExtension(editPane);

        BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();

        blameGutter.addExtension(extension);

        extension.installMouseHandler(blameGutter);

        updateBlameGutterForEditPane(editPane);

        extensions.put(editPane, extension);

    }



    private void uninstallEditPane(EditPane editPane) {

        BlameExtension extension = extensions.remove(editPane);

        if (extension == null || editPane == null) {

            return;

        }

        BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();

        extension.uninstallMouseHandler(blameGutter);

        blameGutter.removeExtension(extension);

        blameGutter.setBlameEnabled(false);

    }



    private void applyToAllEditPanes() {

        for (Map.Entry<EditPane, BlameExtension> entry : extensions.entrySet()) {

            EditPane editPane = entry.getKey();

            updateBlameGutterForEditPane(editPane);

            syncGutterBorders(editPane.getTextArea());

            if (isEnabled()) {

                Buffer buffer = editPane.getBuffer();

                if (buffer != null) {

                    BlameCache cache = caches.get(buffer);

                    if (cache != null) {

                        cache.invalidate();

                    }

                    editPane.getTextArea().getBlameGutter().repaint();

                }

            }

        }

    }



    private BlameCache cacheFor(Buffer buffer) {

        if (buffer == null) {

            return null;

        }

        return caches.computeIfAbsent(buffer, ignored -> new BlameCache());

    }



    private void invalidateBuffer(Buffer buffer) {

        if (buffer == null) {

            return;

        }

        BlameCache cache = caches.get(buffer);

        if (cache != null) {

            cache.invalidate();

        }

        repaintBuffer(buffer);

    }



    private void repaintBuffer(Buffer buffer) {

        for (Map.Entry<EditPane, BlameExtension> entry : extensions.entrySet()) {

            if (entry.getKey().getBuffer() == buffer) {

                updateBlameGutterForEditPane(entry.getKey());

                entry.getKey().getTextArea().getBlameGutter().repaint();

            }

        }

    }



    private static int computeBlameColumnWidth(BlameGutter blameGutter,

                                               BlameCache cache) {

        FontMetrics fm = blameGutter.getFontMetrics(blameGutter.getFont());

        int maxAuthorWidth = cache != null ? cache.maxAuthorWidth(fm) : 0;

        if (maxAuthorWidth <= 0) {

            return 0;

        }

        return maxAuthorWidth + HORIZONTAL_PADDING;

    }



    private final class BlameExtension extends TextAreaExtension {

        private final EditPane editPane;

        private final Color blameColor;

        private final MouseAdapter mouseHandler = new MouseAdapter() {

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isEnabled()) {
                    return;
                }
                BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();
                if (!blameGutter.isBlameEnabled()) {
                    return;
                }
                BlameLine blameLine = blameLineAt(e.getX(), e.getY());
                if (blameLine == null || blameLine.isEmpty()) {
                    return;
                }
                if (TextAreaMouseHandler.isPopupTrigger(e)) {
                    e.consume();
                    showPopupMenu(e, blameLine);
                    return;
                }
                if (TextAreaMouseHandler.isLeftButton(e)) {
                    showCommit(blameLine);
                }
            }
        };



        BlameExtension(EditPane editPane) {

            this.editPane = editPane;

            Color disabled = UIManager.getColor("Label.disabledForeground");

            blameColor = disabled != null ? disabled : Color.GRAY;

        }



        void installMouseHandler(BlameGutter blameGutter) {

            blameGutter.addMouseListener(mouseHandler);

        }



        void uninstallMouseHandler(BlameGutter blameGutter) {

            blameGutter.removeMouseListener(mouseHandler);

        }



        @Override

        public void paintValidLine(Graphics2D gfx, int screenLine, int physicalLine,

                                   int start, int end, int y) {

            if (!isEnabled()) {

                return;

            }

            Buffer buffer = editPane.getBuffer();

            if (buffer == null || buffer.getPath() == null) {

                return;

            }

            BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();

            if (!blameGutter.isBlameEnabled()) {

                return;

            }

            BlameCache cache = cacheFor(buffer);

            BlameLine blameLine = cache.lineForLine(physicalLine + 1);

            if (blameLine == null) {

                cache.scheduleLoad(buffer, GitBlameSupport.this::repaintBuffer);

                return;

            }

            if (blameLine.isEmpty()) {

                return;

            }

            String author = blameLine.author;

            FontMetrics fm = gfx.getFontMetrics();

            int columnLeft = 2;

            int columnRight = blameGutter.getWidth() - 2;

            int maxWidth = Math.max(0, columnRight - columnLeft);

            String display = ellipsize(author, fm, maxWidth);

            if (display.isEmpty()) {

                return;

            }

            gfx.setColor(blameColor);

            gfx.drawString(display, columnLeft, y + fm.getAscent());

        }



        @Override

        public String getToolTipText(int x, int y) {

            if (!isEnabled()) {

                return null;

            }

            BlameGutter blameGutter = editPane.getTextArea().getBlameGutter();

            if (!blameGutter.isBlameEnabled()) {

                return null;

            }

            BlameLine blameLine = blameLineAt(x, y);

            if (blameLine == null || blameLine.isEmpty()) {

                return null;

            }

            return jEdit.getProperty("git.blame.tooltip",

                new Object[] { blameLine.shortHash(), blameLine.summary });

        }



        private BlameLine blameLineAt(int x, int y) {

            JEditTextArea textArea = editPane.getTextArea();

            int lineHeight = textArea.getPainter().getLineHeight();

            if (lineHeight <= 0) {

                return null;

            }

            int screenLine = y / lineHeight;

            if (screenLine < 0 || screenLine >= textArea.getVisibleLines()) {

                return null;

            }

            int physicalLine = textArea.getPhysicalLineOfScreenLine(screenLine);

            if (physicalLine < 0) {

                return null;

            }

            Buffer buffer = editPane.getBuffer();

            if (buffer == null) {

                return null;

            }

            return cacheFor(buffer).lineForLine(physicalLine + 1);

        }



        private void showCommit(BlameLine blameLine) {

            File repo = repositoryFor(editPane.getBuffer());

            if (repo == null) {

                return;

            }

            GitCommitUI.showCommit(editPane.getView(), repo, blameLine);

        }



        private void showPopupMenu(MouseEvent e, BlameLine blameLine) {

            JPopupMenu menu = new JPopupMenu();

            JMenuItem viewItem = new JMenuItem(jEdit.getProperty("git.blame.view"));

            viewItem.addActionListener(ev -> showCommit(blameLine));

            menu.add(viewItem);

            JMenuItem copyItem = new JMenuItem(

                jEdit.getProperty("git.blame.copy-commit-id"));

            copyItem.addActionListener(ev -> GitCommitUI.copyCommitId(blameLine));

            menu.add(copyItem);

            JMenuItem selectItem = new JMenuItem(

                jEdit.getProperty("git.blame.select-in-history"));

            selectItem.addActionListener(ev ->

                GitCommitUI.selectCommitInHistory(editPane.getView(), blameLine.hash));

            menu.add(selectItem);

            menu.show(e.getComponent(), e.getX(), e.getY());

        }

    }



    private static String ellipsize(String text, FontMetrics fm, int maxWidth) {

        if (maxWidth <= 0 || text.isEmpty()) {

            return "";

        }

        if (fm.stringWidth(text) <= maxWidth) {

            return text;

        }

        String ellipsis = "…";

        int ellipsisWidth = fm.stringWidth(ellipsis);

        int end = text.length();

        while (end > 0 && fm.stringWidth(text.substring(0, end)) + ellipsisWidth > maxWidth) {

            end--;

        }

        if (end <= 0) {

            return "";

        }

        return text.substring(0, end) + ellipsis;

    }



    private static File repositoryFor(Buffer buffer) {

        if (buffer == null || buffer.getPath() == null) {

            return null;

        }

        return GitRepository.findRoot(new File(buffer.getPath()).getParentFile());

    }



    private static final class BlameCache {

        private volatile BlameLine[] lines;

        private volatile boolean loading;

        private volatile boolean loadAttempted;



        void invalidate() {

            lines = null;

            loading = false;

            loadAttempted = false;

        }



        BlameLine lineForLine(int line) {

            BlameLine[] data = lines;

            if (data == null || line <= 0 || line > data.length) {

                return null;

            }

            return data[line - 1];

        }



        int maxAuthorWidth(FontMetrics fm) {

            BlameLine[] data = lines;

            if (data == null) {

                return 0;

            }

            int max = 0;

            for (BlameLine line : data) {

                if (line != null && !line.author.isBlank()) {

                    max = Math.max(max, fm.stringWidth(line.author));

                }

            }

            return max;

        }



        void scheduleLoad(Buffer buffer, Consumer<Buffer> repaint) {

            if (!isEnabled() || loading || loadAttempted) {

                return;

            }

            String path = buffer.getPath();

            if (path == null) {

                loadAttempted = true;

                return;

            }

            File file = new File(path);

            File repo = GitRepository.findRoot(file.getParentFile());

            if (repo == null) {

                lines = new BlameLine[0];

                loadAttempted = true;

                return;

            }

            loading = true;

            loadAttempted = true;

            ThreadUtilities.runInBackground(() -> {

                GitRunner runner = new GitRunner();

                String relative = relativize(repo, file);

                GitRunner.Result result = runner.run(

                    repo, "blame", "--line-porcelain", "--", relative);

                BlameLine[] parsed = result.success()

                    ? parsePorcelainBlame(result.output, buffer.getLineCount())

                    : new BlameLine[0];

                lines = parsed;

                loading = false;

                SwingUtilities.invokeLater(() -> repaint.accept(buffer));

            });

        }



        private static String relativize(File repo, File file) {

            String repoPath = repo.getAbsolutePath();

            String filePath = file.getAbsolutePath();

            if (filePath.length() >= repoPath.length()

                && filePath.regionMatches(true, 0, repoPath, 0, repoPath.length())) {

                String relative = filePath.substring(repoPath.length());

                if (relative.startsWith(File.separator)) {

                    relative = relative.substring(1);

                }

                return relative.replace('\\', '/');

            }

            return file.getName();

        }



        private static BlameLine[] parsePorcelainBlame(String output, int lineCount) {

            BlameLine[] blameLines = new BlameLine[Math.max(lineCount, 1)];

            if (output == null || output.isBlank()) {

                return blameLines;

            }

            int index = 0;

            String pendingHash = null;

            String pendingAuthor = null;

            String pendingSummary = null;

            for (String line : output.split("\\R", -1)) {

                if (line.startsWith("\t")) {

                    if (index < blameLines.length && pendingHash != null) {

                        blameLines[index++] = new BlameLine(

                            pendingHash, pendingAuthor, pendingSummary);

                    }

                    pendingHash = null;

                    pendingAuthor = null;

                    pendingSummary = null;

                } else if (line.startsWith("author ")) {

                    pendingAuthor = line.substring(7);

                } else if (line.startsWith("summary ")) {

                    pendingSummary = line.substring(8);

                } else if (!line.isEmpty() && !isMetadataLine(line)) {

                    int space = line.indexOf(' ');

                    if (space > 0) {

                        pendingHash = line.substring(0, space);

                    }

                }

            }

            return blameLines;

        }



        private static boolean isMetadataLine(String line) {

            return line.startsWith("author-mail ")

                || line.startsWith("author-time ")

                || line.startsWith("author-tz ")

                || line.startsWith("committer ")

                || line.startsWith("committer-mail ")

                || line.startsWith("committer-time ")

                || line.startsWith("committer-tz ")

                || line.startsWith("previous ")

                || line.startsWith("filename ")

                || line.startsWith("boundary");

        }

    }



    static final class BlameLine {

        final String hash;

        final String author;

        final String summary;



        BlameLine(String hash, String author, String summary) {

            this.hash = hash != null ? hash : "";

            this.author = author != null ? author : "";

            this.summary = summary != null ? summary : "";

        }



        boolean isEmpty() {

            return hash.isBlank();

        }



        String shortHash() {

            return hash.length() > 7 ? hash.substring(0, 7) : hash;

        }



        GitModels.Commit toCommit() {

            return new GitModels.Commit(hash, shortHash(), author, "", summary);

        }

    }

}


