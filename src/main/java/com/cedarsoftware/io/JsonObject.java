package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cedarsoftware.util.Converter;

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
    private boolean isMap = false;
    private Integer hash = null;

    // Explicit fields for meta data
    private Object items;
    private Object keys;
    private String enumType;

    public String toString() {
        String jType = javaType == null ? "not set" : javaType.getName();
        String targetInfo = target == null ? "null" : jType;
        return "JsonObject(id:" + id + ", type:" + jType + ", target:" + targetInfo + ", line:" + line + ", col:" + col + ", size:" + size() + ")";
    }

    public Object setFinishedTarget(Object o, boolean isFinished) {
        setTarget(o);
        this.isFinished = isFinished;
        return target;
    }

    // Map APIs
    public boolean isMap() {
        return isMap ||
                target instanceof Map ||
                (javaType != null && Map.class.isAssignableFrom(javaType));
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
            Class<?> type = javaType;
            return type != null && !type.isArray();
        }
        return false;
    }

    // Array APIs
    public boolean isArray() {
        if (target == null) {
            if (javaType != null) {
                return javaType.isArray();
            }
            return items != null && keys == null;
        }
        return target.getClass().isArray();
    }

    // Return the array that this JSON object wraps. This is used when there is a Collection class (like ArrayList)
    // represented in the JSON. This also occurs if a specified array type is used (not Object[], but Integer[], for example).
    protected Object getItems() {
        return items;
    }

    protected void setItems(Object array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        if (!array.getClass().isArray()) {
            throw new JsonIoException("Argument array must be an array, type: " + array.getClass().getName());
        }
        this.items = array;
        hash = null;
    }

    // New getters/setters for keys and enum type
    protected Object getKeys() {
        return keys;
    }

    protected void setKeys(Object keys) {
        if (keys == null) {
            throw new JsonIoException("Argument 'keys' cannot be null");
        }
        if (!keys.getClass().isArray()) {
            throw new JsonIoException("Argument 'keys' must be an array, type: " + keys.getClass().getName());
        }
        this.keys = keys;
        hash = null;
    }

    protected String getEnumType() {
        return enumType;
    }

    protected void setEnumType(String enumType) {
        this.enumType = enumType;
        hash = null;
    }

    public int getLength() {
        Integer items = getLenientSize();
        if (items != null) {
            return items;
        }
        throw new JsonIoException("getLength() called on a non-collection, line " + line + ", col " + col);
    }

    private Integer getLenientSize() {
        if (isArray()) {
            if (target == null) {
                Object items = getItems();
                return items == null ? 0 : Array.getLength(items);
            }
            if (char[].class.isAssignableFrom(target.getClass())) {
                // TODO: Verify this for Character[]
                return 1;
            }
            return Array.getLength(target);
        }
        if (isCollection() || isMap()) {
            Object items = getItems();
            return items == null ? 0 : Array.getLength(items);
        }
        return null;
    }

    public void setValue(Object o) {
        put(VALUE, o);
        hash = null;
    }

    public Object getValue() {
        return get(VALUE);
    }

    public boolean hasValue() {
        return containsKey(VALUE) && size() == 1;
    }

    public int size() {
        if (items != null) {
            return getLength();
        }
        return jsonStore.size();
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

            if (enumType != null) {
                result = 31 * result + enumType.hashCode();
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
        int length = Array.getLength(array);

        for (int i = 0; i < length; i++) {
            Object item = Array.get(array, i);
            result = 31 * result + hashCode(item, seen);
        }

        seen.put(array, result);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof JsonObject)) return false;
        JsonObject other = (JsonObject) obj;

        if (!Objects.equals(enumType, other.enumType)) return false;
        if (!Arrays.deepEquals(new Object[]{items}, new Object[]{other.items})) return false;
        if (!Arrays.deepEquals(new Object[]{keys}, new Object[]{other.keys})) return false;
        return jsonStore.equals(other.jsonStore);
    }

    public boolean isEmpty() {
        return jsonStore.isEmpty() && items == null && keys == null;
    }

    public boolean containsKey(Object key) {
        // Handle special keys (meta fields)
        if (ID.equals(key)) {
            return id != -1L;
        }
        if (REF.equals(key)) {
            return refId != null;
        }
        if (ITEMS.equals(key)) {
            return items != null;
        }
        if (KEYS.equals(key)) {
            return keys != null;
        }
        if (ENUM.equals(key)) {
            return enumType != null;
        }

        // Delegate to jsonStore for other keys
        return jsonStore.containsKey(key);
    }

    public boolean containsValue(Object value) {
        // For arrays/collections and maps, check explicit fields
        if (items != null || keys != null) {
            if (value == items || value == keys) return true;
            return false;  // Arrays/maps don't store in jsonStore
        }

        // For enums
        if (enumType != null && value.equals(enumType)) return true;

        // For regular objects
        return jsonStore.containsValue(value);
    }

    public Object get(Object key) {
        if (ID.equals(key)) {
            return id;
        }
        if (REF.equals(key) && isReference()) {
            return refId;
        }
        if (ITEMS.equals(key)) {
            return items;
        }
        if (KEYS.equals(key)) {
            return keys;
        }
        if (ENUM.equals(key)) {
            return enumType;
        }
        return jsonStore.get(key);
    }

    public Object remove(Object key) {
        hash = null;

        // Handle special keys (meta fields)
        if (ID.equals(key)) {
            Object oldId = id;
            id = -1L;
            return oldId;
        }
        if (REF.equals(key)) {
            Object oldRef = refId;
            refId = null;
            return oldRef;
        }
        if (ITEMS.equals(key)) {
            Object oldItems = items;
            items = null;
            return oldItems;
        }
        if (KEYS.equals(key)) {
            Object oldKeys = keys;
            keys = null;
            return oldKeys;
        }
        if (ENUM.equals(key)) {
            String oldEnum = enumType;
            enumType = null;
            return oldEnum;
        }

        // Delegate to jsonStore for other keys
        return jsonStore.remove(key);
    }

    public Object put(Object key, Object value) {
        hash = null;

        // Handle meta fields
        if (ID.equals(key)) {
            Object oldId = id;
            id = Converter.convert(value, Long.class);
            return oldId;
        }
        if (REF.equals(key)) {
            Object oldRef = refId;
            refId = Converter.convert(value, Long.class);
            return oldRef;
        }
        if (ITEMS.equals(key)) {
            if (value != null && !value.getClass().isArray()) {
                throw new IllegalArgumentException("@items value must be an [] array");
            }
            Object oldItems = items;
            items = value;
            return oldItems;
        }
        if (KEYS.equals(key)) {
            if (value != null && !(value instanceof Object[])) {
                throw new IllegalArgumentException("@keys value must be an Object[] array");
            }
            Object oldKeys = keys;
            keys = value;
            return oldKeys;
        }
        if (ENUM.equals(key)) {
            if (value != null && !(value instanceof String)) {
                throw new IllegalArgumentException("@enum value must be a String");
            }
            String oldEnum = enumType;
            enumType = (String) value;
            return oldEnum;
        }

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
        enumType = null;
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
    Map.Entry<Object, Object> asTwoArrays() {
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

        return new AbstractMap.SimpleImmutableEntry<>(keys, items);
    }

    void rehashMaps(boolean useMapsLocal, Object[] keyz, Object[] itemz) {
        Map<Object, Object> targetMap = useMapsLocal ? this : (Map<Object, Object>) target;
        Object sourceKeys = useMapsLocal ? getKeys() : keyz;
        Object sourceValues = useMapsLocal ? getItems() : itemz;

        if (useMapsLocal) {
            remove(KEYS);
            remove(ITEMS);
        }

        jsonStore.clear();
        keys = null;
        items = null;
        hash = null;

        int len = Array.getLength(sourceKeys);
        for (int i = 0; i < len; i++) {
            targetMap.put(Array.get(sourceKeys, i), Array.get(sourceValues, i));
        }
    }
}