package com.cedarsoftware.io;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
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

/**
 * Tree-builder that drives a {@link JsonTokenizer} cursor and assembles a
 * {@link JsonObject} graph. As of 4.103.0 this class no longer performs
 * char-level tokenization itself; that work has been split out into
 * {@link CharStreamTokenizer}. JsonParser is now responsible for:
 * <ul>
 *   <li>iterating over the token stream to materialize JsonObject /
 *       JsonObjectArray / JsonObjectMap tree nodes;</li>
 *   <li>{@code @id}/{@code @ref}/{@code @type}/{@code @items}/{@code @keys}
 *       metadata semantics (peek-through pre-allocation + post-allocation
 *       handling);</li>
 *   <li>{@code curParseDepth} / {@code maxParseDepth} / {@code maxIdValue}
 *       DOS guardrails at the tree level;</li>
 *   <li>resolving {@code @type} strings to {@link Class} instances via the
 *       configured {@link ClassLoader}.</li>
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
class JsonParser {
    private final JsonTokenizer tokenizer;
    private final FastReader input;          // retained for error-snippet rendering
    private final Resolver resolver;
    private final ReadOptions readOptions;
    private final ReferenceTracker references;
    private int curParseDepth = 0;
    private final int maxParseDepth;
    private final long maxIdValue;
    private final ClassLoader classLoader;
    private final Map<String, String> substitutes;

    private static final Map<String, String> SUBSTITUTES = new HashMap<>(16);

    static {
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
        this.substitutes = SUBSTITUTES;
        this.input = reader;
        this.resolver = resolver;
        this.readOptions = resolver.getReadOptions();
        this.references = resolver.getReferences();
        this.maxParseDepth = readOptions.getMaxDepth();
        this.maxIdValue = readOptions.getMaxIdValue();
        this.classLoader = readOptions.getClassLoader();

        this.tokenizer = new CharStreamTokenizer(
                reader,
                readOptions.isStrictJson(),
                readOptions.isAllowNanAndInfinity(),
                readOptions.isIntegerTypeBigInteger(),
                readOptions.isIntegerTypeBoth(),
                readOptions.isFloatingPointBigDecimal(),
                readOptions.isFloatingPointBoth(),
                readOptions.getStringBufferSize(),
                null);
    }

    /**
     * Read a JSON value (see json.org). A value can be a JSON object, array, string,
     * number, ("true", "false"), or "null". Top-level entry point — the caller
     * (today only {@link JsonIo}) constructs a parser, calls this once, and discards.
     *
     * @param suggestedType JsonValue Owning entity.
     */
    Object readValue(Type suggestedType) throws IOException {
        JsonToken first = tokenizer.nextToken();
        if (first == null) {
            error("EOF reached prematurely");
        }
        Object result = readValueOfCurrentToken(suggestedType);

        // Preserve today's quirk: a top-level string value rejects any trailing
        // non-whitespace content. Today this lived inside readString at depth 0;
        // now it lives here, since the tokenizer has no notion of "tree depth".
        // Use hasNonWhitespaceContent() instead of nextToken() so we don't try
        // to tokenize trailing content that may not be a valid token (e.g. a
        // stray ':') and end up reporting the wrong error.
        if (first == JsonToken.VALUE_STRING && tokenizer.hasNonWhitespaceContent()) {
            throw new JsonIoException("EOF expected, content found after string");
        }
        return result;
    }

    /**
     * Dispatch on {@link JsonTokenizer#currentToken()}. Assumes the cursor has
     * already been advanced to the value's start token. For container start
     * tokens, drives the recursive container reader.
     */
    private Object readValueOfCurrentToken(Type suggestedType) throws IOException {
        if (curParseDepth > maxParseDepth) {
            error("Maximum parsing depth exceeded");
        }
        JsonToken t = tokenizer.currentToken();
        if (t == null) {
            return error("Unknown JSON value type");
        }
        switch (t) {
            case START_OBJECT:
                return readJsonObject(suggestedType);
            case START_ARRAY: {
                Type elementType = TypeUtilities.extractArrayComponentType(suggestedType);
                return readArray(elementType);
            }
            case VALUE_STRING:
                return tokenizer.getText();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return materializeNumber();
            case VALUE_TRUE:
                return Boolean.TRUE;
            case VALUE_FALSE:
                return Boolean.FALSE;
            case VALUE_NULL:
                return null;
            default:
                return error("Unknown JSON value type");
        }
    }

    /**
     * Materialize the current numeric token into a {@link Number} matching
     * today's {@code readNumber} return-type contract: {@code Long} for integers
     * (or {@code BigInteger} when forced via integerTypeBigInteger / very-large-with-Both),
     * {@code Double} for decimals (or {@code BigDecimal} / {@code Float} when
     * floatingPoint policy bends the type).
     */
    private Number materializeNumber() throws IOException {
        NumberType nt = tokenizer.getNumberType();
        switch (nt) {
            case INT:
            case LONG:
                return tokenizer.getLongValue();
            case BIG_INTEGER:
                return tokenizer.getBigIntegerValue();
            case DOUBLE:
                return tokenizer.getDoubleValue();
            case FLOAT:
                return tokenizer.getFloatValue();
            case BIG_DECIMAL:
                return tokenizer.getDecimalValue();
            default:
                error("Unknown numeric type: " + nt);
                return null;
        }
    }

    /**
     * Read a JSON object {@code { ... }}. Cursor is positioned on
     * {@link JsonToken#START_OBJECT} (already emitted by caller).
     *
     * @return JsonObject representing the {@code { ... }}. If the JSON object
     * type can be inferred from a {@code @type} field, containing field type, or
     * containing array type, the javaType is set on the JsonObject.
     */
    private JsonObject readJsonObject(Type suggestedType) throws IOException {
        // Performance: Skip injector resolution when there's no meaningful type context
        Class<?> rawClass = TypeUtilities.getRawClass(suggestedType);
        ReadOptionsBuilder.InjectorPlan injectorPlan;
        if (suggestedType == null || rawClass == Object.class || rawClass == null) {
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
            JsonToken t = tokenizer.nextToken();
            if (t == JsonToken.END_OBJECT) {
                break;
            }
            if (t != JsonToken.FIELD_NAME) {
                error("Expected field name in JSON object");
            }

            String field = tokenizer.currentName();
            // Performance: Only check substitutes for fields starting with '@' or '$'.
            // Standard field names (letters, digits) never match any substitute key,
            // so the HashMap lookup is pure overhead for the 99% common case.
            if (field.length() > 0) {
                char firstCh = field.charAt(0);
                if (firstCh == '@' || firstCh == '$') {
                    field = substitutes.getOrDefault(field, field);
                }
            }

            JsonToken valueTok = tokenizer.nextToken();
            if (valueTok == null) {
                error("EOF reached prematurely");
            }
            Type fieldGenericType = null;
            if ((valueTok == JsonToken.START_OBJECT || valueTok == JsonToken.START_ARRAY)
                    && !injectorPlan.isEmpty()) {
                // Field type hints are only consumed by nested object/array parsing. Scalar conversion happens later.
                ReadOptionsBuilder.FieldAssignmentPlan assignmentPlan = injectorPlan.getAssignmentPlan(field);
                fieldGenericType = assignmentPlan == null ? null : assignmentPlan.fieldType;

                // If a field generic type is provided, resolve it using the parent's (i.e. jObj's) resolved type.
                if (fieldGenericType != null) {
                    fieldGenericType = TypeUtilities.resolveType(suggestedType, fieldGenericType);
                }
            }
            Object value = readValueOfCurrentToken(fieldGenericType);

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
                        loadItems((Object[]) value, jObj);
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
     * Read a JSON array. Cursor is positioned on {@link JsonToken#START_ARRAY}
     * (already emitted by caller).
     */
    private Object readArray(Type suggestedType) throws IOException {
        // Performance: Pre-size ArrayList to reduce resizing. Size of 64 eliminates
        // 1-2 resize operations for typical JSON arrays while adding only ~200 bytes overhead.
        final List<Object> list = new ArrayList<>(64);
        ++curParseDepth;

        while (true) {
            JsonToken t = tokenizer.nextToken();
            if (t == JsonToken.END_ARRAY) {
                break;
            }
            if (t == null) {
                error("EOF reached prematurely");
            }
            list.add(readValueOfCurrentToken(suggestedType));
        }

        --curParseDepth;
        return resolver.resolveArray(suggestedType, list);
    }

    /**
     * Load the @id field listed in the JSON
     *
     * @param value Object should be a Long, if not exception is thrown.  It is the value associated to the @id field.
     * @param jObj  JsonObject representing the current item in the JSON being loaded.
     */
    private void loadId(Object value, JsonObject jObj) {
        if (value == null) {
            error("Null value provided for " + ID + " field - expected a number");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + ID + ", instead got: " + value.getClass().getSimpleName());
        }

        long id = ((Number) value).longValue();
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
        if (value == null) {
            error("Null value provided for " + REF + " field - expected a number");
        }
        if (!(value instanceof Number)) {
            error("Expected a number for " + REF + ", instead got: " + value.getClass().getSimpleName());
        }

        long refId = ((Number) value).longValue();
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
        jObj.setKeys((Object[]) value);
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

    private String getMessage(String msg) {
        return msg + "\n" + input.getLastSnippet();
    }
}
