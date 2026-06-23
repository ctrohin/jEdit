/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;

import org.gjt.sp.jedit.jEdit;

final class TestCaseResult {

    final String className;
    final String methodName;
    final TestCaseStatus status;
    final double timeSeconds;
    final String message;
    final File sourceFile;
    final int line;

    TestCaseResult(String className, String methodName, TestCaseStatus status,
                   double timeSeconds, String message, File sourceFile, int line) {
        this.className = className;
        this.methodName = methodName;
        this.status = status;
        this.timeSeconds = timeSeconds;
        this.message = message;
        this.sourceFile = sourceFile;
        this.line = line;
    }

    String displayName() {
        if (methodName == null || methodName.isBlank()) {
            return className;
        }
        return methodName;
    }

    String qualifiedName() {
        if (className == null || className.isBlank()) {
            return displayName();
        }
        if (methodName == null || methodName.isBlank()) {
            return className;
        }
        return className + "." + methodName;
    }

    String suiteName() {
        if (className == null || className.isBlank()) {
            return jEdit.getProperty("test-results.unknown-suite");
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    String treeLabel() {
        String statusLabel = jEdit.getProperty(status.labelProperty());
        String time = timeSeconds > 0
            ? String.format(" (%.2fs)", timeSeconds)
            : "";
        return statusLabel + ": " + displayName() + time;
    }

    boolean hasNavigationTarget() {
        return sourceFile != null && sourceFile.isFile();
    }
}
