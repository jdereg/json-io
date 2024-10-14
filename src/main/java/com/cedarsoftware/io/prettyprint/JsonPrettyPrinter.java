package com.cedarsoftware.io.prettyprint;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class JsonPrettyPrinter {
    /**
     * Takes a compact JSON string and returns a pretty-printed version with proper indentation.
     *
     * @param json The compact JSON string.
     * @return A pretty-printed JSON string.
     */
    public static String prettyPrint(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        json = json.trim();

        // Check if the root is a primitive (number, boolean, string, or null)
        if (isPrimitive(json)) {
            // For primitives, return the trimmed string
            return json;
        }

        StringBuilder prettyJson = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char currentChar = json.charAt(i);

            if (escape) {
                prettyJson.append(currentChar);
                escape = false;
                continue;
            }

            if (currentChar == '\\') {
                escape = true;
                prettyJson.append(currentChar);
                continue;
            }

            if (currentChar == '\"') {
                inString = !inString;
                prettyJson.append(currentChar);
                continue;
            }

            if (!inString) {
                switch (currentChar) {
                    case '{':
                    case '[':
                        prettyJson.append(currentChar);
                        // Peek ahead to see if the next non-whitespace character is '}' or ']'
                        if (!isNextCharClosing(json, i)) {
                            prettyJson.append('\n');
                            indentLevel++;
                            appendIndentation(prettyJson, indentLevel);
                        }
                        break;
                    case '}':
                    case ']':
                        // Newline before the closing brace/bracket if the previous non-whitespace character is not '{' or '['
                        if (!isPrevCharOpening(json, i)) {
                            prettyJson.append('\n');
                            indentLevel--;
                            appendIndentation(prettyJson, indentLevel);
                        } else {
                            // Decrease indentation level even if no newline is added
                            indentLevel--;
                        }
                        prettyJson.append(currentChar);
                        break;
                    case ',':
                        prettyJson.append(currentChar);
                        // Peek ahead to see if the next non-whitespace character is not '}' or ']'
                        if (!isNextCharClosing(json, i)) {
                            prettyJson.append('\n');
                            appendIndentation(prettyJson, indentLevel);
                        }
                        break;
                    case ':':
                        prettyJson.append(currentChar);
                        // prettyJson.append(' '); // uncomment for space after the colon in "field": "value"
                        break;
                    default:
                        if (!Character.isWhitespace(currentChar)) {
                            prettyJson.append(currentChar);
                        }
                        break;
                }
            } else {
                prettyJson.append(currentChar);
            }
        }

        return prettyJson.toString();
    }

    /**
     * Checks if the given JSON string represents a primitive value.
     *
     * @param json The JSON string.
     * @return True if the JSON is a primitive, false otherwise.
     */
    private static boolean isPrimitive(String json) {
        // Check for JSON null, boolean, number, or string
        return isJsonNull(json) || isJsonBoolean(json) || isJsonNumber(json) || isJsonString(json);
    }

    private static boolean isJsonNull(String json) {
        return "null".equals(json);
    }

    private static boolean isJsonBoolean(String json) {
        return "true".equals(json) || "false".equals(json);
    }

    private static boolean isJsonNumber(String json) {
        try {
            Double.parseDouble(json);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isJsonString(String json) {
        return json.startsWith("\"") && json.endsWith("\"");
    }

    /**
     * Checks if the next non-whitespace character is a closing brace '}' or bracket ']'.
     *
     * @param json The JSON string.
     * @param index The current index.
     * @return True if the next non-whitespace character is '}' or ']', false otherwise.
     */
    private static boolean isNextCharClosing(String json, int index) {
        for (int i = index + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '}' || c == ']';
        }
        return false;
    }

    /**
     * Checks if the previous non-whitespace character is an opening brace '{' or bracket '['.
     *
     * @param json The JSON string.
     * @param index The current index.
     * @return True if the previous non-whitespace character is '{' or '[', false otherwise.
     */
    private static boolean isPrevCharOpening(String json, int index) {
        for (int i = index - 1; i >= 0; i--) {
            char c = json.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '{' || c == '[';
        }
        return false;
    }

    /**
     * Appends indentation spaces to the StringBuilder based on the current indentation level.
     *
     * @param sb          The StringBuilder to append to.
     * @param indentLevel The current indentation level.
     */
    private static void appendIndentation(StringBuilder sb, int indentLevel) {
        final int INDENT_SIZE = 2; // Number of spaces for each indentation level
        for (int i = 0; i < indentLevel * INDENT_SIZE; i++) {
            sb.append(' ');
        }
    }
}