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
 *   <li>Parallel arrays (keys[], values[]) for map entries (POJOs, maps with String keys)</li>
 *   <li>Separate items[] array for @items content (arrays, collections)</li>
 *   <li>For @keys/@items format maps: keys[] holds complex keys, items[] holds values</li>
 *   <li>Lazy HashMap index built only when needed for O(1) lookup on large objects</li>
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
    // Shared empty arrays for lazy allocation - avoids creating arrays until first put()
    private static final Object[] EMPTY_ARRAY = new Object[0];
    private static final int INITIAL_CAPACITY = 4;  // Reduced from 16 - most JSON objects are small
    // Threshold for switching from linear search to HashMap index.
    // Lower values reduce O(n²) cost of building objects with many fields.
    private static volatile int INDEX_THRESHOLD = 4;

    // Map storage: parallel arrays for map entries (POJOs, maps with String keys)
    // Uses shared EMPTY_ARRAY until first put() to avoid allocation
    private Object[] keys = EMPTY_ARRAY;
    private Object[] values = EMPTY_ARRAY;
    private int size;

    // Separate storage for @items content (arrays, collections, map values for @keys format)
    private Object[] items;

    // Lazy index for O(1) lookup on large objects
    private transient Map<Object, Integer> index;

    // Flags to track how data was populated
    private boolean itemsWereSet;   // setItems() was called
    private boolean keysWereSet;    // setKeys() was called (vs put())

    // Cached values
    private Integer hash;
    private String typeString;
    private byte jsonTypeCache;

    // Stored element type for collections/arrays (or value type for maps).
    // Used to preserve generic type information that would otherwise be lost
    // when createInstance changes the type to a concrete class.
    private transient java.lang.reflect.Type itemElementType;

    /**
     * Type classification for optimized dispatch in Resolver.
     */
    public enum JsonType { ARRAY, COLLECTION, MAP, OBJECT }

    /**
     * Default constructor uses lazy allocation - arrays are shared empty
     * until first put() to avoid allocating memory for empty objects.
     */
    public JsonObject() {
        // keys and values initialized to EMPTY_ARRAY at field declaration
        // size defaults to 0
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
        if (itemsWereSet && !keysWereSet) {
            Class<?> rawType = getRawType();
            return rawType != null && !rawType.isArray();
        }
        return false;
    }

    public boolean isArray() {
        if (target != null) {
            return target.getClass().isArray();
        }
        if (type != null) {
            return getRawType().isArray();
        }
        return itemsWereSet && !keysWereSet;
    }

    // ========== Items/Keys Access ==========

    /**
     * Get items array for arrays/collections or @items format.
     * Returns null if setItems() was never called.
     */
    public Object[] getItems() {
        return items;
    }

    /**
     * Set items directly - for @items format (arrays, collections, maps with @keys).
     */
    public void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        this.items = array;
        this.itemsWereSet = true;
        this.hash = null;
        this.jsonTypeCache = 0;
    }

    /**
     * Get keys array for @keys format (maps with non-String keys).
     * Returns null if setKeys() was never called.
     */
    public Object[] getKeys() {
        return keysWereSet ? keys : null;
    }

    /**
     * Set keys directly - for @keys format (maps with non-String keys).
     */
    void setKeys(Object[] keyArray) {
        if (keyArray == null) {
            throw new JsonIoException("Argument keys cannot be null");
        }
        this.keys = keyArray;
        this.keysWereSet = true;
        this.hash = null;
        this.jsonTypeCache = 0;
        this.index = null;
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

    // ========== Map Interface ==========

    /**
     * Returns the effective size for Map operations.
     * For @keys/@items format (both set): number of key-value pairs
     * For @keys only (incomplete): 0
     * For POJOs: number of field entries
     */
    @Override
    public int size() {
        if (keysWereSet) {
            // @keys format - only show size if @items is also set
            return itemsWereSet ? Math.min(keys.length, items.length) : 0;
        }
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
        // For @keys/@items format, values are in items array
        return (keysWereSet && itemsWereSet) ? items[idx] : values[idx];
    }

    @Override
    public Object put(Object key, Object value) {
        hash = null;

        int idx = indexOf(key);
        if (idx >= 0) {
            Object old = values[idx];
            values[idx] = value;
            return old;
        }

        ensureCapacity(size + 1);
        keys[size] = key;
        values[size] = value;

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

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
        int len = size();
        for (int i = 0; i < len; i++) {
            if (Objects.equals(value, vals[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object remove(Object key) {
        // Don't support remove for @keys/@items format
        if (keysWereSet) return null;

        int idx = indexOf(key);
        if (idx < 0) return null;

        Object old = values[idx];

        int numMoved = size - idx - 1;
        if (numMoved > 0) {
            System.arraycopy(keys, idx + 1, keys, idx, numMoved);
            System.arraycopy(values, idx + 1, values, idx, numMoved);
        }

        size--;
        keys[size] = null;
        values[size] = null;
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
        Arrays.fill(values, 0, size, null);
        size = 0;
        items = null;
        hash = null;
        index = null;
        itemsWereSet = false;
        keysWereSet = false;
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
        // For @keys format, only search if @items is also set
        if (keysWereSet) {
            if (!itemsWereSet) return -1;  // Incomplete @keys/@items format
            int len = keys.length;
            for (int i = 0; i < len; i++) {
                if (Objects.equals(key, keys[i])) {
                    return i;
                }
            }
            return -1;
        }

        // For POJOs
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

        // Handle lazy allocation: EMPTY_ARRAY has length 0, so first put triggers allocation
        int newCapacity = keys.length == 0
                ? Math.max(INITIAL_CAPACITY, minCapacity)
                : Math.max(keys.length * 2, minCapacity);
        keys = Arrays.copyOf(keys, newCapacity);
        values = Arrays.copyOf(values, newCapacity);
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
                private final Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
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
            Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
            return Objects.equals(vals[idx], entry.getValue());
        }
    }

    private class EntryIterator implements Iterator<Entry<Object, Object>> {
        private final Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
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
            Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
            int len = size();
            for (int i = 0; i < len; i++) {
                result = 31 * result + (keys[i] == null ? 0 : keys[i].hashCode());
                result = 31 * result + hashCodeSafe(vals[i]);
            }
            if (items != null && !keysWereSet) {
                // Include items in hash for arrays/collections
                result = 31 * result + Arrays.hashCode(items);
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
        JsonObject other = (JsonObject) obj;

        int len = size();
        if (len != other.size()) return false;

        Object[] vals = (keysWereSet && itemsWereSet) ? items : values;
        Object[] otherVals = (other.keysWereSet && other.itemsWereSet) ? other.items : other.values;

        for (int i = 0; i < len; i++) {
            if (!Objects.equals(keys[i], other.keys[i])) return false;
            if (!Objects.equals(vals[i], otherVals[i])) return false;
        }

        // Compare items for arrays/collections
        if (!keysWereSet && !other.keysWereSet) {
            return Arrays.equals(items, other.items);
        }

        return true;
    }

    // ========== Resolution Support ==========

    Map.Entry<Object[], Object[]> asTwoArrays() {
        if (keysWereSet && itemsWereSet) {
            if (keys.length != items.length) {
                throw new JsonIoException("@keys and @items must be same length");
            }
            return new AbstractMap.SimpleImmutableEntry<>(keys, items);
        }

        if (keysWereSet) {
            throw new JsonIoException("@keys cannot be set without @items");
        }
        if (itemsWereSet && isMap()) {
            throw new JsonIoException("Map with @items must also have @keys");
        }

        // Return original arrays - traverseMap() uses size to limit iteration
        // IMPORTANT: Do NOT copy arrays here! Reference patching during traversal
        // modifies the arrays in place, and rehashMaps() needs to see those patches.
        return new AbstractMap.SimpleImmutableEntry<>(keys, values);
    }

    @SuppressWarnings("unchecked")
    void rehashMaps() {
        if (!(target instanceof Map)) return;

        // For @keys/@items format: use keys array with items array
        // For POJOs: use keys/values arrays with size
        Object[] k = keys;
        Object[] v = (keysWereSet && itemsWereSet) ? items : values;
        int len = keysWereSet ? keys.length : size;

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
