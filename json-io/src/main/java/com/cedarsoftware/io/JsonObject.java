package com.cedarsoftware.io;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.cedarsoftware.util.MathUtilities;
import com.cedarsoftware.util.convert.Converter;

/**
 * Intermediate-form representation of a JSON object as parsed from the wire and
 * before resolution into a Java object graph. Implements {@link Map}{@code <Object,Object>}
 * over parallel arrays for memory efficiency and insertion-order preservation.
 *
 * <p><strong>Hierarchy.</strong> The class is the lite, common case: a POJO or
 * String-keyed Map represented as parallel {@code keys[]} / {@code data[]} arrays.
 * Two package-private specializations cover the array-shaped and complex-keyed-map
 * cases:</p>
 * <ul>
 *   <li>{@code JsonObjectArray} — array / collection ({@code @items}) shape; stores
 *       its items in a separate {@code Object[]} and overrides {@link #isArray} /
 *       {@link #isCollection} / {@link #getItems} accordingly.</li>
 *   <li>{@code JsonObjectMap} — complex-keyed Map ({@code @keys}/{@code @items}) shape;
 *       stores keys and values in separate parallel arrays and overrides the
 *       {@link Map} surface ({@link #size}, {@link #get}, {@link #entrySet}, &hellip;)
 *       to read from those.</li>
 * </ul>
 *
 * <p>External callers only ever hold {@link JsonObject} references &mdash; subclass
 * identity is an internal dispatch and storage detail. The parser and TOON reader
 * peek through metadata fields ({@code @id}, {@code @type}, {@code @ref}, &hellip;)
 * and allocate the right specialization on first sight of a shape determiner; a lite
 * instance that has already committed to POJO shape is reshaped via
 * {@link #promoteToArray} / {@link #promoteToMap} when an {@code @items} or
 * {@code @keys} payload arrives later.</p>
 *
 * <p><strong>Lite storage design.</strong> POJO field entries and String-keyed Map
 * entries are appended in insertion order to parallel {@code keys[]} / {@code data[]}
 * arrays via {@link #put} or {@link #appendFieldForParser} (the parser-only fast path
 * that skips duplicate-key detection). Lookup is linear up to {@link #getLinearSearchThreshold},
 * after which a lazy hash index is built for O(1) lookup. A key a document repeats is kept as many
 * times as it appears, and every lookup ({@code get}, {@code containsKey}, {@code put}) finds its LAST value, at
 * every size -- as Maps mode, field injection and JavaScript's {@code JSON.parse} do. Calling
 * {@link #setItems} or {@link #setKeys} on a lite instance throws &mdash; those methods
 * are only valid on {@code JsonObjectArray} / {@code JsonObjectMap}.</p>
 *
 * <p>For the {@code @items}-shape and complex-keyed-map storage details, see the
 * package-private subclasses.</p>
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
public class JsonObject extends JsonValue implements Map<Object, Object>, Serializable {
    private static final int INITIAL_CAPACITY = 16;
    // Package-private so subclasses (JsonObjectMap.asTwoArrays) can return canonical empty arrays.
    static final Object[] EMPTY = new Object[0];
    // Threshold for switching from linear search to the hash index.
    // Lower values reduce O(n²) cost of building objects with many fields.
    private static volatile int INDEX_THRESHOLD = 4;
    // Up to this many fields the parsers look a key they appended up by a scan (indexOfAppended), building no index.
    static final int APPENDED_SCAN_LIMIT = 32;

    // Parallel arrays for POJO field entries and Map operations.
    // keys[] holds field names (POJOs, String-keyed maps) or complex keys (via setKeys).
    // data[] holds the corresponding values, grown in parallel with keys[] by put()/appendFieldForParser().
    private Object[] keys;
    private Object[] data;
    private int size;

    // Lazy index for O(1) lookup on objects past the linear-search threshold. Open addressing over positions: a slot
    // holds a position + 1 (0 = empty), found from the key's hash by linear probing, in a power-of-two table kept at
    // most half full. An int per slot, where the HashMap<Object, Integer> it replaces cost a node per field and often a
    // boxed Integer: past the threshold, an object that was looked up paid for its arrays AND a HashMap.
    private transient int[] index;

    private String typeString;
    // Cached value — package-private so subclasses can invalidate it after their own mutations.
    byte jsonTypeCache;

    // The Converter of the read that made this object, which get(key, type) and putAsJson use: its ReadOptions'
    // conversions, zone and locale. Null on one made by hand, which uses the default ReadOptions' Converter. A field
    // more would make a JsonObject 72 bytes, where it is 64 (JDK 17+).
    transient Converter converter;

    // Set on every object a Maps-mode read gives (MapResolver): such a tree, or a part of it, is read by
    // JsonIo.toJava(JsonObject, ..) as Java mode reads its text -- its objects carry that read's state (a type of Object,
    // a container's copy of its items), which Java mode would otherwise take for its own.
    boolean readAsMaps;

    // Set on every object of a Maps-mode read of JSON that names no @type: its BigInteger and BigDecimal values were
    // JSON numbers there, and are written as numbers (JsonWriter.bareBigNumbers).
    boolean plainJson;

    // Stored element type for collections/arrays (or value type for maps).
    // Used to preserve generic type information that would otherwise be lost
    // when createInstance changes the type to a concrete class.
    private transient java.lang.reflect.Type itemElementType;
    // Stored key type for maps. Used to preserve generic key information when type
    // is changed to a concrete class during instance creation.
    private transient java.lang.reflect.Type mapKeyType;

    /**
     * Type classification for optimized dispatch in Resolver.
     */
    public enum JsonType { ARRAY, COLLECTION, MAP, OBJECT }

    public JsonObject() {
        keys = EMPTY;
        data = EMPTY;
        size = 0;
    }

    public JsonObject(int initialCapacity) {
        if (initialCapacity < 1) {
            initialCapacity = 1;
        }
        keys = new Object[initialCapacity];
        data = new Object[initialCapacity];
        size = 0;
    }

    /**
     * Allocate a new JsonObject sized to hold {@code @items} (array/collection) shape data.
     * Returned instance is guaranteed to support {@link #setItems(Object[])} natively.
     * <p>
     * Use this from external factories (e.g., a {@link ClassFactory} that needs to feed an
     * {@code @items} payload to {@link Resolver#toJava(java.lang.reflect.Type, Object)} for
     * downstream array/collection resolution) instead of mutating a lite {@link JsonObject}.
     */
    public static JsonObject newArrayInstance() {
        return new JsonObjectArray();
    }

    /**
     * Lazy-promote a lite JsonObject to a JsonObjectArray when an {@code @items} payload
     * arrives after the object has already committed to lite shape (e.g., a non-metadata
     * field appeared first). Returns {@code lite} unchanged if it is already a JsonObjectArray
     * <em>or</em> a JsonObjectMap — both subclasses store @items natively (JsonObjectMap uses
     * @items as the values half of the @keys/@items pair).
     * <p>
     * Parent metadata (id, type, typeString, itemElementType, mapKeyType, refId) is copied to
     * the promoted instance. Any prior {@code keys[]}/{@code data[]} POJO fields on {@code lite}
     * are NOT carried over — by definition, the arriving {@code @items} payload reclassifies
     * the JSON object as array-shaped, so any preceding non-meta fields are discarded.
     * <p>
     * If {@code references} is non-null and {@code lite} has an id, the references map entry
     * for that id is updated to point at the promoted instance so any forward {@code @ref} to
     * the same id resolves to the correct (now array-shaped) object.
     */
    static JsonObject promoteToArray(JsonObject lite, ReferenceTracker references) {
        if (lite instanceof JsonObjectArray || lite instanceof JsonObjectMap) {
            // Both subclasses store @items natively; no promotion needed.
            return lite;
        }
        JsonObjectArray promoted = new JsonObjectArray();
        promoted.copyMetadataFrom(lite);
        if (references != null && lite.hasId()) {
            references.put(lite.getId(), promoted);
        }
        return promoted;
    }

    /**
     * Lazy-promote a lite JsonObject to a JsonObjectMap when an {@code @keys} payload arrives
     * after the object has already committed to lite shape. Returns {@code lite} unchanged if
     * it is already a JsonObjectMap. A JsonObjectArray with arriving {@code @keys} is a malformed
     * input — the parser flags this elsewhere.
     */
    static JsonObject promoteToMap(JsonObject lite, ReferenceTracker references) {
        if (lite instanceof JsonObjectMap) {
            return lite;
        }
        JsonObjectMap promoted = new JsonObjectMap();
        promoted.copyMetadataFrom(lite);
        if (references != null && lite.hasId()) {
            references.put(lite.getId(), promoted);
        }
        return promoted;
    }

    /**
     * Copy parent-level metadata from {@code src} into this JsonObject. Used by promote helpers
     * when reshaping a lite JsonObject into a JsonObjectArray/JsonObjectMap mid-parse.
     * Package-private so the static promote helpers can invoke this on subclass instances.
     */
    void copyMetadataFrom(JsonObject src) {
        if (src.getType() != null) {
            setType(src.getType());
        }
        if (src.getTypeString() != null) {
            setTypeString(src.getTypeString());
        }
        if (src.getItemElementType() != null) {
            setItemElementType(src.getItemElementType());
        }
        if (src.getMapKeyType() != null) {
            setMapKeyType(src.getMapKeyType());
        }
        if (src.hasId()) {
            setId(src.getId());
        }
        if (src.isReference()) {
            setReferenceId(src.getReferenceId());
        }
        converter = src.converter;
    }

    // ========== Type Classification ==========

    public JsonType getJsonType() {
        if (jsonTypeCache == 0) {
            if (isArray()) {
                jsonTypeCache = 1;
            } else if (isCollection()) {
                jsonTypeCache = 2;
            } else if (isMap()) {
                jsonTypeCache = 3;
            } else {
                jsonTypeCache = 4;
            }
        }
        switch (jsonTypeCache) {
            case 1: return JsonType.ARRAY;
            case 2: return JsonType.COLLECTION;
            case 3: return JsonType.MAP;
            default: return JsonType.OBJECT;
        }
    }

    public String toString() {
        String jType = typeString != null ? typeString : (type == null ? "not set" : type.getTypeName());
        String targetInfo = target == null ? "null" : jType;
        return "JsonObject(id:" + id + ", type:" + jType + ", target:" + targetInfo + ", size:" + size() + ")";
    }

    // ========== Type Checking ==========

    public boolean isMap() {
        return target instanceof Map || (type != null && Map.class.isAssignableFrom(getRawType()));
    }

    public boolean isCollection() {
        if (target instanceof Collection) {
            return true;
        }
        if (isMap()) {
            return false;
        }
        // Lite JsonObject has no @items signal — only target/type can mark it as a collection.
        // (JsonObjectArray overrides isCollection to handle the array/collection distinction.)
        return false;
    }

    public boolean isArray() {
        if (target != null) {
            return target.getClass().isArray();
        }
        if (type != null) {
            return getRawType().isArray();
        }
        return false;  // No @items signal on lite JsonObject — JsonObjectArray.isArray() handles that case.
    }

    // ========== Items/Keys Access ==========

    /**
     * Get items array — always {@code null} on lite {@link JsonObject}. Array/collection
     * shape data lives on {@link JsonObjectArray#getItems()}; complex-key map values live
     * on {@link JsonObjectMap#getItems()} (the values half of @keys/@items).
     */
    public Object[] getItems() {
        return null;
    }

    /**
     * Lite {@link JsonObject} cannot store {@code @items} natively. Allocate a
     * {@link JsonObjectArray} via {@link #newArrayInstance()} or use
     * {@link #promoteToArray(JsonObject, ReferenceTracker)} to reshape an existing lite
     * instance.
     */
    public void setItems(Object[] array) {
        throw new JsonIoException("setItems is not supported on lite JsonObject; "
                + "use JsonObject.newArrayInstance() or promoteToArray() to obtain a JsonObjectArray.");
    }

    /**
     * Get keys array for {@code @keys} format — always {@code null} on lite {@link JsonObject}.
     * Complex-key data lives on {@link JsonObjectMap#getKeys()}.
     */
    public Object[] getKeys() {
        return null;
    }

    /**
     * Lite {@link JsonObject} cannot store {@code @keys} natively (its {@code keys[]} array
     * holds POJO field names; reusing it for complex-key data would clobber those fields).
     * Use {@link #promoteToMap(JsonObject, ReferenceTracker)} to reshape into a
     * {@link JsonObjectMap}.
     */
    void setKeys(Object[] keyArray) {
        throw new JsonIoException("setKeys is not supported on lite JsonObject; "
                + "use JsonObject.promoteToMap() to obtain a JsonObjectMap.");
    }

    public String getTypeString() {
        return typeString;
    }

    void setTypeString(String typeString) {
        this.typeString = typeString;
    }

    /**
     * Get the stored element type for collections/arrays (or value type for maps).
     * This preserves generic type information that would otherwise be lost when
     * the type is changed to a concrete class during instance creation.
     * @return the element/value type, or null if not set
     */
    public java.lang.reflect.Type getItemElementType() {
        return itemElementType;
    }

    /**
     * Set the element type for collections/arrays (or value type for maps).
     * Should be called before createInstance to preserve generic type information.
     * @param elementType the element/value type from the ParameterizedType
     */
    public void setItemElementType(java.lang.reflect.Type elementType) {
        this.itemElementType = elementType;
    }

    /**
     * Get the stored key type for maps.
     * @return the map key type, or null if not set
     */
    public java.lang.reflect.Type getMapKeyType() {
        return mapKeyType;
    }

    /**
     * Set the key type for maps.
     * Should be called before createInstance to preserve generic key type information.
     * @param keyType the key type from the ParameterizedType
     */
    public void setMapKeyType(java.lang.reflect.Type keyType) {
        this.mapKeyType = keyType;
    }

    // ========== Map Interface ==========

    /**
     * Returns the effective size for Map operations.
     * For @keys/@items format (both set): number of key-value pairs
     * For @keys only (incomplete): 0
     * For POJOs: number of field entries
     */
    @Override
    public int size() {
        // Lite JsonObject only ever holds POJO field entries via keys[]/data[].
        return size;
    }

    @Deprecated
    public int getLength() {
        return size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Object get(Object key) {
        int idx = indexOf(key);
        if (idx < 0) return null;
        return data[idx];
    }

    /**
     * The value for the key as the given class, converted by the Converter of the read that gave this object -- the
     * conversions added to its ReadOptions, its zone and locale apply -- or, on one made by hand, by the default
     * ReadOptions' Converter: the same as {@code converter.convert(get(key), type)}, so it replaces a cast
     * ({@code (Integer) map.get("qty")}, which fails on the Long a Maps-mode read gives) or such a call. A value that
     * is already of the class is returned as it is (a nested JsonObject asked for as a JsonObject). An absent key or a
     * null value converts as null does: 0 for a primitive class, null for most others.
     * <pre>{@code
     * JsonObject order = JsonIo.toMaps(json, null).asClass(JsonObject.class);
     * int qty = order.get("qty", int.class);                          // a Long 3 in the tree
     * LocalDate shipped = order.get("shipped", LocalDate.class);      // "2026-01-15"
     * }</pre>
     *
     * @param key  the key
     * @param type the class to return the value as
     * @param <T>  the class's type
     * @return the value as {@code type}
     * @throws IllegalArgumentException if the value does not convert to {@code type}, or {@code type} is null
     * @see #get(Object, Class, Object)
     * @see #get(Object, TypeHolder)
     * @see #putAsJson(Object, Object)
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        Object value = get(key);
        return type != null && type.isInstance(value) ? (T) value : converter().convert(value, type);
    }

    /**
     * The value for the key as the given class, as {@link #get(Object, Class)} gives it, or the default when the key
     * is absent or its value is null.
     * <pre>{@code
     * int batchSize = options.get("batchSize", int.class, 100);
     * }</pre>
     *
     * @param key          the key
     * @param type         the class to return the value as
     * @param defaultValue what to return for an absent key or a null value
     * @param <T>          the class's type
     * @return the value as {@code type}, or {@code defaultValue}
     * @throws IllegalArgumentException if the value does not convert to {@code type}, or {@code type} is null
     */
    public <T> T get(Object key, Class<T> type, T defaultValue) {
        return get(key) == null ? defaultValue : get(key, type);
    }

    /**
     * The value for the key as the given type, which may be generic, converted by the Converter
     * {@link #get(Object, Class)} uses: a JSON array of text as a {@code List<LocalDate>}, a nested object as a
     * {@code Map<String, Integer>}, each element, key and value converted. The same as
     * {@code converter.convert(get(key), type.getType())}; an absent key or a null value converts as null does.
     * <pre>{@code
     * List<LocalDate> due = invoice.get("due", new TypeHolder<List<LocalDate>>() {});
     * }</pre>
     *
     * @param key  the key
     * @param type the type to return the value as
     * @param <T>  the type
     * @return the value as {@code type}
     * @throws IllegalArgumentException if the value (or one of its elements) does not convert, or {@code type} is null
     * @see #get(Object, TypeHolder, Object)
     */
    public <T> T get(Object key, TypeHolder<T> type) {
        return converter().convert(get(key), type == null ? null : type.getType());
    }

    /**
     * The value for the key as the given type, as {@link #get(Object, TypeHolder)} gives it, or the default when the
     * key is absent or its value is null.
     *
     * @param key          the key
     * @param type         the type to return the value as
     * @param defaultValue what to return for an absent key or a null value
     * @param <T>          the type
     * @return the value as {@code type}, or {@code defaultValue}
     * @throws IllegalArgumentException if the value (or one of its elements) does not convert, or {@code type} is null
     */
    public <T> T get(Object key, TypeHolder<T> type, T defaultValue) {
        return get(key) == null ? defaultValue : get(key, type);
    }

    /**
     * Puts the value as plain JSON holds it -- what a Maps-mode read of plain JSON would have put there -- so the
     * tree writes back as plain JSON, its shape unchanged for whoever reads it next. {@link #put(Object, Object)}
     * keeps the Java value, which json-io writes with its type ({@code "when":{"@type":"ZonedDateTime",..}}), for a
     * tree that goes back to json-io; this is the mirror of {@link #get(Object, Class)}, which turns the JSON value
     * back into the Java one.
     * <ul>
     *     <li><b>A number</b> is a Long or a Double when one holds it exactly ({@code 5} for an Integer, {@code 1.1}
     *     for a Float 1.1f or a BigDecimal 1.10), else a BigInteger or a BigDecimal.</li>
     *     <li><b>A value the Converter writes as text</b> is that text: a date or time ({@code "2026-01-15"}), a UUID,
     *     an enum's name, a Character, a Locale, a URI, a Class name ... The Converter is the one
     *     {@link #get(Object, Class)} uses.</li>
     *     <li><b>A Map</b> is a JsonObject, its keys text; <b>a Collection or an array</b> an Object[] (a char[] is its
     *     text); <b>an Optional</b> its content or null. What they hold is put the same way.</li>
     *     <li><b>Any other object</b> is a JsonObject of its fields, as the Converter turns an object into a Map.</li>
     *     <li>null, a String, a Boolean, a Long, a Double and a JsonObject are put as they are.</li>
     * </ul>
     * <pre>{@code
     * order.putAsJson("shipped", LocalDate.of(2026, 1, 15));     // "shipped":"2026-01-15"
     * order.putAsJson("qty", 3);                                  // "qty":3
     * }</pre>
     *
     * @param key   the key
     * @param value the value to put, in its JSON form
     * @return the previous value for the key, or null
     * @throws JsonIoException if the value holds itself (plain JSON has no references)
     */
    public Object putAsJson(Object key, Object value) {
        return put(key, jsonForm(value, null));
    }

    /** The Converter of the read that made this object, else the one json-io's default ReadOptions use. */
    private Converter converter() {
        Converter c = converter;
        return c != null ? c : ReadOptionsBuilder.converterOf(ReadOptionsBuilder.getDefaultReadOptions());
    }

    /** A value that holds itself, which plain JSON, having no references, cannot. */
    private static final class HoldsItself extends JsonIoException {
        private static final long serialVersionUID = 1L;

        HoldsItself(Object value) {
            super("Cannot put a " + value.getClass().getName()
                    + " that holds itself as JSON: plain JSON has no references");
        }
    }

    /**
     * The value as plain JSON holds it (see {@link #putAsJson}). {@code holding} is the containers being converted,
     * made on the first: one met again holds itself.
     */
    private Object jsonForm(Object value, IdentityHashMap<Object, Object> holding) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Long
                || value instanceof Double || value instanceof JsonObject) {
            return value;
        }
        if (value instanceof Float) {
            return Double.parseDouble(value.toString());        // as it is written: 1.1f is 1.1
        }
        if (value instanceof Number) {
            try {
                return MathUtilities.parseToMinimalNumericType(value.toString());
            } catch (NumberFormatException notANumeral) {
                // a Number of a class of its own: its text, or its fields, below
            }
        }
        if (value instanceof AtomicBoolean) {
            return ((AtomicBoolean) value).get();
        }
        if (value instanceof Optional) {
            return jsonForm(((Optional<?>) value).orElse(null), holding);
        }
        if (value instanceof char[]) {
            return new String((char[]) value);
        }
        boolean map = value instanceof Map;
        if (!map && !(value instanceof Collection) && !value.getClass().isArray()) {
            Converter converter = converter();
            // (not isSimpleTypeConversionSupported: that is false where the read's options override the conversion)
            if (converter.isConversionSupportedFor(value.getClass(), String.class)) {
                return converter.convert(value, String.class);
            }
            try {
                return jsonForm(converter.convert(value, Map.class), holding);     // its fields
            } catch (HoldsItself loop) {
                throw new HoldsItself(value);   // named by its class, not by the Map of its fields
            }
        }
        if (holding == null) {
            holding = new IdentityHashMap<>();
        }
        if (holding.put(value, value) != null) {
            throw new HoldsItself(value);
        }
        Object json;
        if (map) {
            JsonObject object = new JsonObject();
            object.converter = converter;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Object name = entry.getKey() instanceof String ? entry.getKey() : jsonForm(entry.getKey(), holding);
                if (name instanceof JsonObject || name instanceof Object[]) {
                    throw new JsonIoException("Cannot put a map whose key is a " + entry.getKey().getClass().getName()
                            + " as JSON: a JSON object's keys are text");
                }
                object.put(String.valueOf(name), jsonForm(entry.getValue(), holding));
            }
            json = object;
        } else {
            Object[] items = value instanceof Collection ? ((Collection<?>) value).toArray() : null;
            int length = items != null ? items.length : Array.getLength(value);
            Object[] array = new Object[length];
            for (int i = 0; i < length; i++) {
                array[i] = jsonForm(items != null ? items[i] : Array.get(value, i), holding);
            }
            json = array;
        }
        holding.remove(value);
        return json;
    }

    @Override
    public Object put(Object key, Object value) {
        int idx = indexOf(key);
        if (idx >= 0) {
            Object old = data[idx];
            data[idx] = value;
            return old;
        }

        ensureCapacity(size + 1);
        keys[size] = key;
        data[size] = value;

        if (index != null) {
            // Index exists, add new key to it
            indexKey(key, size);
        } else if (size == INDEX_THRESHOLD) {
            // Just crossed threshold - build index proactively so next put() uses it
            // This includes the key we just added (at position 'size')
            buildIndex(size + 1);
        }

        size++;
        return null;
    }

    /**
     * Parser-only fast append path.
     * <p>
     * JSON object keys are overwhelmingly unique in practice, so this method skips duplicate-key
     * search and appends directly to avoid per-field {@code indexOf()} churn during parse. The parsers look a field
     * up first only where its name's hash bit is already set, and replace a repeated key's value; given one here, a
     * repeated key is appended again, and lookups find its last value (see {@link #indexOf}).
     * The hash index is built lazily on first lookup via {@code indexOf()} -- but once a lookup has built it
     * (strict TOON checks each key for a duplicate before appending it), every appended key must go into it,
     * or get() and containsKey() miss the fields that follow while keySet() still lists them.
     */
    public void appendFieldForParser(Object key, Object value) {
        ensureCapacity(size + 1);
        keys[size] = key;
        data[size] = value;
        if (index != null) {
            indexKey(key, size);
        }
        size++;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        for (int i = 0; i < size; i++) {
            if (Objects.equals(value, data[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object remove(Object key) {
        int idx = indexOf(key);
        if (idx < 0) return null;

        Object old = data[idx];     // the value get() returned: the last one
        do {
            // Every copy of a key a document repeated: removing one must not bring back the one before it
            int numMoved = size - idx - 1;
            if (numMoved > 0) {
                System.arraycopy(keys, idx + 1, keys, idx, numMoved);
                System.arraycopy(data, idx + 1, data, idx, numMoved);
            }

            size--;
            keys[size] = null;
            data[size] = null;
            index = null;
            idx = indexOf(key);
        } while (idx >= 0);

        return old;
    }

    @Override
    public void putAll(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return;
        ensureCapacity(size + map.size());
        for (Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        super.clear();
        Arrays.fill(keys, 0, size, null);
        Arrays.fill(data, 0, size, null);
        size = 0;
        itemElementType = null;
        mapKeyType = null;
        index = null;
        jsonTypeCache = 0;
    }

    // ========== Value Methods ==========

    public void setValue(Object o) {
        put(VALUE, o);
    }

    public Object getValue() {
        return get(VALUE);
    }

    public boolean hasValue() {
        return size == 1 && containsKey(VALUE);
    }

    // ========== Index Management ==========

    /**
     * Where a parser appended {@code key} already, or -1: a scan from the last key while the object is small and has no
     * index, else {@link #indexOf}. The parsers ask only when the key's hash bit is set already, which two different
     * keys often share; an index built for each object of about twenty fields made a string-heavy read 6% slower.
     */
    int indexOfAppended(Object key) {
        if (size > APPENDED_SCAN_LIMIT || index != null) {
            return indexOf(key);
        }
        for (int i = size - 1; i >= 0; i--) {
            Object k = keys[i];
            if (k == key || key.equals(k)) {
                return i;
            }
        }
        return -1;
    }

    int indexOf(Object key) {
        // Lite JsonObject only ever holds POJO field entries via keys[]/data[].
        if (size == 0) return -1;

        if (size <= INDEX_THRESHOLD) {
            // Linear search for small objects -- from the end, so a key a document repeated resolves to its last
            // value, as the index below does (a later position replaces an earlier one)
            for (int i = size - 1; i >= 0; i--) {
                if (Objects.equals(key, keys[i])) {
                    return i;
                }
            }
            return -1;
        }

        // Use index for large POJO objects
        if (index == null) {
            buildIndex(size);
        }
        int[] table = index;
        int mask = table.length - 1;
        int i = slotOf(key, table.length);
        for (;;) {
            int slot = table[i];
            if (slot == 0) {
                return -1;
            }
            if (Objects.equals(key, keys[slot - 1])) {
                return slot - 1;
            }
            i = (i + 1) & mask;
        }
    }

    /** Indexes positions 0 to count - 1. A key a document repeated resolves to its last position. */
    private void buildIndex(int count) {
        int capacity = 16;
        while (capacity < count * 2) {
            capacity <<= 1;
        }
        int[] table = new int[capacity];
        for (int i = 0; i < count; i++) {
            indexInsert(table, keys[i], i);
        }
        index = table;
    }

    /** Adds the key just stored at {@code pos} to the index, rebuilding it larger rather than pass half full. */
    private void indexKey(Object key, int pos) {
        if ((pos + 1) * 2 > index.length) {
            buildIndex(pos + 1);
        } else {
            indexInsert(index, key, pos);
        }
    }

    private void indexInsert(int[] table, Object key, int pos) {
        int mask = table.length - 1;
        int i = slotOf(key, table.length);
        for (;;) {
            int slot = table[i];
            if (slot == 0 || Objects.equals(key, keys[slot - 1])) {
                table[i] = pos + 1;     // an empty slot, or the key again: its later position wins
                return;
            }
            i = (i + 1) & mask;
        }
    }

    /**
     * The key's home slot in a table of {@code length} (a power of two): Fibonacci hashing, the top bits of the hash
     * times 2^32 / phi. Linear probing needs keys spread evenly; field names like field0..field999 have hash codes
     * in overlapping runs, which the low bits alone pile into long clusters.
     */
    private static int slotOf(Object key, int length) {
        return (Objects.hashCode(key) * 0x9E3779B9) >>> (Integer.numberOfLeadingZeros(length) + 1);
    }

    private void ensureCapacity(int minCapacity) {
        if (keys.length >= minCapacity) return;

        int newCapacity = Math.max(Math.max(keys.length * 2, minCapacity), INITIAL_CAPACITY);
        keys = Arrays.copyOf(keys, newCapacity);
        data = Arrays.copyOf(data, newCapacity);
    }

    // ========== View Classes ==========

    @Override
    public Set<Object> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<Object> values() {
        return new ValuesCollection();
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        return new EntrySet();
    }

    // Package-private fast path for hot loops that need stable insertion-order entry traversal
    // without allocating iterator/entry wrapper objects.
    int fastEntryCount() {
        return size();
    }

    Object fastKeyAt(int index) {
        return keys[index];
    }

    Object fastValueAt(int index) {
        return data[index];
    }

    /** Replaces the value at {@code index}, as {@code setValue()} on that field's entry would, allocating nothing. */
    void fastSetValueAt(int index, Object value) {
        data[index] = value;
    }

    private class KeySet extends AbstractSet<Object> {
        @Override
        public int size() {
            return JsonObject.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObject.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final int len = JsonObject.this.size();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return keys[idx++];
                }
            };
        }
    }

    // A Collection, not a Set: values repeat (and, like HashMap.values(), it is not equal to a Set of the same values)
    private class ValuesCollection extends AbstractCollection<Object> {
        @Override
        public int size() {
            return JsonObject.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObject.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return containsValue(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final Object[] vals = data;
                private final int len = JsonObject.this.size();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return vals[idx++];
                }
            };
        }
    }

    private class EntrySet extends AbstractSet<Entry<Object, Object>> {
        @Override
        public int size() {
            return JsonObject.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObject.this.isEmpty();
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new EntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            int idx = indexOf(entry.getKey());
            if (idx < 0) return false;
            return Objects.equals(data[idx], entry.getValue());
        }
    }

    private class EntryIterator implements Iterator<Entry<Object, Object>> {
        private final int len = JsonObject.this.size();
        private int idx = 0;

        @Override
        public boolean hasNext() {
            return idx < len;
        }

        @Override
        public Entry<Object, Object> next() {
            if (!hasNext()) throw new NoSuchElementException();
            return new FieldEntry(idx++);
        }
    }

    /**
     * One field as an entry of {@link #entrySet()} -- or, of a complex-keyed map, one key/value pair -- reading and
     * writing through to this JsonObject, by way of the fast accessors each shape overrides. A new one per
     * {@code next()}: a single entry re-pointed at each field in turn -- as this used to be -- left anything that
     * keeps entries (a List of them, {@code toArray()}, a sorted stream) holding the last field N times, and made
     * {@code setValue()} on a kept entry write whichever field the iterator had reached. (The JDK fixed the same
     * pattern in EnumMap and IdentityHashMap as a bug, JDK-6312706.) json-io's own hot loops use
     * {@link #fastKeyAt}/{@link #fastValueAt} and allocate nothing.
     */
    final class FieldEntry implements Entry<Object, Object> {
        private final int index;

        FieldEntry(int index) {
            this.index = index;
        }

        @Override
        public Object getKey() {
            return fastKeyAt(index);
        }

        @Override
        public Object getValue() {
            return fastValueAt(index);
        }

        @Override
        public Object setValue(Object value) {
            Object old = fastValueAt(index);
            fastSetValueAt(index, value);       // clears the cached hash
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(getKey(), e.getKey()) && Objects.equals(getValue(), e.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }
    }

    // ========== Hash and Equals ==========
    // A JsonObject is a Map, and as a map -- a plain object's fields, a map's entries, the @keys/@items of one with
    // complex keys -- it follows the Map contract: it equals any Map with the same entries, in whatever order, and its
    // hash is AbstractMap's (the sum of key-hash ^ value-hash), so a HashSet or HashMap finds it by an equal
    // LinkedHashMap and the other way round. A value is compared by its equals(): a JSON array, an Object[], by
    // identity, as in any Map (DeepEquals compares trees by content). An object holding @items (JsonObjectArray) is
    // no map: it equals another of its class, its items compared by content, as before.
    //
    // A JsonObject graph read with @ref is routinely CYCLIC -- a child's "parent" is its parent, a ring's last "next"
    // is its first -- so neither method may walk the graph unguarded.
    //
    // hashCode() is deep where no cycle is reachable -- so it is any equal map's -- and where one is, the sum of this
    // object's own entries hashed shallowly (a nested map, collection or array by its kind). Two equal graphs are both
    // cyclic or both not, so equal maps still hash alike. It is not cached: a nested value changing changes it.
    //
    // equals() is deep: every nested value is compared. A pair this comparison has already taken as equal -- one
    // still being compared further up, or one already found equal -- is taken as equal again. That is how a loop
    // closes, so two reads of one cyclic document are equal while a difference anywhere on the loop is still found;
    // and it compares each pair of objects once, so a densely cross-linked graph costs time linear in its size
    // rather than one visit per path through it. A pair found UNEQUAL is forgotten together with every pair decided
    // after it, since those may have been judged equal only by assuming it was -- a set lookup that tries one
    // candidate, fails, and moves on must not leave that failed assumption behind.

    // Hash of a nested value that is not hashed by content
    private static final int NESTED_MAP = 0x4D41;
    private static final int NESTED_COLLECTION = 0x434F;
    private static final int NESTED_ARRAY = 0x4152;

    // The pairs the comparison running on this thread has taken as equal. Present only during a comparison.
    private static final ThreadLocal<Comparison> COMPARISON = new ThreadLocal<>();

    /** True for a map: false for an object holding @items (an array or a collection). */
    boolean isMapShaped() {
        return true;
    }

    @Override
    public int hashCode() {
        try {
            return quickHash(this, new int[] {QUICK_HASH_BUDGET});
        } catch (NeedsGuard e) {
            Integer deep = deepHash(this, new IdentityHashMap<>());
            return deep != null ? deep : shallowMapHash();
        }
    }

    /** The sum of this object's own entries, each key and value hashed shallowly: it never descends. */
    int shallowMapHash() {
        int len = fastEntryCount();
        int h = 0;
        for (int i = 0; i < len; i++) {
            h += shallowHash(fastKeyAt(i)) ^ shallowHash(fastValueAt(i));
        }
        return h;
    }

    // The containers a hash walks unguarded -- allocating nothing -- before it walks again, guarded: a loop, a large
    // graph, or one whose shared objects an unguarded walk would visit once per path to them.
    private static final int QUICK_HASH_BUDGET = 256;

    private static final class NeedsGuard extends RuntimeException {
        NeedsGuard() {
            super(null, null, false, false);
        }
    }

    private static final NeedsGuard NEEDS_GUARD = new NeedsGuard();

    // A container the guarded walk has entered and not yet left
    private static final Object IN_PROGRESS = new Object();

    /** {@link #deepHash}'s value, unguarded: it throws {@link #NEEDS_GUARD} once it has walked its budget of containers. */
    private static int quickHash(Object value, int[] budget) {
        if (value == null) {
            return 0;
        }
        Class<?> c = value.getClass();
        if (c == String.class || c == Long.class || c == Double.class || c == Boolean.class) {
            return value.hashCode();        // JSON's own values, most of what a tree holds: no interface test
        }
        if (value instanceof JsonObject && !((JsonObject) value).isMapShaped()) {
            return value.hashCode();
        }
        boolean map = value instanceof Map;
        boolean list = value instanceof List;
        if (!map && !list && !(value instanceof Set)) {
            return Objects.hashCode(value);
        }
        if (--budget[0] < 0) {
            throw NEEDS_GUARD;
        }
        int h;
        if (value instanceof JsonObject) {
            JsonObject jObj = (JsonObject) value;
            h = 0;
            for (int i = 0, len = jObj.fastEntryCount(); i < len; i++) {
                h += quickHash(jObj.fastKeyAt(i), budget) ^ quickHash(jObj.fastValueAt(i), budget);
            }
        } else if (map) {
            h = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                h += quickHash(entry.getKey(), budget) ^ quickHash(entry.getValue(), budget);
            }
        } else if (list) {
            h = 1;
            for (Object element : (List<?>) value) {
                h = 31 * h + quickHash(element, budget);
            }
        } else {
            h = 0;
            for (Object element : (Set<?>) value) {
                h += quickHash(element, budget);
            }
        }
        return h;
    }

    /**
     * The hash {@code value.hashCode()} gives -- AbstractMap's for a map, AbstractList's for a list, AbstractSet's for a
     * set, each nested one by its own -- or null when a cycle is reachable from it, where those formulas never end.
     * Each container once: a shared one's hash is remembered.
     */
    private static Integer deepHash(Object value, IdentityHashMap<Object, Object> seen) {
        if (value instanceof JsonObject && !((JsonObject) value).isMapShaped()) {
            return value.hashCode();        // it holds @items: its own hash, which does not descend
        }
        boolean map = value instanceof Map;
        boolean list = value instanceof List;
        if (!map && !list && !(value instanceof Set)) {
            return Objects.hashCode(value);     // a leaf, an array (by identity, as any Map hashes it), another collection
        }
        Object prior = seen.get(value);
        if (prior == IN_PROGRESS) {
            return null;
        }
        if (prior != null) {
            return (Integer) prior;
        }
        seen.put(value, IN_PROGRESS);
        int h;
        if (value instanceof JsonObject) {
            JsonObject jObj = (JsonObject) value;
            h = 0;
            for (int i = 0, len = jObj.fastEntryCount(); i < len; i++) {
                Integer kh = deepHash(jObj.fastKeyAt(i), seen);
                Integer vh = kh == null ? null : deepHash(jObj.fastValueAt(i), seen);
                if (vh == null) {
                    return null;
                }
                h += kh ^ vh;
            }
        } else if (map) {
            h = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                Integer kh = deepHash(entry.getKey(), seen);
                Integer vh = kh == null ? null : deepHash(entry.getValue(), seen);
                if (vh == null) {
                    return null;
                }
                h += kh ^ vh;
            }
        } else if (list) {
            h = 1;
            for (Object element : (List<?>) value) {
                Integer eh = deepHash(element, seen);
                if (eh == null) {
                    return null;
                }
                h = 31 * h + eh;
            }
        } else {
            h = 0;
            for (Object element : (Set<?>) value) {
                Integer eh = deepHash(element, seen);
                if (eh == null) {
                    return null;
                }
                h += eh;
            }
        }
        seen.put(value, h);
        return h;
    }

    // Package-private so subclasses (JsonObjectArray, JsonObjectMap) hash their own storage the same way.
    static int shallowHash(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Map) {     // a JsonObject too: equal maps must hash alike, whatever their class
            return NESTED_MAP;
        }
        if (value instanceof Collection) {
            return NESTED_COLLECTION;
        }
        if (value.getClass().isArray()) {
            return 31 * NESTED_ARRAY + Array.getLength(value);
        }
        return value.hashCode();
    }

    // Hash of an @items payload: the items themselves, each hashed shallowly.
    static int itemsHash(Object[] items) {
        if (items == null) {
            return 0;
        }
        int result = 1;
        for (Object item : items) {
            result = 31 * result + shallowHash(item);
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!isMapShaped()) {
            return itemsHolderEquals(obj);
        }
        if (!(obj instanceof Map) || obj instanceof JsonObject && !((JsonObject) obj).isMapShaped()) {
            return false;
        }
        Map<?, ?> other = (Map<?, ?>) obj;
        int len = fastEntryCount();
        if (len != other.size()) return false;
        int mark = enterComparison(this, other);
        if (mark < 0) {
            return true;    // already taken as equal: the loop has closed, or this pair was compared before
        }
        boolean equal = false;
        try {
            // Objects.equals() written out, in this frame: one stack frame per level keeps the deepest comparable graph
            // at least as deep as it was before the cycle guard. Keys in the same order -- two reads of one text --
            // need no lookup.
            JsonObject same = other instanceof JsonObject ? (JsonObject) other : null;
            for (int i = 0; i < len; i++) {
                Object key = fastKeyAt(i);
                Object value = fastValueAt(i);
                Object otherValue;
                Object otherKey = same == null ? null : same.fastKeyAt(i);
                if (same != null && (key == otherKey || key != null && key.equals(otherKey))) {
                    otherValue = same.fastValueAt(i);
                } else {
                    otherValue = other.get(key);
                    if (otherValue == null && (value != null || !other.containsKey(key))) {
                        return false;
                    }
                }
                if (value != otherValue && (value == null || !value.equals(otherValue))) {
                    return false;
                }
            }
            equal = true;
            return true;
        } finally {
            exitComparison(mark, equal);
        }
    }

    /** An object holding @items equals another of its class: its own entries and its items, by position. */
    private boolean itemsHolderEquals(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) return false;
        JsonObject other = (JsonObject) obj;
        if (size() != other.size()) return false;
        int mark = enterComparison(this, other);
        if (mark < 0) {
            return true;
        }
        boolean equal = false;
        try {
            int len = size;
            Object[] otherKeys = other.keys;
            Object[] otherData = other.data;
            for (int i = 0; i < len; i++) {
                Object a = keys[i];
                Object b = otherKeys[i];
                if (a != b && (a == null || !a.equals(b))) {
                    return false;
                }
                a = data[i];
                b = otherData[i];
                if (a != b && (a == null || !a.equals(b))) {
                    return false;
                }
            }
            equal = storageEquals(other);
            return equal;
        } finally {
            exitComparison(mark, equal);
        }
    }

    /**
     * Compares the storage an object holding {@code @items} adds to the lite keys/data. {@code other} is the same
     * class and size. Called only from {@link #equals(Object)}, while the pair is on this thread's comparison path.
     */
    boolean storageEquals(JsonObject other) {
        return true;
    }

    /**
     * Compares two @items payloads as {@link Arrays#deepEquals} does -- nested arrays by content -- with nested
     * arrays guarded like JsonObjects, since an array can hold itself.
     */
    static boolean itemsEqual(Object[] a, Object[] b) {
        if (a == b) return true;
        if (a == null || b == null || a.length != b.length) return false;
        int mark = enterComparison(a, b);
        if (mark < 0) {
            return true;
        }
        boolean equal = false;
        try {
            for (int i = 0; i < a.length; i++) {
                Object e1 = a[i];
                Object e2 = b[i];
                if (e1 == e2) {
                    continue;
                }
                if (e1 == null || e2 == null) {
                    return false;
                }
                boolean same;
                if (e1 instanceof Object[] && e2 instanceof Object[]) {
                    same = itemsEqual((Object[]) e1, (Object[]) e2);
                } else if (e1.getClass().isArray() || e2.getClass().isArray()) {
                    same = Objects.deepEquals(e1, e2);      // a primitive array: it holds no references
                } else {
                    same = e1.equals(e2);
                }
                if (!same) {
                    return false;
                }
            }
            equal = true;
            return true;
        } finally {
            exitComparison(mark, equal);
        }
    }

    /**
     * Records the pair as taken as equal by the comparison running on this thread, starting one if none is. Returns
     * the mark to hand back to {@link #exitComparison} -- or -1 when the pair (either way round) is already taken as
     * equal, and so need not be compared again.
     */
    private static int enterComparison(Object a, Object b) {
        Comparison comparison = COMPARISON.get();
        if (comparison == null) {
            comparison = new Comparison();
            COMPARISON.set(comparison);
        }
        Pair pair = new Pair(a, b);
        if (!comparison.taken.add(pair)) {
            return -1;
        }
        comparison.order.add(pair);
        return comparison.order.size() - 1;
    }

    /**
     * Ends the comparison of the pair entered at {@code mark}. An unequal pair is forgotten with everything entered
     * after it. The outermost comparison (mark 0) removes the record itself, so nothing is left on the thread -- not
     * even when a nested equals() throws part-way through.
     */
    private static void exitComparison(int mark, boolean equal) {
        if (mark == 0) {
            COMPARISON.remove();
            return;
        }
        if (!equal) {
            Comparison comparison = COMPARISON.get();
            ArrayList<Pair> order = comparison.order;
            for (int i = order.size() - 1; i >= mark; i--) {
                comparison.taken.remove(order.remove(i));
            }
        }
    }

    private static final class Comparison {
        final HashSet<Pair> taken = new HashSet<>();
        final ArrayList<Pair> order = new ArrayList<>();
    }

    // Two objects compared, by identity and either way round: hashCode() cannot be used on a cyclic graph.
    private static final class Pair {
        private final Object a;
        private final Object b;

        Pair(Object a, Object b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(a) + System.identityHashCode(b);
        }

        @Override
        public boolean equals(Object o) {
            Pair p = (Pair) o;
            return (a == p.a && b == p.b) || (a == p.b && b == p.a);
        }
    }

    // ========== Resolution Support ==========

    Map.Entry<Object[], Object[]> asTwoArrays() {
        // Lite JsonObject only ever holds POJO/String-keyed-map data via parallel keys[]/data[].
        // Complex-key (@keys/@items) maps are JsonObjectMap and override this.
        // IMPORTANT: Do NOT copy arrays here! Reference patching during traversal modifies the
        // arrays in place, and rehashMaps() needs to see those patches.
        return new AbstractMap.SimpleImmutableEntry<>(keys, data);
    }

    @SuppressWarnings("unchecked")
    void rehashMaps() {
        if (!(target instanceof Map)) return;

        // Lite JsonObject only ever uses parallel keys[]/data[] for POJO/String-keyed-map data;
        // length is bounded by `size`, not the array's allocated capacity.
        Object[] k = keys;
        Object[] v = data;
        int len = size;

        if (len == 0) return;

        Map<Object, Object> targetMap = (Map<Object, Object>) target;

        for (int i = 0; i < len; i++) {
            Object key = k[i];
            Object value = v[i];

            // Extract targets or values from nested JsonObjects
            if (key instanceof JsonObject) {
                JsonObject jObj = (JsonObject) key;
                if (jObj.target != null) {
                    key = jObj.target;
                } else if (jObj.hasValue()) {
                    key = jObj.getValue();
                }
            }
            if (value instanceof JsonObject) {
                JsonObject jObj = (JsonObject) value;
                if (jObj.target != null) {
                    value = jObj.target;
                } else if (jObj.hasValue()) {
                    value = jObj.getValue();
                }
            }

            targetMap.put(key, value);
        }
    }

    // ========== Static Configuration ==========

    public static void setLinearSearchThreshold(int threshold) {
        if (threshold < 1) {
            throw new JsonIoException("Threshold must be >= 1, was: " + threshold);
        }
        INDEX_THRESHOLD = threshold;
    }

    public static int getLinearSearchThreshold() {
        return INDEX_THRESHOLD;
    }
}
