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

import java.io.File;

import java.util.ArrayList;

import java.util.Map;

import java.util.WeakHashMap;

import java.util.function.Consumer;



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

import org.gjt.sp.jedit.textarea.Gutter;

import org.gjt.sp.jedit.textarea.JEditTextArea;

import org.gjt.sp.jedit.textarea.TextAreaExtension;

import org.gjt.sp.util.ThreadUtilities;



/**

 * Optional git blame annotations in a dedicated gutter column.

 */

public final class GitBlameSupport implements EBComponent {



    private static final String ENABLED_PROPERTY = "git.blame.enabled";

    private static final String COLUMN_WIDTH_PROPERTY = "git.blame.columnWidth";



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



    public static void applyGutterState(Gutter gutter) {

        if (gutter == null) {

            return;

        }

        boolean enabled = isEnabled();

        gutter.setBlameAreaWidth(computeBlameColumnWidth(gutter));

        gutter.setBlameAreaEnabled(enabled);

    }



    @Override

    public void handleMessage(EBMessage message) {

        if (message instanceof EditPaneUpdate update) {

            if (EditPaneUpdate.CREATED.equals(update.getWhat())) {

                installEditPane(update.getEditPane());

            } else if (EditPaneUpdate.DESTROYED.equals(update.getWhat())) {

                uninstallEditPane(update.getEditPane());

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

        Gutter gutter = editPane.getTextArea().getGutter();

        gutter.addExtension(Gutter.OVER_LINE_NUMBERS_LAYER, extension);

        applyGutterState(gutter);

        extensions.put(editPane, extension);

    }



    private void uninstallEditPane(EditPane editPane) {

        BlameExtension extension = extensions.remove(editPane);

        if (extension == null || editPane == null) {

            return;

        }

        Gutter gutter = editPane.getTextArea().getGutter();

        gutter.removeExtension(extension);

        gutter.setBlameAreaEnabled(false);

    }



    private void applyToAllEditPanes() {

        for (Map.Entry<EditPane, BlameExtension> entry : extensions.entrySet()) {

            EditPane editPane = entry.getKey();

            applyGutterState(editPane.getTextArea().getGutter());

            if (isEnabled()) {

                Buffer buffer = editPane.getBuffer();

                if (buffer != null) {

                    BlameCache cache = caches.get(buffer);

                    if (cache != null) {

                        cache.invalidate();

                    }

                    editPane.getTextArea().getGutter().repaint();

                }

            }

        }

    }



    private BlameCache cacheFor(Buffer buffer) {

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

                entry.getKey().getTextArea().getGutter().repaint();

            }

        }

    }



    private static int computeBlameColumnWidth(Gutter gutter) {

        int configured = jEdit.getIntegerProperty(COLUMN_WIDTH_PROPERTY, 96);

        FontMetrics fm = gutter.getFontMetrics(gutter.getFont());

        int minimum = fm.stringWidth("M") * 8 + 8;

        return Math.max(configured, minimum);

    }



    private final class BlameExtension extends TextAreaExtension {

        private final EditPane editPane;

        private final Color blameColor;



        BlameExtension(EditPane editPane) {

            this.editPane = editPane;

            Color disabled = UIManager.getColor("Label.disabledForeground");

            blameColor = disabled != null ? disabled : Color.GRAY;

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

            Gutter gutter = editPane.getTextArea().getGutter();

            if (!gutter.isBlameAreaEnabled()) {

                return;

            }

            BlameCache cache = cacheFor(buffer);

            String author = cache.authorForLine(physicalLine + 1);

            if (author == null) {

                cache.scheduleLoad(buffer, GitBlameSupport.this::repaintBuffer);

                return;

            }

            if (author.isBlank()) {

                return;

            }

            FontMetrics fm = gfx.getFontMetrics();

            int columnLeft = gutter.getBlameAreaLeft() + 2;

            int columnRight = gutter.getBlameAreaRight() - 2;

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

            Gutter gutter = editPane.getTextArea().getGutter();

            if (!gutter.isBlameAreaEnabled()

                || x < gutter.getBlameAreaLeft()

                || x >= gutter.getBlameAreaRight()) {

                return null;

            }

            JEditTextArea textArea = editPane.getTextArea();

            int lineHeight = textArea.getPainter().getLineHeight();

            if (lineHeight <= 0) {

                return null;

            }

            int screenLine = textArea.getFirstLine() + (y / lineHeight);

            if (screenLine < textArea.getFirstLine()

                || screenLine >= textArea.getFirstLine() + textArea.getVisibleLines()) {

                return null;

            }

            Buffer buffer = editPane.getBuffer();

            if (buffer == null) {

                return null;

            }

            int physicalLine = textArea.getPhysicalLineOfScreenLine(screenLine);
            if (physicalLine < 0) {
                return null;
            }
            String author = cacheFor(buffer).authorForLine(physicalLine + 1);

            if (author == null || author.isBlank()) {

                return null;

            }

            return jEdit.getProperty("git.blame.tooltip", new Object[] { author });

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



    private static final class BlameCache {

        private volatile String[] authors;

        private volatile boolean loading;

        private volatile boolean loadAttempted;



        void invalidate() {

            authors = null;

            loading = false;

            loadAttempted = false;

        }



        String authorForLine(int line) {

            String[] data = authors;

            if (data == null || line <= 0 || line > data.length) {

                return null;

            }

            return data[line - 1];

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

                authors = new String[0];

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

                String[] parsed = result.success()

                    ? parsePorcelainBlame(result.output, buffer.getLineCount())

                    : new String[0];

                authors = parsed;

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



        private static String[] parsePorcelainBlame(String output, int lineCount) {

            String[] authors = new String[Math.max(lineCount, 1)];

            if (output == null || output.isBlank()) {

                return authors;

            }

            int index = 0;

            String pendingAuthor = null;

            for (String line : output.split("\\R", -1)) {

                if (line.startsWith("author ")) {

                    pendingAuthor = line.substring(7);

                } else if (line.startsWith("\t")) {

                    if (index < authors.length && pendingAuthor != null) {

                        authors[index++] = pendingAuthor;

                    }

                    pendingAuthor = null;

                }

            }

            return authors;

        }

    }

}


