package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.FastReader;
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
    private static final int STRING_CACHE_MASK = 2047;
    private static final int MAX_CACHED_STRING_LENGTH = 64;
    private static final int NUMBER_CACHE_MASK = 1023;

    // Thread-local reusable buffers — eliminates per-parse array allocation.
    // Stale entries from previous parses are harmless (cache misses or warm hits).
    private static final ThreadLocal<String[]> TL_STRING_CACHE =
            ThreadLocal.withInitial(() -> new String[STRING_CACHE_MASK + 1]);
    private static final ThreadLocal<String[]> TL_NUMBER_KEYS =
            ThreadLocal.withInitial(() -> new String[NUMBER_CACHE_MASK + 1]);
    private static final ThreadLocal<Number[]> TL_NUMBER_VALUES =
            ThreadLocal.withInitial(() -> new Number[NUMBER_CACHE_MASK + 1]);
    private static final ThreadLocal<char[]> TL_LINE_BUF =
            ThreadLocal.withInitial(() -> new char[4096]);

    private static final Map<String, String> META_KEY_MAP = new HashMap<>(32);
    static {
        for (String k : new String[]{JsonValue.ID, JsonValue.SHORT_ID, JsonValue.JSON5_ID, JsonValue.JSON5_SHORT_ID}) {
            META_KEY_MAP.put(k, JsonValue.ID);
        }
        for (String k : new String[]{JsonValue.REF, JsonValue.SHORT_REF, JsonValue.JSON5_REF, JsonValue.JSON5_SHORT_REF}) {
            META_KEY_MAP.put(k, JsonValue.REF);
        }
        for (String k : new String[]{JsonValue.ITEMS, JsonValue.SHORT_ITEMS, JsonValue.JSON5_ITEMS, JsonValue.JSON5_SHORT_ITEMS}) {
            META_KEY_MAP.put(k, JsonValue.ITEMS);
        }
        for (String k : new String[]{JsonValue.KEYS, JsonValue.SHORT_KEYS, JsonValue.JSON5_KEYS, JsonValue.JSON5_SHORT_KEYS}) {
            META_KEY_MAP.put(k, JsonValue.KEYS);
        }
        for (String k : new String[]{JsonValue.TYPE, JsonValue.SHORT_TYPE, JsonValue.JSON5_TYPE, JsonValue.JSON5_SHORT_TYPE}) {
            META_KEY_MAP.put(k, JsonValue.TYPE);
        }
        META_KEY_MAP.put(JsonValue.ENUM, JsonValue.ENUM);
    }

    private final FastReader reader;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;
    private final long maxIdValue;
    private final boolean strictToon;
    private final ClassLoader classLoader;

    // Line management - supports peek/consume pattern
    private String currentLine = null;
    private boolean lineConsumed = true;
    private int lineNumber = 0;
    private int currentIndent = 0;
    private String currentTrimmed = "";
    private final StringBuilder quoteBuf = new StringBuilder(64);
    private final StringBuilder inlineBuf = new StringBuilder(64);
    private String[] cachedFoldSegments;
    private char[] lineBuf;
    private final String[] stringCache;
    private final String[] numberCacheKeys;
    private final Number[] numberCacheValues;

    /**
     * Create a ToonReader that reads from a Reader.
     *
     * @param reader the reader to read TOON content from
     * @param readOptions configuration options (may be null for defaults)
     */
    public ToonReader(Reader reader, ReadOptions readOptions) {
        this(reader, readOptions, null);
    }

    /**
     * Create a ToonReader that reads from a Reader and tracks @id/@ref metadata.
     *
     * @param reader the reader to read TOON content from
     * @param readOptions configuration options (may be null for defaults)
     * @param references reference tracker used during resolution (may be null)
     */
    public ToonReader(Reader reader, ReadOptions readOptions, ReferenceTracker references) {
        this.reader = reader instanceof FastReader ? (FastReader) reader : new FastReader(reader);
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        this.references = references;
        this.maxIdValue = this.readOptions.getMaxIdValue();
        this.strictToon = this.readOptions.isStrictToon();
        this.classLoader = this.readOptions.getClassLoader();
        this.lineBuf = TL_LINE_BUF.get();
        this.stringCache = TL_STRING_CACHE.get();
        this.numberCacheKeys = TL_NUMBER_KEYS.get();
        this.numberCacheValues = TL_NUMBER_VALUES.get();
    }

    private String cacheString(String s) {
        int len = s.length();
        if (len == 0) return "";
        if (len > MAX_CACHED_STRING_LENGTH) return s;
        int slot = s.hashCode() & STRING_CACHE_MASK;
        String[] cache = stringCache;
        String cached = cache[slot];
        if (s.equals(cached)) return cached;
        cache[slot] = s;
        return s;
    }

    private String cacheSubstring(String source, int start, int end) {
        if (start == end) return "";
        return cacheString((start == 0 && end == source.length()) ? source : source.substring(start, end));
    }

    private static String trimAscii(String text) {
        int start = 0;
        int len = text.length();
        int end = len;
        if (start < end && text.charAt(start) > ' ' && text.charAt(end - 1) > ' ') {
            return text;
        }
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        return (start == 0 && end == len) ? text : text.substring(start, end);
    }

    private static String trimAscii(StringBuilder text) {
        int start = 0;
        int textLen = text.length();
        int end = textLen;
        if (start < end && text.charAt(start) > ' ' && text.charAt(end - 1) > ' ') {
            return text.toString();
        }
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        if (start == 0 && end == textLen) {
            return text.toString();
        }
        return text.substring(start, end);
    }

    private String trimAsciiRange(String text, int start, int end) {
        // Fast path: no trimming needed and range spans full string
        if (start < end && text.charAt(start) > ' ' && text.charAt(end - 1) > ' ') {
            if (start == 0 && end == text.length()) {
                return text;
            }
            return cacheSubstring(text, start, end);
        }
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        if (start >= end) return "";
        return cacheSubstring(text, start, end);
    }

    /**
     * Read the complete TOON value.
     *
     * @param suggestedType optional type hint for the Resolver
     * @return parsed value (JsonObject, Object[], or primitive)
     */
    public Object readValue(Type suggestedType) {
        try {
            while (true) {
                String line = peekLine();
                if (line == null) {
                    JsonObject emptyMap = new JsonObject();
                    if (suggestedType != null) {
                        emptyMap.setType(suggestedType);
                    }
                    return emptyMap;
                }

                String trimmed = peekTrimmed();
                if (trimmed.isEmpty()) {
                    consumeLine();
                    continue;  // Skip empty lines
                }

                if ("{}".equals(trimmed)) {
                    consumeLine();
                    JsonObject emptyMap = new JsonObject();
                    if (suggestedType != null) {
                        emptyMap.setType(suggestedType);
                    }
                    return emptyMap;
                }

                if (isArrayStart(trimmed)) {
                    return readArray();
                }

                int colonPos = findColonPosition(trimmed);
                if (colonPos > 0) {
                    return readObject(0, suggestedType);
                }

                consumeLine();
                return readScalar(trimmed);
            }
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
    private JsonObject readObject(int baseIndent, Type suggestedType) throws IOException {
        JsonObject jsonObj = new JsonObject();
        if (suggestedType != null) {
            jsonObj.setType(suggestedType);
        }

        while (true) {
            String line = peekLine();
            if (line == null) {
                break;  // EOF
            }

            int indent = peekIndent();
            if (indent < baseIndent) {
                break;  // Back to parent level
            }

            String trimmed = peekTrimmed();
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
            String key = trimAsciiRange(trimmed, 0, colonPos);
            // Optimize value extraction: skip colon and optional single space directly
            int valueStart = colonPos + 1;
            int valueEnd = trimmed.length();
            if (valueStart < valueEnd && trimmed.charAt(valueStart) == ' ') {
                valueStart++;
            }
            String valuePart = (valueStart >= valueEnd) ? "" : trimAsciiRange(trimmed, valueStart, valueEnd);

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
                if (nextLine != null && peekIndent() > baseIndent) {
                    String nextTrimmed = peekTrimmed();
                    if (isArrayStart(nextTrimmed)) {
                        putValue(jsonObj, key, readArray(), wasQuoted);
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
        if (!trimmed.startsWith("[")) {
            return false;
        }
        int bracketEnd = trimmed.indexOf(']');
        if (bracketEnd < 0 || bracketEnd + 1 >= trimmed.length()) {
            return false;
        }
        // After ']', expect ':' (inline/list) or '{' (tabular: [N]{cols}:)
        char next = trimmed.charAt(bracketEnd + 1);
        return next == ':' || next == '{';
    }

    /**
     * Read an array starting at the given indentation level.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> readArray() throws IOException {
        String line = peekLine();
        if (line == null) {
            return new ArrayList<>();
        }

        String trimmed = peekTrimmed();
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
            count = Integer.parseInt(trimAscii(countStr));
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
                validateFieldDelimiterConsistency(headerStr, delimiter);
                columnHeaders = parseColumnHeaders(headerStr, delimiter);
                afterBracket = braceEnd + 1;
            } else if (strictToon) {
                throw new JsonIoException("Malformed array syntax (missing closing brace) at line " + lineNumber);
            }
        }

        // Get content after [N]: or [N]{cols}:
        int colonPos = trimmed.indexOf(':', afterBracket);
        if (colonPos < 0) {
            throw new JsonIoException("Malformed array syntax (missing colon) at line " + lineNumber);
        }

        String content = trimAsciiRange(trimmed, colonPos + 1, trimmed.length());

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
                headers.add(trimAscii(current));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            headers.add(trimAscii(current));
        }

        return headers;
    }

    private void validateFieldDelimiterConsistency(String headerStr, char delimiter) {
        if (!strictToon) {
            return;
        }
        if (delimiter == ',' && (headerStr.indexOf('\t') >= 0 || headerStr.indexOf('|') >= 0)) {
            throw new JsonIoException("Delimiter mismatch in tabular header at line " + lineNumber);
        }
        if (delimiter == '\t' && (headerStr.indexOf(',') >= 0 || headerStr.indexOf('|') >= 0)) {
            throw new JsonIoException("Delimiter mismatch in tabular header at line " + lineNumber);
        }
        if (delimiter == '|' && (headerStr.indexOf(',') >= 0 || headerStr.indexOf('\t') >= 0)) {
            throw new JsonIoException("Delimiter mismatch in tabular header at line " + lineNumber);
        }
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

            String trimmed = peekTrimmed();
            if (trimmed.isEmpty()) {
                if (strictToon) {
                    throw new JsonIoException("Blank lines are not allowed inside tabular arrays at line " + lineNumber);
                }
                consumeLine();
                continue;
            }

            int indent = peekIndent();

            // First row determines base indent
            if (baseIndent < 0) {
                baseIndent = indent;
            } else if (indent < baseIndent) {
                // Line at lower indent - end of tabular data
                break;
            }

            // Skip lines that look like key: value (they belong to parent object)
            // Check delimiter first (cheap char indexOf) before expensive findColonPosition.
            // Data rows contain the delimiter, so this short-circuits on the common case.
            if (trimmed.indexOf(delimiter) < 0 && findColonPosition(trimmed) > 0) {
                break;
            }

            consumeLine();

            // Parse the row into an object
            JsonObject rowObj = new JsonObject();
            List<Object> values = readInlineArray(trimmed, columnHeaders.size(), delimiter);
            if (strictToon && values.size() != columnHeaders.size()) {
                throw new JsonIoException("Tabular row width mismatch at line " + lineNumber +
                        ", expected " + columnHeaders.size() + " values, got " + values.size());
            }

            for (int i = 0; i < columnHeaders.size() && i < values.size(); i++) {
                String header = columnHeaders.get(i);
                Object value = values.get(i);
                String metaKey = canonicalMetaKey(header);
                if (metaKey != null) {
                    loadMetaField(rowObj, metaKey, value);
                } else {
                    rowObj.appendFieldForParser(header, value);
                }
            }

            elements.add(rowObj);
        }

        if (strictToon) {
            if (elements.size() != count) {
                throw new JsonIoException("Tabular array count mismatch at line " + lineNumber +
                        ", expected " + count + " rows, got " + elements.size());
            }
            if (hasAdditionalTabularRows(baseIndent, delimiter)) {
                throw new JsonIoException("Tabular array has more rows than declared count " + count +
                        " at line " + lineNumber);
            }
        }

        return elements;
    }

    private boolean hasAdditionalTabularRows(int baseIndent, char delimiter) throws IOException {
        String line = peekLine();
        if (line == null) {
            return false;
        }
        String trimmed = peekTrimmed();
        if (trimmed.isEmpty()) {
            return false;
        }
        int indent = peekIndent();
        if (indent < baseIndent) {
            return false;
        }
        return trimmed.indexOf(delimiter) >= 0 || findColonPosition(trimmed) <= 0;
    }

    /**
     * Read an inline array with a specific delimiter.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> readInlineArray(String content, int count, char delimiter) {
        List<Object> elements = new ArrayList<>(count);
        inlineBuf.setLength(0);
        final StringBuilder current = inlineBuf;
        boolean inQuotes = false;
        boolean escaped = false;
        int len = content.length();
        
        for (int i = 0; i < len; i++) {
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
                elements.add(readScalar(trimAscii(current)));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        // Add last element
        if (current.length() > 0 || elements.size() < count) {
            elements.add(readScalar(trimAscii(current)));
        }

        if (strictToon && elements.size() != count) {
            throw new JsonIoException("Inline array count mismatch at line " + lineNumber +
                    ", expected " + count + " values, got " + elements.size());
        }

        return elements;
    }

    /**
     * Read a list format array with - element lines.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> readListArray(int count) throws IOException {
        List<Object> elements = new ArrayList<>(count);
        int baseIndent = -1;

        while (elements.size() < count) {
            String line = peekLine();
            if (line == null) {
                break;  // EOF
            }

            String trimmed = peekTrimmed();
            if (trimmed.isEmpty()) {
                if (strictToon) {
                    throw new JsonIoException("Blank lines are not allowed inside list arrays at line " + lineNumber);
                }
                consumeLine();
                continue;
            }

            int indent = peekIndent();

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
            String elementContent = trimAsciiRange(trimmed, 1, trimmed.length());

            if (elementContent.isEmpty()) {
                // Nested object/array on next lines
                String nextLine = peekLine();
                if (nextLine != null) {
                    String nextTrimmed = peekTrimmed();
                    int nextIndent = peekIndent();
                    if (nextIndent > indent) {
                        if (isArrayStart(nextTrimmed)) {
                            elements.add(readArray());
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

        if (strictToon) {
            if (elements.size() != count) {
                throw new JsonIoException("List array count mismatch at line " + lineNumber +
                        ", expected " + count + " elements, got " + elements.size());
            }
            if (hasAdditionalListElements(baseIndent)) {
                throw new JsonIoException("List array has more elements than declared count " + count +
                        " at line " + lineNumber);
            }
        }

        return elements;
    }

    private boolean hasAdditionalListElements(int baseIndent) throws IOException {
        String line = peekLine();
        if (line == null) {
            return false;
        }
        String trimmed = peekTrimmed();
        if (trimmed.isEmpty()) {
            return false;
        }
        int indent = peekIndent();
        return indent >= baseIndent && trimmed.startsWith("-");
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
            String key = trimAsciiRange(firstFieldLine, 0, colonPos);
            String valuePart = trimAsciiRange(firstFieldLine, colonPos + 1, firstFieldLine.length());

            // Check for combined field+array notation: fieldName[N]: or fieldName[N]{cols}:
            // Also handles folded keys: data.items[N]:
            int bracketStart = key.indexOf('[');
            if (bracketStart >= 0) {
                String realKey = key.substring(0, bracketStart);
                StringBuilder sb = new StringBuilder(key.length() - bracketStart + 2 + valuePart.length());
                sb.append(key, bracketStart, key.length()).append(':');
                if (!valuePart.isEmpty()) {
                    sb.append(' ').append(valuePart);
                }
                boolean wasQuoted = realKey.length() > 0 && realKey.charAt(0) == '"';
                if (wasQuoted) {
                    realKey = unquoteString(realKey);
                }
                putValue(jsonObj, realKey, parseArrayFromLine(sb.toString()), wasQuoted);
            } else {
                boolean wasQuoted = key.length() > 0 && key.charAt(0) == '"';
                key = unquoteString(key);
                if (valuePart.isEmpty()) {
                    // Check for nested structure on next line
                    String nextLine = peekLine();
                    if (nextLine != null && peekIndent() > hyphenIndent) {
                        String nextTrimmed = peekTrimmed();
                        if (isArrayStart(nextTrimmed)) {
                            putValue(jsonObj, key, readArray(), wasQuoted);
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
            if (peekLine() == null) {
                break;  // EOF
            }

            int indent = peekIndent();
            if (indent < fieldIndent) {
                break;  // Back to parent level
            }
            if (indent > fieldIndent) {
                break;  // This belongs to a nested structure
            }

            String trimmed = peekTrimmed();
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
            String key = trimAsciiRange(trimmed, 0, colonPos);
            int valueStart2 = colonPos + 1;
            int valueEnd2 = trimmed.length();
            if (valueStart2 < valueEnd2 && trimmed.charAt(valueStart2) == ' ') {
                valueStart2++;
            }
            String valuePart = (valueStart2 >= valueEnd2) ? "" : trimAsciiRange(trimmed, valueStart2, valueEnd2);

            // Check for combined field+array notation
            // Also handles folded keys: data.items[N]:
            int keyLen = key.length();
            if (keyLen > 0 && key.charAt(keyLen - 1) == ']') {
                int bracketStart = key.lastIndexOf('[');
                if (bracketStart >= 0) {
                    String realKey = key.substring(0, bracketStart);
                    StringBuilder sb = new StringBuilder(keyLen - bracketStart + 2 + valuePart.length());
                    sb.append(key, bracketStart, keyLen).append(':');
                    if (!valuePart.isEmpty()) {
                        sb.append(' ').append(valuePart);
                    }
                    boolean wasQuoted = realKey.length() > 0 && realKey.charAt(0) == '"';
                    if (wasQuoted) {
                        realKey = unquoteString(realKey);
                    }
                    putValue(jsonObj, realKey, parseArrayFromLine(sb.toString()), wasQuoted);
                    continue;
                }
            }

            boolean wasQuoted = keyLen > 0 && key.charAt(0) == '"';
            key = unquoteString(key);

            if (valuePart.isEmpty()) {
                String nextLine = peekLine();
                if (nextLine != null && peekIndent() > fieldIndent) {
                    String nextTrimmed = peekTrimmed();
                    if (isArrayStart(nextTrimmed)) {
                        putValue(jsonObj, key, readArray(), wasQuoted);
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
    private Object readScalar(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        int len = text.length();
        char firstChar = text.charAt(0);
        char lastChar = text.charAt(len - 1);

        if (strictToon) {
            boolean startsQuote = firstChar == '"';
            boolean endsQuote = lastChar == '"';
            if (startsQuote != endsQuote) {
                throw new JsonIoException("Unclosed quoted string at line " + lineNumber);
            }
        }

        // Fast path for null / booleans based on first char and length.
        if (len == 4) {
            if (firstChar == 'n' && "null".equals(text)) {
                return null;
            }
            if (firstChar == 't' && "true".equals(text)) {
                return Boolean.TRUE;
            }
        } else if (len == 5 && firstChar == 'f' && "false".equals(text)) {
            return Boolean.FALSE;
        }

        // Handle quoted strings
        if (len >= 2 && firstChar == '"' && lastChar == '"') {
            return parseQuotedString(text);
        }

        // Avoid number parser for clearly non-numeric tokens.
        if (!(firstChar >= '0' && firstChar <= '9') && firstChar != '-' && firstChar != '+' && firstChar != '.') {
            return text;
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
    private String parseQuotedString(String text) {
        int len = text.length();

        // Fast path: no escape sequences — use cache to deduplicate
        if (text.indexOf('\\', 1) < 0) {
            return cacheSubstring(text, 1, len - 1);
        }

        // Slow path: process escape sequences with reusable StringBuilder
        quoteBuf.setLength(0);
        for (int i = 1; i < len - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < len - 1) {
                char next = text.charAt(++i);
                switch (next) {
                    case '\\':
                        quoteBuf.append('\\');
                        break;
                    case '"':
                        quoteBuf.append('"');
                        break;
                    case 'n':
                        quoteBuf.append('\n');
                        break;
                    case 'r':
                        quoteBuf.append('\r');
                        break;
                    case 't':
                        quoteBuf.append('\t');
                        break;
                    default:
                        throw new JsonIoException("Invalid escape sequence: \\" + next + " at line " + lineNumber);
                }
            } else {
                quoteBuf.append(c);
            }
        }

        return cacheString(quoteBuf.toString());
    }

    /**
     * Try to parse text as a number.
     * Delegates to MathUtilities.parseToMinimalNumericType() which returns the most
     * appropriate type: Long for integers, BigInteger for large integers, Double for
     * decimals with <= 16 mantissa digits, BigDecimal for high-precision decimals.
     * Returns null if text is not a valid number.
     */
    private Number parseNumber(String text) {
        if (text.isEmpty()) {
            return null;
        }

        int slot = text.hashCode() & NUMBER_CACHE_MASK;
        if (text.equals(numberCacheKeys[slot])) {
            return numberCacheValues[slot];
        }

        int len = text.length();
        char first = text.charAt(0);
        int start = (first == '-' || first == '+') ? 1 : 0;
        if (start < len) {
            boolean negative = first == '-';
            long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
            long multmin = limit / 10;
            long result = 0;
            boolean integerOnly = true;
            boolean overflow = false;

            for (int i = start; i < len; i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') {
                    integerOnly = false;
                    break;
                }

                if (!overflow) {
                    int digit = c - '0';
                    if (result < multmin) {
                        overflow = true;
                    } else {
                        result *= 10;
                        if (result < limit + digit) {
                            overflow = true;
                        } else {
                            result -= digit;
                        }
                    }
                }
            }

            if (integerOnly) {
                if (!overflow) {
                    numberCacheKeys[slot] = text;
                    return numberCacheValues[slot] = negative ? result : -result;
                }
                try {
                    numberCacheKeys[slot] = text;
                    return numberCacheValues[slot] = new BigInteger(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        try {
            Number parsed = MathUtilities.parseToMinimalNumericType(text);
            numberCacheKeys[slot] = text;
            numberCacheValues[slot] = parsed;
            return parsed;
        } catch (NumberFormatException e) {
            return null;  // Not a valid number
        }
    }

    // ========== Line Management ==========

    /**
     * Read a line from the FastReader into the reusable lineBuf.
     * Handles \n, \r, and \r\n line endings.
     * Returns the number of characters read into lineBuf, or -1 on EOF.
     * The line data is available in lineBuf[0..returnValue-1].
     */
    private int readLineRaw() throws IOException {
        char[] buf = lineBuf;
        int total = 0;
        while (true) {
            if (total >= buf.length) {
                buf = lineBuf = Arrays.copyOf(buf, buf.length * 2);
            }
            int count = reader.readUntil(buf, total, buf.length - total, '\n', '\r');
            if (count < 0) {
                return total > 0 ? total : -1;
            }
            total += count;
            int c = reader.read();
            if (c == '\n' || c < 0) {
                break;
            }
            if (c == '\r') {
                int peek = reader.read();
                if (peek != '\n' && peek >= 0) {
                    reader.pushback((char) peek);
                }
                break;
            }
            // Buffer was full, char is content — store and continue
            if (total >= buf.length) {
                buf = lineBuf = Arrays.copyOf(buf, buf.length * 2);
            }
            buf[total++] = (char) c;
        }
        return total;
    }

    /**
     * Peek at the next line without consuming it.
     * Computes indent level and trimmed content directly from the raw lineBuf,
     * creating only a single String (the trimmed line) instead of two.
     */
    private String peekLine() throws IOException {
        if (lineConsumed) {
            int lineLen = readLineRaw();
            lineConsumed = false;
            if (lineLen >= 0) {
                lineNumber++;
                final char[] buf = lineBuf;  // localize after readLineRaw (which may resize)

                // Compute indent directly from buf — no intermediate String needed
                int spaces = 0;
                while (spaces < lineLen && buf[spaces] == ' ') {
                    spaces++;
                }
                if (spaces < lineLen && buf[spaces] == '\t' && strictToon) {
                    throw new JsonIoException("Tabs are not allowed in indentation at line " + lineNumber);
                }
                if (strictToon && spaces % INDENT_SIZE != 0) {
                    throw new JsonIoException("Indentation must be a multiple of " + INDENT_SIZE +
                            " spaces at line " + lineNumber);
                }
                currentIndent = spaces / INDENT_SIZE;

                // Compute trim-end directly from buf
                int trimEnd = lineLen;
                while (trimEnd > spaces && buf[trimEnd - 1] <= ' ') {
                    trimEnd--;
                }

                // Create ONE String: the trimmed content only
                String trimmed;
                if (spaces == 0 && trimEnd == lineLen) {
                    trimmed = new String(buf, 0, lineLen);
                } else {
                    trimmed = (spaces < trimEnd) ? new String(buf, spaces, trimEnd - spaces) : "";
                }
                currentTrimmed = trimmed;
                currentLine = trimmed;
            } else {
                currentLine = null;
                currentIndent = 0;
                currentTrimmed = "";
            }
        }
        return currentLine;
    }

    private int peekIndent() {
        return currentIndent;
    }

    private String peekTrimmed() {
        return currentTrimmed;
    }

    /**
     * Consume the current line (move to next).
     */
    private void consumeLine() {
        lineConsumed = true;
    }

    /**
     * Find the position of the colon in a key: value pair.
     * Returns -1 if no valid colon found (ignores colons inside quoted strings).
     */
    private int findColonPosition(String text) {
        int colon = text.indexOf(':');
        if (colon < 0) {
            return -1;
        }
        // Fast path: if no quote exists, or the first quote is after the first colon,
        // the colon is definitely unquoted - skip the expensive char-by-char scan.
        int quote = text.indexOf('"');
        if (quote < 0 || quote > colon) {
            return colon;
        }

        boolean inQuotes = false;
        boolean escaped = false;
        int len = text.length();

        for (int i = 0; i < len; i++) {
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
        int len = text.length();
        if (len < 2) {
            return text;
        }
        char first = text.charAt(0);
        char last = text.charAt(len - 1);
        if (first == '"' && last == '"') {
            return parseQuotedString(text);
        }
        if (strictToon && (first == '"') != (last == '"')) {
            throw new JsonIoException("Unclosed quoted key at line " + lineNumber);
        }
        return text;
    }

    // ========== Key Folding Support ==========

    /**
     * Validate all segments of a dotted key and cache the split result.
     * Called only when key is known to contain '.' and not start with '"'.
     * Uses manual dot-splitting to avoid regex compilation overhead.
     */
    private boolean validateAndCacheFoldedKey(String key) {
        int len = key.length();
        int segStart = 0;
        int segCount = 0;

        for (int i = 0; i <= len; i++) {
            if (i == len || key.charAt(i) == '.') {
                int segEnd = i;
                // Last segment may have array notation like "items[2]"
                if (i == len) {
                    for (int j = segStart; j < segEnd; j++) {
                        if (key.charAt(j) == '[') {
                            segEnd = j;
                            break;
                        }
                    }
                }
                // Validate segment as identifier: [A-Za-z_][A-Za-z0-9_]*
                if (segEnd <= segStart) return false;
                char first = key.charAt(segStart);
                if (!Character.isLetter(first) && first != '_') return false;
                for (int j = segStart + 1; j < segEnd; j++) {
                    char c = key.charAt(j);
                    if (!Character.isLetterOrDigit(c) && c != '_') return false;
                }
                segCount++;
                segStart = i + 1;
            }
        }

        if (segCount < 2) return false;

        // Manual dot-split (no regex) — only done after validation passes
        String[] segments = new String[segCount];
        segStart = 0;
        int idx = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || key.charAt(i) == '.') {
                segments[idx++] = key.substring(segStart, i);
                segStart = i + 1;
            }
        }
        cachedFoldSegments = segments;
        return true;
    }

    /**
     * Put a value into a JsonObject, optionally expanding dotted keys.
     * If wasQuoted is true, the key is treated as a literal (no expansion).
     */
    private void putValue(JsonObject target, String key, Object value, boolean wasQuoted) {
        if (!wasQuoted) {
            char first = key.charAt(0);
            // Meta key fast path: only @/$ prefixed keys can be meta
            if (first == '@' || first == '$') {
                String meta = canonicalMetaKey(key);
                if (meta != null) {
                    loadMetaField(target, meta, value);
                    return;
                }
            }
            // Folded key check: only non-quoted keys with '.' can be folded
            if (key.indexOf('.') >= 0 && validateAndCacheFoldedKey(key)) {
                putWithKeyExpansion(target, key, value);
                return;
            }
        }
        if (strictToon) {
            Object existing = target.get(key);
            if (existing != null) {
                boolean existingObj = existing instanceof JsonObject;
                boolean incomingObj = value instanceof JsonObject;
                if (existingObj != incomingObj) {
                    throw new JsonIoException("Path expansion conflict at line " + lineNumber + " for key: " + key);
                }
            }
        }
        target.appendFieldForParser(key, value);
    }

    private String canonicalMetaKey(String key) {
        return META_KEY_MAP.get(key);
    }

    private void loadMetaField(JsonObject target, String metaKey, Object value) {
        if (JsonValue.TYPE.equals(metaKey)) {
            loadType(value, target);
        } else if (JsonValue.ID.equals(metaKey)) {
            loadId(value, target);
        } else if (JsonValue.REF.equals(metaKey)) {
            loadRef(value, target);
        } else if (JsonValue.ITEMS.equals(metaKey)) {
            loadItems(value, target);
        } else if (JsonValue.KEYS.equals(metaKey)) {
            loadKeys(value, target);
        } else if (JsonValue.ENUM.equals(metaKey)) {
            loadEnum(value, target);
        }
    }

    private void loadType(Object value, JsonObject target) {
        if (!(value instanceof String)) {
            throw new JsonIoException("Expected a String for " + JsonValue.TYPE + " at line " + lineNumber);
        }

        String typeName = (String) value;
        target.setTypeString(typeName);

        String resolvedName = readOptions.getTypeNameAlias(typeName);
        if (resolvedName == null) {
            resolvedName = typeName;
        }

        Class<?> typeClass = ClassUtilities.forName(resolvedName, classLoader);
        if (typeClass == null) {
            if (readOptions.isFailOnUnknownType()) {
                throw new JsonIoException("Unknown type (class) '" + typeName + "' at line " + lineNumber);
            }
            typeClass = readOptions.getUnknownTypeClass();
            if (typeClass == null) {
                typeClass = LinkedHashMap.class;
            }
        }
        target.setType(typeClass);
    }

    private void loadId(Object value, JsonObject target) {
        if (!(value instanceof Number)) {
            throw new JsonIoException("Expected a number for " + JsonValue.ID + " at line " + lineNumber);
        }
        long id = ((Number) value).longValue();
        if (id < -maxIdValue || id > maxIdValue) {
            throw new JsonIoException("ID value out of safe range at line " + lineNumber + ": " + id);
        }
        target.setId(id);
        if (references != null) {
            references.put(id, target);
        }
    }

    private void loadRef(Object value, JsonObject target) {
        if (!(value instanceof Number)) {
            throw new JsonIoException("Expected a number for " + JsonValue.REF + " at line " + lineNumber);
        }
        long refId = ((Number) value).longValue();
        if (refId < -maxIdValue || refId > maxIdValue) {
            throw new JsonIoException("Reference ID value out of safe range at line " + lineNumber + ": " + refId);
        }
        target.setReferenceId(refId);
    }

    private void loadItems(Object value, JsonObject target) {
        Object[] items = toObjectArray(value, JsonValue.ITEMS);
        if (items != null) {
            target.setItems(items);
        }
    }

    private void loadKeys(Object value, JsonObject target) {
        Object[] keys = toObjectArray(value, JsonValue.KEYS);
        if (keys != null) {
            target.setKeys(keys);
        }
    }

    private void loadEnum(Object value, JsonObject target) {
        if (!(value instanceof String)) {
            throw new JsonIoException("Expected a String for " + JsonValue.ENUM + " at line " + lineNumber);
        }
        loadType(value, target);
        if (target.getItems() == null) {
            target.setItems(ArrayUtilities.EMPTY_OBJECT_ARRAY);
        }
    }

    private Object[] toObjectArray(Object value, String metaKey) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            return ((List<?>) value).toArray();
        }
        if (value instanceof Object[]) {
            return (Object[]) value;
        }
        if (value.getClass().isArray()) {
            throw new JsonIoException("Expected object array for " + metaKey + " at line " + lineNumber
                    + " but found primitive array type: " + value.getClass().getName());
        }
        throw new JsonIoException("Expected array value for " + metaKey + " at line " + lineNumber);
    }

    /**
     * Put a value into a JsonObject, expanding dotted keys into nested structure.
     * For example: putWithKeyExpansion(obj, "data.meta.items", value) creates:
     * obj = {data: {meta: {items: value}}}
     */
    private void putWithKeyExpansion(JsonObject target, String key, Object value) {
        String[] segments = cachedFoldSegments;
        JsonObject current = target;

        // Navigate/create nested structure for all segments except the last
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            Object existing = current.get(segment);
            if (existing instanceof JsonObject) {
                current = (JsonObject) existing;
            } else if (existing != null) {
                if (strictToon) {
                    throw new JsonIoException("Path expansion conflict at line " + lineNumber + " for key: " + key);
                }
                JsonObject nested = new JsonObject();
                current.put(segment, nested);
                current = nested;
            } else {
                JsonObject nested = new JsonObject();
                current.put(segment, nested);
                current = nested;
            }
        }

        // Put the value at the last segment
        String lastSegment = segments[segments.length - 1];
        Object existingLast = current.get(lastSegment);
        if (existingLast instanceof JsonObject && value instanceof JsonObject) {
            mergeJsonObjects((JsonObject) existingLast, (JsonObject) value);
            return;
        }
        if (existingLast != null && strictToon) {
            throw new JsonIoException("Path expansion conflict at line " + lineNumber + " for key: " + key);
        }
        current.put(lastSegment, value);
    }

    private void mergeJsonObjects(JsonObject target, JsonObject source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object sourceVal = entry.getValue();
            Object targetVal = target.get(key);
            if (targetVal instanceof JsonObject && sourceVal instanceof JsonObject) {
                mergeJsonObjects((JsonObject) targetVal, (JsonObject) sourceVal);
            } else if (targetVal != null && strictToon) {
                throw new JsonIoException("Path expansion conflict at line " + lineNumber + " for key: " + key);
            } else {
                target.put(key, sourceVal);
            }
        }
    }
}
