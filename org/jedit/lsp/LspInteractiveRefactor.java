/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.jedit.lsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.View;
import org.gjt.sp.jedit.browser.VFSBrowser;
import org.gjt.sp.util.Log;

/**
 * Prompts for Dart-style interactive refactor parameters stored in
 * {@link CodeAction#getData()} under {@code parameters}.
 */
final class LspInteractiveRefactor {

    private LspInteractiveRefactor() {}

    /**
     * Returns a command with user-provided parameter values, the original command
     * when no prompting is required, or {@code null} if the user cancelled.
     */
    static Command applyInteractiveParameters(View view, CodeAction action, Command command) {
        if (view == null || command == null || action == null) {
            return command;
        }
        if (!isRefactorKind(action.getKind())) {
            return command;
        }

        List<Map<String, Object>> parameters = getInteractiveParameters(action);
        Map<String, Object> argObject = getCommandArgumentObject(command);
        if (parameters == null || argObject == null) {
            return command;
        }

        List<Object> argValues = getArgumentValues(argObject);
        if (argValues == null || parameters.size() != argValues.size()) {
            return command;
        }

        List<Object> newValues = new ArrayList<>(argValues);
        for (int i = 0; i < parameters.size(); i++) {
            PromptResult prompt = promptForParameter(view, parameters.get(i));
            if (prompt.cancelled) {
                return null;
            }
            newValues.set(i, prompt.value);
        }

        Map<String, Object> newArgObject = new LinkedHashMap<>(argObject);
        newArgObject.put("arguments", newValues);
        return new Command(command.getTitle(), command.getCommand(), List.of(newArgObject));
    }

    private static boolean isRefactorKind(String kind) {
        if (kind == null) {
            return false;
        }
        return kind.equals(CodeActionKind.Refactor)
            || kind.startsWith(CodeActionKind.Refactor + ".");
    }

    private static List<Map<String, Object>> getInteractiveParameters(CodeAction action) {
        Map<String, Object> data = LspGsonArgs.asStringObjectMap(action.getData());
        if (data == null) {
            return null;
        }
        Object rawParameters = data.get("parameters");
        if (!(rawParameters instanceof List)) {
            return null;
        }
        List<?> parameterList = (List<?>) rawParameters;
        List<Map<String, Object>> parameters = new ArrayList<>(parameterList.size());
        for (Object item : parameterList) {
            Map<String, Object> parameter = LspGsonArgs.asStringObjectMap(item);
            if (parameter == null) {
                return null;
            }
            parameters.add(parameter);
        }
        return parameters;
    }

    private static Map<String, Object> getCommandArgumentObject(Command command) {
        List<Object> arguments = command.getArguments();
        if (arguments == null || arguments.size() != 1) {
            return null;
        }
        Map<String, Object> argObject = LspGsonArgs.asStringObjectMap(arguments.get(0));
        if (argObject == null || !(argObject.get("arguments") instanceof List)) {
            return null;
        }
        return argObject;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getArgumentValues(Map<String, Object> argObject) {
        Object rawValues = argObject.get("arguments");
        if (!(rawValues instanceof List)) {
            return null;
        }
        return new ArrayList<>((List<Object>) rawValues);
    }

    private static PromptResult promptForParameter(View view, Map<String, Object> parameter) {
        String kind = LspGsonArgs.asString(parameter.get("kind"));
        if (kind == null) {
            return PromptResult.value(parameter.get("defaultValue"));
        }

        switch (kind) {
            case "saveUri":
                return promptSaveUri(view, parameter);
            case "openUri":
                return promptOpenUri(view, parameter);
            case "string":
            case "text":
                return promptString(view, parameter);
            case "boolean":
                return promptBoolean(view, parameter);
            case "pick":
            case "selection":
                return promptPick(view, parameter);
            default:
                Log.log(Log.WARNING, LspInteractiveRefactor.class,
                    "Unsupported interactive refactor parameter kind: " + kind
                        + "; using default value");
                return PromptResult.value(parameter.get("defaultValue"));
        }
    }

    private static PromptResult promptSaveUri(View view, Map<String, Object> parameter) {
        String defaultUri = LspGsonArgs.asString(parameter.get("defaultValue"));
        String initialPath = LspDocumentUri.uriToPath(defaultUri);
        if (initialPath == null && defaultUri != null) {
            initialPath = defaultUri;
        }

        String[] selected = GUIUtilities.showVFSFileDialog(
            view, initialPath, VFSBrowser.SAVE_DIALOG, false);
        if (selected == null || selected.length == 0 || selected[0] == null) {
            return PromptResult.cancelled();
        }
        return PromptResult.value(normalizeFileUri(selected[0]));
    }

    private static PromptResult promptOpenUri(View view, Map<String, Object> parameter) {
        String defaultUri = LspGsonArgs.asString(parameter.get("defaultValue"));
        String initialPath = LspDocumentUri.uriToPath(defaultUri);
        if (initialPath == null && defaultUri != null) {
            initialPath = defaultUri;
        }

        String[] selected = GUIUtilities.showVFSFileDialog(
            view, initialPath, VFSBrowser.OPEN_DIALOG, false);
        if (selected == null || selected.length == 0 || selected[0] == null) {
            return PromptResult.cancelled();
        }
        return PromptResult.value(normalizeFileUri(selected[0]));
    }

    private static PromptResult promptString(View view, Map<String, Object> parameter) {
        String label = firstNonEmpty(
            LspGsonArgs.asString(parameter.get("parameterLabel")),
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            "Value");
        Object defaultValue = parameter.get("defaultValue");
        String initial = defaultValue != null ? String.valueOf(defaultValue) : "";

        String value = (String) JOptionPane.showInputDialog(
            view,
            label,
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            initial);
        if (value == null) {
            return PromptResult.cancelled();
        }
        return PromptResult.value(value);
    }

    private static PromptResult promptBoolean(View view, Map<String, Object> parameter) {
        String message = firstNonEmpty(
            LspGsonArgs.asString(parameter.get("parameterLabel")),
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            "Continue?");
        int choice = JOptionPane.showConfirmDialog(
            view,
            message,
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.CLOSED_OPTION || choice == JOptionPane.CANCEL_OPTION) {
            return PromptResult.cancelled();
        }
        return PromptResult.value(choice == JOptionPane.YES_OPTION);
    }

    private static PromptResult promptPick(View view, Map<String, Object> parameter) {
        List<String> values = getStringValues(parameter.get("values"));
        if (values.isEmpty()) {
            return PromptResult.value(parameter.get("defaultValue"));
        }

        String message = firstNonEmpty(
            LspGsonArgs.asString(parameter.get("parameterLabel")),
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            "Choose an option");
        Object selected = JOptionPane.showInputDialog(
            view,
            message,
            LspGsonArgs.asString(parameter.get("parameterTitle")),
            JOptionPane.QUESTION_MESSAGE,
            null,
            values.toArray(),
            values.get(0));
        if (selected == null) {
            return PromptResult.cancelled();
        }
        return PromptResult.value(selected);
    }

    private static List<String> getStringValues(Object rawValues) {
        List<String> values = new ArrayList<>();
        if (!(rawValues instanceof List)) {
            return values;
        }
        for (Object item : (List<?>) rawValues) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    private static String normalizeFileUri(String path) {
        String uri = LspDocumentUri.pathToUri(path);
        if (uri == null) {
            return path;
        }
        if (uri.startsWith("file:///") && uri.length() > 8) {
            char drive = uri.charAt(8);
            if (drive >= 'a' && drive <= 'z') {
                return "file:///" + Character.toUpperCase(drive) + uri.substring(9);
            }
        }
        return uri;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static final class PromptResult {
        final boolean cancelled;
        final Object value;

        private PromptResult(boolean cancelled, Object value) {
            this.cancelled = cancelled;
            this.value = value;
        }

        static PromptResult cancelled() {
            return new PromptResult(true, null);
        }

        static PromptResult value(Object value) {
            return new PromptResult(false, value);
        }
    }
}
