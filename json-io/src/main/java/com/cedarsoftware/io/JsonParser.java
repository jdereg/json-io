package com.cedarsoftware.io;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
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
import com.cedarsoftware.util.StringUtilities;
import com.cedarsoftware.util.TypeUtilities;

import static com.cedarsoftware.io.JsonObject.ENUM;
import static com.cedarsoftware.io.JsonObject.ID;
import static com.cedarsoftware.io.JsonObject.ITEMS;
import static com.cedarsoftware.io.JsonObject.KEYS;
import static com.cedarsoftware.io.JsonObject.REF;
import static com.cedarsoftware.io.JsonObject.SHORT_ID;
import static com.cedarsoftware.io.JsonObject.SHORT_ITEMS;
import static com.cedarsoftware.io.JsonObject.SHORT_KEYS;
import static com.cedarsoftware.io.JsonObject.SHORT_REF;
import static com.cedarsoftware.io.JsonObject.SHORT_TYPE;
import static com.cedarsoftware.io.JsonObject.TYPE;
import static com.cedarsoftware.io.JsonValue.JSON5_ID;
import static com.cedarsoftware.io.JsonValue.JSON5_ITEMS;
import static com.cedarsoftware.io.JsonValue.JSON5_KEYS;
import static com.cedarsoftware.io.JsonValue.JSON5_REF;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_ID;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_ITEMS;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_KEYS;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_REF;
import static com.cedarsoftware.io.JsonValue.JSON5_SHORT_TYPE;
import static com.cedarsoftware.io.JsonValue.JSON5_TYPE;
import static com.cedarsoftware.util.MathUtilities.parseBigDecimal;
import static com.cedarsoftware.util.MathUtilities.parseBigInteger;
import static com.cedarsoftware.util.MathUtilities.parseDouble;
import static com.cedarsoftware.util.MathUtilities.parseToMinimalNumericType;

/**
 * Parse the JSON input stream supplied by the FastPushbackReader to the constructor.
 * Parse the JSON input stream supplied by the FastPushbackReader to the constructor.
 * The entire JSON input stream will be read until it is emptied: an EOF (-1) is read.
 * <p>
 * While reading the content, Java Maps (JsonObjects) are used to hold the contents of
 * JSON objects { }.  Lists are used to hold the contents of JSON arrays.  Each object
 * that has an @id field will be copied into the supplied 'objectsMap' constructor
 * argument.  This allows the user of this class to locate any referenced object
 * directly.
 * <p>
 * When this parser completes, the @ref (references to objects identified with @id)
 * are stored as a JsonObject with a @ref as the key and the ID value of the object.
 * No substitution has yet occurred (substituting the @ref pointers with a Java
 * reference to the actual Map (Map containing the @id)).
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
class JsonParser {
    private final FastReader input;
    private final StringBuilder strBuf;
    private final char[] readBuf = new char[256];  // Reusable buffer for bulk string reading
    private final FastReader.BufferSlice readSlice = new FastReader.BufferSlice();
    private final StringBuilder numBuf = new StringBuilder();
    private int curParseDepth = 0;
    private final boolean allowNanAndInfinity;
    private final int maxParseDepth;
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;

    // Instance-level cache for string deduplication (array-based for zero-allocation hits)
    // Uses simple hash-indexed slots with last-write-wins collision handling
    private static final int STRING_CACHE_MASK = 2047;  // 2048 slots (power of 2 - 1)
    private static final int MAX_CACHED_STRING_LENGTH = 64;
    private static final int NO_PREFETCH = -2;
    private final String[] stringCacheArray = new String[STRING_CACHE_MASK + 1];
    // Performance: Hoisted ReadOptions constants to avoid repeated method calls
    private final long maxIdValue;
    private final boolean strictJson;
    private final boolean integerTypeBigInteger;
    private final boolean integerTypeBoth;
    private final boolean floatingPointBigDecimal;
    private final boolean floatingPointBoth;
    private final ClassLoader classLoader;
    private final Map<CharSequence, CharSequence> substitutes;
    
    private static final Map<CharSequence, CharSequence> SUBSTITUTES = new HashMap<>(16);

    // Static lookup tables for performance
    private static final char[] ESCAPE_CHAR_MAP = new char[128];
    private static final int[] HEX_VALUE_MAP = new int[128];

    static {
        // Initialize escape character map
        ESCAPE_CHAR_MAP['\\'] = '\\';
        ESCAPE_CHAR_MAP['/'] = '/';
        ESCAPE_CHAR_MAP['"'] = '"';
        ESCAPE_CHAR_MAP['\''] = '\'';
        ESCAPE_CHAR_MAP['b'] = '\b';
        ESCAPE_CHAR_MAP['f'] = '\f';
        ESCAPE_CHAR_MAP['n'] = '\n';
        ESCAPE_CHAR_MAP['r'] = '\r';
        ESCAPE_CHAR_MAP['t'] = '\t';

        // Initialize hex value map
        Arrays.fill(HEX_VALUE_MAP, -1);
        for (int i = '0'; i <= '9'; i++) {
            HEX_VALUE_MAP[i] = i - '0';
        }
        for (int i = 'a'; i <= 'f'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'a');
        }
        for (int i = 'A'; i <= 'F'; i++) {
            HEX_VALUE_MAP[i] = 10 + (i - 'A');
        }

        // Initialize substitutions for short meta keys (@t, @i, @r, @e, @k)
        SUBSTITUTES.put(SHORT_ID, ID);
        SUBSTITUTES.put(SHORT_REF, REF);
        SUBSTITUTES.put(SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(SHORT_TYPE, TYPE);
        SUBSTITUTES.put(SHORT_KEYS, KEYS);

        // Initialize substitutions for JSON5 meta keys ($type, $id, $ref, $items, $keys)
        SUBSTITUTES.put(JSON5_ID, ID);
        SUBSTITUTES.put(JSON5_REF, REF);
        SUBSTITUTES.put(JSON5_ITEMS, ITEMS);
        SUBSTITUTES.put(JSON5_TYPE, TYPE);
        SUBSTITUTES.put(JSON5_KEYS, KEYS);

        // Initialize substitutions for JSON5 short meta keys ($t, $i, $r, $e, $k)
        SUBSTITUTES.put(JSON5_SHORT_ID, ID);
        SUBSTITUTES.put(JSON5_SHORT_REF, REF);
        SUBSTITUTES.put(JSON5_SHORT_ITEMS, ITEMS);
        SUBSTITUTES.put(JSON5_SHORT_TYPE, TYPE);
        SUBSTITUTES.put(JSON5_SHORT_KEYS, KEYS);
    }

    JsonParser(FastReader reader, Resolver resolver) {
        // For substitutes, use the static map directly (read-only)
        this.substitutes = SUBSTITUTES;

        input = reader;
        this.resolver = resolver;
        readOptions = resolver.getReadOptions();
        references = resolver.getReferences();
        maxParseDepth = readOptions.getMaxDepth();
        allowNanAndInfinity = readOptions.isAllowNanAndInfinity();

        // Initialize string buffer management using ReadOptions configuration
        this.strBuf = new StringBuilder(readOptions.getStringBufferSize());

        // Performance: Hoist ReadOptions constants to avoid repeated method calls
        this.maxIdValue = readOptions.getMaxIdValue();
        this.strictJson = readOptions.isStrictJson();
        this.integerTypeBigInteger = readOptions.isIntegerTypeBigInteger();
        this.integerTypeBoth = readOptions.isIntegerTypeBoth();
        this.floatingPointBigDecimal = readOptions.isFloatingPointBigDecimal();
        this.floatingPointBoth = readOptions.isFloatingPointBoth();
        this.classLoader = readOptions.getClassLoader();
    }

    /**
     * Read a JSON value (see json.org).  A value can be a JSON object, array, string, number, ("true", "false"), or "null".
     * @param suggestedType JsonValue Owning entity.
     */
    Object readValue(Type suggestedType) throws IOException {
        return readValue(skipWhitespaceRead(true), suggestedType);
    }

    private Object readValue(int c, Type suggestedType) throws IOException {
        if (curParseDepth > maxParseDepth) {
            error("Maximum parsing depth exceeded");
        }
        // Fast path for objects and arrays (most common cases)
        if (c == '{') {
            JsonObject jObj = readJsonObject(suggestedType);
            return jObj;
        }
        if (c == '[') {
            Type elementType = TypeUtilities.extractArrayComponentType(suggestedType);
            return readArray(elementType);
        }

        // Handle less common value types
        switch (c) {
            case '"':
                return readString('"');
            case '\'':
                // JSON5 single-quoted strings
                if (strictJson) {
                    error("Single-quoted strings not allowed in strict JSON mode");
                }
                return readString('\'');
            case 'f':
            case 'F':
                readToken("false");
                return false;
            case 'n':
                readToken("null");
                return null;
            case 'N':
                // Could be null or NaN - let readNumber handle it
                return readNumber(c);
            case 't':
            case 'T':
                readToken("true");
                return true;
            case '-':
            case 'I':
                return readNumber(c);
            case '.':
                // JSON5 leading decimal point (e.g., .5 equals 0.5)
                if (strictJson) {
                    error("Leading decimal point not allowed in strict JSON mode");
                }
                return readNumber(c);
            case '+':
                // JSON5 explicit positive sign (e.g., +5)
                if (strictJson) {
                    error("Explicit positive sign not allowed in strict JSON mode");
                }
                return readNumber(c);
            default:
                if (c >= '0' && c <= '9') {
                    return readNumber(c);
                }
                return error("Unknown JSON value type");
        }
    }

    /**
     * Read a JSON object { ... }
     *
     * @return JsonObject that represents the { ... } being read in.  If the JSON object type can be inferred,
     * from a @type field, containing field type, or containing array type, then the javaType will be set on the
     * JsonObject.
     */
    private JsonObject readJsonObject(Type suggestedType) throws IOException {
        // The '{' has already been consumed by readValue()
        // Read the first char of the next field at the top of every loop iteration; the
        // trailing-comma branch then hands the char straight to readFieldName instead of
        // pushing it back and re-reading it.
        int c = skipWhitespaceRead(true);
        if (c == '}') {    // empty object
            // Return a new, empty JsonObject (prevents @id/@ref from interfering)
            return new JsonObject();
        }

        // Performance: Skip injector resolution when there's no meaningful type context
        Class<?> rawClass = TypeUtilities.getRawClass(suggestedType);
        ReadOptionsBuilder.InjectorPlan injectorPlan;
        if (suggestedType == null || rawClass == Object.class || rawClass == null) {
            // No type context - skip expensive injector work
            injectorPlan = ReadOptionsBuilder.InjectorPlan.EMPTY;
        } else {
            injectorPlan = ReadOptionsBuilder.getInjectorPlan(readOptions, rawClass);
        }

        // Peek-through-metadata: defer JsonObject allocation until we know which subclass to
        // instantiate (lite/Array/Map). Buffer @id/@type/@ref while scanning until we encounter
        // either @items/@keys (heavy shape determined) or a non-metadata field (lite shape).
        JsonObject jObj = null;
        boolean preAlloc = true;
        Long pendingId = null;
        long pendingRefId = 0;
        Class<?> pendingType = null;
        String pendingTypeString = null;

        ++curParseDepth;

        while (true) {
            CharSequence field = readFieldName(c);
            // Performance: Only check substitutes for fields starting with '@' or '$'.
            // Standard field names (letters, digits) never match any substitute key,
            // so the HashMap lookup is pure overhead for the 99% common case.
            if (field.length() > 0) {
                char firstCh = field.charAt(0);
                if (firstCh == '@' || firstCh == '$') {
                    field = substitutes.getOrDefault(field, field);
                }
            }

            int valueStart = skipWhitespaceRead(true);
            Type fieldGenericType = null;
            if ((valueStart == '{' || valueStart == '[') && !injectorPlan.isEmpty()) {
                // Field type hints are only consumed by nested object/array parsing. Scalar conversion happens later.
                ReadOptionsBuilder.FieldAssignmentPlan assignmentPlan = injectorPlan.getAssignmentPlan(field);
                fieldGenericType = assignmentPlan == null ? null : assignmentPlan.fieldType;

                // If a field generic type is provided, resolve it using the parent's (i.e. jObj's) resolved type.
                if (fieldGenericType != null) {
                    // Use the parent's type (which has been resolved) as context to resolve the field type.
                    fieldGenericType = TypeUtilities.resolveType(suggestedType, fieldGenericType);
                }
            }
            Object value = readValue(valueStart, fieldGenericType);

            if (preAlloc) {
                // Pre-allocation phase: classify field. Buffer pure metadata, otherwise pick
                // the right subclass and process the trigger field.
                boolean isMetadata = field.length() > 0 && field.charAt(0) == '@';

                if (!isMetadata) {
                    // Non-metadata field → lite shape
                    jObj = new JsonObject();
                    applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
                    jObj.appendFieldForParser(field, value);
                    preAlloc = false;
                } else if (StringUtilities.equals(field, ITEMS)) {
                    if (value != null && !value.getClass().isArray()) {
                        error("Expected @items to have an array [], but found: " + value.getClass().getName());
                    }
                    jObj = new JsonObjectArray();
                    applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
                    loadItems((Object[]) value, jObj);
                    preAlloc = false;
                } else if (StringUtilities.equals(field, KEYS)) {
                    jObj = new JsonObjectMap();
                    applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
                    loadKeys(value, jObj);
                    preAlloc = false;
                } else if (StringUtilities.equals(field, TYPE)) {
                    pendingType = loadType(value);
                    pendingTypeString = (String) value;
                } else if (StringUtilities.equals(field, ID)) {
                    pendingId = validateAndExtractIdValue(value, ID);
                } else if (StringUtilities.equals(field, REF)) {
                    pendingRefId = validateAndExtractIdValue(value, REF);
                } else if (StringUtilities.equals(field, ENUM)) {
                    // @enum sets type and (if items not yet present) marks empty items — array-shaped
                    jObj = new JsonObjectArray();
                    applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
                    loadEnum(value, jObj);
                    preAlloc = false;
                } else {
                    // Unknown @-prefixed field → treat as lite, preserve the field
                    jObj = new JsonObject();
                    applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
                    jObj.appendFieldForParser(field, value);
                    preAlloc = false;
                }
            } else {
                // Post-allocation phase: standard field handling

                // Fast path for regular fields (95%+ of fields don't start with '@')
                // Note: length check MUST come first for short-circuit evaluation (empty field names are valid JSON)
                if (field.length() == 0 || field.charAt(0) != '@') {
                    jObj.appendFieldForParser(field, value);
                } else {
                    // Process special meta fields (@type, @id, @ref, etc.)
                    // Use StringUtilities.equals() for CharSequence comparison with String constants
                    if (StringUtilities.equals(field, TYPE)) {
                        Class<?> type = loadType(value);
                        jObj.setTypeString((String) value);
                        jObj.setType(type);
                    } else if (StringUtilities.equals(field, ID)) {
                        loadId(value, jObj);
                    } else if (StringUtilities.equals(field, REF)) {
                        loadRef(value, jObj);
                    } else if (StringUtilities.equals(field, ITEMS)) {
                        if (value != null && !value.getClass().isArray()) {
                            error("Expected @items to have an array [], but found: " + value.getClass().getName());
                        }
                        // Lazy-promote: if a non-metadata field appeared first, jObj is lite.
                        // The arriving @items reclassifies the JSON object as array-shaped.
                        jObj = JsonObject.promoteToArray(jObj, references);
                        loadItems((Object[])value, jObj);
                    } else if (StringUtilities.equals(field, KEYS)) {
                        // Lazy-promote: arriving @keys reclassifies as complex-key map shape.
                        jObj = JsonObject.promoteToMap(jObj, references);
                        loadKeys(value, jObj);
                    } else if (StringUtilities.equals(field, ENUM)) {
                        // Legacy support (@enum was used to indicate EnumSet in prior versions).
                        // Treated as array shape (loadEnum sets items for EnumSet detection).
                        jObj = JsonObject.promoteToArray(jObj, references);
                        loadEnum(value, jObj);
                    } else {
                        jObj.appendFieldForParser(field, value); // Store unrecognized @-prefixed fields
                    }
                }
            }

            c = skipWhitespaceRead(true);
            if (c == '}') {
                break;
            } else if (c != ',') {
                error("Object not ended with '}', instead found '" + (char) c + "'");
            }
            // Check for trailing comma (JSON5 feature)
            c = skipWhitespaceRead(true);
            if (c == '}') {
                // Trailing comma before closing brace
                if (strictJson) {
                    error("Trailing commas not allowed in strict JSON mode");
                }
                break;
            }
            // c is now the first char of the next field name — loop back and reuse it.
        }

        // Metadata-only object (e.g., {"@type":"Foo","@id":1} with no shape determiner): allocate
        // lite JsonObject now and apply buffered metadata.
        if (preAlloc) {
            jObj = new JsonObject();
            applyPendingMetadata(jObj, suggestedType, pendingType, pendingTypeString, pendingId, pendingRefId);
        }

        --curParseDepth;
        return jObj;
    }

    /**
     * Apply buffered metadata accumulated during the pre-allocation peek-through phase to the
     * freshly allocated JsonObject. Mirrors today's order: suggestedType first, then any explicit
     * {@code @type} (which may override), then {@code @id} (with reference-tracker registration),
     * then {@code @ref}.
     */
    private void applyPendingMetadata(JsonObject jObj, Type suggestedType,
                                      Class<?> pendingType, String pendingTypeString,
                                      Long pendingId, long pendingRefId) {
        // Set the refined type on the JsonObject.
        // Performance: Skip type resolution for null or simple Class types (most common case).
        // Only ParameterizedType and other complex types need resolution against themselves.
        if (suggestedType == null || suggestedType instanceof Class) {
            jObj.setType(suggestedType);
        } else {
            jObj.setType(TypeUtilities.resolveType(suggestedType, suggestedType));
        }
        if (pendingType != null) {
            jObj.setTypeString(pendingTypeString);
            jObj.setType(pendingType);
        }
        if (pendingId != null) {
            references.put(pendingId, jObj);
            jObj.setId(pendingId);
        }
        if (pendingRefId != 0) {
            jObj.setReferenceId(pendingRefId);
        }
    }

    /**
     * Validate an {@code @id} or {@code @ref} value during the pre-allocation peek-through phase
     * and return the validated long value. Mirrors the validation logic in
     * {@link #loadId(Object, JsonObject)} / {@link #loadRef(Object, JsonValue)} so error ordering
     * matches today's behavior (validation occurs as soon as the field is read, not deferred to
     * post-allocation).
     */
    private long validateAndExtractIdValue(Object value, String fieldName) {
        if (value == null) {
            error("Null value provided for " + fieldName + " field - expected a number");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + fieldName + ", instead got: " + value.getClass().getSimpleName());
        }
        long id = ((Number) value).longValue();
        if (id < -maxIdValue || id > maxIdValue) {
            String label = ID.equals(fieldName) ? "ID" : "Reference ID";
            String idLabel = ID.equals(fieldName) ? "IDs" : "reference IDs";
            error(label + " value out of safe range: " + id + " - " + idLabel + " must be between -" + maxIdValue + " and +" + maxIdValue);
        }
        return id;
    }

    /**
     * Read a JSON array
     */
    private Object readArray(Type suggestedType) throws IOException {
        // Performance: Pre-size ArrayList to reduce resizing. Size of 64 eliminates
        // 1-2 resize operations for typical JSON arrays while adding only ~200 bytes overhead.
        final List<Object> list = new ArrayList<>(64);
        ++curParseDepth;

        // Peek for an empty array first so readValue never has to handle ']' as a value-start
        // (that case used to pushback ']' and return an EMPTY_ARRAY sentinel — both gone now).
        int c = skipWhitespaceRead(true);
        if (c == ']') {
            --curParseDepth;
            return resolver.resolveArray(suggestedType, list);
        }
        // Read the first char of the next value at the top of every iteration; after the
        // trailing-comma branch this lets us hand the char straight to readValue rather than
        // pushing it back and re-reading it.
        while (true) {
            // Pass along the full Type to readValue so that any generic information is preserved.
            list.add(readValue(c, suggestedType));

            c = skipWhitespaceRead(true);

            if (c == ']') {
                break;
            } else if (c != ',') {
                error("Expected ',' or ']' inside array");
            }
            // Check for trailing comma (JSON5 feature)
            c = skipWhitespaceRead(true);
            if (c == ']') {
                // Trailing comma before closing bracket
                if (strictJson) {
                    error("Trailing commas not allowed in strict JSON mode");
                }
                break;
            }
            // c is now the first char of the next value — loop back and reuse it.
        }

        --curParseDepth;
        return resolver.resolveArray(suggestedType, list);
    }

    /**
     * Read the field name of a JSON object.
     * Supports both quoted strings (standard JSON) and unquoted identifiers (JSON5).
     *
     * @return CharSequence field name.
     */
    private CharSequence readFieldName() throws IOException {
        return readFieldName(skipWhitespaceRead(true));
    }

    /**
     * Read a field name when the caller has already consumed the first non-whitespace
     * character (e.g. when peeking past a comma or open-brace).
     */
    private CharSequence readFieldName(int c) throws IOException {
        CharSequence field;
        boolean colonConsumed = false;

        if (c == '"') {
            // Standard double-quoted field name
            field = readString('"');
        } else if (c == '\'') {
            // JSON5 single-quoted field name
            if (strictJson) {
                error("Single-quoted strings not allowed in strict JSON mode");
            }
            field = readString('\'');
        } else if (isIdentifierStart(c)) {
            // JSON5 unquoted field name
            if (strictJson) {
                error("Unquoted field names not allowed in strict JSON mode");
            }
            field = readUnquotedFieldName(c);
            colonConsumed = true;
        } else {
            error("Expected quote before field name");
            return null; // Unreachable, but satisfies compiler
        }

        if (!colonConsumed) {
            c = skipWhitespaceRead(true);
            if (c != ':') {
                error("Expected ':' between field and value, instead found '" + (char) c + "'");
            }
        }
        return field;
    }

    /**
     * Check if character is a valid ECMAScript identifier start character.
     * Per JSON5 spec, identifiers follow ECMAScript 5.1 IdentifierName production.
     */
    private boolean isIdentifierStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
    }

    /**
     * Check if character is a valid ECMAScript identifier part character.
     */
    private boolean isIdentifierPart(int c) {
        return isIdentifierStart(c) || (c >= '0' && c <= '9');
    }

    /**
     * Read an unquoted identifier (JSON5 feature).
     * The first character has already been read and validated as identifier start.
     *
     * @param firstChar the first character of the identifier (already read)
     * @return the complete identifier string
     */
    private String readUnquotedFieldName(int firstChar) throws IOException {
        strBuf.setLength(0);
        strBuf.append((char) firstChar);

        int c;
        while (true) {
            c = input.read();
            if (c == -1 || !isIdentifierPart(c)) {
                break;
            }
            strBuf.append((char) c);
        }

        c = skipWhitespaceRead(c, true);
        if (c != ':') {
            error("Expected ':' between field and value, instead found '" + (char) c + "'");
        }
        return strBuf.toString();
    }

    /**
     * Return the specified token from the reader.  If it is not found,
     * throw an IOException indicating that.  Converting to c to
     * (char) c is acceptable because the 'tokens' allowed in a
     * JSON input stream (true, false, null) are all ASCII.
     */
    private void readToken(CharSequence token) {
        final int len = token.length();

        // Optimized path for common short tokens
        if (len <= 5) {
            // Fast validation for common tokens: true, false, null
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) {
                    error("EOF reached while reading token: " + token);
                }

                // Fast ASCII lowercase conversion (faster than Character.toLowerCase)
                if (c >= 'A' && c <= 'Z') {
                    c += 32; // Convert uppercase to lowercase
                }

                if (token.charAt(i) != c) {
                    error("Expected token: " + token);
                }
            }
        } else {
            // Fallback for longer tokens (infinity, etc.)
            for (int i = 1; i < len; i++) {
                int c = input.read();
                if (c == -1) {
                    error("EOF reached while reading token: " + token);
                }
                // Fast ASCII lowercase conversion (tokens are ASCII)
                if (c >= 'A' && c <= 'Z') {
                    c += 32;
                }
                int loTokenChar = token.charAt(i);

                if (loTokenChar != c) {
                    error("Expected token: " + token);
                }
            }
        }
    }

    /**
     * Read a JSON number.
     *
     * @param c int a character representing the first digit of the number that
     *          was already read.
     * @return a Number (a Long or a Double) depending on whether the number is
     * a decimal number or integer.  This choice allows all smaller types (Float, int, short, byte)
     * to be represented as well.
     */
    private Number readNumber(int c) {
        // Fast path: simple positive integers (1-9 followed by digits).
        // This is the most common case in JSON (ids, counts, indices, timestamps).
        // Accumulates directly into a long — no StringBuilder, no String allocation.
        if (c >= '1' && c <= '9' && !integerTypeBigInteger) {
            final FastReader in = input;
            long n = c - '0';
            int digitCount = 1;

            while (true) {
                int d = in.read();
                if (d >= '0' && d <= '9') {
                    if (++digitCount > 18) {
                        // Overflow risk — fall back to general path with accumulated prefix
                        return readNumberContinuation(n, d);
                    }
                    n = n * 10 + (d - '0');
                } else if (d == '.' || d == 'e' || d == 'E') {
                    // Float — fall back to general path with integer prefix
                    return readNumberContinuation(n, d);
                } else {
                    // End of number — push back terminator and return
                    if (d != -1) {
                        in.pushback((char) d);
                    }
                    return n;
                }
            }
        }

        // Handle NaN and Infinity (non-standard JSON extension)
        if (allowNanAndInfinity && (c == '-' || c == 'N' || c == 'I')) {
            final boolean isNeg = (c == '-');
            if (isNeg) {
                c = input.read();
            }

            if (c == 'I') {
                readToken("infinity");
                return isNeg ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            } else if (c == 'N') {
                readToken("nan");
                return Double.NaN;
            } else {
                // Number like "-2", not "-Infinity" - continue to normal processing
                return readNumberGeneral('-', c);
            }
        }

        // All numbers go through the general path with direct StringBuilder parsing
        return readNumberGeneral(c);
    }

    /**
     * Continue number parsing after the fast integer path has accumulated a prefix.
     * Writes the accumulated long to StringBuilder, then continues reading remaining
     * digits, decimal points, or exponents from the stream.
     */
    private Number readNumberContinuation(long prefix, int c) {
        final FastReader in = input;
        StringBuilder number = numBuf;
        number.setLength(0);
        number.append(prefix);

        boolean isFloat = false;
        boolean seenDot = false;
        boolean seenExp = false;

        while (true) {
            if (c >= '0' && c <= '9') {
                number.append((char) c);
            } else if (c == '.') {
                if (seenDot || seenExp) return (Number) error("Invalid number: " + number + ".");
                number.append((char) c);
                isFloat = true;
                seenDot = true;
            } else if (c == 'e' || c == 'E') {
                if (seenExp) return (Number) error("Invalid number: " + number + (char) c);
                number.append((char) c);
                isFloat = true;
                seenExp = true;
                int next = in.read();
                if (next == '+' || next == '-') { number.append((char) next); next = in.read(); }
                if (next < '0' || next > '9') {
                    if (next != -1) in.pushback((char) next);
                    return (Number) error("Invalid exponent in number: " + number);
                }
                number.append((char) next);
            } else {
                if (c != -1) in.pushback((char) c);
                break;
            }
            c = in.read();
        }

        try {
            if (isFloat) return readFloatingPoint(number);
            return readInteger(number);
        } catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    /**
     * Parse a JSON number using direct StringBuilder parsing.
     * Integers are parsed directly from StringBuilder without String allocation.
     * This optimization comes from the original heap-based parser.
     * Supports JSON5 hexadecimal numbers (0xFF) in permissive mode.
     */
    private Number readNumberGeneral(int firstChar) {
        return readNumberGeneral(firstChar, NO_PREFETCH);
    }

    private Number readNumberGeneral(int firstChar, int prefetchedAfterSign) {
        final FastReader in = input;
        boolean isFloat = false;
        boolean isNegative = (firstChar == '-');
        boolean isPositive = (firstChar == '+');  // JSON5 explicit positive sign
        boolean seenDot = false;
        boolean seenExp = false;
        boolean seenDigit = false;
        boolean seenDigitAfterExp = false;

        int firstNumberChar = firstChar;
        if (isNegative || isPositive) {
            firstNumberChar = prefetchedAfterSign == NO_PREFETCH ? in.read() : prefetchedAfterSign;
        }

        int pendingChar = NO_PREFETCH;
        if (firstNumberChar == '0') {
            int next = in.read();
            if (next == 'x' || next == 'X') {
                // JSON5 hexadecimal number
                if (strictJson) {
                    error("Hexadecimal numbers not allowed in strict JSON mode");
                }
                return readHexNumber(isNegative);
            }
            pendingChar = next;
        }

        // We are sure we have a positive or negative number, so we read char by char.
        StringBuilder number = numBuf;
        number.setLength(0);
        if (isNegative) {
            number.append((char) firstChar);
        }
        if ((isPositive || isNegative) && firstNumberChar == -1) {
            return (Number) error(isPositive ? "Unexpected end of input after '+'" : "Invalid number: -");
        }

        // Process the first numeric character after any optional sign.
        if (firstNumberChar >= '0' && firstNumberChar <= '9') {
            number.append((char) firstNumberChar);
            seenDigit = true;
        } else if (firstNumberChar == '.') {
            number.append((char) firstNumberChar);
            isFloat = true;
            seenDot = true;
        } else {
            return (Number) error("Invalid number: " + (isPositive ? "+" : "") + number + (char) firstNumberChar);
        }

        while (true) {
            int c = pendingChar == NO_PREFETCH ? in.read() : pendingChar;
            pendingChar = NO_PREFETCH;
            if (c >= '0' && c <= '9') {
                number.append((char) c);
                seenDigit = true;
                if (seenExp) {
                    seenDigitAfterExp = true;
                }
            } else if (c == '.') {
                if (seenDot || seenExp) {
                    return (Number) error("Invalid number: " + number + ".");
                }
                number.append((char) c);
                isFloat = true;
                seenDot = true;
            } else if (c == 'e' || c == 'E') {
                if (seenExp || !seenDigit) {
                    return (Number) error("Invalid number: " + number + (char) c);
                }
                number.append((char) c);
                isFloat = true;
                seenExp = true;

                int next = in.read();
                if (next == '+' || next == '-') {
                    number.append((char) next);
                    next = in.read();
                }
                if (next < '0' || next > '9') {
                    if (next != -1) {
                        in.pushback((char) next);
                    }
                    return (Number) error("Invalid exponent in number: " + number);
                }
                number.append((char) next);
                seenDigit = true;
                seenDigitAfterExp = true;
            } else if (c == -1) {
                break;
            } else {
                in.pushback((char) c);
                break;
            }
        }

        if (!seenDigit || (seenExp && !seenDigitAfterExp)) {
            return (Number) error("Invalid number: " + number);
        }

        try {
            if (isFloat) {
                return readFloatingPoint(number);
            } else {
                return readInteger(number);
            }
        }
        catch (Exception e) {
            return (Number) error("Invalid number: " + number, e);
        }
    }

    /**
     * Parse integer directly from StringBuilder without String allocation.
     * This optimization comes from the original heap-based parser and avoids
     * creating a String object for most integer values.
     */
    private Number readInteger(CharSequence number) {
        // BigInteger mode - use fast parser, accepts CharSequence directly
        if (integerTypeBigInteger) {
            return parseBigInteger(number);
        }

        int len = number.length();

        // Direct parsing for integers that fit in a long (up to 18 digits, or 19 if positive)
        // Long.MAX_VALUE = 9223372036854775807 (19 digits)
        // Long.MIN_VALUE = -9223372036854775808 (19 digits + sign)
        boolean isNeg = number.charAt(0) == '-';
        int digitCount = isNeg ? len - 1 : len;

        if (digitCount <= 18) {
            // Safe to parse directly - won't overflow
            long n = 0;
            int start = isNeg ? 1 : 0;
            for (int i = start; i < len; i++) {
                n = n * 10 + (number.charAt(i) - '0');
            }
            return isNeg ? -n : n;
        }

        // For 19+ digit numbers, use String parsing with overflow handling
        String numStr = number.toString();
        try {
            return Long.parseLong(numStr);
        } catch (Exception e) {
            BigInteger bigInt = parseBigInteger(numStr);
            if (integerTypeBoth) {
                return bigInt;
            } else {
                // Super-big integers (more than 19 digits) will "wrap around" as expected, similar to casting a long
                // to an int, where the originating long is larger than Integer.MAX_VALUE.
                return bigInt.longValue();
            }
        }
    }

    private Number readFloatingPoint(CharSequence numStr) {
        if (floatingPointBigDecimal) {
            return parseBigDecimal(numStr);
        }

        // Hot path: default mode is DOUBLE, so bypass minimal-type analysis.
        // CharSequence overload avoids the .toString() materialization the JDK parser forced.
        if (!floatingPointBoth) {
            return parseDouble(numStr);
        }

        return parseToMinimalNumericType(numStr);
    }

    /**
     * Read a JSON5 hexadecimal number.
     * Called after "0x" or "0X" has been consumed.
     * Supports optional negative sign before the 0x prefix.
     *
     * @param isNegative true if the number was preceded by a minus sign
     * @return the parsed number as a Long
     */
    private Number readHexNumber(boolean isNegative) {
        final FastReader in = input;
        final int[] hexMap = HEX_VALUE_MAP;
        long value = 0;
        int digitCount = 0;

        while (true) {
            int c = in.read();
            int digit = (c >= 0 && c < 128) ? hexMap[c] : -1;
            if (digit < 0) {
                // End of hex digits
                if (c != -1) {
                    in.pushback((char) c);
                }
                break;
            }

            digitCount++;
            if (digitCount > 16) {
                error("Hexadecimal number too large");
            }
            value = (value << 4) | digit;
        }

        if (digitCount == 0) {
            error("Expected hexadecimal digit after 0x");
        }

        return isNegative ? -value : value;
    }

    /**
     * Read a JSON string
     * This method assumes the initial quote has already been read.
     * Supports both double-quoted (standard JSON) and single-quoted (JSON5) strings.
     *
     * @param quoteChar the quote character that started the string ('"' or '\'')
     * @return CharSequence read from JSON input stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private CharSequence readString(char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;

        // Fast path: attempt to read the entire string in one bulk read.
        // Most JSON strings are short (< 256 chars) and have no escape sequences.
        // This path avoids StringBuilder entirely — goes straight from char[] to cache.
        final FastReader.BufferSlice slice = readSlice;
        int charsRead = in.readUntilBorrowed(slice, buf.length, quoteChar, '\\');
        char[] chars;
        int offset;
        boolean borrowed = false;
        if (charsRead == FastReader.COPY_REQUIRED) {
            charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
            chars = buf;
            offset = 0;
        } else if (charsRead >= 0) {
            borrowed = true;
            chars = slice.getBuffer();
            offset = slice.getOffset();
        } else {
            chars = buf;
            offset = 0;
        }
        if (charsRead >= 0 && charsRead < buf.length) {
            if (borrowed) {
                int delimiter = chars[offset + charsRead];
                if (delimiter == quoteChar) {
                    CharSequence value = cacheStringFromChars(chars, offset, charsRead);
                    slice.release();

                    int c = in.read();
                    if (c == -1) {
                        error("EOF reached while reading JSON string");
                    }
                    if (c != quoteChar) {
                        error("Expected closing quote while reading JSON string");
                    }
                    if (curParseDepth == 0) {
                        c = skipWhitespaceRead(false);
                        if (c != -1) {
                            throw new JsonIoException("EOF expected, content found after string");
                        }
                    }
                    return value;
                }

                final StringBuilder str = strBuf;
                str.setLength(0);
                if (charsRead > 0) {
                    str.append(chars, offset, charsRead);
                }
                slice.release();

                int c = in.read();
                if (c == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (c != '\\') {
                    error("Expected escape delimiter while reading JSON string");
                }
                int escapeChar = in.read();
                if (escapeChar == -1) {
                    error("EOF reached while reading escape sequence");
                }
                return readStringWithEscapes(str, escapeChar, quoteChar);
            }

            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }
            if (c == quoteChar) {
                // Common case: short string, no escapes — bypass StringBuilder
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) {
                        throw new JsonIoException("EOF expected, content found after string");
                    }
                }
                return cacheStringFromChars(chars, offset, charsRead);
            }
            // Delimiter was backslash — read the escape character and handle it
            int escapeChar = in.read();
            if (escapeChar == -1) {
                error("EOF reached while reading escape sequence");
            }
            final StringBuilder str = strBuf;
            str.setLength(0);
            if (charsRead > 0) {
                str.append(chars, offset, charsRead);
            }
            return readStringWithEscapes(str, escapeChar, quoteChar);
        }

        // String exceeds buffer or EOF — use StringBuilder slow path
        final StringBuilder str = strBuf;
        str.setLength(0);
        if (charsRead == -1) {
            error("EOF reached while reading JSON string");
        }
        if (charsRead > 0) {
            str.append(chars, offset, charsRead);
        }
        if (borrowed) {
            slice.release();
        }
        return readStringSlowPath(str, quoteChar);
    }

    /**
     * Slow path for strings that exceed the read buffer (> 256 chars).
     * Continues reading chunks into StringBuilder until the closing quote.
     */
    private CharSequence readStringSlowPath(StringBuilder str, char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;

        while (true) {
            int charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
            if (charsRead == -1) {
                error("EOF reached while reading JSON string");
            }
            if (charsRead > 0) {
                str.append(buf, 0, charsRead);
            }
            if (charsRead == buf.length) {
                continue;
            }

            int c = in.read();
            if (c == -1) {
                error("EOF reached while reading JSON string");
            }
            if (c == quoteChar) {
                if (curParseDepth == 0) {
                    c = skipWhitespaceRead(false);
                    if (c != -1) {
                        throw new JsonIoException("EOF expected, content found after string");
                    }
                }
                break;
            }
            // Must be backslash — handle escapes
            return readStringWithEscapes(str, c, quoteChar);
        }
        return cacheString(str);
    }

    /**
     * Handle escape sequences in a string. Called when a backslash delimiter is encountered.
     * The backslash has been consumed; 'delimChar' is the character after it (first escape char).
     */
    private CharSequence readStringWithEscapes(StringBuilder str, int delimChar, char quoteChar) throws IOException {
        final FastReader in = input;
        final char[] buf = readBuf;
        final char[] ESCAPE_CHARS = ESCAPE_CHAR_MAP;
        final int[] HEX_VALUES = HEX_VALUE_MAP;

        // Process the first escape that brought us here
        int c = delimChar;
        // Jump into the escape handling
        while (true) {
            // c is the character after '\\'
            if (c == -1) {
                error("EOF reached while reading escape sequence");
            }

            // Handle escape using lookup table for common escapes
            if (c < ESCAPE_CHARS.length) {
                char escaped = ESCAPE_CHARS[c];
                if (escaped != '\0') {
                    str.append(escaped);
                } else if (c == 'u') {
                    handleUnicodeEscape(str, HEX_VALUES);
                } else if (c == '\n') {
                    if (strictJson) { error("Multi-line strings not allowed in strict JSON mode"); }
                } else if (c == '\r') {
                    if (strictJson) { error("Multi-line strings not allowed in strict JSON mode"); }
                    int next = in.read();
                    if (next != '\n' && next != -1) { in.pushback((char) next); }
                } else {
                    error("Invalid character escape sequence specified: " + (char) c);
                }
            } else {
                error("Invalid character escape sequence specified: " + (char) c);
            }

            // Continue reading the rest of the string
            while (true) {
                int charsRead = in.readUntil(buf, 0, buf.length, quoteChar, '\\');
                if (charsRead == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (charsRead > 0) {
                    str.append(buf, 0, charsRead);
                }
                if (charsRead == buf.length) {
                    continue;
                }

                c = in.read();
                if (c == -1) {
                    error("EOF reached while reading JSON string");
                }
                if (c == quoteChar) {
                    if (curParseDepth == 0) {
                        c = skipWhitespaceRead(false);
                        if (c != -1) {
                            throw new JsonIoException("EOF expected, content found after string");
                        }
                    }
                    return cacheString(str);
                }
                // Another backslash — read escape char and loop back
                c = in.read();
                break; // break inner loop, continue outer escape-handling loop
            }
        }
    }

    /**
     * Handle \\uXXXX Unicode escape sequences, including surrogate pairs.
     */
    private void handleUnicodeEscape(StringBuilder str, int[] HEX_VALUES) {
        final FastReader in = input;

        int value = 0;
        for (int i = 0; i < 4; i++) {
            int c = in.read();
            if (c == -1) { error("EOF reached while reading Unicode escape sequence"); }
            int digit = (c < 128) ? HEX_VALUES[c] : -1;
            if (digit < 0) { error("Expected hexadecimal digit, got: " + (char) c); }
            value = (value << 4) | digit;
        }

        if (value < 0xD800 || value > 0xDFFF) {
            str.append((char) value);
            return;
        }

        // Handle surrogate pairs (high surrogate: 0xD800-0xDBFF)
        if (value <= 0xDBFF) {
            int next = in.read();
            if (next == '\\') {
                next = in.read();
                if (next == 'u') {
                    int lowSurrogate = 0;
                    for (int i = 0; i < 4; i++) {
                        int c = in.read();
                        if (c == -1) { error("EOF reached while reading Unicode escape sequence"); }
                        int digit = (c < 128) ? HEX_VALUES[c] : -1;
                        if (digit < 0) { error("Expected hexadecimal digit, got: " + (char) c); }
                        lowSurrogate = (lowSurrogate << 4) | digit;
                    }
                    if (lowSurrogate >= 0xDC00 && lowSurrogate <= 0xDFFF) {
                        int codePoint = 0x10000 + ((value - 0xD800) << 10) + (lowSurrogate - 0xDC00);
                        str.appendCodePoint(codePoint);
                        return;
                    }
                    str.append((char) value);
                    str.append((char) lowSurrogate);
                    return;
                }
                in.pushback((char) next);
                in.pushback('\\');
            } else if (next != -1) {
                in.pushback((char) next);
            }
        }
        str.append((char) value);
    }

    private static int cacheHash(char first, char mid, char last, int len) {
        return (first * 31 + mid) * 31 + last + len;
    }

    /**
     * Convert CharSequence to String, using array-based cache for string deduplication.
     * Uses a sampled slot hash and verifies full content on hits, creating a String only on misses.
     */
    private CharSequence cacheString(CharSequence str) {
        final int len = str.length();
        if (len == 0) {
            return "";
        }

        // Long string values are frequently unique; skip cache bookkeeping in those cases.
        if (len > MAX_CACHED_STRING_LENGTH) {
            return str.toString();
        }

        final int slot = cacheHash(str.charAt(0), str.charAt(len >> 1), str.charAt(len - 1), len) & STRING_CACHE_MASK;
        final String cached = stringCacheArray[slot];

        if (cached != null && cached.length() == len && cached.contentEquals(str)) {
            return cached;  // Cache hit - no String allocation!
        }

        // Cache miss - create String and cache it
        final String s = str.toString();
        stringCacheArray[slot] = s;
        return s;
    }

    /**
     * Cache a string directly from a char[] range, bypassing StringBuilder entirely.
     * Used by the readString fast path for short strings without escape sequences.
     */
    private CharSequence cacheStringFromChars(char[] buf, int offset, int len) {
        if (len == 0) {
            return "";
        }

        if (len > MAX_CACHED_STRING_LENGTH) {
            return new String(buf, offset, len);
        }

        final int slot = cacheHash(buf[offset], buf[offset + (len >> 1)], buf[offset + len - 1], len) & STRING_CACHE_MASK;
        final String cached = stringCacheArray[slot];

        if (cached != null && cached.length() == len) {
            // Verify content matches the char[] buffer
            boolean match = true;
            for (int i = 0; i < len; i++) {
                if (cached.charAt(i) != buf[offset + i]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return cached;  // Cache hit - no String allocation!
            }
        }

        // Cache miss - create String from char[] and cache it
        final String s = new String(buf, offset, len);
        stringCacheArray[slot] = s;
        return s;
    }

    /**
     * Read until non-whitespace character and then return it.
     * This saves extra read/pushback.
     *
     * @return int representing the next non-whitespace character in the stream.
     * @throws IOException for stream errors or parsing errors.
     */
    private int skipWhitespaceRead(boolean throwOnEof) throws IOException {
        return skipWhitespaceRead(input.read(), throwOnEof);
    }

    private int skipWhitespaceRead(int c, boolean throwOnEof) throws IOException {
        final Reader in = input;
        // Strict mode has no comments, so use a tighter whitespace-only loop.
        if (strictJson) {
            while (true) {
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    c = in.read();
                    continue;
                }
                if (c == '/') {
                    int next = in.read();
                    if (next == '/' || next == '*') {
                        error("Comments not allowed in strict JSON mode");
                    }
                    if (next != -1) {
                        input.pushback((char) next);
                    }
                    return c;
                }
                break;
            }
            if (c == -1 && throwOnEof) {
                error("EOF reached prematurely");
            }
            return c;
        }

        // Performance: Direct character comparison is faster than array bounds check + lookup.
        // JSON whitespace is defined as: space (0x20), tab (0x09), newline (0x0A), carriage return (0x0D)
        while (true) {
            // Skip standard whitespace
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                c = in.read();
                continue;
            }

            // Check for comments (JSON5 feature)
            if (c == '/') {
                int next = in.read();
                if (next == '/') {
                    // Single-line comment: skip until end of line
                    skipSingleLineComment();
                    c = in.read();
                    continue;
                } else if (next == '*') {
                    // Block comment: skip until */
                    skipBlockComment();
                    c = in.read();
                    continue;
                } else {
                    // Not a comment, push back and return '/'
                    if (next != -1) {
                        input.pushback((char) next);
                    }
                    return c;
                }
            }

            break;
        }

        if (c == -1 && throwOnEof) {
            error("EOF reached prematurely");
        }
        return c;
    }

    /**
     * Skip a single-line comment (// until end of line).
     * The leading // has already been consumed.
     */
    private void skipSingleLineComment() {
        int c;
        while ((c = input.read()) != -1) {
            if (c == '\n' || c == '\r') {
                // End of line reached, comment is done
                // Handle \r\n as a single line ending
                if (c == '\r') {
                    int next = input.read();
                    if (next != '\n' && next != -1) {
                        input.pushback((char) next);
                    }
                }
                return;
            }
        }
        // EOF reached - that's OK for single-line comment at end of file
    }

    /**
     * Skip a block comment (slash-star until star-slash).
     * The leading slash-star has already been consumed.
     */
    private void skipBlockComment() {
        boolean sawStar = false;
        int c;
        while ((c = input.read()) != -1) {
            if (sawStar && c == '/') {
                return;
            }
            sawStar = c == '*';
        }
        // EOF reached without closing comment
        error("Unterminated block comment");
    }

    /**
     * Load the @id field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown.  It is the value associated to the @id field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadId(Object value, JsonObject jObj) {
        // Fix null validation - add comprehensive null and type checks
        if (value == null) {
            error("Null value provided for " + ID + " field - expected a number");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + ID + ", instead got: " + value.getClass().getSimpleName());
        }

        long id = ((Number) value).longValue();

        // Performance: Use hoisted maxIdValue constant
        if (id < -maxIdValue || id > maxIdValue) {
            error("ID value out of safe range: " + id + " - IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        }

        references.put(id, jObj);
        jObj.setId(id);
    }

    /**
     * Load the @ref field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown. It is the value associated to the @ref field.
     * @param jObj  JsonValue that will be stuffed with the reference id and marked as finished.
     */
    private void loadRef(Object value, JsonValue jObj) {
        // Fix null validation - add comprehensive null and type checks
        if (value == null) {
            error("Null value provided for " + REF + " field - expected a number");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + REF + ", instead got: " + value.getClass().getSimpleName());
        }

        long refId = ((Number) value).longValue();

        // Performance: Use hoisted maxIdValue constant
        if (refId < -maxIdValue || refId > maxIdValue) {
            error("Reference ID value out of safe range: " + refId + " - reference IDs must be between -" + maxIdValue + " and +" + maxIdValue);
        }

        jObj.setReferenceId(refId);
    }

    /**
     * Load the @enum (EnumSet) field listed in the JSON
     *
     * @param value Object should be a String, if not exception is thrown. It is the class of the Enum.
     */
    private void loadEnum(Object value, JsonObject jObj) {
        if (!(value instanceof String)) {
            error("Expected a String for " + ENUM + ", instead got: " + value);
        }
        Class<?> enumClass = stringToClass((String) value);
        jObj.setTypeString((String) value);
        jObj.setType(enumClass);

        // Only set empty items if no items were specified in JSON
        if (jObj.getItems() == null) {
            jObj.setItems(ArrayUtilities.EMPTY_OBJECT_ARRAY);   // Indicate EnumSet (has both @type and @items)
        }
    }

    /**
     * Load the @type field listed in the JSON
     *
     * @param value Object should be a String, if not an exception is thrown.  It is the value associated to the @type field.
     */
    private Class<?> loadType(Object value) {
        if (!(value instanceof String)) {
            error("Expected a String for " + TYPE + ", instead got: " + value);
        }
        String javaType = (String) value;
        final String substitute = readOptions.getTypeNameAlias(javaType);
        if (substitute != null) {
            javaType = substitute;
        }

        // Resolve class during parsing
        return stringToClass(javaType);
    }

    /**
     * Load the @items field listed in the JSON
     *
     * @param value Object should be an array, if not exception is thrown.  It is the value associated to the @items field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadItems(Object[] value, JsonObject jObj) {
        if (value == null) {
            return;
        }
        // Performance: Remove duplicate array check - signature already ensures Object[]
        jObj.setItems(value);
    }

    /**
     * Load the @keys field listed in the JSON
     *
     * @param value Object should be an array, if not exception is thrown.  It is the value associated to the @keys field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadKeys(Object value, JsonObject jObj) {
        if (value == null) {
            return;
        }
        if (!value.getClass().isArray()) {
            error("Expected @keys to have an array [], but found: " + value.getClass().getName());
        }
        jObj.setKeys((Object[])value);
    }

    private Class<?> stringToClass(String className) {
        String resolvedName = readOptions.getTypeNameAlias(className);
        Class<?> clazz = ClassUtilities.forName(resolvedName, classLoader);
        if (clazz == null) {
            if (readOptions.isFailOnUnknownType()) {
                error("Unknown type (class) '" + className + "' not defined.");
            }
            clazz = readOptions.getUnknownTypeClass();
            if (clazz == null) {
                clazz = LinkedHashMap.class;
            }
        }
        return clazz;
    }

    private Object error(String msg) {
        throw new JsonIoException(getMessage(msg));
    }

    private Object error(String msg, Exception e) {
        throw new JsonIoException(getMessage(msg), e);
    }

    private String getMessage(String msg) {
        return msg + "\n" + input.getLastSnippet();
    }
}
