/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.lsp;

import java.awt.Color;

import org.jedit.build.BuildProblemsHub;
import org.jedit.build.TestProblemsExporter;
import org.gjt.sp.jedit.Buffer;
import org.gjt.sp.jedit.jEdit;

/**
 * One problem from LSP, build output, or test results.
 */
public final class UnifiedProblem implements Comparable<UnifiedProblem> {

    public enum Severity {
        ERROR("lsp-problems.severity.error", new Color(0xD32F2F)),
        WARNING("lsp-problems.severity.warning", new Color(0xF57C00)),
        INFO("lsp-problems.severity.info", new Color(0x1976D2));

        private final String labelProperty;
        private final Color color;

        Severity(String labelProperty, Color color) {
            this.labelProperty = labelProperty;
            this.color = color;
        }

        String label() {
            return jEdit.getProperty(labelProperty);
        }

        Color color() {
            return color;
        }
    }

    public final ProblemsHub.Source source;
    public final Severity severity;
    public final String uri;
    public final int line;
    public final int column;
    public final String message;
    private final LspDiagnosticProblem lspProblem;

    private UnifiedProblem(ProblemsHub.Source source, Severity severity, String uri,
                           int line, int column, String message,
                           LspDiagnosticProblem lspProblem) {
        this.source = source;
        this.severity = severity;
        this.uri = uri;
        this.line = line;
        this.column = column;
        this.message = message;
        this.lspProblem = lspProblem;
    }

    static UnifiedProblem fromLsp(LspDiagnosticProblem problem) {
        Severity severity = switch (problem.getSeverity()) {
            case ERROR -> Severity.ERROR;
            case WARNING -> Severity.WARNING;
            default -> Severity.INFO;
        };
        return new UnifiedProblem(
            ProblemsHub.Source.LSP,
            severity,
            problem.getUri(),
            problem.getLine() + 1,
            problem.getCharacter() + 1,
            problem.getMessage(),
            problem);
    }

    static UnifiedProblem fromBuild(BuildProblemsHub.BuildProblem problem) {
        return new UnifiedProblem(
            ProblemsHub.Source.BUILD,
            problem.error ? Severity.ERROR : Severity.WARNING,
            LspDocumentUri.pathToUri(problem.path),
            problem.line,
            problem.column,
            problem.message,
            null);
    }

    static UnifiedProblem fromTest(TestProblemsExporter.ExportedProblem testCase) {
        return new UnifiedProblem(
            ProblemsHub.Source.TEST,
            Severity.ERROR,
            LspDocumentUri.pathToUri(testCase.path),
            testCase.line,
            1,
            testCase.message,
            null);
    }

    LspDiagnosticProblem getLspProblem() {
        return lspProblem;
    }

    int startOffset(Buffer buffer) {
        if (lspProblem != null) {
            return lspProblem.getStartOffset(buffer);
        }
        if (buffer == null) {
            return 0;
        }
        int index = Math.max(0, line - 1);
        if (index >= buffer.getLineCount()) {
            index = Math.max(0, buffer.getLineCount() - 1);
        }
        return buffer.getLineStartOffset(index);
    }

    @Override
    public int compareTo(UnifiedProblem other) {
        int lineCompare = Integer.compare(line, other.line);
        if (lineCompare != 0) {
            return lineCompare;
        }
        return message.compareToIgnoreCase(other.message);
    }
}
