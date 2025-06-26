package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class holds a JSON object in a LinkedHashMap.
 * LinkedHashMap used to keep fields in same order as they are
 * when reflecting them in Java.  Instances of this class hold a
 * Map-of-Map representation of a Java object, read from the JSON
 * input stream.
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
public class JsonObject extends JsonValue implements Map<Object, Object> {
    private final Map<Object, Object> jsonStore = new LinkedHashMap<>();
    private Integer hash = null;

    // Explicit fields for meta data
    private Object[] items;
    private Object[] keys;
    private String typeString;
    
    // Cached collections for array-based data
    private Set<Object> cachedKeySet;
    private Collection<Object> cachedValues;
    
    // Cache for sorted state to avoid repeated O(n) checks
    private Boolean sortedCache;
    
    // Cached array lengths to avoid expensive Array.getLength() JNI calls
    private Integer keysLength;
    private Integer itemsLength;

    public String toString() {
        String jType = typeString != null ? typeString : (type == null ? "not set" : type.getTypeName());
        String targetInfo = target == null ? "null" : jType;
        return "JsonObject(id:" + id + ", type:" + jType + ", target:" + targetInfo + ", line:" + line + ", col:" + col + ", size:" + size() + ")";
    }

    // Map APIs
    public boolean isMap() {
        return target instanceof Map || (type != null && Map.class.isAssignableFrom(getRawType()));
    }

    // Collection APIs
    public boolean isCollection() {
        if (target instanceof Collection) {
            return true;
        }
        if (isMap()) {
            return false;
        }
        if (items != null && keys == null) {
            Class<?> type = getRawType();
            return type != null && !type.isArray();
        }
        return false;
    }

    // Array APIs
    public boolean isArray() {
        if (target == null) {
            if (type != null) {
                return getRawType().isArray();
            }
            return items != null && keys == null;
        }
        return target.getClass().isArray();
    }

    // Return the array that this JSON object wraps. This is used when there is a Collection class (like ArrayList)
    // represented in the JSON. 
    public Object[] getItems() {
        return items;
    }

    public void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        this.items = array;
        hash = null;
        // Invalidate cached collections and cache array length
        cachedKeySet = null;
        cachedValues = null;
        itemsLength = array.length;
    }

    // New getters/setters for keys
    public Object[] getKeys() {
        return keys;
    }

    void setKeys(Object[] keys) {
        if (keys == null) {
            throw new JsonIoException("Argument 'keys' cannot be null");
        }
        this.keys = keys;
        hash = null;
        // Invalidate cached collections and sorted state, cache array length
        cachedKeySet = null;
        cachedValues = null;
        sortedCache = null;
        keysLength = keys.length;
    }

    /**
     * Return the raw value provided for the {@code @type} field, if any.
     *
     * @return String containing the raw type name or {@code null} if none was provided
     */
    public String getTypeString() {
        return typeString;
    }

    void setTypeString(String typeString) {
        this.typeString = typeString;
    }

    public int size() {
        if (items != null) {
            return itemsLength != null ? itemsLength : Array.getLength(items);
        }
        return jsonStore.size();
    }

    /**
     * @deprecated Use size() instead. This method will be removed in a future release.
     */
    @Deprecated
    public int getLength() {
        return size();
    }
    
    public void setValue(Object o) {
        jsonStore.put(VALUE, o);
        hash = null;
    }

    public Object getValue() {
        return jsonStore.get(VALUE);
    }

    public boolean hasValue() {
        return size() == 1 && jsonStore.containsKey(VALUE);
    }

    public int hashCode() {
        if (hash == null) {
            int result = 1;

            if (keys != null) {
                result = 31 * result + fastArrayHashCode(keys);
            }

            if (items != null) {
                result = 31 * result + fastArrayHashCode(items);
            }

            if (!jsonStore.isEmpty()) {
                result = 31 * result + jsonStore.hashCode();
            }

            hash = result;
        }
        return hash;
    }

    /**
     * Fast hash code calculation for arrays that avoids IdentityHashMap overhead
     * when possible, but falls back to cycle-safe calculation for complex nested arrays.
     */
    private int fastArrayHashCode(Object[] array) {
        if (array == null) {
            return 1;
        }

        // Quick check if array contains any arrays (nested structure)
        boolean hasNestedArrays = false;
        for (Object item : array) {
            if (item != null && item.getClass().isArray()) {
                hasNestedArrays = true;
                break;
            }
        }

        if (!hasNestedArrays) {
            // Simple case - no nested arrays, use standard Arrays.hashCode equivalent
            int result = 1;
            for (Object item : array) {
                result = 31 * result + (item == null ? 0 : item.hashCode());
            }
            return result;
        } else {
            // Complex case - has nested arrays, use cycle-safe calculation
            return hashCode(array, new IdentityHashMap<>());
        }
    }

    /**
     * Cycle-safe hash code calculation for nested array structures.
     */
    private int hashCode(Object array, Map<Object, Integer> seen) {
        if (array == null) {
            return 1;
        }
        if (!array.getClass().isArray()) {
            return array.hashCode();
        }

        Integer cachedHash = seen.get(array);
        if (cachedHash != null) {
            return cachedHash;
        }

        seen.put(array, null);  // Mark as being processed

        int result = 1;

        for (Object item : (Object[])array) {
            result = 31 * result + hashCode(item, seen);
        }

        seen.put(array, result);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonObject)) return false;
        JsonObject other = (JsonObject) obj;

        // Compare 'items' shallowly (element-by-element if both are arrays).
        if (!shallowArrayEquals(this.items, other.items)) {
            return false;
        }

        // Compare 'keys' shallowly (element-by-element if both are arrays).
        if (!shallowArrayEquals(this.keys, other.keys)) {
            return false;
        }

        // Compare the Map portion the standard way.
        return jsonStore.equals(other.jsonStore);
    }

    /**
     * Compare two Objects if both are arrays, element by element,
     * otherwise do a simple Object.equals().
     */
    private static boolean shallowArrayEquals(Object[] arr1, Object[] arr2) {
        if (arr1 == arr2) {
            return true;            // Same reference or both null
        }
        if (arr1 == null || arr2 == null) {
            return false;           // One is null, the other is not
        }

        // If both are arrays, compare lengths and elements with .equals()
        
        int len1 = Array.getLength(arr1);
        int len2 = Array.getLength(arr2);
        if (len1 != len2) {
            return false;
        }
        for (int i = 0; i < len1; i++) {
            Object e1 = arr1[i];
            Object e2 = arr2[i];
            if (!Objects.equals(e1, e2)) {
                return false;
            }
        }
        return true;
    }
    
    public boolean isEmpty() {
        // Fast path: directly check emptiness without calculating size
        if (items != null) {
            return (itemsLength != null ? itemsLength : items.length) == 0;
        }
        return jsonStore.isEmpty();
    }

    public Object remove(Object key) {
        hash = null;

        // Delegate to jsonStore for other keys
        return jsonStore.remove(key);
    }

    public Object put(Object key, Object value) {
        hash = null;

        // For other keys, delegate to jsonStore
        return jsonStore.put(key, value);
    }

    public void putAll(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        
        // Optimize for bulk operations when we're using jsonStore
        if (keys == null && items == null) {
            if (jsonStore instanceof LinkedHashMap && jsonStore.isEmpty()) {
                // For empty LinkedHashMap, we can use the more efficient putAll
                hash = null;
                jsonStore.putAll(map);
                return;
            }
        }
        
        // Invalidate hash once for the entire operation
        hash = null;
        
        // Default behavior: iterate through entries
        for (Entry<?, ?> entry : map.entrySet()) {
            putInternal(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Internal put method that doesn't invalidate hash (for bulk operations).
     */
    private Object putInternal(Object key, Object value) {
        // For other keys, delegate to jsonStore
        return jsonStore.put(key, value);
    }

    public void clear() {
        super.clear();
        jsonStore.clear();
        items = null;
        keys = null;
        hash = null;
        // Clear cached collections, sorted state, and length caches
        cachedKeySet = null;
        cachedValues = null;
        sortedCache = null;
        keysLength = null;
        itemsLength = null;
    }

    @Override
    public boolean containsKey(Object key) {
        // Check keys array if present
        if (keys != null) {
            // Early exit for null key if no nulls in array
            if (key == null) {
                for (Object k : keys) {
                    if (k == null) {
                        return true;
                    }
                }
                return false;
            }
            
            // For non-null keys, use optimized search  
            int keyLen = keysLength != null ? keysLength : keys.length;
            if (keyLen <= 8) {
                // Linear search for small arrays
                for (Object k : keys) {
                    if (key.equals(k)) {
                        return true;
                    }
                }
            } else if (isSorted() && key instanceof String) {
                // Binary search for sorted String keys
                return binarySearch(keys, key) >= 0;
            } else {
                // Linear search fallback
                for (Object k : keys) {
                    if (Objects.equals(key, k)) {
                        return true;
                    }
                }
            }
            return false;
        }
        // Otherwise delegate to jsonStore
        return jsonStore.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        // Check items array if present
        if (items != null) {
            // Early exit for null value if no nulls in array
            if (value == null) {
                for (Object v : items) {
                    if (v == null) {
                        return true;
                    }
                }
                return false;
            }
            
            // For non-null values, optimize based on type
            int itemLen = itemsLength != null ? itemsLength : items.length;
            if (itemLen <= 8) {
                // Linear search for small arrays
                for (Object v : items) {
                    if (value.equals(v)) {
                        return true;
                    }
                }
            } else {
                // For larger arrays, still use linear search but with early type check
                Class<?> valueClass = value.getClass();
                for (Object v : items) {
                    if (v != null && v.getClass() == valueClass && value.equals(v)) {
                        return true;
                    }
                }
            }
            return false;
        }
        // Otherwise delegate to jsonStore
        return jsonStore.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        // Check keys/items arrays if present
        if (keys != null && items != null) {
            // For small arrays, linear search is faster due to cache locality
            int keyLen = keysLength != null ? keysLength : keys.length;
            if (keyLen <= 8) {
                for (int i = 0; i < keyLen; i++) {
                    if (Objects.equals(key, keys[i])) {
                        return items[i];
                    }
                }
            } else {
                // For larger arrays, try binary search if keys appear sorted
                if (isSorted()) {
                    int index = binarySearch(keys, key);
                    if (index >= 0) {
                        return items[index];
                    }
                } else {
                    // Fall back to linear search
                    for (int i = 0; i < keyLen; i++) {
                        if (Objects.equals(key, keys[i])) {
                            return items[i];
                        }
                    }
                }
            }
            return null;
        }
        // Otherwise delegate to jsonStore
        return jsonStore.get(key);
    }

    /**
     * Check if the keys array appears to be sorted for optimization purposes.
     * Only checks for String keys as they are the most common case.
     * Uses caching to avoid repeated O(n) scans.
     */
    private boolean isSorted() {
        if (keys == null) return false;
        
        // Return cached result if available
        if (sortedCache != null) {
            return sortedCache;
        }
        
        // Calculate and cache the result
        sortedCache = calculateSorted();
        return sortedCache;
    }
    
    /**
     * Calculate if the keys array is sorted (called only once per keys array).
     */
    private boolean calculateSorted() {
        int keyLen = keysLength != null ? keysLength : keys.length;
        if (keyLen < 2) return true;
        
        // Quick check - if not all strings, assume not sorted
        for (Object key : keys) {
            if (!(key instanceof String)) {
                return false;
            }
        }
        
        // Check if sorted
        for (int i = 1; i < keyLen; i++) {
            if (((String) keys[i-1]).compareTo((String) keys[i]) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Binary search for String keys in sorted array.
     */
    private int binarySearch(Object[] keys, Object key) {
        if (!(key instanceof String)) {
            return -1; // Binary search only for String keys
        }
        
        String searchKey = (String) key;
        int left = 0;
        int right = (keysLength != null ? keysLength : keys.length) - 1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            int cmp = searchKey.compareTo((String) keys[mid]);
            
            if (cmp == 0) {
                return mid;
            } else if (cmp < 0) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        return -1; // Not found
    }

    @Override
    public Set<Object> keySet() {
        // If we have keys array, convert to a Set
        if (keys != null) {
            if (cachedKeySet == null) {
                cachedKeySet = new LinkedHashSet<>(Arrays.asList(keys));
            }
            return cachedKeySet;
        }
        // Otherwise use jsonStore's keySet
        return jsonStore.keySet();
    }

    @Override
    public Collection<Object> values() {
        // If we have items array, convert to a Collection
        if (items != null) {
            if (cachedValues == null) {
                Collection<Object> valueList = new LinkedHashSet<>();
                Collections.addAll(valueList, items);
                cachedValues = valueList;
            }
            return cachedValues;
        }
        // Otherwise use jsonStore's values
        return jsonStore.values();
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        // If we have keys/items arrays, create entries from them
        if (keys != null && items != null) {
            return new ArrayEntrySet();
        }
        // Otherwise use jsonStore's entrySet
        return jsonStore.entrySet();
    }

    /**
     * Custom EntrySet implementation for array-based data that minimizes object allocations.
     */
    private class ArrayEntrySet extends AbstractSet<Entry<Object, Object>> {
        @Override
        public int size() {
            return keys != null ? (keysLength != null ? keysLength : keys.length) : 0;
        }

        @Override
        public boolean isEmpty() {
            return keys == null || (keysLength != null ? keysLength : keys.length) == 0;
        }

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new ArrayEntryIterator();
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object key = entry.getKey();
            Object value = entry.getValue();
            
            // Find key in keys array and check if corresponding value matches
            if (keys != null && items != null) {
                int keyLen = keysLength != null ? keysLength : keys.length;
                for (int i = 0; i < keyLen; i++) {
                    if (Objects.equals(keys[i], key)) {
                        return Objects.equals(items[i], value);
                    }
                }
            }
            return false;
        }
    }

    /**
     * Iterator for array-based entries that reuses a single Entry object.
     */
    private class ArrayEntryIterator implements Iterator<Entry<Object, Object>> {
        private int index = 0;
        private final ArrayEntry reusableEntry = new ArrayEntry();

        @Override
        public boolean hasNext() {
            return keys != null && index < (keysLength != null ? keysLength : keys.length);
        }

        @Override
        public Entry<Object, Object> next() {
            if (!hasNext()) {
                throw new java.util.NoSuchElementException();
            }
            reusableEntry.setIndex(index++);
            return reusableEntry;
        }
    }

    /**
     * Reusable Entry implementation that references array positions.
     */
    private class ArrayEntry implements Entry<Object, Object> {
        private int index;

        void setIndex(int index) {
            this.index = index;
        }

        @Override
        public Object getKey() {
            return keys[index];
        }

        @Override
        public Object getValue() {
            return items[index];
        }

        @Override
        public Object setValue(Object value) {
            Object oldValue = items[index];
            items[index] = value;
            hash = null;
            // Invalidate cached values since content changed
            cachedValues = null;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return Objects.equals(keys[index], e.getKey()) &&
                    Objects.equals(items[index], e.getValue());
        }

        @Override
        public int hashCode() {
            return (keys[index] == null ? 0 : keys[index].hashCode()) ^
                    (items[index] == null ? 0 : items[index].hashCode());
        }
    }

    // *****************************************************************************************************************

    /**
     * Return the keys/values of this Map as a Map.Entry, where the key is Object[] of keys, and the value is
     * Object[] values. Currently, this has a side effect on the JsonObject, moving the keys from the jsonStore
     * to the 'keys' member variable, and the values from the jsonStore to the 'items' member variable. This
     * happens when the JSON representation of the Map has String keys, as opposed to being written with @keys
     * and @items. During parsing, these make it into the JsonObject, and this method moves them from the jsonStore
     * to the explicit member variables. No code should be written that depends on this.
     */
    Map.Entry<Object[], Object[]> asTwoArrays() {
        if (keys == null && items == null && !isReference()) {
            // Only convert regular fields to arrays, not meta fields
            final Object[] newKeys = new Object[jsonStore.size()];
            final Object[] newValues = new Object[jsonStore.size()];
            int i = 0;

            for (Map.Entry<?, ?> entry : jsonStore.entrySet()) {
                newKeys[i] = entry.getKey();
                newValues[i] = entry.getValue();
                i++;
            }

            // Move the String-key defined Map to @keys, @items - direct member variables.
            setKeys(newKeys);
            setItems(newValues);

            // Clear the jsonStore now that the String key/values have been moved.
            jsonStore.clear();
            return new AbstractMap.SimpleImmutableEntry<>(newKeys, newValues);
        }

        // Add validation for unbalanced keys/items
        if ((keys == null && items != null) || (keys != null && items == null)) {
            throw new JsonIoException("@keys or @items cannot be empty if the other is not empty");
        }

        if (keys != null && items != null) {
            int keyLen = keysLength != null ? keysLength : Array.getLength(keys);
            int itemLen = itemsLength != null ? itemsLength : Array.getLength(items);
            if (keyLen != itemLen) {
                throw new JsonIoException("@keys and @items must be same length");
            }
        }

        return new AbstractMap.SimpleImmutableEntry<>(keys, items);
    }

    /**
     * Rehash map entries from keys/items arrays to the target map and jsonStore.
     * Called during the resolution process to ensure proper map structure.
     */
    void rehashMaps() {
        if (keys == null || items == null) {
            return; // Nothing to do if arrays aren't present
        }

        hash = null; // Invalidate hash
        Map<Object, Object> targetMap = (Map<Object, Object>) target;

        // Transfer all entries from keys/items arrays to jsonStore and target
        final int len = keysLength != null ? keysLength : keys.length;
        for (int i = 0; i < len; i++) {
            Object key = keys[i];
            Object value = items[i];

            // Add to JsonObject's internal store
            jsonStore.put(key, value);

            // Add to target map if available
            if (targetMap != null) {
                targetMap.put(key, value);
            }
        }

        // Clear arrays to free memory
        keys = null;
        items = null;
        
        // Clear cached collections, sorted state, and length caches since we moved to jsonStore
        cachedKeySet = null;
        cachedValues = null;
        sortedCache = null;
        keysLength = null;
        itemsLength = null;
    }
}