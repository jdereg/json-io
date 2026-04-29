package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * This class holds a JSON object using parallel arrays for memory efficiency
 * and insertion-order preservation. Instances of this class hold a Map-of-Map
 * representation of a Java object, read from the JSON input stream.
 *
 * <p>Storage design:</p>
 * <ul>
 *   <li>Parallel arrays ({@code keys[]} + {@code data[]}) for all modes.</li>
 *   <li>For POJOs / String-keyed maps: entries are appended to {@code keys[]}
 *       and {@code data[]} via {@link #put} / {@link #appendFieldForParser}.</li>
 *   <li>For arrays / collections ({@code @items}): {@code data[]} is replaced
 *       wholesale by {@link #setItems}.</li>
 *   <li>For complex-keyed maps ({@code @keys/@items}): both {@code keys[]} and
 *       {@code data[]} are replaced wholesale by {@link #setKeys} and
 *       {@link #setItems}.</li>
 *   <li>Lazy HashMap index built only when needed for O(1) lookup on large objects.</li>
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
public class JsonObject extends JsonValue implements Map<Object, Object>, Serializable {
    private static final int INITIAL_CAPACITY = 16;
    // Package-private so subclasses (JsonObjectMap.asTwoArrays) can return canonical empty arrays.
    static final Object[] EMPTY = new Object[0];
    // Threshold for switching from linear search to HashMap index.
    // Lower values reduce O(n²) cost of building objects with many fields.
    private static volatile int INDEX_THRESHOLD = 4;

    // Parallel arrays for POJO field entries and Map operations.
    // keys[] holds field names (POJOs, String-keyed maps) or complex keys (via setKeys).
    // data[] holds the corresponding values, grown in parallel with keys[] by put()/appendFieldForParser().
    private Object[] keys;
    private Object[] data;
    private int size;

    // Lazy index for O(1) lookup on large objects
    private transient Map<Object, Integer> index;

    // Storage mode: bit 0 = items were set, bit 1 = keys were set.
    // Lite JsonObject only ever sits in MODE_POJO now — setItems/setKeys throw on parent
    // since @items lives on JsonObjectArray and @keys lives on JsonObjectMap. The constants
    // and field remain (defended-dead in parent) so subclasses sharing the same package can
    // touch storageMode for their own legacy bookkeeping; they will be removed in 4b.
    static final byte MODE_POJO = 0;
    static final byte MODE_ITEMS = 1;
    static final byte MODE_KEYS_ONLY = 2;
    static final byte MODE_KEYS_ITEMS = 3;
    byte storageMode;

    // Cached values — package-private so subclasses can invalidate after their own mutations.
    Integer hash;
    private String typeString;
    byte jsonTypeCache;

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
     * the JSON object as array-shaped, so any preceding non-meta fields are discarded
     * (matches the pre-refactor behavior where {@code storageMode |= MODE_ITEMS} masked them).
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

    @Override
    public Object put(Object key, Object value) {
        hash = null;

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
            index.put(key, size);
        } else if (size == INDEX_THRESHOLD) {
            // Just crossed threshold - build index proactively so next put() uses it
            // This includes the key we just added (at position 'size')
            buildIndex();
            index.put(key, size);
        }

        size++;
        return null;
    }

    /**
     * Parser-only fast append path.
     * <p>
     * JSON object keys are overwhelmingly unique in practice, so this method skips duplicate-key
     * search and appends directly to avoid per-field {@code indexOf()} churn during parse.
     * The hash index is NOT maintained here — it is built lazily on first lookup via {@code indexOf()}.
     */
    public void appendFieldForParser(Object key, Object value) {
        ensureCapacity(size + 1);
        keys[size] = key;
        data[size] = value;
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

        Object old = data[idx];

        int numMoved = size - idx - 1;
        if (numMoved > 0) {
            System.arraycopy(keys, idx + 1, keys, idx, numMoved);
            System.arraycopy(data, idx + 1, data, idx, numMoved);
        }

        size--;
        keys[size] = null;
        data[size] = null;
        hash = null;
        index = null;

        return old;
    }

    @Override
    public void putAll(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return;
        hash = null;
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
        hash = null;
        index = null;
        storageMode = MODE_POJO;
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

    private int indexOf(Object key) {
        // Lite JsonObject only ever holds POJO field entries via keys[]/data[].
        if (size == 0) return -1;

        if (size <= INDEX_THRESHOLD) {
            // Linear search for small objects
            for (int i = 0; i < size; i++) {
                if (Objects.equals(key, keys[i])) {
                    return i;
                }
            }
            return -1;
        }

        // Use index for large POJO objects
        if (index == null) {
            buildIndex();
        }
        Integer idx = index.get(key);
        return idx != null ? idx : -1;
    }

    private void buildIndex() {
        index = new HashMap<>(size + (size >> 1));
        for (int i = 0; i < size; i++) {
            index.put(keys[i], i);
        }
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

    private class ValuesCollection extends AbstractSet<Object> {
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
        private final Object[] vals = data;
        private final int len = JsonObject.this.size();
        private int idx = 0;
        private final ReusableEntry entry = new ReusableEntry();

        @Override
        public boolean hasNext() {
            return idx < len;
        }

        @Override
        public Entry<Object, Object> next() {
            if (!hasNext()) throw new NoSuchElementException();
            entry.index = idx++;
            entry.vals = vals;
            return entry;
        }
    }

    private class ReusableEntry implements Entry<Object, Object> {
        int index;
        Object[] vals;

        @Override
        public Object getKey() {
            return keys[index];
        }

        @Override
        public Object getValue() {
            return vals[index];
        }

        @Override
        public Object setValue(Object value) {
            Object old = vals[index];
            vals[index] = value;
            hash = null;
            return old;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(keys[index], e.getKey()) &&
                    Objects.equals(vals[index], e.getValue());
        }

        @Override
        public int hashCode() {
            return (keys[index] == null ? 0 : keys[index].hashCode()) ^
                    (vals[index] == null ? 0 : vals[index].hashCode());
        }
    }

    // ========== Hash and Equals ==========

    @Override
    public int hashCode() {
        if (hash == null) {
            int result = 1;
            int len = size();
            for (int i = 0; i < len; i++) {
                result = 31 * result + (keys[i] == null ? 0 : keys[i].hashCode());
                result = 31 * result + hashCodeSafe(data[i]);
            }
            hash = result;
        }
        return hash;
    }

    // Package-private so subclasses (JsonObjectArray, JsonObjectMap) can reuse for
    // their own hashCode overrides.
    int hashCodeSafe(Object obj) {
        if (obj == null) return 0;
        if (!obj.getClass().isArray()) return obj.hashCode();
        return arrayHashCode(obj, new IdentityHashMap<>());
    }

    private int arrayHashCode(Object array, Map<Object, Integer> seen) {
        if (array == null) return 0;
        if (!array.getClass().isArray()) return array.hashCode();

        Integer cached = seen.get(array);
        if (cached != null) return cached;

        seen.put(array, 0);
        int result = 1;
        if (array instanceof Object[]) {
            for (Object item : (Object[]) array) {
                result = 31 * result + arrayHashCode(item, seen);
            }
        }
        seen.put(array, result);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonObject)) return false;
        // Cross-shape comparison returns false. Different subclasses store their data
        // in different fields; each overrides equals to handle its own shape. This base
        // implementation handles only lite POJO/string-keyed-map data via keys[]/data[].
        if (this.getClass() != obj.getClass()) return false;
        JsonObject other = (JsonObject) obj;

        int len = size();
        if (len != other.size()) return false;

        for (int i = 0; i < len; i++) {
            if (!Objects.equals(keys[i], other.keys[i])) return false;
            if (!Objects.equals(data[i], other.data[i])) return false;
        }
        return true;
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

        hash = null;
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
