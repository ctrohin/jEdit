/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Distinguishes plain Dart packages from Flutter projects that share pubspec.yaml.
 */
final class DartProjectSupport {

    private DartProjectSupport() {}

    static boolean isFlutterProject(File projectRoot) {
        File pubspec = ProjectRoots.findPubspecYaml(projectRoot);
        if (pubspec == null || !pubspec.isFile()) {
            return false;
        }
        try {
            String text = Files.readString(pubspec.toPath(), StandardCharsets.UTF_8);
            return isFlutterPubspec(text);
        } catch (IOException ex) {
            return false;
        }
    }

    static boolean isFlutterPubspec(String pubspecText) {
        if (pubspecText == null || pubspecText.isBlank()) {
            return false;
        }
        if (pubspecText.contains("flutter:")) {
            return true;
        }
        return pubspecText.contains("sdk: flutter")
            || pubspecText.contains("sdk:flutter");
    }

    static ProjectKind resolvePubspecKind(File projectRoot) {
        if (ProjectRoots.findPubspecYaml(projectRoot) == null) {
            return null;
        }
        return isFlutterProject(projectRoot) ? ProjectKind.FLUTTER : ProjectKind.DART;
    }
}
