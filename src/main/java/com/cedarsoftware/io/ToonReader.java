package com.cedarsoftware.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Parse TOON (Token-Oriented Object Notation) format into JsonObject structures.
 * <p>
 * ToonReader produces the same JsonObject/Object[]/primitive structures as JsonParser,
 * allowing seamless integration with the existing Resolver infrastructure for type conversion.
 * <p>
 * TOON format characteristics:
 * <ul>
 *   <li>Indentation-based structure (2 spaces = 1 level)</li>
 *   <li>key: value syntax for objects</li>
 *   <li>Inline arrays: [N]: elem1,elem2,elem3</li>
 *   <li>List arrays: [N]: followed by - elem lines</li>
 *   <li>Minimal quoting (only when necessary)</li>
 *   <li>5 escape sequences: \\, \", \n, \r, \t</li>
 * </ul>
 *
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
public class ToonReader {

    private static final int INDENT_SIZE = 2;  // 2 spaces per indent level (matches ToonWriter)
    private static final char DELIMITER = ','; // Default delimiter (matches ToonWriter)

    private final BufferedReader reader;
    private final ReadOptions readOptions;

    // Line management - supports peek/consume pattern
    private String currentLine = null;
    private boolean lineConsumed = true;
    private int lineNumber = 0;

    /**
     * Create a ToonReader that reads from a Reader.
     *
     * @param reader the reader to read TOON content from
     * @param readOptions configuration options (may be null for defaults)
     */
    public ToonReader(Reader reader, ReadOptions readOptions) {
        this.reader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
    }

    /**
     * Read the complete TOON value.
     *
     * @param suggestedType optional type hint for the Resolver
     * @return parsed value (JsonObject, Object[], or primitive)
     */
    public Object readValue(Type suggestedType) {
        try {
            String line = peekLine();
            if (line == null) {
                // Empty input - TOON spec: empty documents yield {}
                JsonObject emptyMap = new JsonObject();
                if (suggestedType != null) {
                    emptyMap.setType(suggestedType);
                }
                return emptyMap;
            }

            // Determine type from first line content
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                consumeLine();
                return readValue(suggestedType);  // Skip empty lines (recurses until non-empty or EOF)
            }

            // Check for empty map syntax: {}
            if ("{}".equals(trimmed)) {
                consumeLine();
                JsonObject emptyMap = new JsonObject();
                if (suggestedType != null) {
                    emptyMap.setType(suggestedType);
                }
                return emptyMap;
            }

            // Check for array syntax: [N]: ...
            if (isArrayStart(trimmed)) {
                return readArray(0, suggestedType);
            }

            // Check for object syntax: key: value
            int colonPos = findColonPosition(trimmed);
            if (colonPos > 0) {
                return readObject(0, suggestedType);
            }

            // Single scalar value
            consumeLine();
            return readScalar(trimmed);
        } catch (IOException e) {
            throw new JsonIoException("Error reading TOON input at line " + lineNumber, e);
        }
    }

    // ========== Object Parsing ==========

    /**
     * Read an object as key: value pairs at the given indentation level.
     *
     * @param baseIndent the base indentation level
     * @param suggestedType type hint for the Resolver
     * @return JsonObject containing the parsed fields
     */
    JsonObject readObject(int baseIndent, Type suggestedType) throws IOException {
        JsonObject jsonObj = new JsonObject();
        if (suggestedType != null) {
            jsonObj.setType(suggestedType);
        }

        while (true) {
            String line = peekLine();
            if (line == null) {
                break;  // EOF
            }

            int indent = getIndentLevel(line);
            if (indent < baseIndent) {
                break;  // Back to parent level
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                consumeLine();
                continue;  // Skip empty lines
            }

            // Must be at exactly our indent level for keys at this level
            if (indent > baseIndent) {
                break;  // This line belongs to a nested structure
            }

            // Parse key: value
            int colonPos = findColonPosition(trimmed);
            if (colonPos <= 0) {
                break;  // Not a key: value pair
            }

            consumeLine();
            String key = trimmed.substring(0, colonPos).trim();
            String valuePart = trimmed.substring(colonPos + 1).trim();

            // Unquote key if needed
            key = unquoteString(key);

            // Check for nested structure (value is empty, next line is indented more)
            if (valuePart.isEmpty()) {
                String nextLine = peekLine();
                if (nextLine != null && getIndentLevel(nextLine) > baseIndent) {
                    String nextTrimmed = nextLine.trim();
                    if (isArrayStart(nextTrimmed)) {
                        jsonObj.put(key, readArray(baseIndent + 1, null));
                    } else {
                        jsonObj.put(key, readObject(baseIndent + 1, null));
                    }
                } else {
                    jsonObj.put(key, null);  // Empty value
                }
            } else if (isArrayStart(valuePart)) {
                // Inline array on same line as key
                jsonObj.put(key, parseArrayFromLine(valuePart));
            } else if ("{}".equals(valuePart)) {
                // Empty map as value
                jsonObj.put(key, new JsonObject());
            } else {
                // Scalar value
                jsonObj.put(key, readScalar(valuePart));
            }
        }

        return jsonObj;
    }

    // ========== Array Parsing ==========

    /**
     * Check if a line starts with array syntax [N]:
     */
    private boolean isArrayStart(String trimmed) {
        return trimmed.startsWith("[") && trimmed.contains("]:");
    }

    /**
     * Read an array starting at the given indentation level.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    List<Object> readArray(int baseIndent, Type suggestedType) throws IOException {
        String line = peekLine();
        if (line == null) {
            return new ArrayList<>();
        }

        String trimmed = line.trim();
        consumeLine();

        return parseArrayFromLine(trimmed);
    }

    /**
     * Parse an array from a line containing [N]: ...
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> parseArrayFromLine(String trimmed) throws IOException {
        // Extract count from [N]:
        int bracketEnd = trimmed.indexOf(']');
        if (bracketEnd < 0) {
            throw new JsonIoException("Malformed array syntax at line " + lineNumber + ": " + trimmed);
        }

        String countStr = trimmed.substring(1, bracketEnd);
        int count;
        try {
            count = Integer.parseInt(countStr);
        } catch (NumberFormatException e) {
            throw new JsonIoException("Invalid array count at line " + lineNumber + ": " + countStr);
        }

        // Handle empty array
        if (count == 0) {
            return new ArrayList<>();
        }

        // Get content after [N]:
        int colonPos = trimmed.indexOf(':', bracketEnd);
        if (colonPos < 0) {
            throw new JsonIoException("Malformed array syntax (missing colon) at line " + lineNumber);
        }

        String content = trimmed.substring(colonPos + 1).trim();

        if (!content.isEmpty()) {
            // Inline array: [N]: elem1,elem2,elem3
            return readInlineArray(content, count);
        } else {
            // List format array: [N]: followed by - elem lines
            return readListArray(count);
        }
    }

    /**
     * Read an inline array: [N]: elem1,elem2,elem3
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    List<Object> readInlineArray(String content, int count) {
        List<Object> elements = new ArrayList<>(count);
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                current.append(c);
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
                continue;
            }

            if (c == DELIMITER && !inQuotes) {
                elements.add(readScalar(current.toString().trim()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        // Add last element
        if (current.length() > 0 || elements.size() < count) {
            elements.add(readScalar(current.toString().trim()));
        }

        return elements;
    }

    /**
     * Read a list format array with - element lines.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    List<Object> readListArray(int count) throws IOException {
        List<Object> elements = new ArrayList<>(count);
        int baseIndent = -1;

        while (elements.size() < count) {
            String line = peekLine();
            if (line == null) {
                break;  // EOF
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                consumeLine();
                continue;
            }

            int indent = getIndentLevel(line);

            // First element determines base indent
            if (baseIndent < 0) {
                baseIndent = indent;
            } else if (indent < baseIndent) {
                break;  // Back to parent level
            }

            // Must start with -
            if (!trimmed.startsWith("-")) {
                break;  // Not a list element
            }

            consumeLine();
            String elementContent = trimmed.substring(1).trim();

            if (elementContent.isEmpty()) {
                // Nested object/array on next lines
                String nextLine = peekLine();
                if (nextLine != null) {
                    String nextTrimmed = nextLine.trim();
                    int nextIndent = getIndentLevel(nextLine);
                    if (nextIndent > indent) {
                        if (isArrayStart(nextTrimmed)) {
                            elements.add(readArray(nextIndent, null));
                        } else if (findColonPosition(nextTrimmed) > 0) {
                            elements.add(readObject(nextIndent, null));
                        } else {
                            elements.add(null);
                        }
                    } else {
                        elements.add(null);
                    }
                } else {
                    elements.add(null);
                }
            } else if (isArrayStart(elementContent)) {
                elements.add(parseArrayFromLine(elementContent));
            } else if (findColonPosition(elementContent) > 0 && !elementContent.startsWith("\"")) {
                // Inline object - but this shouldn't happen in TOON format
                // The value after - should be a scalar or trigger nested parsing
                elements.add(readScalar(elementContent));
            } else {
                elements.add(readScalar(elementContent));
            }
        }

        return elements;
    }

    // ========== Scalar Parsing ==========

    /**
     * Parse a scalar value (null, boolean, number, or string).
     */
    Object readScalar(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // Handle null
        if ("null".equals(text)) {
            return null;
        }

        // Handle booleans
        if ("true".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equals(text)) {
            return Boolean.FALSE;
        }

        // Handle quoted strings
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return parseQuotedString(text);
        }

        // Try to parse as number
        Number num = parseNumber(text);
        if (num != null) {
            return num;
        }

        // Unquoted string
        return text;
    }

    /**
     * Parse a quoted string, handling escape sequences.
     * Only 5 valid escapes: \\, \", \n, \r, \t
     */
    String parseQuotedString(String text) {
        StringBuilder sb = new StringBuilder();
        int len = text.length();

        // Skip opening and closing quotes
        for (int i = 1; i < len - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < len - 1) {
                char next = text.charAt(++i);
                switch (next) {
                    case '\\':
                        sb.append('\\');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    default:
                        throw new JsonIoException("Invalid escape sequence: \\" + next + " at line " + lineNumber);
                }
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Try to parse text as a number.
     * Returns Long for integers, Double for decimals, null if not a number.
     */
    Number parseNumber(String text) {
        if (text.isEmpty()) {
            return null;
        }

        char first = text.charAt(0);
        if (!Character.isDigit(first) && first != '-' && first != '+' && first != '.') {
            return null;
        }

        try {
            // Check if it contains decimal point or exponent
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                return Double.parseDouble(text);
            } else {
                // Try long first
                return Long.parseLong(text);
            }
        } catch (NumberFormatException e) {
            return null;  // Not a valid number
        }
    }

    // ========== Line Management ==========

    /**
     * Peek at the next line without consuming it.
     */
    String peekLine() throws IOException {
        if (lineConsumed) {
            currentLine = reader.readLine();
            lineConsumed = false;
            if (currentLine != null) {
                lineNumber++;
            }
        }
        return currentLine;
    }

    /**
     * Consume the current line (move to next).
     */
    void consumeLine() {
        lineConsumed = true;
    }

    /**
     * Get the indentation level of a line (number of leading spaces / 2).
     */
    int getIndentLevel(String line) {
        if (line == null) {
            return 0;
        }
        int spaces = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                spaces++;
            } else {
                break;
            }
        }
        return spaces / INDENT_SIZE;
    }

    /**
     * Find the position of the colon in a key: value pair.
     * Returns -1 if no valid colon found (ignores colons inside quoted strings).
     */
    private int findColonPosition(String text) {
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (c == ':' && !inQuotes) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Unquote a string if it's quoted.
     */
    private String unquoteString(String text) {
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2) {
            return parseQuotedString(text);
        }
        return text;
    }
}
