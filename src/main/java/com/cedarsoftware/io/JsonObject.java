package com.cedarsoftware.io;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.convert.Converter;

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

    private static final Set<String> logicalPrimitives = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "boolean",
            "java.lang.Boolean",
            "double",
            "java.lang.Double",
            "long",
            "java.lang.Long",
            "byte",
            "java.lang.Byte",
            "char",
            "java.lang.Character",
            "float",
            "java.lang.Float",
            "int",
            "java.lang.Integer",
            "short",
            "java.lang.Short",
            "date",
            "java.util.Date",
            "BigInt",
            "java.math.BigInteger",
            "BigDec",
            "java.math.BigDecimal",
            "class",
            "java.lang.Class",
            "string",
            "java.lang.String"
    )));

    public String toString() {
        String jType = javaType == null ? "not set" : javaType.getName();
        String targetInfo = getTarget() == null ? "null" : jType;
        return "JsonObject(id:" + id + ", type:" + jType + ", target:" + targetInfo + ", line:" + line + ", col:" + col + ", size:" + size() + ")";
    }

    // TODO: Remove this API and use setTarget() once finished flag is removed.
    public Object setFinishedTarget(Object o, boolean isFinished) {
        this.setTarget(o);
        this.isFinished = isFinished;
        return this.getTarget();
    }

    public boolean isLogicalPrimitive() {
        if (getJavaType() == null) {
            return false;
        }

        return logicalPrimitives.contains(getJavaTypeName());
    }

    public Object getPrimitiveValue(Converter converter, ClassLoader classloader) {
        final Object value = getValue();
        String type = getJavaTypeName();
        if ("class".equals(type) || "java.lang.Class".equals(type)) {
            return ClassUtilities.forName((String) value, classloader);
        }
        Class<?> clazz = ClassUtilities.forName(type, classloader);
        if (clazz == null) {
            throw new JsonIoException("Invalid primitive type, line " + line + ", col " + col);
        }
        return converter.convert(value, clazz);
    }
    
    // Map APIs
    public boolean isMap() {
        return isMap || getTarget() instanceof Map;
    }

    // Collection APIs
    public boolean isCollection() {
        if (getTarget() instanceof Collection) {
            return true;
        }
        if (containsKey(ITEMS) && !containsKey(KEYS)) {
            Class<?> type = getJavaType();
            return type != null && !type.isArray();
        }
        return false;
    }

    // Array APIs
    public boolean isArray() {
        if (getTarget() == null) {
            if (getJavaType() != null) {
                return getJavaType().isArray();
            }
            return containsKey(ITEMS) && !containsKey(KEYS);
        }
        return getTarget().getClass().isArray();
    }

    // Return the array that this JSON object wraps.  This is used when there is a Collection class (like ArrayList)
    // represented in the JSON.  This also occurs if a specified array type is used (not Object[], but Integer[], for
    // example).
    public Object[] getJsonArray() {
        return (Object[]) get(ITEMS);
    }

    public void setJsonArray(Object[] jsonArray) {
        put(ITEMS, jsonArray);
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
            if (getTarget() == null) {
                Object[] items = getJsonArray();
                return items == null ? 0 : items.length;
            }
            if (char[].class.isAssignableFrom(getTarget().getClass())) {
                // Verify this for Character[]
                return 1;
            }
            return Array.getLength(getTarget());
        }
        if (isCollection() || isMap()) {
            Object[] items = getJsonArray();
            return items == null ? 0 : items.length;
        }
        return null;
    }

    public Object setValue(Object o) {
        return this.put(VALUE, o);
    }

    public Object getValue() {
        return this.get(VALUE);
    }

    public boolean hasValue() {
        return this.containsKey(VALUE) && size() == 1;
    }

    public int size() {
        if (containsKey(ITEMS)) {
            if (getJsonArray() == null) {
                return 0;
            }
            return getJsonArray().length;
        }

        return jsonStore.size();
    }

    private int calculateArrayHash() {
        int hashCode = 0;
        Object array = getJsonArray();
        if (array != null) {
            int len = Array.getLength(array);
            for (int j = 0; j < len; j++) {
                Object elem = Array.get(array, j);
                hashCode += elem == null ? 0 : elem.hashCode();
            }
        } else {
            hashCode = super.hashCode();
        }
        return hashCode;
    }

    public int hashCode() {
        if (hash == null) {
            if (isArray() || isCollection()) {
                hash = calculateArrayHash();
            } else {
                hash = jsonStore.hashCode();
            }
        }
        return hash;
    }

    public boolean isEmpty() {
        return jsonStore.isEmpty();
    }

    public boolean containsKey(Object key) {
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
        return jsonStore.remove(key);
    }

    public Object put(Object key, Object value) {
        hash = null;
        if ((ITEMS.equals(key) && containsKey(KEYS)) || (KEYS.equals(key) && containsKey(ITEMS))) {
            isMap = true;
        }
        return jsonStore.put(key, value);
    }

    public void putAll(Map<?, ?> map) {
        hash = null;
        jsonStore.putAll(map);
    }

    public void clear() {
        super.clear();
        jsonStore.clear();
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
     * Object[] values. Currently, this has a side effect on the JsonObject, changing how it stores the keys
     * and items internally.  No calling code should be written to be sensitive to this.
     */
    Map.Entry<Object[], Object[]> asTwoArrays() {
        if (!containsKey(KEYS) && !isReference()) {
            final Object[] keys = new Object[size()];
            final Object[] values = new Object[size()];
            int i = 0;

            for (Object e : entrySet()) {
                final Map.Entry entry = (Map.Entry) e;
                keys[i] = entry.getKey();
                values[i] = entry.getValue();
                i++;
            }
            Class<?> saveType = getJavaType();
            jsonStore.clear();  // Removes key/values (they are added in as parallel collections), and leaves ID alone.
            setJavaType(saveType);
            put(KEYS, keys);
            put(ITEMS, values);
        }

        return new AbstractMap.SimpleImmutableEntry<>((Object[]) get(KEYS), (Object[]) get(ITEMS));
    }

    void rehashMaps(boolean useMapsLocal, Object[] keys, Object[] items) {
        Object[] javaKeys, javaValues;
        Map<Object, Object> map;

        if (useMapsLocal) {   // Move from two Object[]'s storage internally back to Map(key, value)
            map = this;
            javaKeys = (Object[]) remove(KEYS);
            javaValues = (Object[]) remove(ITEMS);
        } else {              // Populate peer Java Map instance
            map = (Map<Object, Object>) getTarget();
            javaKeys = keys;
            javaValues = items;
        }
        jsonStore.clear();
        hash = null;
        int len = javaKeys.length;

        for (int i=0; i < len; i++) {
            map.put(javaKeys[i], javaValues[i]);
        }
    }
}
