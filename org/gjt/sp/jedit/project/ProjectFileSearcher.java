/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.gjt.sp.jedit.project;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.gjt.sp.jedit.MiscUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.jedit.search.BoyerMooreSearchMatcher;
import org.gjt.sp.jedit.search.PatternSearchMatcher;
import org.gjt.sp.jedit.search.SearchMatcher;
import org.gjt.sp.util.Log;

/**
 * Searches project files and lists project file paths.
 */
public final class ProjectFileSearcher {

    public enum ExtensionMode {
        INCLUDE,
        EXCLUDE
    }

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
        ".git", ".svn", ".hg", "node_modules", "build", "target",
        ".dart_tool", ".idea", ".gradle", "dist", "out");

    private ProjectFileSearcher() {}

    public static List<ProjectSearchMatch> search(File projectRoot, String query,
                                                    boolean regexp,
                                                    ExtensionMode extensionMode,
                                                    String extensionsText) {
        List<ProjectSearchMatch> results = new ArrayList<>();
        if (projectRoot == null || query == null || query.isBlank()) {
            return results;
        }
        SearchMatcher matcher = createMatcher(query, regexp);
        if (matcher == null) {
            return results;
        }
        Set<String> extensions = parseExtensions(extensionsText);
        AtomicBoolean cancelled = new AtomicBoolean();
        search(projectRoot, matcher, extensionMode, extensions, cancelled, results::add);
        return results;
    }

    public static void search(File projectRoot, SearchMatcher matcher,
                              ExtensionMode extensionMode, Set<String> extensions,
                              AtomicBoolean cancelled,
                              java.util.function.Consumer<ProjectSearchMatch> consumer) {
        if (projectRoot == null || matcher == null || consumer == null) {
            return;
        }
        boolean skipHidden = jEdit.getBooleanProperty("search.skipHidden.toggle", true);
        boolean skipBinary = jEdit.getBooleanProperty("search.skipBinary.toggle", true);
        final Charset charset = resolveCharset();
        try {
            Files.walkFileTree(projectRoot.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelled.get()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (skipHidden) {
                        String name = dir.getFileName().toString();
                        if (name.startsWith(".") && !projectRoot.toPath().equals(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    String dirName = dir.getFileName().toString();
                    if (SKIP_DIR_NAMES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancelled.get()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName().toString();
                    if (skipHidden && fileName.startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (!acceptsExtension(fileName, extensions, extensionMode)) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (skipBinary && isBinaryFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    searchFile(file.toFile(), charset, matcher, consumer);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            Log.log(Log.ERROR, ProjectFileSearcher.class,
                "Error walking project folder " + projectRoot, ex);
        }
    }

    public static List<String> listProjectFiles(File projectRoot) {
        List<String> paths = new ArrayList<>();
        if (projectRoot == null) {
            return paths;
        }
        boolean skipHidden = jEdit.getBooleanProperty("search.skipHidden.toggle", true);
        try {
            Path rootPath = projectRoot.toPath();
            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (skipHidden) {
                        String name = dir.getFileName().toString();
                        if (name.startsWith(".") && !rootPath.equals(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    String dirName = dir.getFileName().toString();
                    if (SKIP_DIR_NAMES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String fileName = file.getFileName().toString();
                    if (skipHidden && fileName.startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }
                    paths.add(file.toString());
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            Log.log(Log.ERROR, ProjectFileSearcher.class,
                "Error listing project files in " + projectRoot, ex);
        }
        paths.sort(String.CASE_INSENSITIVE_ORDER);
        return paths;
    }

    public static List<String> filterQuickOpenPaths(List<String> paths, String query) {
        if (query == null || query.isBlank()) {
            int limit = Math.min(paths.size(), 200);
            return paths.subList(0, limit);
        }
        String lower = query.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String path : paths) {
            String name = fileNameFromPath(path);
            if (name.contains(lower) || pathContainsQuery(path, lower)) {
                matches.add(path);
            }
            if (matches.size() >= 200) {
                break;
            }
        }
        matches.sort((a, b) -> {
            int rankA = quickOpenRank(fileNameFromPath(a), lower);
            int rankB = quickOpenRank(fileNameFromPath(b), lower);
            if (rankA != rankB) {
                return Integer.compare(rankA, rankB);
            }
            return a.compareToIgnoreCase(b);
        });
        return matches;
    }

    private static String fileNameFromPath(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean pathContainsQuery(String path, String lowerQuery) {
        return path.replace('\\', '/').toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private static int quickOpenRank(String nameLower, String query) {
        if (nameLower.startsWith(query)) {
            return 0;
        }
        if (nameLower.contains(query)) {
            return 1;
        }
        return 2;
    }

    private static void searchFile(File file, Charset charset, SearchMatcher matcher,
                                   java.util.function.Consumer<ProjectSearchMatch> consumer) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), charset)) {
            String line;
            int lineIndex = 0;
            while ((line = reader.readLine()) != null) {
                int offset = 0;
                boolean firstTime = true;
                while (offset <= line.length()) {
                    SearchMatcher.Match match = matcher.nextMatch(
                        line.substring(offset), offset == 0, true, firstTime, false);
                    if (match == null) {
                        break;
                    }
                    int start = offset + match.start;
                    int end = offset + match.end;
                    consumer.accept(new ProjectSearchMatch(
                        file.getPath(), lineIndex, start, end, line));
                    if (end <= start) {
                        break;
                    }
                    offset = end;
                    firstTime = false;
                }
                lineIndex++;
            }
        } catch (Exception ex) {
            Log.log(Log.DEBUG, ProjectFileSearcher.class,
                "Skipped unreadable file " + file, ex);
        }
    }

    private static SearchMatcher createMatcher(String query, boolean regexp) {
        try {
            if (regexp) {
                Pattern pattern = Pattern.compile(query,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
                return new PatternSearchMatcher(pattern, true, false);
            }
            return new BoyerMooreSearchMatcher(query, true, false);
        } catch (Exception ex) {
            Log.log(Log.ERROR, ProjectFileSearcher.class,
                "Invalid search pattern: " + query, ex);
            return null;
        }
    }

    static Set<String> parseExtensions(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> extensions = new HashSet<>();
        for (String part : text.split(",")) {
            String ext = part.trim();
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            if (!ext.isEmpty()) {
                extensions.add(ext.toLowerCase(Locale.ROOT));
            }
        }
        return extensions;
    }

    static boolean acceptsExtension(String fileName, Set<String> extensions,
                                    ExtensionMode mode) {
        if (extensions.isEmpty()) {
            return true;
        }
        String ext = fileExtension(fileName);
        boolean matches = extensions.contains(ext);
        return mode == ExtensionMode.INCLUDE ? matches : !matches;
    }

    private static String fileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isBinaryFile(Path file) {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return MiscUtilities.isBinary(in);
        } catch (IOException ex) {
            return true;
        }
    }

    private static Charset resolveCharset() {
        String encoding = jEdit.getProperty("buffer.encoding", "UTF-8");
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }
}
