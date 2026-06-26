/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class NpmTestFramework {

    private NpmTestFramework() {}

    static boolean usesVitest(File packageJson) {
        if (packageJson == null || !packageJson.isFile()) {
            return false;
        }
        try (Reader reader = new FileReader(packageJson)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject object = root.getAsJsonObject();
            return hasDependency(object, "vitest")
                || scriptContains(object, "vitest");
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean usesJest(File packageJson) {
        if (packageJson == null || !packageJson.isFile()) {
            return false;
        }
        try (Reader reader = new FileReader(packageJson)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return false;
            }
            JsonObject object = root.getAsJsonObject();
            return hasDependency(object, "jest")
                || scriptContains(object, "jest");
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean hasDependency(JsonObject object, String name) {
        for (String section : new String[] {"dependencies", "devDependencies"}) {
            JsonElement deps = object.get(section);
            if (deps != null && deps.isJsonObject() && deps.getAsJsonObject().has(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean scriptContains(JsonObject object, String token) {
        JsonElement scripts = object.get("scripts");
        if (scripts == null || !scripts.isJsonObject()) {
            return false;
        }
        for (var entry : scripts.getAsJsonObject().entrySet()) {
            String value = entry.getValue().getAsString();
            if (value != null && value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
