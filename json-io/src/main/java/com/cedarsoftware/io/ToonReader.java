package com.cedarsoftware.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import com.cedarsoftware.util.MathUtilities;

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

            // Check for combined field+array notation: fieldName[N]: or fieldName[N]{cols}:
            // Also handles folded keys with array: data.items[N]:
            // Per TOON spec, this means 'fieldName' contains an array of N elements
            // Tabular format: fieldName[N]{col1,col2,...}: followed by CSV rows
            if (key.contains("[")) {
                int bracketStart = key.indexOf('[');
                String realKey = key.substring(0, bracketStart);
                String arraySyntax = key.substring(bracketStart) + ":";
                if (!valuePart.isEmpty()) {
                    arraySyntax += " " + valuePart;
                }
                // Check if key was quoted - quoted keys are NOT expanded
                boolean wasQuoted = realKey.startsWith("\"");
                if (wasQuoted) {
                    realKey = unquoteString(realKey);
                }
                // parseArrayFromLine handles inline, list-format, and tabular arrays
                putValue(jsonObj, realKey, parseArrayFromLine(arraySyntax), wasQuoted);
                continue;
            }

            // Check if key was quoted (before unquoting) - quoted keys are NOT expanded
            boolean wasQuoted = key.startsWith("\"");

            // Unquote key if needed
            key = unquoteString(key);

            // Check for nested structure (value is empty, next line is indented more)
            if (valuePart.isEmpty()) {
                String nextLine = peekLine();
                if (nextLine != null && getIndentLevel(nextLine) > baseIndent) {
                    String nextTrimmed = nextLine.trim();
                    if (isArrayStart(nextTrimmed)) {
                        putValue(jsonObj, key, readArray(baseIndent + 1, null), wasQuoted);
                    } else {
                        putValue(jsonObj, key, readObject(baseIndent + 1, null), wasQuoted);
                    }
                } else {
                    putValue(jsonObj, key, null, wasQuoted);  // Empty value
                }
            } else if (isArrayStart(valuePart)) {
                // Inline array on same line as key
                putValue(jsonObj, key, parseArrayFromLine(valuePart), wasQuoted);
            } else if ("{}".equals(valuePart)) {
                // Empty map as value
                putValue(jsonObj, key, new JsonObject(), wasQuoted);
            } else {
                // Scalar value
                putValue(jsonObj, key, readScalar(valuePart), wasQuoted);
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
     * Parse an array from a line containing [N]: ... or [N]{cols}: ...
     * Supports delimiter variants: [N] for comma, [N\t] for tab, [N|] for pipe
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> parseArrayFromLine(String trimmed) throws IOException {
        // Extract count from [N]:
        int bracketEnd = trimmed.indexOf(']');
        if (bracketEnd < 0) {
            throw new JsonIoException("Malformed array syntax at line " + lineNumber + ": " + trimmed);
        }

        String countStr = trimmed.substring(1, bracketEnd);

        // Detect delimiter from count string: [2] = comma, [2\t] = tab, [2|] = pipe
        char delimiter = DELIMITER;  // Default to comma
        if (!countStr.isEmpty()) {
            char lastChar = countStr.charAt(countStr.length() - 1);
            if (lastChar == '\t') {
                delimiter = '\t';
                countStr = countStr.substring(0, countStr.length() - 1);
            } else if (lastChar == '|') {
                delimiter = '|';
                countStr = countStr.substring(0, countStr.length() - 1);
            }
        }

        int count;
        try {
            count = Integer.parseInt(countStr.trim());
        } catch (NumberFormatException e) {
            throw new JsonIoException("Invalid array count at line " + lineNumber + ": " + countStr);
        }

        // Handle empty array
        if (count == 0) {
            return new ArrayList<>();
        }

        // Check for tabular format: [N]{col1,col2,...}:
        int afterBracket = bracketEnd + 1;
        List<String> columnHeaders = null;
        if (afterBracket < trimmed.length() && trimmed.charAt(afterBracket) == '{') {
            int braceEnd = trimmed.indexOf('}', afterBracket);
            if (braceEnd > afterBracket) {
                String headerStr = trimmed.substring(afterBracket + 1, braceEnd);
                columnHeaders = parseColumnHeaders(headerStr, delimiter);
                afterBracket = braceEnd + 1;
            }
        }

        // Get content after [N]: or [N]{cols}:
        int colonPos = trimmed.indexOf(':', afterBracket);
        if (colonPos < 0) {
            throw new JsonIoException("Malformed array syntax (missing colon) at line " + lineNumber);
        }

        String content = trimmed.substring(colonPos + 1).trim();

        if (columnHeaders != null) {
            // Tabular format: [N]{cols}: followed by CSV rows
            return readTabularArray(count, columnHeaders, delimiter);
        } else if (!content.isEmpty()) {
            // Inline array: [N]: elem1,elem2,elem3
            return readInlineArray(content, count, delimiter);
        } else {
            // List format array: [N]: followed by - elem lines
            return readListArray(count);
        }
    }

    /**
     * Parse column headers from a delimiter-separated string.
     */
    private List<String> parseColumnHeaders(String headerStr, char delimiter) {
        List<String> headers = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < headerStr.length(); i++) {
            char c = headerStr.charAt(i);
            if (c == delimiter) {
                headers.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            headers.add(current.toString().trim());
        }

        return headers;
    }

    /**
     * Read a tabular array: rows of delimiter-separated data where each row becomes an object.
     */
    private List<Object> readTabularArray(int count, List<String> columnHeaders, char delimiter) throws IOException {
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

            // First row determines base indent
            if (baseIndent < 0) {
                baseIndent = indent;
            } else if (indent < baseIndent) {
                // Line at lower indent - end of tabular data
                break;
            }

            // Skip lines that look like key: value (they belong to parent object)
            // Check that the line doesn't contain the delimiter (unless it's comma which could be in key:value)
            String delimStr = String.valueOf(delimiter);
            if (findColonPosition(trimmed) > 0 && !trimmed.contains(delimStr)) {
                break;
            }

            consumeLine();

            // Parse the row into an object
            JsonObject rowObj = new JsonObject();
            List<Object> values = readInlineArray(trimmed, columnHeaders.size(), delimiter);

            for (int i = 0; i < columnHeaders.size() && i < values.size(); i++) {
                rowObj.put(columnHeaders.get(i), values.get(i));
            }

            elements.add(rowObj);
        }

        return elements;
    }

    /**
     * Read an inline array: [N]: elem1,elem2,elem3
     * Returns ArrayList instead of Object[] for better Java interoperability.
     * Uses the default comma delimiter.
     */
    List<Object> readInlineArray(String content, int count) {
        return readInlineArray(content, count, DELIMITER);
    }

    /**
     * Read an inline array with a specific delimiter.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    List<Object> readInlineArray(String content, int count, char delimiter) {
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

            if (c == delimiter && !inQuotes) {
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
            } else if ("{}".equals(elementContent)) {
                // Empty object: - {}
                elements.add(new JsonObject());
            } else if (isArrayStart(elementContent)) {
                elements.add(parseArrayFromLine(elementContent));
            } else if (findColonPosition(elementContent) > 0 && !elementContent.startsWith("\"")) {
                // Per TOON spec: first field of object is on hyphen line
                // e.g., "- name: John" followed by "  age: 30" on next line
                // This is an inline object - read it with subsequent fields
                JsonObject inlineObj = readInlineObject(elementContent, indent);
                elements.add(inlineObj);
            } else {
                elements.add(readScalar(elementContent));
            }
        }

        return elements;
    }

    /**
     * Read an inline object where the first field is on the current line.
     * Per TOON spec: "- name: John" followed by indented "age: 30"
     *
     * @param firstFieldLine the first field (e.g., "name: John")
     * @param hyphenIndent the indent level of the hyphen that preceded this
     * @return JsonObject with all fields
     */
    private JsonObject readInlineObject(String firstFieldLine, int hyphenIndent) throws IOException {
        JsonObject jsonObj = new JsonObject();

        // Parse first field from the provided line
        int colonPos = findColonPosition(firstFieldLine);
        if (colonPos > 0) {
            String key = firstFieldLine.substring(0, colonPos).trim();
            String valuePart = firstFieldLine.substring(colonPos + 1).trim();

            // Check for combined field+array notation: fieldName[N]: or fieldName[N]{cols}:
            // Also handles folded keys: data.items[N]:
            if (key.contains("[")) {
                int bracketStart = key.indexOf('[');
                String realKey = key.substring(0, bracketStart);
                String arraySyntax = key.substring(bracketStart) + ":";
                if (!valuePart.isEmpty()) {
                    arraySyntax += " " + valuePart;
                }
                boolean wasQuoted = realKey.startsWith("\"");
                if (wasQuoted) {
                    realKey = unquoteString(realKey);
                }
                putValue(jsonObj, realKey, parseArrayFromLine(arraySyntax), wasQuoted);
            } else {
                boolean wasQuoted = key.startsWith("\"");
                key = unquoteString(key);
                if (valuePart.isEmpty()) {
                    // Check for nested structure on next line
                    String nextLine = peekLine();
                    if (nextLine != null && getIndentLevel(nextLine) > hyphenIndent) {
                        String nextTrimmed = nextLine.trim();
                        if (isArrayStart(nextTrimmed)) {
                            putValue(jsonObj, key, readArray(hyphenIndent + 1, null), wasQuoted);
                        } else {
                            putValue(jsonObj, key, readObject(hyphenIndent + 1, null), wasQuoted);
                        }
                    } else {
                        putValue(jsonObj, key, null, wasQuoted);
                    }
                } else if (isArrayStart(valuePart)) {
                    putValue(jsonObj, key, parseArrayFromLine(valuePart), wasQuoted);
                } else if ("{}".equals(valuePart)) {
                    putValue(jsonObj, key, new JsonObject(), wasQuoted);
                } else {
                    putValue(jsonObj, key, readScalar(valuePart), wasQuoted);
                }
            }
        }

        // Read subsequent fields at the same indent level as the first field's content
        // The first field's key started at hyphenIndent+1 (after the "- "), so subsequent
        // fields should also be at hyphenIndent+1
        int fieldIndent = hyphenIndent + 1;

        while (true) {
            String line = peekLine();
            if (line == null) {
                break;  // EOF
            }

            int indent = getIndentLevel(line);
            if (indent < fieldIndent) {
                break;  // Back to parent level
            }
            if (indent > fieldIndent) {
                break;  // This belongs to a nested structure
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                consumeLine();
                continue;
            }

            // Must be a key: value at our indent level
            colonPos = findColonPosition(trimmed);
            if (colonPos <= 0) {
                break;  // Not a key: value pair
            }

            consumeLine();
            String key = trimmed.substring(0, colonPos).trim();
            String valuePart = trimmed.substring(colonPos + 1).trim();

            // Check for combined field+array notation
            // Also handles folded keys: data.items[N]:
            if (key.endsWith("]") && key.contains("[")) {
                int bracketStart = key.lastIndexOf('[');
                String realKey = key.substring(0, bracketStart);
                String arraySyntax = key.substring(bracketStart) + ":";
                if (!valuePart.isEmpty()) {
                    arraySyntax += " " + valuePart;
                }
                boolean wasQuoted = realKey.startsWith("\"");
                if (wasQuoted) {
                    realKey = unquoteString(realKey);
                }
                putValue(jsonObj, realKey, parseArrayFromLine(arraySyntax), wasQuoted);
                continue;
            }

            boolean wasQuoted = key.startsWith("\"");
            key = unquoteString(key);

            if (valuePart.isEmpty()) {
                String nextLine = peekLine();
                if (nextLine != null && getIndentLevel(nextLine) > fieldIndent) {
                    String nextTrimmed = nextLine.trim();
                    if (isArrayStart(nextTrimmed)) {
                        putValue(jsonObj, key, readArray(fieldIndent + 1, null), wasQuoted);
                    } else {
                        putValue(jsonObj, key, readObject(fieldIndent + 1, null), wasQuoted);
                    }
                } else {
                    putValue(jsonObj, key, null, wasQuoted);
                }
            } else if (isArrayStart(valuePart)) {
                putValue(jsonObj, key, parseArrayFromLine(valuePart), wasQuoted);
            } else if ("{}".equals(valuePart)) {
                putValue(jsonObj, key, new JsonObject(), wasQuoted);
            } else {
                putValue(jsonObj, key, readScalar(valuePart), wasQuoted);
            }
        }

        return jsonObj;
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
     * Delegates to MathUtilities.parseToMinimalNumericType() which returns the most
     * appropriate type: Long for integers, BigInteger for large integers, Double for
     * decimals with <= 16 mantissa digits, BigDecimal for high-precision decimals.
     * Returns null if text is not a valid number.
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
            return MathUtilities.parseToMinimalNumericType(text);
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

    // ========== Key Folding Support ==========

    /**
     * Check if a key is a folded dotted key that should be expanded.
     * A key is foldable if it contains dots and all segments match the safe identifier pattern.
     * Quoted keys (starting with ") are NOT expanded.
     */
    private boolean isFoldedKey(String key) {
        if (key.startsWith("\"") || !key.contains(".")) {
            return false;
        }
        // Check that all segments match safe identifier pattern: ^[A-Za-z_][A-Za-z0-9_]*$
        // The last segment may have array notation like "items[2]"
        String[] segments = key.split("\\.");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            // Last segment may have array notation
            if (i == segments.length - 1 && segment.contains("[")) {
                segment = segment.substring(0, segment.indexOf('['));
            }
            if (!isValidIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a string is a valid identifier segment for key folding.
     */
    private boolean isValidIdentifier(String segment) {
        if (segment.isEmpty()) {
            return false;
        }
        char first = segment.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            return false;
        }
        for (int i = 1; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /**
     * Put a value into a JsonObject, optionally expanding dotted keys.
     * If wasQuoted is true, the key is treated as a literal (no expansion).
     */
    private void putValue(JsonObject target, String key, Object value, boolean wasQuoted) {
        if (wasQuoted || !isFoldedKey(key)) {
            target.put(key, value);
        } else {
            putWithKeyExpansion(target, key, value);
        }
    }

    /**
     * Put a value into a JsonObject, expanding dotted keys into nested structure.
     * For example: putWithKeyExpansion(obj, "data.meta.items", value) creates:
     * obj = {data: {meta: {items: value}}}
     */
    private void putWithKeyExpansion(JsonObject target, String key, Object value) {
        String[] segments = key.split("\\.");
        JsonObject current = target;

        // Navigate/create nested structure for all segments except the last
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            Object existing = current.get(segment);
            if (existing instanceof JsonObject) {
                current = (JsonObject) existing;
            } else {
                JsonObject nested = new JsonObject();
                current.put(segment, nested);
                current = nested;
            }
        }

        // Put the value at the last segment
        String lastSegment = segments[segments.length - 1];
        current.put(lastSegment, value);
    }
}
