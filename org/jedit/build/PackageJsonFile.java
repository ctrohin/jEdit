/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright © 2026 jEdit contributors
 */

package org.jedit.build;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.gjt.sp.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class PackageJsonFile {

    private PackageJsonFile() {}

    static List<String> scriptNames(File packageJson) {
        if (packageJson == null || !packageJson.isFile()) {
            return List.of();
        }
        try (Reader reader = new FileReader(packageJson)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return List.of();
            }
            JsonObject object = root.getAsJsonObject();
            JsonElement scripts = object.get("scripts");
            if (scripts == null || !scripts.isJsonObject()) {
                return List.of();
            }
            Map<String, String> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : scripts.getAsJsonObject().entrySet()) {
                sorted.put(entry.getKey(), entry.getValue().getAsString());
            }
            return new ArrayList<>(sorted.keySet());
        } catch (Exception ex) {
            Log.log(Log.WARNING, PackageJsonFile.class,
                "Could not parse " + packageJson, ex);
            return Collections.emptyList();
        }
    }
}
