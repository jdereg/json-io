package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
    public static String FIELD_PREFIX = "-~";
    public static String FIELD_SUFFIX = "~-";
    private final Map<Object, Object> jsonStore = new LinkedHashMap<>();
    private Integer hash = null;

    // Explicit fields for meta data
    private Object[] items;
    private Object[] keys;

    public String toString() {
        String jType = type == null ? "not set" : type.getTypeName();
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

    void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        if (!array.getClass().isArray()) {
            throw new JsonIoException("Argument array must be an array, type: " + array.getClass().getName());
        }
        this.items = array;
        hash = null;
    }

    // New getters/setters for keys
    public Object[] getKeys() {
        return keys;
    }

    void setKeys(Object[] keys) {
        if (keys == null) {
            throw new JsonIoException("Argument 'keys' cannot be null");
        }
        if (!keys.getClass().isArray()) {
            throw new JsonIoException("Argument 'keys' must be an array, type: " + keys.getClass().getName());
        }
        this.keys = keys;
        hash = null;
    }

    public int size() {
        if (items != null) {
            return Array.getLength(items);
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
                result = 31 * result + hashCode(keys, new IdentityHashMap<>());
            }

            if (items != null) {
                result = 31 * result + hashCode(items, new IdentityHashMap<>());
            }

            if (!jsonStore.isEmpty()) {
                result = 31 * result + jsonStore.hashCode();
            }

            hash = result;
        }
        return hash;
    }

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
        return size() < 1;
    }

    public boolean containsKey(Object key) {
        // Delegate to jsonStore for other keys
        return jsonStore.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return jsonStore.containsValue(value);
    }

    public Object get(Object key) {
        return jsonStore.get(key);
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
        for (Entry<?, ?> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public void clear() {
        super.clear();
        jsonStore.clear();
        items = null;
        keys = null;
        hash = null;
    }

    public Set<Object> keySet() {
        return jsonStore.keySet();
    }

    public Collection<Object> values() {
        return jsonStore.values();
    }

    public Set<Entry<Object, Object>> entrySet() {
        return jsonStore.entrySet();
    }

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

        if (keys != null && items != null && Array.getLength(keys) != Array.getLength(items)) {
            throw new JsonIoException("@keys and @items must be same length");
        }

        return new AbstractMap.SimpleImmutableEntry<>(keys, (Object[])items);
    }

    void rehashMaps() {
        Map<Object, Object> targetMap = (Map<Object, Object>) target;
        hash = null;

        int len = keys.length;
        for (int i = 0; i < len; i++) {
            Object key = keys[i];
            Object value = items[i];
            
            if (key instanceof String) {
                String k = (String) key;
                if (k.startsWith(FIELD_PREFIX) && k.endsWith(FIELD_SUFFIX)) {
                    continue;
                }
            }
            put(key, value);
            if (targetMap != null) {
                targetMap.put(key, value);
            }
        }
        keys = null;
        items = null;
    }
}