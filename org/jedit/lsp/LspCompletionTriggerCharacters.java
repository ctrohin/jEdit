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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Completion trigger characters for each supported LSP mode.
 * <p>
 * Single-character triggers fire when that character is typed. Multi-character
 * sequences (for example {@code </} in XML) fire when the last character of the
 * sequence is typed and the preceding buffer text matches the prefix.
 */
public final class LspCompletionTriggerCharacters {

    /**
     * A trigger specification for one LSP mode.
     *
     * @param characters single characters that trigger completion when typed
     * @param sequences multi-character sequences; the last character must also
     *                  appear in {@code characters} (for example {@code </} uses
     *                  {@code /} after {@code <})
     */
    public record TriggerSpec(List<String> characters, List<String> sequences) {
        public TriggerSpec {
            characters = List.copyOf(characters);
            sequences = List.copyOf(sequences);
        }
    }

    private static final Map<String, TriggerSpec> BY_MODE = buildMap();

    private LspCompletionTriggerCharacters() {}

    /**
     * Returns trigger characters for the given LSP mode, or an empty spec if unknown.
     */
    public static TriggerSpec forMode(String modeName) {
        TriggerSpec spec = BY_MODE.get(normalizeMode(modeName));
        return spec != null ? spec : new TriggerSpec(List.of(), List.of());
    }

    /**
     * Single trigger characters advertised to LSP servers during initialize.
     */
    public static List<String> charactersForMode(String modeName) {
        return forMode(modeName).characters();
    }

    /**
     * All supported LSP mode names that have trigger definitions.
     */
    public static Map<String, TriggerSpec> allModes() {
        return Collections.unmodifiableMap(BY_MODE);
    }

    /**
     * Returns the LSP trigger character to report when the user typed {@code ch}
     * at {@code caret}, or {@code null} if completion should not be triggered.
     */
    public static String resolveTrigger(String modeName, CharSequence text, int caret, char ch) {
        TriggerSpec spec = forMode(modeName);
        if (spec.characters().isEmpty()) {
            return null;
        }

        String typed = String.valueOf(ch);
        if (!spec.characters().contains(typed)) {
            return null;
        }

        for (String sequence : spec.sequences()) {
            if (!sequence.isEmpty()
                && sequence.charAt(sequence.length() - 1) == ch
                && caret >= sequence.length()
                && regionMatches(text, caret - sequence.length(), sequence)) {
                return typed;
            }
        }

        if (spec.sequences().isEmpty() || !isSequenceSuffixChar(spec, ch)) {
            return typed;
        }

        return isPartOfUnmatchedSequence(spec, text, caret, ch) ? null : typed;
    }

    private static boolean isSequenceSuffixChar(TriggerSpec spec, char ch) {
        for (String sequence : spec.sequences()) {
            if (!sequence.isEmpty() && sequence.charAt(sequence.length() - 1) == ch) {
                return true;
            }
        }
        return false;
    }

    /**
     * When a character ends a multi-char sequence, only trigger if the typed
     * character is not waiting for more sequence input (e.g. {@code <} before {@code /}).
     */
    private static boolean isPartOfUnmatchedSequence(
        TriggerSpec spec, CharSequence text, int caret, char ch) {
        for (String sequence : spec.sequences()) {
            if (sequence.length() < 2) {
                continue;
            }
            for (int prefixLen = 1; prefixLen < sequence.length(); prefixLen++) {
                if (sequence.charAt(prefixLen) == ch
                    && caret >= prefixLen
                    && regionMatches(text, caret - prefixLen, sequence.substring(0, prefixLen))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean regionMatches(CharSequence text, int offset, String value) {
        if (offset < 0 || offset + value.length() > text.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (text.charAt(offset + i) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeMode(String modeName) {
        if (modeName == null) {
            return null;
        }
        return switch (modeName) {
            case "cpp" -> "c++";
            case "typescript" -> "javascript";
            default -> modeName;
        };
    }

    private static Map<String, TriggerSpec> buildMap() {
        Map<String, TriggerSpec> map = new LinkedHashMap<>();

        // External language servers
        map.put("python", spec(
            ".", "'", "\""
        ));
        map.put("java", spec(
            ".", "@", ":", "<", "(", "\"", "'", "/", "*", "#"
        ));
        map.put("c", spec(
            ".", ">", ":", "/", "*", "<", "\"", "'"
        ));
        map.put("c++", spec(
            ".", ">", ":", "/", "*", "<", "\"", "'", "-", "~"
        ));
        map.put("javascript", spec(
            ".", "\"", "'", "/", "@", "<", ":", "-", "#", "`"
        ));
        map.put("dart", spec(
            "."
        ));
        map.put("rust", spec(
            ".", ":", "'", "\""
        ));

        // Built-in build / package-manager config servers
        map.put("maven", xmlSpec());
        map.put("ant", xmlSpec());
        map.put("json", spec(
            "\"", ":", ",", "{", "[", "@"
        ));
        map.put("yaml", spec(
            ":", "-", "\"", "'"
        ));
        map.put("gradle", spec(
            ".", "(", "\"", "'"
        ));
        map.put("pip", spec(
            "-", "=", "[", ","
        ));

        return Map.copyOf(map);
    }

    private static TriggerSpec xmlSpec() {
        return new TriggerSpec(
            List.of("<", "/", ":", "\"", "'", "="),
            List.of("</"));
    }

    private static TriggerSpec spec(String... characters) {
        return new TriggerSpec(List.of(characters), List.of());
    }
}
