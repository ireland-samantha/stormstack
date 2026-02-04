/*
 * Copyright (c) 2026 Samantha Ireland
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ca.samanthaireland.stormstack.thunder.auth.util;

import java.util.*;

/**
 * Simple JSON parser for basic object and array parsing.
 *
 * <p>This utility avoids external dependencies in the core module.
 * Supports only the limited JSON subset needed for OAuth2 grant parameters.
 */
public final class SimpleJsonParser {

    private SimpleJsonParser() {
    }

    /**
     * Parses a JSON object into a String-to-String map.
     *
     * <p>Only supports simple key-value pairs with string values.
     *
     * @param json the JSON string to parse
     * @return the parsed map
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Map<String, String> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        json = json.trim();

        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Expected JSON object");
        }

        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new HashMap<>();
        int pos = 0;

        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            if (pos >= content.length()) {
                break;
            }

            // Parse key
            if (content.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected '\"' at position " + pos);
            }
            pos++;
            int keyStart = pos;
            while (pos < content.length() && content.charAt(pos) != '"') {
                if (content.charAt(pos) == '\\' && pos + 1 < content.length()) {
                    pos++;  // Skip escaped character
                }
                pos++;
            }
            if (pos >= content.length()) {
                throw new IllegalArgumentException("Unterminated key string");
            }
            String key = unescapeString(content.substring(keyStart, pos));
            pos++;

            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            // Expect colon
            if (pos >= content.length() || content.charAt(pos) != ':') {
                throw new IllegalArgumentException("Expected ':' after key");
            }
            pos++;

            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            // Parse value
            if (pos >= content.length() || content.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected string value at position " + pos);
            }
            pos++;
            int valueStart = pos;
            while (pos < content.length() && content.charAt(pos) != '"') {
                if (content.charAt(pos) == '\\' && pos + 1 < content.length()) {
                    pos++;  // Skip escaped character
                }
                pos++;
            }
            if (pos >= content.length()) {
                throw new IllegalArgumentException("Unterminated value string");
            }
            String value = unescapeString(content.substring(valueStart, pos));
            pos++;

            result.put(key, value);

            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            // Expect comma or end
            if (pos < content.length()) {
                if (content.charAt(pos) == ',') {
                    pos++;
                } else if (content.charAt(pos) != '}') {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
        }

        return result;
    }

    /**
     * Parses a JSON array of strings.
     *
     * @param json the JSON string to parse
     * @return the parsed list of strings
     * @throws IllegalArgumentException if the JSON is invalid
     */
    public static Set<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }

        json = json.trim();

        if (!json.startsWith("[") || !json.endsWith("]")) {
            throw new IllegalArgumentException("Expected JSON array");
        }

        String content = json.substring(1, json.length() - 1).trim();
        if (content.isEmpty()) {
            return Set.of();
        }

        Set<String> result = new HashSet<>();
        int pos = 0;

        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            if (pos >= content.length()) {
                break;
            }

            // Parse string value
            if (content.charAt(pos) != '"') {
                throw new IllegalArgumentException("Expected string value at position " + pos);
            }
            pos++;
            int valueStart = pos;
            while (pos < content.length() && content.charAt(pos) != '"') {
                if (content.charAt(pos) == '\\' && pos + 1 < content.length()) {
                    pos++;  // Skip escaped character
                }
                pos++;
            }
            if (pos >= content.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            String value = unescapeString(content.substring(valueStart, pos));
            pos++;

            result.add(value);

            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) {
                pos++;
            }

            // Expect comma or end
            if (pos < content.length()) {
                if (content.charAt(pos) == ',') {
                    pos++;
                } else if (content.charAt(pos) != ']') {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
        }

        return result;
    }

    private static String unescapeString(String s) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"':
                        result.append('"');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    default:
                        result.append(next);
                }
                i += 2;
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
}
