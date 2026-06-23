/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.awt.Color;

enum TestCaseStatus {
    PASSED("test-results.status.passed", new Color(0x2E, 0x7D, 0x32)),
    FAILED("test-results.status.failed", new Color(0xC6, 0x28, 0x28)),
    ERROR("test-results.status.error", new Color(0xC6, 0x28, 0x28)),
    SKIPPED("test-results.status.skipped", new Color(0xF5, 0x7F, 0x17)),
    DISCOVERED("test-results.status.discovered", new Color(0x75, 0x75, 0x75));

    private final String labelProperty;
    private final Color color;

    TestCaseStatus(String labelProperty, Color color) {
        this.labelProperty = labelProperty;
        this.color = color;
    }

    String labelProperty() {
        return labelProperty;
    }

    Color color() {
        return color;
    }
}
