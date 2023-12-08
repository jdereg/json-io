package com.cedarsoftware.util.io;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class holds a JSON object in a LinkedHashMap.
 * LinkedHashMap used to keep fields in same order as they are
 * when reflecting them in Java.  Instances of this class hold a
 * Map-of-Map representation of a Java object, read from the JSON
 * input stream.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.*
 */
public class JsonObject extends JsonValue implements Map<Object, Object> {
    private final Map<Object, Object> jsonStore = new LinkedHashMap<>();
    private boolean isMap = false;
    private Integer hash = null;

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

    public Class<?> getTargetClass() {
        if (getTarget() != null) {
            return getTarget().getClass();
        }
        return null;
    }

    public boolean isLogicalPrimitive() {
        if (getJavaType() == null) {
            return false;
        }
        switch (getJavaTypeName()) {
            case "boolean":
            case "java.lang.Boolean":
            case "double":
            case "java.lang.Double":
            case "long":
            case "java.lang.Long":
            case "byte":
            case "java.lang.Byte":
            case "char":
            case "java.lang.Character":
            case "float":
            case "java.lang.Float":
            case "int":
            case "java.lang.Integer":
            case "short":
            case "java.lang.Short":
            case "date":
            case "java.util.Date":
            case "BigInt":
            case "java.math.BigInteger":
            case "BigDec":
            case "java.math.BigDecimal":
                return true;
            case "class":
            case "java.lang.Class":
                return true;
            default:
                return false;
        }
    }

    public Object getPrimitiveValue() {
        final Object value = getValue();
        String type = getJavaTypeName();
        if ("class".equals(type) || "java.lang.Class".equals(type)) {
            return MetaUtils.classForName((String) value, JsonObject.class.getClassLoader());
        }
        Class<?> clazz = MetaUtils.classForName(type, JsonObject.class.getClassLoader());
        if (clazz == null) {
            throw new JsonIoException("Invalid primitive type, line " + line + ", col " + col);
        }
        return MetaUtils.convert(clazz, value);
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
            String typeName = getJavaTypeName();
            return typeName != null && !typeName.contains("[");
        }
        return false;
    }

    // Array APIs
    public boolean isArray() {
        if (getTarget() == null) {
            if (getJavaType() != null) {
                return getJavaTypeName().contains("[");
            }
            return containsKey(ITEMS) && !containsKey(KEYS);
        }
        return getTarget().getClass().isArray();
    }

    // Return the array that this JSON object wraps.  This is used when there is a Collection class (like ArrayList)
    // represented in the JSON.  This also occurs if a specified array type is used (not Object[], but Integer[], for
    // example).
    public Object[] getArray() {
        return (Object[]) get(ITEMS);
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
                Object[] items = (Object[]) get(ITEMS);
                return items == null ? 0 : items.length;
            }
            return Array.getLength(getTarget());
        }
        if (isCollection() || isMap()) {
            Object[] items = (Object[]) get(ITEMS);
            return items == null ? 0 : items.length;
        }
        return null;
    }

    public Class<?> getComponentType() {
        return getTarget().getClass().getComponentType();
    }

    void moveBytesToMate() {
        final byte[] bytes = (byte[]) getTarget();
        final Object[] items = getArray();
        final int len = items.length;

        for (int i = 0; i < len; i++) {
            bytes[i] = ((Number) items[i]).byteValue();
        }
        hash = null;
    }

    void moveCharsToMate() {
        Object[] items = getArray();
        if (items == null) {
            setTarget(null);
        }
        else if (items.length == 0) {
            setTarget(new char[0]);
        }
        else if (items.length == 1) {
            String s = (String) items[0];
            setTarget(s.toCharArray());
        }
        else {
            throw new JsonIoException("char[] should only have one String in the [], found " + items.length + ", line " + line + ", col " + col);
        }
        hash = null;
    }

    public Object setValue(Object o) {
        return this.put(VALUE, o);
    }

    public Object getValue() {
        return this.get(VALUE);
    }

    void clearArray() {
        remove(ITEMS);
        hash = null;
    }

    public int size() {
        if (containsKey(ITEMS)) {
            if (getArray() == null) {
                return 0;
            }
            return getArray().length;
        }
        else if (containsKey(REF)) {
            return 0;
        }

        return jsonStore.size();
    }

    private int calculateArrayHash() {
        int hashCode = 0;
        Object array = get(ITEMS);
        if (array != null) {
            int len = Array.getLength(array);
            for (int j = 0; j < len; j++) {
                Object elem = Array.get(array, j);
                hashCode += elem == null ? 0 : elem.hashCode();
            }
        }
        else {
            hashCode = super.hashCode();
        }
        return hashCode;
    }

    public int hashCode() {
        if (hash == null) {
            if (isArray() || isCollection()) {
                hash = calculateArrayHash();
            }
            else {
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
        if (key == null) {
            return jsonStore.put(null, value);
        }

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
}
