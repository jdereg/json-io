package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.FastReader;
import com.cedarsoftware.util.MathUtilities;
import com.cedarsoftware.util.internal.VectorizedArrays;

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
    /** One entry of a header's field list (spec 6): a name, and for a nested field group its own entries. */
    private static final class FieldEntry {
        private final String name;
        private final String metaKey;       // set when the name, quoted or not, is a json-io meta key ($type, ...)
        private final FieldEntry[] group;   // the nested field group, or null for a leaf field

        private FieldEntry(String name, String metaKey, FieldEntry[] group) {
            this.name = name;
            this.metaKey = metaKey;
            this.group = group;
        }
    }

    private static final class ArrayHeader {
        private final int count;
        private final char delimiter;
        private final List<String> columnHeaders;   // the field names, when no field carries a nested field group
        private final FieldEntry[] fieldTree;       // the field list, when a field carries a nested field group
        private final int leafCount;                // cells per row under fieldTree
        private final int valueStart;               // index in the line buffer just after the header's colon
        private final boolean keyed;                // [N:]: an object whose entries are rows that carry their keys

        private ArrayHeader(int count, char delimiter, FieldEntry[] fields, int valueStart, boolean keyed) {
            this.count = count;
            this.delimiter = delimiter;
            this.valueStart = valueStart;
            this.keyed = keyed;
            if (fields == null) {
                this.columnHeaders = null;
                this.fieldTree = null;
                this.leafCount = 0;
            } else if (hasNestedGroup(fields)) {
                this.columnHeaders = null;
                this.fieldTree = fields;
                this.leafCount = countLeaves(fields);
            } else {
                List<String> names = new ArrayList<>(fields.length);
                for (FieldEntry field : fields) {
                    names.add(field.name);
                }
                this.columnHeaders = names;
                this.fieldTree = null;
                this.leafCount = fields.length;
            }
        }

        private boolean isTabular() {
            return columnHeaders != null || fieldTree != null;
        }

        private static boolean hasNestedGroup(FieldEntry[] fields) {
            for (FieldEntry field : fields) {
                if (field.group != null) {
                    return true;
                }
            }
            return false;
        }

        private static int countLeaves(FieldEntry[] fields) {
            int leaves = 0;
            for (FieldEntry field : fields) {
                leaves += field.group == null ? 1 : countLeaves(field.group);
            }
            return leaves;
        }
    }

    private static final int DEFAULT_INDENT_SIZE = 2;  // §12 default: 2 spaces per indent level (matches ToonWriter)
    private static final char DELIMITER = ','; // Default delimiter (matches ToonWriter)
    private static final int STRING_CACHE_MASK = 4095;
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
    private final boolean toonExpandPaths;
    private final int indentSize;
    private final int maxDepth;
    private final ClassLoader classLoader;

    // Line management - supports peek/consume pattern
    private boolean currentHasLine = false;
    private boolean lineConsumed = true;
    private int lineNumber = 0;
    private int currentIndent = 0;
    private int lineSpaces = 0;   // leading spaces of the line readContentLine() last returned
    private int currentTrimStart = 0;
    private int currentTrimEnd = 0;
    private int currentLineEnd = 0;   // end of the line before its trailing whitespace was trimmed
    private String currentTrimmed = null;
    private final StringBuilder quoteBuf = new StringBuilder(64);
    private final StringBuilder inlineBuf = new StringBuilder(64);
    private int fieldListEnd;     // set by parseFieldList: the index just after the field list's closing brace
    private int rowCellCursor;    // the next cell buildRow takes
    // Set once the document names a meta key ($type, $id, $ref, $items, $keys, $enum). A document that names none
    // is plain data: its lists hold nothing for the resolver to build, follow or convert (see metaKeysSeen()).
    private boolean metaKeysSeen;
    private String[] cachedFoldSegments;
    private char[] ownedLineBuf;
    private char[] lineBuf;
    private int lineStart = 0;
    private final FastReader.BufferSlice lineSlice = new FastReader.BufferSlice();
    private final String[] stringCache;
    private final String[] numberCacheKeys;
    private final Number[] numberCacheValues;

    // Per-instance scratch for the cache-verify compare in cacheSubstringFromBuf.
    // Replaces a CharBufScratch ThreadLocal borrow — JFR showed ThreadLocalMap.getEntry
    // hot on this path, one map walk per cache-checked string. The reader is per-parse
    // and single-threaded; bounded by MAX_CACHED_STRING_LENGTH.
    private final char[] cacheCmpScratch = new char[MAX_CACHED_STRING_LENGTH];

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
    /**
     * True once the document read so far named a meta key ({@code $type}, {@code $id}, {@code $ref}, {@code $items},
     * {@code $keys}, {@code $enum}; {@code @} for {@code $}). A document that names none is plain data.
     */
    boolean metaKeysSeen() {
        return metaKeysSeen;
    }

    public ToonReader(Reader reader, ReadOptions readOptions, ReferenceTracker references) {
        this.reader = reader instanceof FastReader ? (FastReader) reader : new FastReader(reader);
        this.readOptions = readOptions == null ? ReadOptionsBuilder.getDefaultReadOptions() : readOptions;
        this.references = references;
        this.maxIdValue = this.readOptions.getMaxIdValue();
        this.strictToon = this.readOptions.isStrictToon();
        this.toonExpandPaths = this.readOptions.isToonExpandPaths();
        this.indentSize = this.readOptions.getToonIndentSize();
        this.maxDepth = this.readOptions.getMaxDepth();
        this.classLoader = this.readOptions.getClassLoader();
        this.ownedLineBuf = TL_LINE_BUF.get();
        this.lineBuf = ownedLineBuf;
        this.stringCache = TL_STRING_CACHE.get();
        this.numberCacheKeys = TL_NUMBER_KEYS.get();
        this.numberCacheValues = TL_NUMBER_VALUES.get();
    }

    /**
     * O(1) hash shared by all string/number cache methods.
     * Samples first, middle, and last chars + length instead of hashing all chars.
     * All cache methods (cacheString, cacheSubstring, cacheSubstringFromBuf, parseNumber)
     * must use this same formula so cross-lookups find each other's entries.
     * Caller applies the appropriate mask (STRING_CACHE_MASK or NUMBER_CACHE_MASK).
     *
     * <p>Propagating len through an extra multiplication (vs. plain addition)
     * cuts measured slot collisions ~5 percentage points on representative
     * JSON/TOON workloads. Mirrors {@code CharStreamTokenizer.cacheHash} so
     * both readers stay aligned.
     */
    private static int cacheHash(char first, char mid, char last, int len) {
        int h = first;
        h = h * 31 + mid;
        h = h * 31 + last;
        h = h * 31 + len;
        return h;
    }

    private String cacheString(String s) {
        int len = s.length();
        if (len == 0) return "";
        if (len > MAX_CACHED_STRING_LENGTH) return s;
        int slot = cacheHash(s.charAt(0), s.charAt(len >> 1), s.charAt(len - 1), len) & STRING_CACHE_MASK;
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

        int slot = cacheHash(source.charAt(start), source.charAt(start + (len >> 1)), source.charAt(end - 1), len) & STRING_CACHE_MASK;
        String[] cache = stringCache;
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

    /**
     * Read the complete TOON value.
     *
     * @param suggestedType optional type hint for the Resolver
     * @return parsed value (JsonObject, Object[], or primitive)
     */
    public Object readValue(Type suggestedType) {
        try {
            Object value = readRootValueRaw(suggestedType);
            // §14.2 strict: a TOON document MUST be exactly one value. Trailing non-blank
            // content after the root value indicates malformed input (two primitives at
            // root, missing-colon line dangling after a key:nested structure, header with
            // missing colon followed by orphan rows, etc.). Non-strict tolerates it.
            if (strictToon) {
                while (hasLine()) {
                    if (isTrimmedEmpty()) {
                        consumeLine();
                        continue;
                    }
                    String trailing = peekTrimmed();
                    throw new JsonIoException("Unexpected content after root value at line "
                            + lineNumber + ": " + trailing);
                }
            }
            return value;
        } catch (IOException e) {
            throw new JsonIoException("Error reading TOON input at line " + lineNumber, e);
        } finally {
            lineSlice.release();
        }
    }

    private Object readRootValueRaw(Type suggestedType) throws IOException {
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
                Object value = readArray();
                if (suggestedType != null && value instanceof JsonObject) {
                    // A keyed header at the root, [N:]{...}:, is the root object (spec 9.5).
                    ((JsonObject) value).setType(suggestedType);
                }
                return value;
            }

            if (findColonInBuf() > 0) {
                return readObject(0, suggestedType);
            }

            String trimmed = peekTrimmed();
            consumeLine();
            return readScalar(trimmed);
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
        // Peek-through-metadata: defer JsonObject allocation until we know which subclass to
        // instantiate. Buffer @id/@type/@ref in PendingMeta while scanning until we encounter
        // @items (→ array shape), @keys (→ complex-key map shape), or a non-metadata field
        // (→ lite shape).
        JsonObject jsonObj = null;
        PendingMeta pending = new PendingMeta();

        while (true) {
            if (!hasLine()) {
                break;  // EOF
            }

            // §12: blank (whitespace-only) lines MAY be skipped regardless of their leading
            // space count, including in the middle of a nested object body. The indent check
            // must come AFTER the blank-line skip — otherwise a blank line at depth 0 inside
            // a nested context (baseIndent ≥ 1) would falsely "exit" the parent.
            if (isTrimmedEmpty()) {
                consumeLine();
                continue;  // Skip empty lines
            }

            int indent = peekIndent();
            if (indent < baseIndent) {
                break;  // Back to parent level
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

            jsonObj = readAndDispatchField(jsonObj, suggestedType, pending,
                    key, trimStart, valueStart, trimEnd, baseIndent);
        }

        // Metadata-only object (e.g., {"@type":"Foo","@id":1} with no shape determiner): allocate
        // lite JsonObject now and apply buffered metadata.
        if (jsonObj == null) {
            jsonObj = new JsonObject();
            applyPendingMetadata(jsonObj, suggestedType, pending);
        }

        return jsonObj;
    }

    /**
     * Resolve {@code key}, parse the value, and dispatch the field through the peek-through
     * pipeline. {@code jsonObj} may be {@code null} when still in pre-allocation phase; the
     * returned value is the freshly-allocated JsonObject (subclass picked by shape) or the
     * still-{@code null} sentinel if this field was buffered as pure metadata. {@code fieldDepth} is the depth at
     * which the field stands: its line's own, or for a list item's first field one below its hyphen (spec 10).
     */
    private JsonObject readAndDispatchField(JsonObject jsonObj, Type suggestedType, PendingMeta pending,
                                            String key, int keyStart, int valueStart, int trimEnd, int fieldDepth)
            throws IOException {
        // Resolve key, value, and wasQuoted into a uniform shape so the dispatch below
        // treats combined-array-notation fields (e.g., "@items[3]:") and standard fields
        // (e.g., "@type: HashMap") the same way.
        String fieldKey;
        Object fieldValue;
        boolean fieldWasQuoted;
        int bracketStart = findUnquotedBracketPosition(key);
        // §14.2 non-strict tolerance: when the bracket segment is malformed (non-integer
        // count, missing close-bracket, trailing non-{ text) AND strict is off, parseHeader
        // returns null and we fall back to literal-key handling — the whole "key[…]" string
        // becomes the literal field name and the value is parsed normally.
        ArrayHeader arrayHeader = null;
        if (bracketStart >= 0) {
            // The header is parsed from the line, not from the key: the key stops at the line's first colon,
            // and a field list may hold one. The key was trimmed, so find where it starts in the buffer.
            int keyOffset = keyStart;
            while (lineBuf[keyOffset] <= ' ') {
                keyOffset++;
            }
            arrayHeader = parseHeader(lineBuf, keyOffset + bracketStart, trimEnd, false);
        }
        if (arrayHeader != null) {
            fieldKey = cacheSubstring(key, 0, bracketStart);
            fieldWasQuoted = fieldKey.startsWith("\"");
            if (fieldWasQuoted) {
                fieldKey = unquoteString(fieldKey);
            }
            fieldValue = readHeaderValue(arrayHeader, trimEnd, fieldDepth);
        } else {
            fieldWasQuoted = key.startsWith("\"");
            fieldKey = unquoteString(key);
            if (isTrimmedEmpty(lineBuf, valueStart, trimEnd)) {
                // What the field holds lies deeper than the field itself. A list item's first field stands one
                // level below its hyphen (spec 10), so the lines at that level are the item's other fields, not
                // this field's content: "- a:" followed by "b: 1" is {a: {}, b: 1}.
                if (hasLine() && peekIndent() > fieldDepth) {
                    if (isArrayStartInBuf()) {
                        fieldValue = readArray();
                    } else {
                        // The actual indent of the first nested line is the recursive base
                        fieldValue = readObject(peekIndent(), null);
                    }
                } else {
                    // §8: bare "key:" with no nested body (next line is a sibling at the same
                    // indent, or EOF) is an empty nested object — not null. Null is emitted
                    // explicitly as "key: null" per §4. Round-trips cleanly with the encoder,
                    // which emits empty Maps as bare "key:" too.
                    fieldValue = new JsonObject();
                }
            } else if (isArrayStart(lineBuf, valueStart, trimEnd)) {
                fieldValue = readKeylessArray(valueStart, trimEnd, fieldDepth);
            } else if (isEmptyObject(lineBuf, valueStart, trimEnd)) {
                fieldValue = new JsonObject();
            } else {
                fieldValue = readScalar(lineBuf, valueStart, trimEnd);
            }
        }

        return dispatchField(jsonObj, suggestedType, pending, fieldKey, fieldValue, fieldWasQuoted);
    }

    /**
     * Dispatch a parsed (key, value) field through the peek-through pipeline.
     * <ul>
     *   <li>If {@code jsonObj} is {@code null} (pre-allocation phase), classify the field:
     *       a shape-determining meta key ({@code @items}/{@code @keys}/{@code @enum}) allocates
     *       the appropriate subclass; pure meta keys ({@code @type}/{@code @id}/{@code @ref})
     *       are buffered into {@code pending}; anything else commits to lite {@link JsonObject}.</li>
     *   <li>Once allocated, fields are stored via {@link #putValue}.</li>
     * </ul>
     */
    private JsonObject dispatchField(JsonObject jsonObj, Type suggestedType, PendingMeta pending,
                                     String fieldKey, Object fieldValue, boolean fieldWasQuoted) {
        if (jsonObj == null) {
            String meta = metaKeyFor(fieldKey, fieldWasQuoted);
            if (meta != null) {
                metaKeysSeen = true;
            }
            if (JsonValue.ITEMS.equals(meta)) {
                jsonObj = new JsonObjectArray();
                applyPendingMetadata(jsonObj, suggestedType, pending);
                return loadItems(fieldValue, jsonObj);
            }
            if (JsonValue.KEYS.equals(meta)) {
                jsonObj = new JsonObjectMap();
                applyPendingMetadata(jsonObj, suggestedType, pending);
                return loadKeys(fieldValue, jsonObj);
            }
            if (JsonValue.ENUM.equals(meta)) {
                jsonObj = new JsonObjectArray();
                applyPendingMetadata(jsonObj, suggestedType, pending);
                return loadEnum(fieldValue, jsonObj);
            }
            if (JsonValue.TYPE.equals(meta)) {
                pending.type = resolveBufferedType(fieldValue);
                pending.typeString = (String) fieldValue;
                return null;
            }
            if (JsonValue.ID.equals(meta)) {
                pending.id = validateBufferedIdValue(fieldValue, JsonValue.ID);
                return null;
            }
            if (JsonValue.REF.equals(meta)) {
                pending.refId = validateBufferedIdValue(fieldValue, JsonValue.REF);
                return null;
            }

            // Non-metadata (or unrecognized @-prefixed) field → commit to lite shape.
            jsonObj = new JsonObject();
            applyPendingMetadata(jsonObj, suggestedType, pending);
        }

        // Post-allocation: putValue may itself lazy-promote (e.g., @items field on a lite
        // jsonObj that was committed via a non-meta first field).
        return putValue(jsonObj, fieldKey, fieldValue, fieldWasQuoted);
    }

    /**
     * Holder for buffered pre-allocation metadata. {@code id} is boxed because {@code 0} is a
     * valid id (we need to distinguish "id was set to 0" from "id was never set"); {@code refId}
     * uses {@code 0} as the unset sentinel because it follows the existing isReference()
     * convention on JsonValue.
     */
    private static final class PendingMeta {
        Class<?> type;
        String typeString;
        Long id;
        long refId;
    }

    /**
     * Returns the canonical meta-key constant ({@link JsonValue#ITEMS}, {@link JsonValue#KEYS},
     * etc.) when {@code key} is a recognized metadata key, or {@code null} otherwise. Quoted
     * keys are never treated as metadata.
     */
    private static String metaKeyFor(String key, boolean wasQuoted) {
        if (wasQuoted || key.isEmpty()) {
            return null;
        }
        char first = key.charAt(0);
        if (first != '@' && first != '$') {
            return null;
        }
        return META_KEY_MAP.get(key);
    }

    /**
     * Apply buffered pre-allocation metadata to a freshly-allocated JsonObject. Order mirrors
     * JsonParser.applyPendingMetadata: suggestedType first, then explicit @type override, then
     * @id (with reference-tracker registration), then @ref.
     */
    private void applyPendingMetadata(JsonObject jObj, Type suggestedType, PendingMeta pending) {
        if (suggestedType != null) {
            jObj.setType(suggestedType);
        }
        if (pending.type != null) {
            jObj.setTypeString(pending.typeString);
            jObj.setType(pending.type);
        }
        if (pending.id != null) {
            jObj.setId(pending.id);
            if (references != null) {
                references.put(pending.id, jObj);
            }
        }
        if (pending.refId != 0) {
            jObj.setReferenceId(pending.refId);
        }
    }

    /**
     * Resolve a buffered {@code @type} value to a Class without yet attaching it to a target.
     * Mirrors {@link #loadType(Object, JsonObject)}'s resolution and unknown-type handling.
     */
    private Class<?> resolveBufferedType(Object value) {
        if (!(value instanceof String)) {
            throw new JsonIoException("Expected a String for " + JsonValue.TYPE + " at line " + lineNumber);
        }
        String typeName = (String) value;
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
        return typeClass;
    }

    /**
     * Validate a buffered {@code @id}/{@code @ref} value during the pre-allocation phase.
     * Mirrors the validation logic in {@link #loadId(Object, JsonObject)} so error ordering
     * matches today's behavior.
     */
    private long validateBufferedIdValue(Object value, String fieldName) {
        if (!(value instanceof Number)) {
            throw new JsonIoException("Expected a number for " + fieldName + " at line " + lineNumber);
        }
        long id = ((Number) value).longValue();
        if (id < -maxIdValue || id > maxIdValue) {
            throw new JsonIoException(("@id".equals(fieldName) ? "ID" : "Reference ID")
                    + " value out of safe range at line " + lineNumber + ": " + id);
        }
        return id;
    }

    // ========== Array Parsing ==========

    /**
     * Read the array whose keyless header is the current line, or the object when that header is keyed.
     * An array is returned as an ArrayList instead of Object[] for better Java interoperability.
     */
    private Object readArray() throws IOException {
        if (!hasLine()) {
            return new ArrayList<>();
        }

        int start = currentTrimStart;
        int end = currentTrimEnd;
        int depth = currentIndent;
        consumeLine();

        return readKeylessArray(start, end, depth);
    }

    /**
     * Read an array from its keyless header in lineBuf[start..end): [N]: ..., [N]{fields}:, or the literal [];
     * or the object a keyless keyed header [N:]{fields}: announces.
     * Supports delimiter variants: [N] for comma, [N\t] for tab, [N|] for pipe.
     * An array is returned as an ArrayList instead of Object[] for better Java interoperability.
     *
     * @param headerDepth the depth at which the header stands
     */
    private Object readKeylessArray(int start, int end, int headerDepth) throws IOException {
        final char[] buf = lineBuf;
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }
        // §9.1 canonical empty-array literal: "[]" with no count, no header, no body.
        // Accept this in addition to the legacy "[0]:" form (both are valid per spec).
        if (end - start == 2 && buf[start] == '[' && buf[start + 1] == ']') {
            return new ArrayList<>();
        }
        return readHeaderValue(parseHeader(buf, start, end, true), end, headerDepth);
    }

    /**
     * Read what a header announces: for an array, the values after its colon, or the rows or list items on the
     * lines below it; for a keyed header, the object whose entries are the rows below it. lineBuf still holds
     * the header's line, and lineEnd is the end of that line's content.
     */
    private Object readHeaderValue(ArrayHeader header, int lineEnd, int headerDepth) throws IOException {
        if (header.keyed) {
            // §6: a keyed header carries nothing after its colon. Only spaces are whitespace there: a tab is
            // content, though the line's trimmed end no longer shows it.
            for (int i = header.valueStart; i < currentLineEnd; i++) {
                if (lineBuf[i] != ' ') {
                    throw new JsonIoException("Content after the colon of a keyed header at line " + lineNumber);
                }
            }
            return readKeyedObject(header, headerDepth);
        }
        if (header.count == 0) {
            return new ArrayList<>();
        }
        if (header.isTabular()) {
            return readTabularArray(header);
        }
        int valueStart = header.valueStart;
        while (valueStart < lineEnd && lineBuf[valueStart] == ' ') {
            valueStart++;
        }
        if (valueStart < lineEnd) {
            return readInlineArray(lineBuf, valueStart, lineEnd, header.count, header.delimiter);
        }
        return readListArray(header.count);
    }

    /**
     * Parse the header whose bracket segment starts at buf[bracket] (spec 6): [N], with a tab or a pipe before
     * the closing bracket to declare that delimiter, then an optional field list, then the colon. A colon
     * directly after the length, [N:], makes it a keyed header (spec 9.5), which must have a field list. The
     * header is read from the line rather than from the text before the line's first colon, because a keyed
     * bracket and a field name may hold a colon, and a nested field group holds braces.
     * <p>
     * A malformed header is an error, except behind a key in non-strict mode (§14.2 non-strict tolerance):
     * there null is returned and the caller reads the line as "key: value", bracket text and all.
     *
     * @param keyless true when no key stands before the bracket, so the line has no "key: value" reading
     */
    private ArrayHeader parseHeader(char[] buf, int bracket, int end, boolean keyless) {
        final boolean mustThrow = strictToon || keyless;
        int bracketEnd = -1;
        for (int i = bracket + 1; i < end; i++) {
            if (buf[i] == ']') {
                bracketEnd = i;
                break;
            }
        }
        if (bracketEnd < 0) {
            if (mustThrow) {
                throw new JsonIoException("Malformed array syntax at line " + lineNumber + ": "
                        + new String(buf, bracket, end - bracket));
            }
            return null;
        }

        // Detect delimiter from the bracket segment: [2] = comma, [2\t] = tab, [2|] = pipe
        int countEnd = bracketEnd;
        char delimiter = DELIMITER;
        if (countEnd > bracket + 1) {
            char lastChar = buf[countEnd - 1];
            if (lastChar == '\t' || lastChar == '|') {
                delimiter = lastChar;
                countEnd--;
            }
        }

        // A colon directly after the length marks a keyed header: [2:], [2:\t], [2:|]
        boolean keyed = countEnd > bracket + 1 && buf[countEnd - 1] == ':';
        if (keyed) {
            countEnd--;
            // §6: the colon stands exactly there, so the length before it is digits and nothing else.
            boolean digitsOnly = countEnd > bracket + 1;
            for (int i = bracket + 1; digitsOnly && i < countEnd; i++) {
                digitsOnly = buf[i] >= '0' && buf[i] <= '9';
            }
            if (!digitsOnly) {
                if (mustThrow) {
                    throw new JsonIoException("Malformed keyed header (length) at line " + lineNumber + ": "
                            + new String(buf, bracket, end - bracket));
                }
                return null;
            }
        }

        int count = parseHeaderCount(buf, bracket + 1, countEnd, mustThrow);
        if (count == INVALID_COUNT) {
            return null;
        }

        int pos = bracketEnd + 1;
        FieldEntry[] fields = null;
        if (pos < end && buf[pos] == '{') {
            fields = parseFieldList(buf, pos, end, delimiter, 1);
            if (fields == null) {
                if (mustThrow) {
                    throw new JsonIoException("Malformed array header (field list) at line " + lineNumber + ": "
                            + new String(buf, bracket, end - bracket));
                }
                return null;
            }
            pos = fieldListEnd;
        } else if (keyed) {
            // §6: the rows of a keyed header need its field list to say what their cells are.
            if (mustThrow) {
                throw new JsonIoException("Malformed keyed header (no field list) at line " + lineNumber + ": "
                        + new String(buf, bracket, end - bracket));
            }
            return null;
        }
        while (pos < end && buf[pos] <= ' ') {
            pos++;
        }
        if (pos >= end || buf[pos] != ':') {
            // Trailing content after the bracket segment or the field list (whitespace between bracket and
            // fields, extra-bracket noise, a missing colon). Non-strict: literal-key handling at caller.
            if (mustThrow) {
                throw new JsonIoException("Malformed array header (unexpected content after "
                        + (fields == null ? "bracket" : "fields") + " segment) at line " + lineNumber + ": "
                        + new String(buf, bracket, end - bracket));
            }
            return null;
        }
        return new ArrayHeader(count, delimiter, fields, pos + 1, keyed);
    }

    private static final int INVALID_COUNT = Integer.MIN_VALUE;

    /**
     * The length a bracket segment declares, from buf[start..end). Returns INVALID_COUNT for text that is not
     * an integer when the caller falls back to a literal key (mustThrow false).
     */
    private int parseHeaderCount(char[] buf, int start, int end, boolean mustThrow) {
        // Plain digits with no leading zero are the whole of what a writer produces: no String needed.
        int digits = end - start;
        if (digits > 0 && digits <= 9 && (digits == 1 || buf[start] != '0')) {
            int n = 0;
            int i = start;
            for (; i < end; i++) {
                char c = buf[i];
                if (c < '0' || c > '9') {
                    break;
                }
                n = n * 10 + (c - '0');
            }
            if (i == end) {
                return n;
            }
        }

        String countStr = new String(buf, start, end - start);
        String trimmedCount = trimAscii(countStr);
        int count;
        try {
            count = Integer.parseInt(trimmedCount);
        } catch (NumberFormatException e) {
            if (mustThrow) {
                throw new JsonIoException("Invalid array count at line " + lineNumber + ": " + countStr);
            }
            return INVALID_COUNT;  // §14.2 non-strict tolerance: non-integer bracket content → literal key
        }
        // §14.2 strict: bracket-length leading zeros (e.g. "03") and negative lengths
        // are malformed array headers.
        if (strictToon) {
            if (trimmedCount.length() > 1 && trimmedCount.charAt(0) == '0') {
                throw new JsonIoException("Array count with leading zeros at line " + lineNumber + ": " + countStr);
            }
            if (count < 0) {
                throw new JsonIoException("Array count must not be negative at line " + lineNumber + ": " + countStr);
            }
        }
        return count;
    }

    /**
     * Parse the field list that opens with the brace at buf[open] (spec 6): field entries separated by the
     * header's delimiter, each a name that its own nested field group may follow directly, as in
     * {id,customer{name,country},total}. Quotes are honoured: a brace, a delimiter or a colon inside a quoted
     * span is part of the name. Leaves {@link #fieldListEnd} just after the closing brace.
     * <p>
     * Returns null when the list cannot be read: a brace without its partner, or text between a nested group
     * and the next delimiter. In strict mode it is also null for a nested group with no name or no fields, and
     * for whitespace between a name and its group; and a name repeated within one list is an error there
     * (§14.2), while non-strict mode lets the last one win (§14.4). The same name at two levels is no repeat.
     */
    private FieldEntry[] parseFieldList(char[] buf, int open, int end, char delimiter, int depth) {
        if (depth > maxDepth) {
            throw new JsonIoException("Field list nested deeper than " + maxDepth + " levels at line " + lineNumber);
        }
        List<FieldEntry> entries = new ArrayList<>();
        Set<String> names = strictToon ? new HashSet<>() : null;
        int i = open + 1;
        while (true) {
            // A name runs to the first unquoted delimiter or brace.
            int nameStart = i;
            boolean inQuotes = false;
            while (i < end) {
                char c = buf[i];
                if (inQuotes) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inQuotes = false;
                    }
                } else if (c == '"') {
                    inQuotes = true;
                } else if (c == delimiter || c == '{' || c == '}') {
                    break;
                } else if (strictToon && (c == ',' || c == '\t' || c == '|')) {
                    // §14.2: an unquoted delimiter other than the header's own. Inside a quoted name these
                    // characters are literal data.
                    throw new JsonIoException("Delimiter mismatch in tabular header at line " + lineNumber);
                }
                i++;
            }
            if (i >= end) {
                return null;    // the line ended inside the field list
            }
            int nameEnd = i;

            FieldEntry[] group = null;
            if (buf[i] == '{') {
                if (strictToon && (nameEnd == nameStart || buf[nameEnd - 1] <= ' ')) {
                    return null;    // a group with no name, or whitespace between the name and its group
                }
                group = parseFieldList(buf, i, end, delimiter, depth + 1);
                if (group == null || (strictToon && group.length == 0)) {
                    return null;
                }
                i = fieldListEnd;
                while (i < end && buf[i] == ' ') {
                    i++;
                }
                if (i >= end || (buf[i] != delimiter && buf[i] != '}')) {
                    return null;    // text between a nested group and the next delimiter
                }
            }

            boolean last = buf[i] == '}';
            // A trailing empty entry, as in {a,}, adds no field.
            if (!last || group != null || nameEnd > nameStart) {
                FieldEntry entry = fieldEntry(nameStart, nameEnd, group);
                if (names != null && !names.add(entry.name)) {
                    throw new JsonIoException("Duplicate field name '" + entry.name + "' in array header at line "
                            + lineNumber);
                }
                entries.add(entry);
            }
            i++;
            if (last) {
                fieldListEnd = i;
                return entries.toArray(new FieldEntry[0]);
            }
        }
    }

    /** The field entry whose name is the text in lineBuf[nameStart..nameEnd): trimmed, and unescaped if quoted. */
    private FieldEntry fieldEntry(int nameStart, int nameEnd, FieldEntry[] group) {
        String raw = trimAsciiRangeBuf(nameStart, nameEnd);
        String name = unquoteString(raw);
        // A meta key quoted or not, as in a header without a nested field group (buildColumnMetaKeys): json-io
        // writes a table's $type column in quotes, $ being no character of an unquoted key
        String metaKey = null;
        if (group == null && !name.isEmpty()) {
            char first = name.charAt(0);
            if (first == '@' || first == '$') {
                metaKey = META_KEY_MAP.get(name);
            }
        }
        return new FieldEntry(name, metaKey, group);
    }

    /**
     * Read the entry rows of a keyed tabular object (spec 9.5): one line "entrykey: cell,cell" per entry, one
     * level below the header. The key is the text before the line's first unquoted colon; the cells after it
     * are read exactly as a row of a tabular array. Every line at that depth is an entry row, so the object
     * ends only where the depth drops back to the header's, or at the end of the input.
     * <p>
     * As the syntax is new in spec v4.0 there is no lenient reading to keep: a row with too few or too many
     * cells, a line without a colon and a line at another depth are errors in both modes. Non-strict mode
     * differs by three of the recoveries of §14.4: the declared count is not checked, a repeated key lets the
     * last one win, and a first row indented too far sets the depth of the rows.
     */
    private JsonObject readKeyedObject(ArrayHeader header, int headerDepth) throws IOException {
        final char delimiter = header.delimiter;
        final List<String> columnHeaders = header.columnHeaders;
        final FieldEntry[] fieldTree = header.fieldTree;
        final int leafCount = header.leafCount;
        final String[] columnMetaKeys = fieldTree == null ? buildColumnMetaKeys(columnHeaders) : null;
        final Object[] cells = fieldTree == null ? null : new Object[leafCount];
        JsonObject entries = new JsonObject();
        int entryCount = 0;
        int entryDepth = -1;
        int blankLine = 0;      // a blank line after the first row: an error in strict mode if a row follows it

        while (hasLine()) {
            if (isTrimmedEmpty()) {
                if (entryCount > 0 && blankLine == 0) {
                    blankLine = lineNumber;
                }
                consumeLine();
                continue;
            }

            int depth = peekIndent();
            if (depth <= headerDepth) {
                break;  // Back to the header's level: the object is complete
            }
            if (entryDepth < 0) {
                if (strictToon && depth != headerDepth + 1) {
                    throw new JsonIoException("Entry row indented more than one level below its header at line "
                            + lineNumber);
                }
                entryDepth = depth;
            } else if (depth != entryDepth) {
                throw new JsonIoException("Line at the wrong depth among the entry rows at line " + lineNumber);
            }
            if (strictToon && blankLine != 0) {
                throw new JsonIoException("Blank lines are not allowed among entry rows at line " + blankLine);
            }

            int colonPos = findColonInBuf();
            if (colonPos < 0) {
                throw new JsonIoException("Entry row without a colon at line " + lineNumber + ": "
                        + peekTrimmed());
            }
            int trimStart = currentTrimStart;
            int trimEnd = currentTrimEnd;
            consumeLine();
            String key = unquoteString(trimAsciiRangeBuf(trimStart, trimStart + colonPos));
            int valueStart = trimStart + colonPos + 1;

            // A bare "key:" holds no cell at all, not one empty cell.
            int valuesCount = 0;
            JsonObject entry = null;
            if (!isTrimmedEmpty(lineBuf, valueStart, trimEnd)) {
                if (fieldTree == null) {
                    entry = new JsonObject(leafCount);
                    valuesCount = parseRowIntoObject(lineBuf, valueStart, trimEnd, columnHeaders, columnMetaKeys,
                            delimiter, entry);
                } else {
                    valuesCount = readRowCells(lineBuf, valueStart, trimEnd, delimiter, cells);
                    if (valuesCount == leafCount) {
                        rowCellCursor = 0;
                        entry = buildRow(fieldTree, cells);
                    }
                }
            }
            if (valuesCount != leafCount) {
                throw new JsonIoException("Entry row width mismatch at line " + lineNumber +
                        ", expected " + leafCount + " values, got " + valuesCount);
            }

            // §14.3: entry keys are the keys of the object. They are never json-io meta keys: an entry is
            // always an object, which none of @type, @id, @ref, @items or @keys can be.
            if (strictToon && entries.containsKey(key)) {
                throw new JsonIoException("Duplicate key '" + key + "' at line " + lineNumber);
            }
            entries.appendFieldForParser(key, entry);
            entryCount++;
        }

        if (strictToon && entryCount != header.count) {
            throw new JsonIoException("Keyed object count mismatch at line " + lineNumber +
                    ", expected " + header.count + " entries, got " + entryCount);
        }
        return entries;
    }

    /**
     * Read a tabular array: rows of delimiter-separated data where each row becomes an object.
     */
    private List<Object> readTabularArray(ArrayHeader header) throws IOException {
        final int count = header.count;
        final char delimiter = header.delimiter;
        final List<String> columnHeaders = header.columnHeaders;
        final FieldEntry[] fieldTree = header.fieldTree;
        List<Object> elements = new ArrayList<>(count);
        int baseIndent = -1;
        int colHeadersSize = header.leafCount;
        String[] columnMetaKeys = fieldTree == null ? buildColumnMetaKeys(columnHeaders) : null;
        Object[] cells = fieldTree == null ? null : new Object[colHeadersSize];

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

            JsonObject rowObj;
            if (fieldTree == null) {
                // Parse the row directly into a pre-sized object (no intermediate List)
                rowObj = new JsonObject(colHeadersSize);
                int valuesCount = parseRowIntoObject(lineBuf, trimStart, trimEnd, columnHeaders, columnMetaKeys,
                        delimiter, rowObj);
                if (strictToon && valuesCount != colHeadersSize) {
                    throw new JsonIoException("Tabular row width mismatch at line " + lineNumber +
                            ", expected " + colHeadersSize + " values, got " + valuesCount);
                }
            } else {
                // §9.3: a row under nested field groups holds one cell per leaf field, in the header's
                // depth-first order. Without every cell the nesting cannot be rebuilt, in either mode.
                int valuesCount = readRowCells(lineBuf, trimStart, trimEnd, delimiter, cells);
                if (valuesCount != colHeadersSize) {
                    throw new JsonIoException("Tabular row width mismatch at line " + lineNumber +
                            ", expected " + colHeadersSize + " values, got " + valuesCount);
                }
                rowCellCursor = 0;
                rowObj = buildRow(fieldTree, cells);
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
    private static String[] buildColumnMetaKeys(List<String> columnHeaders) {
        int size = columnHeaders.size();
        String[] metaKeys = null;
        for (int i = 0; i < size; i++) {
            String header = columnHeaders.get(i);
            if (header.isEmpty()) {
                continue;
            }
            char first = header.charAt(0);
            if (first != '@' && first != '$') {
                continue;
            }
            String metaKey = META_KEY_MAP.get(header);
            if (metaKey != null) {
                if (metaKeys == null) {
                    metaKeys = new String[size];
                }
                metaKeys[i] = metaKey;
            }
        }
        return metaKeys;
    }

    private int parseRowIntoObject(char[] buf, int start, int end, List<String> columnHeaders, String[] columnMetaKeys,
                                   char delimiter, JsonObject target) {
        while (start < end && buf[start] <= ' ') {
            start++;
        }
        while (end > start && buf[end - 1] <= ' ') {
            end--;
        }

        int colIndex = 0;
        int colHeadersSize = columnHeaders.size();

        int tokenStart = start;
        for (int i = start; i < end; i++) {
            char c = buf[i];
            if (c == delimiter) {
                if (colIndex < colHeadersSize) {
                    appendColumn(target, columnHeaders, columnMetaKeys, colIndex, readScalar(buf, tokenStart, i));
                }
                colIndex++;
                tokenStart = i + 1;
            } else if (c == '"' || c == '\\') {
                return parseQuotedRowRemainder(buf, tokenStart, end, columnHeaders, columnMetaKeys, delimiter, target,
                        colIndex);
            }
        }
        if (tokenStart < end || colIndex < colHeadersSize) {
            if (colIndex < colHeadersSize) {
                appendColumn(target, columnHeaders, columnMetaKeys, colIndex, readScalar(buf, tokenStart, end));
            }
            colIndex++;
        }
        return colIndex;
    }

    private int parseQuotedRowRemainder(char[] buf, int start, int end, List<String> columnHeaders,
                                        String[] columnMetaKeys, char delimiter, JsonObject target, int colIndex) {
        int colHeadersSize = columnHeaders.size();
        inlineBuf.setLength(0);
        final StringBuilder current = inlineBuf;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = start; i < end; i++) {
            char c = buf[i];

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
                    appendColumn(target, columnHeaders, columnMetaKeys, colIndex, readScalar(trimAscii(current)));
                }
                colIndex++;
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0 || colIndex < colHeadersSize) {
            if (colIndex < colHeadersSize) {
                appendColumn(target, columnHeaders, columnMetaKeys, colIndex, readScalar(trimAscii(current)));
            }
            colIndex++;
        }

        return colIndex;
    }

    /**
     * Split a row at its unquoted delimiters and read each cell (spec 9.3, 11.2). Returns the number of cells
     * the row holds; the first cells.length of them are stored.
     */
    private int readRowCells(char[] buf, int start, int end, char delimiter, Object[] cells) {
        int count = 0;
        int tokenStart = start;
        boolean inQuotes = false;
        for (int i = start; i < end; i++) {
            char c = buf[i];
            if (inQuotes) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == delimiter) {
                if (count < cells.length) {
                    cells[count] = readScalar(buf, tokenStart, i);
                }
                count++;
                tokenStart = i + 1;
            }
        }
        if (count < cells.length) {
            cells[count] = readScalar(buf, tokenStart, end);
        }
        return count + 1;
    }

    /**
     * Build the object a row stands for by walking the field list in header order (spec 9.3): a leaf field
     * takes the next cell, and a nested field group becomes an object built from its own fields.
     */
    private JsonObject buildRow(FieldEntry[] fields, Object[] cells) {
        JsonObject obj = new JsonObject(fields.length);
        for (FieldEntry field : fields) {
            if (field.group != null) {
                obj.appendFieldForParser(field.name, buildRow(field.group, cells));
            } else if (field.metaKey != null) {
                loadMetaField(obj, field.metaKey, cells[rowCellCursor++]);
            } else {
                obj.appendFieldForParser(field.name, cells[rowCellCursor++]);
            }
        }
        return obj;
    }

    private void appendColumn(JsonObject target, List<String> columnHeaders, String[] columnMetaKeys, int colIndex,
                              Object value) {
        String metaKey = columnMetaKeys == null ? null : columnMetaKeys[colIndex];
        if (metaKey != null) {
            loadMetaField(target, metaKey, value);
        } else {
            target.appendFieldForParser(columnHeaders.get(colIndex), value);
        }
    }

    /**
     * Read an inline array with a specific delimiter.
     * Returns ArrayList instead of Object[] for better Java interoperability.
     */
    private List<Object> readInlineArray(char[] buf, int start, int end, int count, char delimiter) {
        List<Object> elements = new ArrayList<>(count);
        boolean hasQuotesOrEscapes = false;
        for (int i = start; i < end; i++) {
            char c = buf[i];
            if (c == '"' || c == '\\') {
                hasQuotesOrEscapes = true;
                break;
            }
        }

        if (!hasQuotesOrEscapes) {
            int tokenStart = start;
            for (int i = start; i < end; i++) {
                if (buf[i] == delimiter) {
                    if (tokenStart == i) {
                        // Empty token between delimiters (e.g. "a,,c") parses as empty string
                        // per §4 — empty unquoted token can only be a string, not a keyword
                        // or number, so type-inference picks string.
                        elements.add("");
                    } else {
                        elements.add(readScalar(buf, tokenStart, i));
                    }
                    tokenStart = i + 1;
                }
            }
            if (tokenStart < end) {
                elements.add(readScalar(buf, tokenStart, end));
            } else if (elements.size() < count) {
                // Trailing empty token (e.g. "a,b,") — same rule as above.
                elements.add("");
            }
        } else {
            inlineBuf.setLength(0);
            final StringBuilder current = inlineBuf;
            boolean inQuotes = false;
            boolean escaped = false;

            for (int i = start; i < end; i++) {
                char c = buf[i];

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
                // Bare hyphen "- " — per §9.4 emits an empty object UNLESS the next line is
                // a nested array or object at deeper indent. Match the writer's "empty
                // object list item as bare hyphen" emission.
                if (hasLine()) {
                    int nextIndent = peekIndent();
                    if (nextIndent > indent) {
                        if (isArrayStartInBuf()) {
                            elements.add(readArray());
                        } else if (findColonInBuf() > 0) {
                            elements.add(readObject(nextIndent, null));
                        } else {
                            // Deeper-indented content that isn't an array or key:value pair
                            // is unusual; treat the bare hyphen as an empty object and let
                            // the outer loop handle that next line on its own.
                            elements.add(new JsonObject());
                        }
                    } else {
                        elements.add(new JsonObject());
                    }
                } else {
                    elements.add(new JsonObject());
                }
            } else if (isEmptyObject(lineBuf, elementStart, trimEnd)) {
                // Empty object: - {}
                elements.add(new JsonObject());
            } else if (isArrayStart(lineBuf, elementStart, trimEnd)) {
                elements.add(readKeylessArray(elementStart, trimEnd, indent));
            } else {
                // Pre-check whether the hyphen-line slice contains an unquoted colon
                // (indicating an inline object like "- name: John"). Work directly on
                // lineBuf — no String materialization — since lineBuf[elementStart..trimEnd)
                // still holds the hyphen line until the first hasLine() inside readInlineObject.
                // The colon scan skips quoted spans, so it tells a quoted key (- "my key": 1) from a
                // quoted string that holds a colon (- "a: b") by itself.
                if (findColonInBuf(elementStart, trimEnd) > 0) {
                    // Per TOON spec: first field of object is on hyphen line
                    // e.g., "- name: John" followed by "  age: 30" on next line
                    JsonObject inlineObj = readInlineObject(elementStart, trimEnd, indent);
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
    private JsonObject readInlineObject(int firstFieldStart, int firstFieldEnd, int hyphenIndent) throws IOException {
        // Peek-through-metadata: same allocation strategy as readObject. Buffer @id/@type/@ref
        // through the first-field handling and the subsequent-field loop until the shape is
        // determined.
        JsonObject jsonObj = null;
        PendingMeta pending = new PendingMeta();

        // Parse first field directly from lineBuf[firstFieldStart..firstFieldEnd).
        // Safe to use lineBuf here because the hyphen-line contents persist in the
        // buffer until the first hasLine() call below (consumeLine() just marks the
        // line; the next line isn't fetched until hasLine() triggers readLineRaw()).
        int relColon = findColonInBuf(firstFieldStart, firstFieldEnd);
        if (relColon > 0) {
            int colonAbs = firstFieldStart + relColon;
            String key = trimAsciiRangeBuf(firstFieldStart, colonAbs);
            int valueStart = colonAbs + 1;
            while (valueStart < firstFieldEnd && lineBuf[valueStart] == ' ') {
                valueStart++;
            }
            int valueEnd = firstFieldEnd;
            // Trim trailing ASCII whitespace within the value slice
            while (valueEnd > valueStart && lineBuf[valueEnd - 1] <= ' ') {
                valueEnd--;
            }

            // §10: a first field carried on the hyphen line stands one level below the hyphen.
            jsonObj = readAndDispatchField(jsonObj, null, pending,
                    key, firstFieldStart, valueStart, valueEnd, hyphenIndent + 1);
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

            jsonObj = readAndDispatchField(jsonObj, null, pending,
                    key, trimStart, valueStart2, trimEnd, fieldIndent);
        }

        // Metadata-only or empty inline object → allocate lite now and apply buffered metadata.
        if (jsonObj == null) {
            jsonObj = new JsonObject();
            applyPendingMetadata(jsonObj, null, pending);
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
     * Parse a quoted string, handling escape sequences per §7.1.
     * Explicit escapes: backslash-backslash, backslash-quote, \n, \r, \t. Plus the
     * backslash-u + 4-hex-digit form (case-insensitive) for any non-explicit codepoint.
     * Lone surrogates in the U+D800-U+DFFF range MUST be rejected.
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
                    case 'u':
                        quoteBuf.append(decodeUnicodeEscape(text, i + 1, end - 1));
                        i += 4;
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
                    case 'u':
                        quoteBuf.append(decodeUnicodeEscape(buf, i + 1, end - 1));
                        i += 4;
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
     * Decode a §7.1 backslash-u + 4-hex-digit unicode escape (case-insensitive). Rejects
     * input with fewer than 4 trailing hex digits and rejects lone surrogates
     * (U+D800-U+DFFF) per §7.1 ABNF (supplementary scalars MUST be literal UTF-8).
     *
     * @param src input source (String or char[] wrapped via overload)
     * @param hexStart index of the first hex digit (immediately after the 'u')
     * @param boundary one past the last index allowed (closing-quote position)
     * @return the decoded char value
     */
    private char decodeUnicodeEscape(String src, int hexStart, int boundary) {
        if (hexStart + 4 > boundary) {
            throw new JsonIoException("Invalid escape sequence: \\u followed by fewer than 4 hex digits at line " + lineNumber);
        }
        int value = 0;
        for (int j = 0; j < 4; j++) {
            int digit = hexDigitOrThrow(src.charAt(hexStart + j));
            value = (value << 4) | digit;
        }
        if (value >= 0xD800 && value <= 0xDFFF) {
            throw new JsonIoException("Invalid escape sequence: \\u" + String.format("%04x", value)
                    + " is a lone surrogate at line " + lineNumber);
        }
        return (char) value;
    }

    private char decodeUnicodeEscape(char[] src, int hexStart, int boundary) {
        if (hexStart + 4 > boundary) {
            throw new JsonIoException("Invalid escape sequence: \\u followed by fewer than 4 hex digits at line " + lineNumber);
        }
        int value = 0;
        for (int j = 0; j < 4; j++) {
            int digit = hexDigitOrThrow(src[hexStart + j]);
            value = (value << 4) | digit;
        }
        if (value >= 0xD800 && value <= 0xDFFF) {
            throw new JsonIoException("Invalid escape sequence: \\u" + String.format("%04x", value)
                    + " is a lone surrogate at line " + lineNumber);
        }
        return (char) value;
    }

    private int hexDigitOrThrow(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new JsonIoException("Invalid hex digit in \\u escape: '" + c + "' at line " + lineNumber);
    }

    /**
     * Try to parse text as a number.
     * Delegates to MathUtilities.parseToMinimalNumericType() which returns the most
     * appropriate type: Long for integers, BigInteger for large integers, Double for
     * decimals with <= 16 mantissa digits, BigDecimal for high-precision decimals.
     * Returns null if text is not a valid number.
     */
    /**
     * Detect a forbidden-leading-zero integer form per §2 canonical numbers (§4
     * type-inference). After an optional minus, a '0' followed by any decimal digit
     * (e.g. "05", "-007", "0123") is not a canonical number and MUST decode as a
     * string. A bare "0" and forms with leading "0." or "0e" remain canonical.
     */
    private static boolean hasForbiddenLeadingZero(String s, int start, int end) {
        if (start >= end) {
            return false;
        }
        int p = s.charAt(start) == '-' ? start + 1 : start;
        if (p >= end || s.charAt(p) != '0') {
            return false;
        }
        int next = p + 1;
        if (next >= end) {
            return false;
        }
        char c = s.charAt(next);
        return c >= '0' && c <= '9';
    }

    private static boolean hasForbiddenLeadingZero(char[] buf, int start, int end) {
        if (start >= end) {
            return false;
        }
        int p = buf[start] == '-' ? start + 1 : start;
        if (p >= end || buf[p] != '0') {
            return false;
        }
        int next = p + 1;
        if (next >= end) {
            return false;
        }
        char c = buf[next];
        return c >= '0' && c <= '9';
    }

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
        // §2 / §4: leading-zero integer forms (e.g. "05", "-007", "0123") are NOT
        // canonical numbers and MUST be treated as strings by type-inference. Returning
        // null here makes the caller fall back to cacheSubstring (string).
        if (hasForbiddenLeadingZero(text, start, end)) {
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

        boolean negative = text.charAt(start) == '-';
        int digitStart = negative ? start + 1 : start;
        if (digitStart < end) {
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
                    Number big = MathUtilities.parseBigInteger(key);
                    numKeys[slot] = key;
                    numVals[slot] = big;
                    return big;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        // Only a token of the number grammar is a number; see isNumberToken.
        if (!isNumberToken(text, start, end)) {
            return null;
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
        // §2 / §4 — see parseNumber(String,...) for rationale.
        if (hasForbiddenLeadingZero(buf, start, end)) {
            return null;
        }

        // Fast path: positive unsigned integer (digits-only, no overflow).
        // Skips the number cache entirely — no hash, no charAt-loop miss check,
        // no cacheSubstringFromBuf key materialization, no store. For this shape
        // of token, the integer parse loop itself is cheaper than the cache
        // infrastructure around it, and test workloads with large int[] / Long
        // fields exercise this path heavily. Falls through to the full path for
        // signed integers, decimals, BigInteger, and anything requiring
        // MathUtilities parsing.
        char firstChar = buf[start];
        if (firstChar >= '0' && firstChar <= '9') {
            long result = 0;
            boolean integerOnly = true;
            for (int i = start; i < end; i++) {
                char c = buf[i];
                if (c < '0' || c > '9') {
                    integerOnly = false;
                    break;
                }
                int digit = c - '0';
                // Overflow guard: (Long.MAX_VALUE - digit) / 10 is the largest
                // value that can be multiplied by 10 and have `digit` added
                // without exceeding Long.MAX_VALUE.
                if (result > (Long.MAX_VALUE - digit) / 10) {
                    integerOnly = false;
                    break;
                }
                result = result * 10 + digit;
            }
            if (integerOnly) {
                return Long.valueOf(result);
            }
            // Fall through to full path
        }

        String[] numKeys = numberCacheKeys;
        Number[] numVals = numberCacheValues;
        int len = end - start;
        int slot = cacheHash(buf[start], buf[start + (len >> 1)], buf[end - 1], len) & NUMBER_CACHE_MASK;
        String cachedKey = numKeys[slot];
        if (cachedKey != null && cachedKey.length() == len) {
            for (int i = 0; i < len; i++) {
                if (buf[start + i] != cachedKey.charAt(i)) {
                    cachedKey = null;
                    break;
                }
            }
            if (cachedKey != null) {
                return numVals[slot];
            }
        }

        boolean negative = firstChar == '-';
        int digitStart = negative ? start + 1 : start;
        if (digitStart < end) {
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
                    Number big = MathUtilities.parseBigInteger(key);
                    numKeys[slot] = key;
                    numVals[slot] = big;
                    return big;
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        // Only a token of the number grammar is a number; see isNumberToken.
        if (!isNumberToken(buf, start, end)) {
            return null;
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
     * Read a line from the FastReader into lineBuf.
     * Handles \n, \r, and \r\n line endings.
     * Returns the number of characters read, or -1 on EOF.
     * The line data is available in lineBuf[lineStart..lineStart+returnValue-1].
     */
    private int readLineRaw() {
        lineSlice.release();
        int borrowed = reader.readLineBorrowed(lineSlice);
        if (borrowed != FastReader.COPY_REQUIRED) {
            if (borrowed >= 0) {
                lineBuf = lineSlice.getBuffer();
                lineStart = lineSlice.getOffset();
            }
            return borrowed;
        }

        lineStart = 0;
        char[] buf = ownedLineBuf;
        lineBuf = buf;
        int total = 0;
        while (true) {
            if (total >= buf.length) {
                buf = ownedLineBuf = lineBuf = Arrays.copyOf(buf, buf.length * 2);
            }
            int count = reader.readLine(buf, total, buf.length - total);
            if (count < 0) {
                return total > 0 ? total : -1;
            }
            total += count;
            if (count < buf.length - total + count) {
                // readLine returned fewer chars than maxLen — line ending was found and consumed
                break;
            }
            // maxLen reached without finding line ending — grow buffer and continue
        }
        return total;
    }

    /**
     * Read the next line that is not a comment into lineBuf. Returns its length, or -1 on EOF, and leaves the
     * count of its leading spaces in {@link #lineSpaces}.
     * <p>
     * Spec 5.1: a comment line is a line whose first character after zero or more leading spaces is '#'. It is
     * removed before anything else looks at the lines, so it is never a row, a list item or a blank line, it
     * never opens or closes a scope, and its own indentation is not checked. Only spaces may come before the
     * '#': a line indented with a tab is not a comment. A '#' anywhere else on a line is content.
     */
    private int readContentLine() {
        while (true) {
            int lineLen = readLineRaw();
            if (lineLen < 0) {
                return -1;
            }
            lineNumber++;
            final char[] buf = lineBuf;  // localize after readLineRaw (which may resize/borrow)
            final int start = lineStart;
            int spaces = 0;
            while (spaces < lineLen && buf[start + spaces] == ' ') {
                spaces++;
            }
            if (spaces == lineLen || buf[start + spaces] != '#') {
                lineSpaces = spaces;
                return lineLen;
            }
        }
    }

    /**
     * Ensure the next line is loaded without materializing a trimmed String unless requested.
     */
    private boolean hasLine() throws IOException {
        if (lineConsumed) {
            int lineLen = readContentLine();
            lineConsumed = false;
            if (lineLen >= 0) {
                final char[] buf = lineBuf;  // localize after readContentLine (which may resize/borrow)
                final int start = lineStart;

                // Indent was counted directly from buf — no intermediate String needed
                final int spaces = lineSpaces;
                // §12: whitespace-only lines MAY be treated as blank regardless of their
                // leading-space count. Skip strict-indent + tab checks for such lines so a
                // 3-space empty line between depth-0 fields (in strict mode) doesn't reject.
                boolean whitespaceOnly = (spaces == lineLen);
                if (!whitespaceOnly) {
                    if (spaces < lineLen && buf[start + spaces] == '\t' && strictToon) {
                        throw new JsonIoException("Tabs are not allowed in indentation at line " + lineNumber);
                    }
                    if (strictToon && spaces % indentSize != 0) {
                        throw new JsonIoException("Indentation must be a multiple of " + indentSize +
                                " spaces at line " + lineNumber);
                    }
                }
                currentIndent = whitespaceOnly ? 0 : spaces / indentSize;

                // Compute trim-end directly from buf
                int trimStart = start + spaces;
                int trimEnd = start + lineLen;
                currentLineEnd = trimEnd;
                while (trimEnd > trimStart && buf[trimEnd - 1] <= ' ') {
                    trimEnd--;
                }
                currentTrimStart = trimStart;
                currentTrimEnd = trimEnd;
                currentTrimmed = trimStart < trimEnd ? null : "";
                currentHasLine = true;
            } else {
                currentHasLine = false;
                currentIndent = 0;
                lineStart = 0;
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
        // §9.1 canonical empty-array literal "[]" (no count, no header, no body).
        if (end - start == 2 && buf[start + 1] == ']') {
            return true;
        }
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
    /**
     * Find the key:value colon in the current line buffer.
     * Single-pass scan replaces the former three-loop approach (find colon, check quotes, slow path).
     * Uses a {@code c <= ':'} range guard so letters and underscores (the vast majority of
     * key characters) need only one comparison per character.
     */
    private int findColonInBuf() {
        return findColonInBuf(currentTrimStart, currentTrimEnd);
    }

    /**
     * Ranged variant of {@link #findColonInBuf()}. Returns the colon offset RELATIVE to
     * {@code start} (i.e., {@code absoluteIndex - start}), or {@code -1} if not found.
     * Used when parsing a first-field slice from within {@code lineBuf} without
     * materializing it as a String.
     */
    private int findColonInBuf(int start, int end) {
        char[] buf = lineBuf;

        // Single pass: find ':' while watching for '"'.
        // Letters (a-z = 97-122, A-Z = 65-90) and underscore (95) are all > ':' (58),
        // so the range guard skips them with one comparison.
        for (int i = start; i < end; i++) {
            char c = buf[i];
            if (c <= ':') {
                if (c == ':') return i - start;
                if (c == '"') {
                    // Quoted key — fall to quote-aware scan from the beginning
                    return findColonQuoteAware(buf, start, end);
                }
            }
        }
        return -1;
    }

    /** Scan for the first unquoted colon, handling escape sequences within quoted strings. */
    private static int findColonQuoteAware(char[] buf, int start, int end) {
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
     * Create a String from a char[] range, probing the string cache first.
     * Uses the shared O(1) slot computation (first + middle + last + length)
     * instead of an O(n) polynomial hash, eliminating the hash loop that
     * dominated JFR profiles for this method.
     */
    private String cacheSubstringFromBuf(char[] buf, int start, int end) {
        int len = end - start;
        if (len == 0) return "";
        if (len > MAX_CACHED_STRING_LENGTH) {
            return new String(buf, start, len);
        }

        int slot = cacheHash(buf[start], buf[start + (len >> 1)], buf[end - 1], len) & STRING_CACHE_MASK;
        String[] cache = stringCache;
        String cached = cache[slot];

        if (cached != null && cached.length() == len) {
            // Bulk-extract cached's chars into the per-instance scratch (String.getChars
            // is a HotSpot intrinsic with SIMD on supported HW), then compare char[] to
            // char[] via VectorizedArrays.equalsRange, which dispatches to JDK 9+'s
            // SIMD-vectorized Arrays.equals(arr, int, int, arr, int, int) intrinsic at
            // runtime (with a JDK 8 loop fallback). Replaces per-char cached.charAt(j)
            // — eliminates the LATIN1/UTF16 coder branch + per-char bounds check +
            // virtual dispatch on every character of the cache-verify step.
            char[] cachedChars = cacheCmpScratch;
            cached.getChars(0, len, cachedChars, 0);
            if (VectorizedArrays.equalsRange(buf, start, end, cachedChars, 0, len)) {
                return cached;
            }
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
        // §9.1 canonical empty-array literal "[]" (no count, no header, no body).
        if (end - start == 2 && buf[start + 1] == ']') {
            return true;
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

    /** A number starts with a digit or a minus (spec 4): {@code +1} and {@code .5} are strings. */
    private boolean isLikelyNumberStart(char firstChar) {
        return (firstChar >= '0' && firstChar <= '9') || firstChar == '-';
    }

    /**
     * TOON's number grammar (spec 4), which is JSON's:
     * {@code -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?}. A token is a number if and only if it
     * matches; every other token -- {@code .5}, {@code 1.}, {@code +1}, {@code 05}, {@code 1e},
     * {@code Infinity}, {@code 0x10}, {@code 1_000}, a date -- is a string. The decision is made here and
     * not left to Java's number parsers, which accept more ({@code Double.parseDouble} reads {@code .5},
     * {@code 1.}, {@code +1}, {@code 0x1p3} and {@code 1d}); read as numbers, such strings would come
     * back changed.
     * <p>
     * It also keeps the cost of a token that merely starts with a digit ("2024-03-15", "10:30:00") to
     * this one pass: before there was a check, such a token went to the parser, and the
     * {@code NumberFormatException} it threw ({@code Throwable.fillInStackTrace}) was the #1 leaf in a
     * JFR profile of TOON reading (291 samples).
     */
    private static boolean isNumberToken(char[] buf, int start, int end) {
        int i = start;
        if (i < end && buf[i] == '-') {
            i++;
        }
        if (i >= end) {
            return false;
        }
        char c = buf[i++];
        if (c >= '1' && c <= '9') {
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                i++;
            }
        } else if (c != '0') {
            return false;
        }
        if (i < end && buf[i] == '.') {
            int fraction = ++i;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                i++;
            }
            if (i == fraction) {
                return false;
            }
        }
        if (i < end && (buf[i] == 'e' || buf[i] == 'E')) {
            i++;
            if (i < end && (buf[i] == '+' || buf[i] == '-')) {
                i++;
            }
            int exponent = i;
            while (i < end && buf[i] >= '0' && buf[i] <= '9') {
                i++;
            }
            if (i == exponent) {
                return false;
            }
        }
        return i == end;
    }

    private static boolean isNumberToken(String text, int start, int end) {
        int i = start;
        if (i < end && text.charAt(i) == '-') {
            i++;
        }
        if (i >= end) {
            return false;
        }
        char c = text.charAt(i++);
        if (c >= '1' && c <= '9') {
            while (i < end && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                i++;
            }
        } else if (c != '0') {
            return false;
        }
        if (i < end && text.charAt(i) == '.') {
            int fraction = ++i;
            while (i < end && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                i++;
            }
            if (i == fraction) {
                return false;
            }
        }
        if (i < end && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < end && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            int exponent = i;
            while (i < end && text.charAt(i) >= '0' && text.charAt(i) <= '9') {
                i++;
            }
            if (i == exponent) {
                return false;
            }
        }
        return i == end;
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

    private int findUnquotedBracketPosition(String text) {
        int bracket = text.indexOf('[');
        if (bracket < 0) {
            return -1;
        }
        int quote = text.indexOf('"');
        if (quote < 0 || quote > bracket) {
            return bracket;
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

            if (c == '[' && !inQuotes) {
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
     * <p>
     * Returns the (possibly promoted) target — meta fields like {@code @items}/{@code @keys}
     * may lazy-promote a lite JsonObject to JsonObjectArray/JsonObjectMap.
     */
    private JsonObject putValue(JsonObject target, String key, Object value, boolean wasQuoted) {
        if (!wasQuoted) {
            char first = key.charAt(0);
            // Meta key fast path: only @/$ prefixed keys can be meta
            if (first == '@' || first == '$') {
                String meta = META_KEY_MAP.get(key);
                if (meta != null) {
                    return loadMetaField(target, meta, value);
                }
            }
            // §13.4 path expansion: gated on toonExpandPaths (default off → literal keys).
            // When enabled, unquoted dotted keys whose segments are all IdentifierSegments
            // are split into nested objects on decode.
            if (toonExpandPaths && key.indexOf('.') >= 0 && validateAndCacheFoldedKey(key)) {
                putWithKeyExpansion(target, key, value);
                return target;
            }
        }
        if (strictToon) {
            // §8 / §14.4: duplicate sibling keys at the same depth MUST error in strict
            // mode. Non-strict mode applies last-write-wins silently (appendFieldForParser
            // below appends the key again, and every JsonObject lookup reads its last value). For target.get to distinguish
            // "key is present with null value" from "key is absent", use containsKey.
            if (target.containsKey(key)) {
                Object existing = target.get(key);
                boolean existingObj = existing instanceof JsonObject;
                boolean incomingObj = value instanceof JsonObject;
                if (existingObj != incomingObj) {
                    throw new JsonIoException("Path expansion conflict at line " + lineNumber + " for key: " + key);
                }
                throw new JsonIoException("Duplicate key '" + key + "' at line " + lineNumber);
            }
        }
        target.appendFieldForParser(key, value);
        return target;
    }

    /**
     * Process a meta field. Returns the (possibly promoted) target — for {@code @items}/
     * {@code @keys}/{@code @enum} the lite JsonObject is replaced with the right subclass
     * via {@link JsonObject#promoteToArray}/{@link JsonObject#promoteToMap}. Callers must
     * use the returned reference from this point on.
     */
    private JsonObject loadMetaField(JsonObject target, String metaKey, Object value) {
        metaKeysSeen = true;
        if (JsonValue.TYPE.equals(metaKey)) {
            loadType(value, target);
        } else if (JsonValue.ID.equals(metaKey)) {
            loadId(value, target);
        } else if (JsonValue.REF.equals(metaKey)) {
            loadRef(value, target);
        } else if (JsonValue.ITEMS.equals(metaKey)) {
            return loadItems(value, target);
        } else if (JsonValue.KEYS.equals(metaKey)) {
            return loadKeys(value, target);
        } else if (JsonValue.ENUM.equals(metaKey)) {
            return loadEnum(value, target);
        }
        return target;
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

    /**
     * Process an {@code @items} payload. May lazy-promote {@code target} from a lite
     * {@link JsonObject} to a {@link JsonObjectArray}; callers must use the returned reference.
     */
    private JsonObject loadItems(Object value, JsonObject target) {
        Object[] items = toObjectArray(value, JsonValue.ITEMS);
        if (items != null) {
            target = JsonObject.promoteToArray(target, references);
            target.setItems(items);
        }
        return target;
    }

    /**
     * Process an {@code @keys} payload. May lazy-promote {@code target} from a lite
     * {@link JsonObject} to a {@link JsonObjectMap}; callers must use the returned reference.
     */
    private JsonObject loadKeys(Object value, JsonObject target) {
        Object[] keys = toObjectArray(value, JsonValue.KEYS);
        if (keys != null) {
            target = JsonObject.promoteToMap(target, references);
            target.setKeys(keys);
        }
        return target;
    }

    /**
     * Process a legacy {@code @enum} marker (used for EnumSet detection). May lazy-promote
     * {@code target} to a {@link JsonObjectArray} if items haven't been set yet.
     */
    private JsonObject loadEnum(Object value, JsonObject target) {
        if (!(value instanceof String)) {
            throw new JsonIoException("Expected a String for " + JsonValue.ENUM + " at line " + lineNumber);
        }
        loadType(value, target);
        if (target.getItems() == null) {
            target = JsonObject.promoteToArray(target, references);
            target.setItems(ArrayUtilities.EMPTY_OBJECT_ARRAY);
        }
        return target;
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
