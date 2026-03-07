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
    private boolean currentHasLine = false;
    private boolean lineConsumed = true;
    private int lineNumber = 0;
    private int currentIndent = 0;
    private int currentTrimStart = 0;
    private int currentTrimEnd = 0;
    private String currentTrimmed = null;
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
        if (start == 0 && end == source.length()) {
            return cacheString(source);
        }

        int len = end - start;
        if (len > MAX_CACHED_STRING_LENGTH) {
            return source.substring(start, end);
        }

        int hash = 0;
        for (int i = start; i < end; i++) {
            hash = 31 * hash + source.charAt(i);
        }

        String[] cache = stringCache;
        int slot = hash & STRING_CACHE_MASK;
        String cached = cache[slot];
        if (cached != null && cached.length() == len && source.regionMatches(start, cached, 0, len)) {
            return cached;
        }

        String substring = source.substring(start, end);
        cache[slot] = substring;
        return substring;
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
                if (!hasLine()) {
                    JsonObject emptyMap = new JsonObject();
                    if (suggestedType != null) {
                        emptyMap.setType(suggestedType);
                    }
                    return emptyMap;
                }

                if (isTrimmedEmpty()) {
                    consumeLine();
                    continue;  // Skip empty lines
                }

                if (isEmptyObjectInBuf()) {
                    consumeLine();
                    JsonObject emptyMap = new JsonObject();
                    if (suggestedType != null) {
                        emptyMap.setType(suggestedType);
                    }
                    return emptyMap;
                }

                if (isArrayStartInBuf()) {
                    return readArray();
                }

                if (findColonInBuf() > 0) {
                    return readObject(0, suggestedType);
                }

                String trimmed = peekTrimmed();
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
            if (!hasLine()) {
                break;  // EOF
            }

            int indent = peekIndent();
            if (indent < baseIndent) {
                break;  // Back to parent level
            }

            if (isTrimmedEmpty()) {
                consumeLine();
                continue;  // Skip empty lines
            }

            // Must be at exactly our indent level for keys at this level
            if (indent > baseIndent) {
                break;  // This line belongs to a nested structure
            }

            // Parse key: value — find colon directly in the line buffer
            int colonPos = findColonInBuf();
            if (colonPos <= 0) {
                break;  // Not a key: value pair
            }

            // Extract key and value directly from buffer (avoids full-line String creation)
            int trimStart = currentTrimStart;
            int trimEnd = currentTrimEnd;
            consumeLine();
            String key = trimAsciiRangeBuf(trimStart, trimStart + colonPos);
            int valueStart = trimStart + colonPos + 1;
            if (valueStart < trimEnd && lineBuf[valueStart] == ' ') {
                valueStart++;
            }
            // Check for combined field+array notation: fieldName[N]: or fieldName[N]{cols}:
            // Also handles folded keys with array: data.items[N]:
            // Per TOON spec, this means 'fieldName' contains an array of N elements
            // Tabular format: fieldName[N]{col1,col2,...}: followed by CSV rows
            if (key.contains("[")) {
                int bracketStart = key.indexOf('[');
                String realKey = key.substring(0, bracketStart);
                String arraySyntax = key.substring(bracketStart) + ":";
                if (!isTrimmedEmpty(lineBuf, valueStart, trimEnd)) {
                    arraySyntax += " " + trimAsciiRangeBuf(valueStart, trimEnd);
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
            if (isTrimmedEmpty(lineBuf, valueStart, trimEnd)) {
                if (hasLine() && peekIndent() > baseIndent) {
                    if (isArrayStartInBuf()) {
                        putValue(jsonObj, key, readArray(), wasQuoted);
                    } else {
                        putValue(jsonObj, key, readObject(baseIndent + 1, null), wasQuoted);
                    }
                } else {
                    putValue(jsonObj, key, null, wasQuoted);  // Empty value
                }
            } else if (isArrayStart(lineBuf, valueStart, trimEnd)) {
                // Inline array on same line as key
                putValue(jsonObj, key, parseArrayFromLine(trimAsciiRangeBuf(valueStart, trimEnd)), wasQuoted);
            } else if (isEmptyObject(lineBuf, valueStart, trimEnd)) {
                // Empty map as value
                putValue(jsonObj, key, new JsonObject(), wasQuoted);
            } else {
                // Scalar value
                putValue(jsonObj, key, readScalar(lineBuf, valueStart, trimEnd), wasQuoted);
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
        if (!hasLine()) {
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
        int colHeadersSize = columnHeaders.size();

        while (elements.size() < count) {
            if (!hasLine()) {
                break;  // EOF
            }

            if (isTrimmedEmpty()) {
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
            // Check delimiter first (cheap char indexOf) before expensive findColonInBuf.
            // Data rows contain the delimiter, so this short-circuits on the common case.
            if (indexOfCharInBuf(delimiter) < 0 && findColonInBuf() > 0) {
                break;
            }

            int trimStart = currentTrimStart;
            int trimEnd = currentTrimEnd;
            consumeLine();

            // Parse the row directly into a pre-sized object (no intermediate List)
            JsonObject rowObj = new JsonObject(colHeadersSize);
            String rowContent = trimAsciiRangeBuf(trimStart, trimEnd);
            int valuesCount = parseRowIntoObject(rowContent, columnHeaders, delimiter, rowObj);
            if (strictToon && valuesCount != colHeadersSize) {
                throw new JsonIoException("Tabular row width mismatch at line " + lineNumber +
                        ", expected " + colHeadersSize + " values, got " + valuesCount);
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
        if (!hasLine()) {
            return false;
        }
        if (isTrimmedEmpty()) {
            return false;
        }
        int indent = peekIndent();
        if (indent < baseIndent) {
            return false;
        }
        return indexOfCharInBuf(delimiter) >= 0 || findColonInBuf() <= 0;
    }

    /**
     * Parse a delimiter-separated row directly into a JsonObject using column headers.
     * Avoids the intermediate List that readInlineArray() would create for tabular rows.
     * Returns the number of values parsed (for strict-mode validation).
     */
    private int parseRowIntoObject(String content, List<String> columnHeaders, char delimiter, JsonObject target) {
        int len = content.length();
        int colIndex = 0;
        int colHeadersSize = columnHeaders.size();

        // Fast path: no quotes or escapes — parse directly from string ranges
        if (content.indexOf('"') < 0 && content.indexOf('\\') < 0) {
            int tokenStart = 0;
            for (int i = 0; i < len; i++) {
                if (content.charAt(i) == delimiter) {
                    if (colIndex < colHeadersSize) {
                        appendColumn(target, columnHeaders.get(colIndex), readScalar(content, tokenStart, i));
                    }
                    colIndex++;
                    tokenStart = i + 1;
                }
            }
            if (tokenStart < len || colIndex < colHeadersSize) {
                if (colIndex < colHeadersSize) {
                    appendColumn(target, columnHeaders.get(colIndex), readScalar(content, tokenStart, len));
                }
                colIndex++;
            }
            return colIndex;
        }

        // Slow path: handle quotes and escapes via StringBuilder
        inlineBuf.setLength(0);
        final StringBuilder current = inlineBuf;
        boolean inQuotes = false;
        boolean escaped = false;

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
                if (colIndex < colHeadersSize) {
                    appendColumn(target, columnHeaders.get(colIndex), readScalar(trimAscii(current)));
                }
                colIndex++;
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        // Last element
        if (current.length() > 0 || colIndex < colHeadersSize) {
            if (colIndex < colHeadersSize) {
                appendColumn(target, columnHeaders.get(colIndex), readScalar(trimAscii(current)));
            }
            colIndex++;
        }

        return colIndex;
    }

    private void appendColumn(JsonObject target, String header, Object value) {
        String metaKey = META_KEY_MAP.get(header);
        if (metaKey != null) {
            loadMetaField(target, metaKey, value);
        } else {
            target.appendFieldForParser(header, value);
        }
    }

    /**
     * Read an inline array with a specific delimiter.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> readInlineArray(String content, int count, char delimiter) {
        List<Object> elements = new ArrayList<>(count);
        int len = content.length();

        // Fast path: no quotes or escapes — parse directly from string ranges
        if (content.indexOf('"') < 0 && content.indexOf('\\') < 0) {
            int tokenStart = 0;
            for (int i = 0; i < len; i++) {
                if (content.charAt(i) == delimiter) {
                    elements.add(readScalar(content, tokenStart, i));
                    tokenStart = i + 1;
                }
            }
            if (tokenStart < len || elements.size() < count) {
                elements.add(readScalar(content, tokenStart, len));
            }
        } else {
            // Slow path: handle quotes and escapes via StringBuilder
            inlineBuf.setLength(0);
            final StringBuilder current = inlineBuf;
            boolean inQuotes = false;
            boolean escaped = false;

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
            if (!hasLine()) {
                break;  // EOF
            }

            if (isTrimmedEmpty()) {
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
            if (!startsWithDashInBuf()) {
                break;  // Not a list element
            }

            int trimStart = currentTrimStart;
            int trimEnd = currentTrimEnd;
            consumeLine();
            int elementStart = trimStart + 1;
            while (elementStart < trimEnd && lineBuf[elementStart] <= ' ') {
                elementStart++;
            }

            if (elementStart >= trimEnd) {
                // Nested object/array on next lines
                if (hasLine()) {
                    int nextIndent = peekIndent();
                    if (nextIndent > indent) {
                        if (isArrayStartInBuf()) {
                            elements.add(readArray());
                        } else if (findColonInBuf() > 0) {
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
            } else if (isEmptyObject(lineBuf, elementStart, trimEnd)) {
                // Empty object: - {}
                elements.add(new JsonObject());
            } else if (isArrayStart(lineBuf, elementStart, trimEnd)) {
                elements.add(parseArrayFromLine(trimAsciiRangeBuf(elementStart, trimEnd)));
            } else {
                String elementContent = trimAsciiRangeBuf(elementStart, trimEnd);
                if (findColonPosition(elementContent) > 0 && !elementContent.startsWith("\"")) {
                    // Per TOON spec: first field of object is on hyphen line
                    // e.g., "- name: John" followed by "  age: 30" on next line
                    // This is an inline object - read it with subsequent fields
                    JsonObject inlineObj = readInlineObject(elementContent, indent);
                    elements.add(inlineObj);
                } else {
                    elements.add(readScalar(lineBuf, elementStart, trimEnd));
                }
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
        if (!hasLine()) {
            return false;
        }
        if (isTrimmedEmpty()) {
            return false;
        }
        int indent = peekIndent();
        return indent >= baseIndent && startsWithDashInBuf();
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
                boolean wasQuoted = !realKey.isEmpty() && realKey.charAt(0) == '"';
                if (wasQuoted) {
                    realKey = unquoteString(realKey);
                }
                putValue(jsonObj, realKey, parseArrayFromLine(sb.toString()), wasQuoted);
            } else {
                boolean wasQuoted = !key.isEmpty() && key.charAt(0) == '"';
                key = unquoteString(key);
                if (valuePart.isEmpty()) {
                    // Check for nested structure on next line
                    if (hasLine() && peekIndent() > hyphenIndent) {
                        if (isArrayStartInBuf()) {
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
            if (!hasLine()) {
                break;  // EOF
            }

            if (peekIndent() != fieldIndent) {
                break;  // Not at our indent level
            }

            if (isTrimmedEmpty()) {
                consumeLine();
                continue;
            }

            // Find colon directly in the line buffer
            int bufColonPos = findColonInBuf();
            if (bufColonPos <= 0) {
                break;  // Not a key: value pair
            }

            int trimStart = currentTrimStart;
            int trimEnd = currentTrimEnd;
            consumeLine();
            String key = trimAsciiRangeBuf(trimStart, trimStart + bufColonPos);
            int valueStart2 = trimStart + bufColonPos + 1;
            if (valueStart2 < trimEnd && lineBuf[valueStart2] == ' ') {
                valueStart2++;
            }
            // Check for combined field+array notation
            // Also handles folded keys: data.items[N]:
            int keyLen = key.length();
            if (keyLen > 0 && key.charAt(keyLen - 1) == ']') {
                int bracketStart = key.lastIndexOf('[');
                if (bracketStart >= 0) {
                    String realKey = key.substring(0, bracketStart);
                    String valuePart = trimAsciiRangeBuf(valueStart2, trimEnd);
                    StringBuilder sb = new StringBuilder(keyLen - bracketStart + 2 + valuePart.length());
                    sb.append(key, bracketStart, keyLen).append(':');
                    if (!valuePart.isEmpty()) {
                        sb.append(' ').append(valuePart);
                    }
                    boolean wasQuoted = !realKey.isEmpty() && realKey.charAt(0) == '"';
                    if (wasQuoted) {
                        realKey = unquoteString(realKey);
                    }
                    putValue(jsonObj, realKey, parseArrayFromLine(sb.toString()), wasQuoted);
                    continue;
                }
            }

            boolean wasQuoted = keyLen > 0 && key.charAt(0) == '"';
            key = unquoteString(key);

            if (isTrimmedEmpty(lineBuf, valueStart2, trimEnd)) {
                if (hasLine() && peekIndent() > fieldIndent) {
                    if (isArrayStartInBuf()) {
                        putValue(jsonObj, key, readArray(), wasQuoted);
                    } else {
                        putValue(jsonObj, key, readObject(fieldIndent + 1, null), wasQuoted);
                    }
                } else {
                    putValue(jsonObj, key, null, wasQuoted);
                }
            } else if (isArrayStart(lineBuf, valueStart2, trimEnd)) {
                putValue(jsonObj, key, parseArrayFromLine(trimAsciiRangeBuf(valueStart2, trimEnd)), wasQuoted);
            } else if (isEmptyObject(lineBuf, valueStart2, trimEnd)) {
                putValue(jsonObj, key, new JsonObject(), wasQuoted);
            } else {
                putValue(jsonObj, key, readScalar(lineBuf, valueStart2, trimEnd), wasQuoted);
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
        return readScalar(text, 0, text.length());
    }

    private Object readScalar(String text, int start, int end) {
        while (start < end && text.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && text.charAt(end - 1) <= ' ') {
            end--;
        }
        if (start >= end) {
            return null;
        }

        int len = end - start;
        char firstChar = text.charAt(start);
        char lastChar = text.charAt(end - 1);

        if (strictToon) {
            boolean startsQuote = firstChar == '"';
            boolean endsQuote = lastChar == '"';
            if (startsQuote != endsQuote) {
                throw new JsonIoException("Unclosed quoted string at line " + lineNumber);
            }
        }

        if (len == 4) {
            if (matchesLiteral(text, start, "null")) {
                return null;
            }
            if (matchesLiteral(text, start, "true")) {
                return Boolean.TRUE;
            }
        } else if (len == 5 && matchesLiteral(text, start, "false")) {
            return Boolean.FALSE;
        }

        if (len >= 2 && firstChar == '"' && lastChar == '"') {
            return parseQuotedString(text, start, end);
        }

        if (!isLikelyNumberStart(firstChar)) {
            return cacheSubstring(text, start, end);
        }

        Number num = parseNumber(text, start, end);
        if (num != null) {
            return num;
        }

        return cacheSubstring(text, start, end);
    }

    private Object readScalar(char[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        if (start >= end) {
            return null;
        }

        int len = end - start;
        char firstChar = buf[start];
        char lastChar = buf[end - 1];

        if (strictToon) {
            boolean startsQuote = firstChar == '"';
            boolean endsQuote = lastChar == '"';
            if (startsQuote != endsQuote) {
                throw new JsonIoException("Unclosed quoted string at line " + lineNumber);
            }
        }

        if (len == 4) {
            if (matchesLiteral(buf, start, "null")) {
                return null;
            }
            if (matchesLiteral(buf, start, "true")) {
                return Boolean.TRUE;
            }
        } else if (len == 5 && matchesLiteral(buf, start, "false")) {
            return Boolean.FALSE;
        }

        if (len >= 2 && firstChar == '"' && lastChar == '"') {
            return parseQuotedString(buf, start, end);
        }

        if (!isLikelyNumberStart(firstChar)) {
            return cacheSubstringFromBuf(buf, start, end);
        }

        Number num = parseNumber(buf, start, end);
        if (num != null) {
            return num;
        }

        return cacheSubstringFromBuf(buf, start, end);
    }

    /**
     * Parse a quoted string, handling escape sequences.
     * Only 5 valid escapes: \\, \", \n, \r, \t
     */
    private String parseQuotedString(String text) {
        return parseQuotedString(text, 0, text.length());
    }

    private String parseQuotedString(String text, int start, int end) {
        int escapePos = text.indexOf('\\', start + 1);
        if (escapePos < 0 || escapePos >= end - 1) {
            return cacheSubstring(text, start + 1, end - 1);
        }

        quoteBuf.setLength(0);
        for (int i = start + 1; i < end - 1; i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < end - 1) {
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

    private String parseQuotedString(char[] buf, int start, int end) {
        int escapePos = -1;
        for (int i = start + 1; i < end - 1; i++) {
            if (buf[i] == '\\') {
                escapePos = i;
                break;
            }
        }
        if (escapePos < 0) {
            return cacheSubstringFromBuf(buf, start + 1, end - 1);
        }

        quoteBuf.setLength(0);
        for (int i = start + 1; i < end - 1; i++) {
            char c = buf[i];
            if (c == '\\' && i + 1 < end - 1) {
                char next = buf[++i];
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
        return parseNumber(text, 0, text.length());
    }

    private Number parseNumber(String text, int start, int end) {
        if (start >= end) {
            return null;
        }

        String[] numKeys = numberCacheKeys;
        Number[] numVals = numberCacheValues;
        int len = end - start;
        int hash = 0;
        for (int i = start; i < end; i++) {
            hash = 31 * hash + text.charAt(i);
        }
        int slot = hash & NUMBER_CACHE_MASK;
        String cachedKey = numKeys[slot];
        if (cachedKey != null && cachedKey.length() == len && text.regionMatches(start, cachedKey, 0, len)) {
            return numVals[slot];
        }

        char first = text.charAt(start);
        int digitStart = (first == '-' || first == '+') ? start + 1 : start;
        if (digitStart < end) {
            boolean negative = first == '-';
            long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
            long multmin = limit / 10;
            long result = 0;
            boolean integerOnly = true;
            boolean overflow = false;

            for (int i = digitStart; i < end; i++) {
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
                String key = cacheSubstring(text, start, end);
                if (!overflow) {
                    Number boxed = Long.valueOf(negative ? result : -result);
                    numKeys[slot] = key;
                    numVals[slot] = boxed;
                    return boxed;
                }
                try {
                    Number big = new BigInteger(key);
                    numKeys[slot] = key;
                    numVals[slot] = big;
                    return big;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        try {
            String key = cacheSubstring(text, start, end);
            Number parsed = MathUtilities.parseToMinimalNumericType(key);
            numKeys[slot] = key;
            numVals[slot] = parsed;
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Number parseNumber(char[] buf, int start, int end) {
        if (start >= end) {
            return null;
        }

        String[] numKeys = numberCacheKeys;
        Number[] numVals = numberCacheValues;
        int len = end - start;
        int hash = 0;
        for (int i = start; i < end; i++) {
            hash = 31 * hash + buf[i];
        }
        int slot = hash & NUMBER_CACHE_MASK;
        String cachedKey = numKeys[slot];
        if (cachedKey != null && cachedKey.length() == len) {
            boolean match = true;
            for (int i = 0; i < len; i++) {
                if (buf[start + i] != cachedKey.charAt(i)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return numVals[slot];
            }
        }

        char first = buf[start];
        int digitStart = (first == '-' || first == '+') ? start + 1 : start;
        if (digitStart < end) {
            boolean negative = first == '-';
            long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
            long multmin = limit / 10;
            long result = 0;
            boolean integerOnly = true;
            boolean overflow = false;

            for (int i = digitStart; i < end; i++) {
                char c = buf[i];
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
                String key = cacheSubstringFromBuf(buf, start, end);
                if (!overflow) {
                    Number boxed = Long.valueOf(negative ? result : -result);
                    numKeys[slot] = key;
                    numVals[slot] = boxed;
                    return boxed;
                }
                try {
                    Number big = new BigInteger(key);
                    numKeys[slot] = key;
                    numVals[slot] = big;
                    return big;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        try {
            String key = cacheSubstringFromBuf(buf, start, end);
            Number parsed = MathUtilities.parseToMinimalNumericType(key);
            numKeys[slot] = key;
            numVals[slot] = parsed;
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ========== Line Management ==========

    /**
     * Read a line from the FastReader into the reusable lineBuf.
     * Handles \n, \r, and \r\n line endings.
     * Returns the number of characters read into lineBuf, or -1 on EOF.
     * The line data is available in lineBuf[0..returnValue-1].
     */
    private int readLineRaw() {
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
     * Ensure the next line is loaded without materializing a trimmed String unless requested.
     */
    private boolean hasLine() throws IOException {
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
                currentTrimStart = spaces;
                currentTrimEnd = trimEnd;
                currentTrimmed = spaces < trimEnd ? null : "";
                currentHasLine = true;
            } else {
                currentHasLine = false;
                currentIndent = 0;
                currentTrimStart = 0;
                currentTrimEnd = 0;
                currentTrimmed = "";
            }
        }
        return currentHasLine;
    }

    private int peekIndent() {
        return currentIndent;
    }

    private String peekTrimmed() {
        if (currentTrimmed == null) {
            currentTrimmed = new String(lineBuf, currentTrimStart, currentTrimEnd - currentTrimStart);
        }
        return currentTrimmed;
    }

    private boolean isTrimmedEmpty() {
        return currentTrimStart >= currentTrimEnd;
    }

    private boolean isArrayStartInBuf() {
        int start = currentTrimStart;
        int end = currentTrimEnd;
        if (start >= end) return false;
        char[] buf = lineBuf;
        if (buf[start] != '[') return false;
        for (int i = start + 1; i < end; i++) {
            if (buf[i] == ']') {
                return i + 1 < end && (buf[i + 1] == ':' || buf[i + 1] == '{');
            }
        }
        return false;
    }

    private boolean isEmptyObjectInBuf() {
        return (currentTrimEnd - currentTrimStart) == 2
                && lineBuf[currentTrimStart] == '{'
                && lineBuf[currentTrimStart + 1] == '}';
    }

    private boolean startsWithDashInBuf() {
        return currentTrimStart < currentTrimEnd && lineBuf[currentTrimStart] == '-';
    }

    private int indexOfCharInBuf(char c) {
        char[] buf = lineBuf;
        for (int i = currentTrimStart; i < currentTrimEnd; i++) {
            if (buf[i] == c) return i - currentTrimStart;
        }
        return -1;
    }

    /**
     * Consume the current line (move to next).
     */
    private void consumeLine() {
        lineConsumed = true;
    }

    // ========== Buffer-direct helpers ==========

    /**
     * Find colon position in the current line buffer (within trimmed range).
     * Returns offset relative to currentTrimStart, or -1 if no valid colon found.
     */
    private int findColonInBuf() {
        int start = currentTrimStart;
        int end = currentTrimEnd;
        char[] buf = lineBuf;

        // Find first colon
        int colonPos = -1;
        for (int i = start; i < end; i++) {
            if (buf[i] == ':') {
                colonPos = i;
                break;
            }
        }
        if (colonPos < 0) return -1;

        // Fast path: no quote before the colon — colon is valid
        boolean hasQuoteBefore = false;
        for (int i = start; i < colonPos; i++) {
            if (buf[i] == '"') {
                hasQuoteBefore = true;
                break;
            }
        }
        if (!hasQuoteBefore) return colonPos - start;

        // Slow path: scan for unquoted colon
        boolean inQuotes = false;
        boolean escaped = false;
        for (int i = start; i < end; i++) {
            char c = buf[i];
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ':' && !inQuotes) return i - start;
        }
        return -1;
    }

    /**
     * Create a String from lineBuf range, probing the string cache first.
     */
    private String cacheSubstringFromBuf(char[] buf, int start, int end) {
        int len = end - start;
        if (len == 0) return "";
        if (len > MAX_CACHED_STRING_LENGTH) {
            return new String(buf, start, len);
        }

        int hash = 0;
        for (int i = start; i < end; i++) {
            hash = 31 * hash + buf[i];
        }

        String[] cache = stringCache;
        int slot = hash & STRING_CACHE_MASK;
        String cached = cache[slot];
        if (cached != null && cached.length() == len) {
            boolean match = true;
            for (int j = 0; j < len; j++) {
                if (buf[start + j] != cached.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return cached;
        }

        String s = new String(buf, start, len);
        cache[slot] = s;
        return s;
    }

    /**
     * Trim whitespace and extract a cached String from lineBuf.
     */
    private String trimAsciiRangeBuf(int start, int end) {
        char[] buf = lineBuf;
        if (start < end && buf[start] > ' ' && buf[end - 1] > ' ') {
            return cacheSubstringFromBuf(buf, start, end);
        }
        while (start < end && buf[start] <= ' ') start++;
        while (end > start && buf[end - 1] <= ' ') end--;
        if (start >= end) return "";
        return cacheSubstringFromBuf(buf, start, end);
    }

    private boolean isTrimmedEmpty(char[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        return start >= end;
    }

    private boolean isArrayStart(char[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        if (start >= end || buf[start] != '[') {
            return false;
        }
        int bracketEnd = -1;
        for (int i = start + 1; i < end; i++) {
            if (buf[i] == ']') {
                bracketEnd = i;
                break;
            }
        }
        if (bracketEnd < 0 || bracketEnd + 1 >= end) {
            return false;
        }
        char next = buf[bracketEnd + 1];
        return next == ':' || next == '{';
    }

    private boolean isEmptyObject(char[] buf, int start, int end) {
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        return end - start == 2 && buf[start] == '{' && buf[start + 1] == '}';
    }

    private boolean isLikelyNumberStart(char firstChar) {
        return (firstChar >= '0' && firstChar <= '9') || firstChar == '-' || firstChar == '+' || firstChar == '.';
    }

    private boolean matchesLiteral(String text, int start, String literal) {
        return text.regionMatches(start, literal, 0, literal.length());
    }

    private boolean matchesLiteral(char[] buf, int start, String literal) {
        for (int i = 0; i < literal.length(); i++) {
            if (buf[start + i] != literal.charAt(i)) {
                return false;
            }
        }
        return true;
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

        // Count segments (validation pass)
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

        // Split in one pass, reusing the validated segment count
        String[] segments = new String[segCount];
        segStart = 0;
        for (int i = 0, idx = 0; i <= len; i++) {
            if (i == len || key.charAt(i) == '.') {
                segments[idx++] = cacheSubstring(key, segStart, i);
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
                String meta = META_KEY_MAP.get(key);
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
