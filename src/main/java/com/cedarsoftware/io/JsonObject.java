package com.cedarsoftware.io;

import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class holds a JSON object using parallel arrays for memory efficiency
 * and insertion-order preservation. Instances of this class hold a
 * Map-of-Map representation of a Java object, read from the JSON input stream.
 *
 * <p>Storage design:</p>
 * <ul>
 *   <li>Parallel arrays (keys[], values[]) maintain insertion order</li>
 *   <li>Lazy HashMap index built only when needed for O(1) lookup on large objects</li>
 *   <li>Items-only mode (arrays/collections) has null keys</li>
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
    // Initial capacity for arrays - most JSON objects have < 16 fields
    private static final int INITIAL_CAPACITY = 16;

    // Threshold for building lazy index - below this, linear search is faster due to cache locality
    private static volatile int INDEX_THRESHOLD = 16;

    // Primary storage: parallel arrays maintain insertion order for map entries
    private Object[] mapKeys;
    private Object[] mapValues;
    private int mapSize;

    // Lazy index for O(1) lookup on large objects (maps key -> array index)
    private transient Map<Object, Integer> index;

    // Separate arrays for @items/@keys format (arrays, collections, maps with non-String keys)
    // These are SEPARATE from mapKeys/mapValues - they coexist!
    private Object[] itemsArray;   // For @items content
    private Object[] keysArray;    // For @keys content (non-String map keys)

    // Cached hash code
    private Integer hash = null;

    // Type information
    private String typeString;

    // Cached type classification for Resolver dispatch optimization
    // 0 = not computed, 1 = ARRAY, 2 = COLLECTION, 3 = MAP, 4 = OBJECT
    private byte jsonTypeCache = 0;

    /**
     * Type classification for optimized dispatch in Resolver.
     * Avoids repeated isArray()/isCollection()/isMap() checks.
     */
    public enum JsonType { ARRAY, COLLECTION, MAP, OBJECT }

    public JsonObject() {
        mapKeys = new Object[INITIAL_CAPACITY];
        mapValues = new Object[INITIAL_CAPACITY];
        mapSize = 0;
    }

    /**
     * Get the cached type classification for this JsonObject.
     * Computed lazily on first access. Used by Resolver for fast dispatch.
     */
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

    // ========== Type checking methods ==========

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
        // Items-only mode: itemsArray set but not keysArray
        if (itemsArray != null && keysArray == null) {
            Class<?> type = getRawType();
            return type != null && !type.isArray();
        }
        return false;
    }

    public boolean isArray() {
        if (target == null) {
            if (type != null) {
                return getRawType().isArray();
            }
            // Items-only mode: itemsArray set but not keysArray
            return itemsArray != null && keysArray == null;
        }
        return target.getClass().isArray();
    }

    // ========== Items/Keys access (for @items/@keys format) ==========

    /**
     * Return the values as an array. Used for collections/arrays (@items format).
     * Returns null if setItems() was never called.
     * Returns reference to internal array for in-place modification during resolution.
     */
    public Object[] getItems() {
        return itemsArray;  // null if setItems() was never called
    }

    /**
     * Set items directly (for @items format - arrays/collections).
     * This is SEPARATE from the map storage - it can coexist with map entries.
     * IMPORTANT: The array is stored by reference (not copied) to allow in-place
     * modification during resolution. This is required for reference patching to work.
     */
    public void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        // Store array by reference - resolver modifies in-place for reference patching
        itemsArray = array;
        hash = null;
        jsonTypeCache = 0;
    }

    /**
     * Return the keys as an array. Used for @keys format (non-String map keys).
     * Returns null if setKeys() was never called.
     * Returns reference to internal array for in-place modification during resolution.
     */
    public Object[] getKeys() {
        return keysArray;  // null if setKeys() was never called
    }

    /**
     * Set keys directly (for @keys/@items format).
     * This is SEPARATE from the map storage - it can coexist with map entries.
     * IMPORTANT: The array is stored by reference (not copied) to allow in-place
     * modification during resolution. This is required for reference patching to work.
     */
    void setKeys(Object[] keyArray) {
        if (keyArray == null) {
            throw new JsonIoException("Argument 'keys' cannot be null");
        }
        // Store array by reference - resolver modifies in-place for reference patching
        keysArray = keyArray;
        hash = null;
        jsonTypeCache = 0;
    }

    public String getTypeString() {
        return typeString;
    }

    void setTypeString(String typeString) {
        this.typeString = typeString;
    }

    // ========== Map implementation ==========

    /**
     * Check if this JsonObject uses @keys/@items format for Map storage.
     * This is used for Maps with non-String keys where regular JSON object keys can't be used.
     * Note: For arrays/collections, only itemsArray is set (keysArray is null).
     * For maps with non-String keys, both keysArray and itemsArray are set.
     */
    private boolean hasKeysItemsMapFormat() {
        return keysArray != null && itemsArray != null;
    }

    /**
     * Check if this JsonObject uses @keys/@items format for Map storage.
     * When true, the Map entries are stored in keysArray/itemsArray rather than mapKeys/mapValues.
     * Used by JsonWriter to avoid double-processing during reference tracing.
     *
     * @return true if using @keys/@items format (both keysArray and itemsArray are set)
     */
    public boolean usesKeysItemsFormat() {
        return keysArray != null && itemsArray != null;
    }

    /**
     * Get the effective size for Map interface operations.
     *
     * The Map interface size depends on the storage format:
     * - @keys/@items format (both set): number of key-value pairs from keysArray/itemsArray
     * - Regular JSON objects: number of entries in mapKeys/mapValues
     * - Arrays/collections (only itemsArray): returns mapSize (additional map properties, often 0)
     *
     * Note: For arrays, itemsArray.length is accessed via getItems(), not via Map.size().
     */
    private int effectiveSize() {
        if (keysArray != null && itemsArray != null) {
            // @keys/@items format for maps with non-String keys
            return Math.min(keysArray.length, itemsArray.length);
        }
        // For regular objects AND arrays, use mapSize
        // Arrays store data in itemsArray, accessed via getItems() not Map interface
        return mapSize;
    }

    /**
     * Get effective keys array for Map operations.
     * - @keys/@items format: returns keysArray
     * - Regular maps: returns mapKeys
     * - Arrays (only itemsArray): returns mapKeys (for additional properties like @type)
     */
    private Object[] effectiveKeys() {
        if (keysArray != null) {
            return keysArray;
        }
        return mapKeys;
    }

    /**
     * Get effective values array for Map operations.
     * - @keys/@items format: returns itemsArray (the values)
     * - Regular maps: returns mapValues
     * - Arrays (only itemsArray): returns mapValues (for additional properties)
     */
    private Object[] effectiveValues() {
        if (keysArray != null && itemsArray != null) {
            return itemsArray;
        }
        return mapValues;
    }

    @Override
    public int size() {
        return effectiveSize();
    }

    /**
     * @deprecated Use size() instead.
     */
    @Deprecated
    public int getLength() {
        return size();
    }

    @Override
    public boolean isEmpty() {
        return effectiveSize() == 0;
    }

    @Override
    public Object put(Object key, Object value) {
        hash = null;

        // Check if key exists
        int idx = indexOf(key);
        if (idx >= 0) {
            Object oldValue = mapValues[idx];
            mapValues[idx] = value;
            return oldValue;
        }

        // New key - append
        ensureCapacity(mapSize + 1);
        mapKeys[mapSize] = key;
        mapValues[mapSize] = value;

        // Update index if it exists
        if (index != null) {
            index.put(key, mapSize);
        }

        mapSize++;
        return null;
    }

    @Override
    public Object get(Object key) {
        int idx = indexOf(key);
        return idx >= 0 ? effectiveValues()[idx] : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        Object[] vals = effectiveValues();
        int len = effectiveSize();
        for (int i = 0; i < len; i++) {
            if (Objects.equals(value, vals[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object remove(Object key) {
        int idx = indexOf(key);
        if (idx < 0) return null;

        Object oldValue = mapValues[idx];

        // Shift remaining elements
        int numMoved = mapSize - idx - 1;
        if (numMoved > 0) {
            System.arraycopy(mapKeys, idx + 1, mapKeys, idx, numMoved);
            System.arraycopy(mapValues, idx + 1, mapValues, idx, numMoved);
        }

        mapSize--;
        mapKeys[mapSize] = null;    // Help GC
        mapValues[mapSize] = null;
        hash = null;
        index = null;         // Invalidate index

        return oldValue;
    }

    @Override
    public void putAll(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return;

        hash = null;
        ensureCapacity(mapSize + map.size());

        for (Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void clear() {
        super.clear();
        for (int i = 0; i < mapSize; i++) {
            mapKeys[i] = null;
            mapValues[i] = null;
        }
        mapSize = 0;
        hash = null;
        index = null;
        itemsArray = null;
        keysArray = null;
        jsonTypeCache = 0;
    }

    // ========== Value/setValue for simple value objects ==========

    public void setValue(Object o) {
        put(VALUE, o);
    }

    public Object getValue() {
        return get(VALUE);
    }

    public boolean hasValue() {
        return mapSize == 1 && containsKey(VALUE);
    }

    // ========== Index management ==========

    /**
     * Find the index of a key. Uses linear search for small objects,
     * lazy HashMap index for large objects.
     * Works with either mapKeys/mapValues or keysArray/itemsArray depending on format.
     */
    private int indexOf(Object key) {
        Object[] keys = effectiveKeys();
        int len = effectiveSize();

        // For @keys/@items format, always use linear search (typically small)
        // For mapKeys format, use index for larger objects
        if (hasKeysItemsMapFormat() || len <= INDEX_THRESHOLD) {
            // Linear search for small objects (cache-friendly)
            for (int i = 0; i < len; i++) {
                if (Objects.equals(key, keys[i])) {
                    return i;
                }
            }
            return -1;
        }

        // Build and use index for larger mapKeys objects
        if (index == null) {
            buildIndex();
        }
        Integer idx = index.get(key);
        return idx != null ? idx : -1;
    }

    private void buildIndex() {
        // Index is only built for mapKeys/mapValues mode (not keysArray/itemsArray)
        index = new HashMap<>(mapSize + (mapSize >> 1)); // 1.5x size
        for (int i = 0; i < mapSize; i++) {
            index.put(mapKeys[i], i);
        }
    }

    private void ensureCapacity(int minCapacity) {
        if (mapKeys.length < minCapacity) {
            int newCapacity = Math.max(mapKeys.length * 2, minCapacity);
            Object[] newKeys = new Object[newCapacity];
            Object[] newValues = new Object[newCapacity];
            System.arraycopy(mapKeys, 0, newKeys, 0, mapSize);
            System.arraycopy(mapValues, 0, newValues, 0, mapSize);
            mapKeys = newKeys;
            mapValues = newValues;
        }
    }

    // ========== View classes ==========

    @Override
    public Set<Object> keySet() {
        return new ArrayKeySet();
    }

    @Override
    public Collection<Object> values() {
        return new ArrayValueCollection();
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        return new ArrayEntrySet();
    }

    private class ArrayKeySet extends AbstractSet<Object> {
        @Override
        public int size() {
            return effectiveSize();
        }

        @Override
        public boolean isEmpty() {
            return effectiveSize() == 0;
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final Object[] keys = effectiveKeys();
                private final int len = effectiveSize();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new java.util.NoSuchElementException();
                    return keys[idx++];
                }
            };
        }
    }

    private class ArrayValueCollection extends AbstractSet<Object> {
        @Override
        public int size() {
            return effectiveSize();
        }

        @Override
        public boolean isEmpty() {
            return effectiveSize() == 0;
        }

        @Override
        public boolean contains(Object o) {
            return containsValue(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final Object[] vals = effectiveValues();
                private final int len = effectiveSize();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new java.util.NoSuchElementException();
                    return vals[idx++];
                }
            };
        }
    }

    private class ArrayEntrySet extends AbstractSet<Entry<Object, Object>> {
        @Override
        public int size() {
            return effectiveSize();
        }

        @Override
        public boolean isEmpty() {
            return effectiveSize() == 0;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new ArrayEntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            int idx = indexOf(entry.getKey());
            return idx >= 0 && Objects.equals(effectiveValues()[idx], entry.getValue());
        }
    }

    private class ArrayEntryIterator implements Iterator<Entry<Object, Object>> {
        private final Object[] keys = effectiveKeys();
        private final Object[] vals = effectiveValues();
        private final int len = effectiveSize();
        private int idx = 0;
        private final ArrayEntry reusableEntry = new ArrayEntry();

        @Override
        public boolean hasNext() {
            return idx < len;
        }

        @Override
        public Entry<Object, Object> next() {
            if (!hasNext()) throw new java.util.NoSuchElementException();
            reusableEntry.setIndex(idx++, keys, vals);
            return reusableEntry;
        }
    }

    private class ArrayEntry implements Entry<Object, Object> {
        private int idx;
        private Object[] keys;
        private Object[] vals;

        void setIndex(int index, Object[] keys, Object[] vals) {
            this.idx = index;
            this.keys = keys;
            this.vals = vals;
        }

        @Override
        public Object getKey() {
            return keys[idx];
        }

        @Override
        public Object getValue() {
            return vals[idx];
        }

        @Override
        public Object setValue(Object value) {
            Object oldValue = vals[idx];
            vals[idx] = value;
            hash = null;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(keys[idx], e.getKey()) &&
                    Objects.equals(vals[idx], e.getValue());
        }

        @Override
        public int hashCode() {
            return (keys[idx] == null ? 0 : keys[idx].hashCode()) ^
                    (vals[idx] == null ? 0 : vals[idx].hashCode());
        }
    }

    // ========== Hash and equals ==========

    @Override
    public int hashCode() {
        if (hash == null) {
            Object[] keys = effectiveKeys();
            Object[] vals = effectiveValues();
            int len = effectiveSize();
            int result = 1;
            for (int i = 0; i < len; i++) {
                result = 31 * result + (keys[i] == null ? 0 : keys[i].hashCode());
                result = 31 * result + hashCodeSafe(vals[i]);
            }
            hash = result;
        }
        return hash;
    }

    private int hashCodeSafe(Object obj) {
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
        for (Object item : (Object[]) array) {
            result = 31 * result + arrayHashCode(item, seen);
        }
        seen.put(array, result);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonObject)) return false;
        JsonObject other = (JsonObject) obj;

        int len = effectiveSize();
        if (len != other.effectiveSize()) return false;

        Object[] keys = effectiveKeys();
        Object[] vals = effectiveValues();
        Object[] otherKeys = other.effectiveKeys();
        Object[] otherVals = other.effectiveValues();

        for (int i = 0; i < len; i++) {
            if (!Objects.equals(keys[i], otherKeys[i])) return false;
            if (!Objects.equals(vals[i], otherVals[i])) return false;
        }
        return true;
    }

    // ========== Resolution support methods ==========

    /**
     * Return the keys/values as a Map.Entry of parallel arrays.
     * Used during resolution for Maps.
     *
     * Priority:
     * 1. If @keys/@items were set (keysArray/itemsArray), return those
     * 2. Otherwise, copy map entries to keysArray/itemsArray format for processing
     *
     * IMPORTANT: This method NEVER clears mapKeys/mapValues. The JsonObject's Map
     * interface must always work because:
     * - In Maps mode, the JsonObject itself may be the final result
     * - Even with external targets, the JsonObject may be returned for certain cases
     *
     * The keysArray/itemsArray are used for:
     * - Resolution of nested JsonObjects
     * - Rehashing into target Maps
     *
     * Returns references to internal arrays for in-place modification during resolution.
     */
    Map.Entry<Object[], Object[]> asTwoArrays() {
        // If @keys/@items format was used, validate and return those arrays
        if (keysArray != null || itemsArray != null) {
            // Validate: if one is set, the other must be too (unless it's a pure array with only @items)
            // For maps, both must be present and have same length
            if (keysArray != null && itemsArray == null) {
                throw new JsonIoException("@keys or @items cannot be empty when the other is not");
            }
            if (keysArray == null && isMap()) {
                throw new JsonIoException("@keys or @items cannot be empty when the other is not");
            }
            if (keysArray != null && itemsArray != null && keysArray.length != itemsArray.length) {
                throw new JsonIoException("@keys and @items must be same length");
            }
            return new AbstractMap.SimpleImmutableEntry<>(keysArray, itemsArray);
        }

        // Otherwise, copy map entries to keysArray/itemsArray format for processing
        if (mapSize == 0) {
            return new AbstractMap.SimpleImmutableEntry<>(new Object[0], new Object[0]);
        }

        // Copy data to keysArray/itemsArray for processing
        // NEVER clear mapKeys/mapValues - the JsonObject's Map interface must always work
        keysArray = new Object[mapSize];
        itemsArray = new Object[mapSize];
        System.arraycopy(mapKeys, 0, keysArray, 0, mapSize);
        System.arraycopy(mapValues, 0, itemsArray, 0, mapSize);

        return new AbstractMap.SimpleImmutableEntry<>(keysArray, itemsArray);
    }

    /**
     * Rehash map entries from keysArray/itemsArray to the target map.
     * Called during the resolution process to ensure proper map structure.
     *
     * Note: asTwoArrays() already transfers mapKeys/mapValues to keysArray/itemsArray
     * format before processing, so we only need to handle keysArray/itemsArray here.
     *
     * IMPORTANT: We do NOT clear keysArray/itemsArray after rehashing because:
     * - In Maps mode, the JsonObject itself may be returned, not the target Map
     * - The JsonObject's Map interface uses keysArray/itemsArray for @keys/@items format
     * - Keeping them allows the Map interface to work correctly in all modes
     */
    @SuppressWarnings("unchecked")
    void rehashMaps() {
        if (keysArray == null || itemsArray == null) return;
        if (!(target instanceof Map)) return;

        hash = null;
        Map<Object, Object> targetMap = (Map<Object, Object>) target;

        int len = Math.min(keysArray.length, itemsArray.length);
        for (int i = 0; i < len; i++) {
            targetMap.put(keysArray[i], itemsArray[i]);
        }

        // Do NOT clear keysArray/itemsArray - they're needed for the Map interface
        // in Maps mode where the JsonObject itself is returned, not the target Map
    }

    // ========== Static configuration ==========

    /**
     * Sets the linear search threshold for JsonObject operations.
     * @param threshold must be >= 1
     */
    public static void setLinearSearchThreshold(int threshold) {
        if (threshold < 1) {
            throw new JsonIoException("LinearSearchThreshold must be >= 1, value: " + threshold);
        }
        INDEX_THRESHOLD = threshold;
    }

    /**
     * Gets the current linear search threshold.
     */
    public static int getLinearSearchThreshold() {
        return INDEX_THRESHOLD;
    }
}
