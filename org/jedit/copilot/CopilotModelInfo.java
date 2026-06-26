/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.copilot;

import org.gjt.sp.jedit.jEdit;

final class CopilotModelInfo {

    static CopilotModelInfo accountDefault() {
        return new CopilotModelInfo(null,
            jEdit.getProperty("copilot.model.default"),
            jEdit.getProperty("copilot.model.default.description"));
    }

    private final String id;
    private final String displayName;
    private final String description;

    CopilotModelInfo(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    String id() {
        return id;
    }

    String displayName() {
        return displayName;
    }

    String description() {
        return description;
    }

    boolean isAccountDefault() {
        return id == null || id.isBlank();
    }

    static CopilotModelInfo fromBridgeJson(String id, String name, String description) {
        String display = name != null && !name.isBlank() ? name : id;
        return new CopilotModelInfo(id, display, description);
    }

    static boolean isDuplicateAuto(CopilotModelInfo model) {
        if (model == null || model.isAccountDefault()) {
            return true;
        }
        String autoLabel = jEdit.getProperty("copilot.model.default");
        if (model.displayName() != null
            && model.displayName().equalsIgnoreCase(autoLabel)) {
            return true;
        }
        String modelId = model.id();
        return modelId != null
            && (modelId.equalsIgnoreCase("default") || modelId.equalsIgnoreCase("auto"));
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : id;
    }
}
