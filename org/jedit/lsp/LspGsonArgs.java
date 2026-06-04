/*
 * jEdit - Programmer's Text Editor
 * :tabSize=8:indentSize=8:noTabs=false:
 * :folding=explicit:collapseFolds=1:
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * LSP4J deserializes {@code Command#getArguments()} with Gson; values are often
 * {@link JsonObject}, not {@link Map}. These helpers convert to plain Java types
 * for reading and for {@code workspace/executeCommand} outbound payloads.
 */
final class LspGsonArgs {

    private LspGsonArgs() {}

    static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof JsonObject) {
            return jsonObjectToMap((JsonObject) value);
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String) {
                    result.put((String) entry.getKey(), toJavaValue(entry.getValue()));
                }
            }
            return result;
        }
        return null;
    }

    static String asString(Object value) {
        if (value instanceof JsonPrimitive) {
            JsonPrimitive primitive = (JsonPrimitive) value;
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    static Integer asInteger(Object value) {
        if (value == null || value instanceof JsonNull) {
            return null;
        }
        if (value instanceof JsonPrimitive) {
            JsonPrimitive primitive = (JsonPrimitive) value;
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            }
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    static Map<String, Object> copyFirstArgumentMap(List<Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        return asStringObjectMap(arguments.get(0));
    }

    static List<Object> toExecuteArguments(List<Object> arguments) {
        if (arguments == null) {
            return null;
        }
        List<Object> converted = new ArrayList<>(arguments.size());
        for (Object argument : arguments) {
            converted.add(toJavaValue(argument));
        }
        return converted;
    }

    static Object toJavaValue(Object value) {
        if (value instanceof JsonElement) {
            return jsonElementToJava((JsonElement) value);
        }
        if (value instanceof Map) {
            return asStringObjectMap(value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(toJavaValue(item));
            }
            return converted;
        }
        return value;
    }

    private static Object jsonElementToJava(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isNumber()) {
                return primitive.getAsNumber();
            }
            if (primitive.isString()) {
                return primitive.getAsString();
            }
        }
        if (element.isJsonObject()) {
            return jsonObjectToMap(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(jsonElementToJava(item));
            }
            return list;
        }
        return null;
    }

    private static Map<String, Object> jsonObjectToMap(JsonObject jsonObject) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            map.put(entry.getKey(), jsonElementToJava(entry.getValue()));
        }
        return map;
    }
}
