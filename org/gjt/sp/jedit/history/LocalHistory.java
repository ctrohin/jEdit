/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

/**
 * Lightweight per-file local history stored under the jEdit settings directory.
 */
public final class LocalHistory {

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private LocalHistory() {}

    public static void recordSave(Buffer buffer) {
        if (buffer == null || buffer.getPath() == null || buffer.getPath().isBlank()) {
            return;
        }
        try {
            Path source = Path.of(buffer.getPath());
            if (!Files.isRegularFile(source)) {
                return;
            }
            Path target = snapshotPath(buffer.getPath(), LocalDateTime.now());
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Log.log(Log.DEBUG, LocalHistory.class, "Failed to record local history", ex);
        }
    }

    public static List<Entry> entriesFor(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        Path dir = historyDir(path);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::getFileName).reversed())
                .forEach(file -> entries.add(new Entry(file)));
        } catch (IOException ex) {
            Log.log(Log.DEBUG, LocalHistory.class, "Failed to list local history", ex);
        }
        return List.copyOf(entries);
    }

    public static Path snapshotPath(String bufferPath, LocalDateTime time) {
        String stamp = STAMP.format(time);
        String safeName = new java.io.File(bufferPath).getName();
        return historyDir(bufferPath).resolve(stamp + "_" + safeName);
    }

    private static Path historyDir(String bufferPath) {
        String key = Integer.toHexString(
            MiscUtilities.resolveSymlinks(bufferPath).toLowerCase(Locale.ROOT).hashCode());
        return Path.of(jEdit.getSettingsDirectory(), "local-history", key);
    }

    public static final class Entry {
        public final Path file;
        public final String label;

        Entry(Path file) {
            this.file = file;
            String name = file.getFileName().toString();
            int underscore = name.indexOf('_');
            this.label = underscore > 0 ? name.substring(0, underscore) : name;
        }
    }
}
