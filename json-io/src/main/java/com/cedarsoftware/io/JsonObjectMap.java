package com.cedarsoftware.io;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Specialization of {@link JsonObject} for complex-keyed map-shaped JSON values
 * (those carried as the {@code @keys} + {@code @items} pair in the json-io intermediate form,
 * representing Java {@code Map} instances whose keys are not Strings).
 * <p>
 * Simple String-keyed maps remain represented by the lite {@link JsonObject} since their storage
 * shape matches the typical POJO/object case (parallel {@code keys[]} / {@code data[]}).
 * <p>
 * Package-private by design. External callers continue to hold {@link JsonObject}
 * references; subclass identity is an internal dispatch and storage detail.
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
class JsonObjectMap extends JsonObject {

    // Shape-specific storage. For complex-keyed maps, the parser produces a (@keys, @items)
    // pair; keysRef holds the keys side, valuesRef holds the values side. Parent's keys[]/data[]
    // remain unused on JsonObjectMap instances; they are inherited but never populated here.
    private Object[] keysRef;
    private Object[] valuesRef;

    JsonObjectMap() {
        super();
    }

    JsonObjectMap(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Object[] getKeys() {
        return keysRef;
    }

    @Override
    void setKeys(Object[] keyArray) {
        if (keyArray == null) {
            throw new JsonIoException("Argument keys cannot be null");
        }
        this.keysRef = keyArray;
        // Invalidate cached state on parent (package-private access).
        this.hash = null;
        this.jsonTypeCache = 0;
    }

    @Override
    public Object[] getItems() {
        return valuesRef;
    }

    @Override
    public void setItems(Object[] array) {
        if (array == null) {
            throw new JsonIoException("Argument array cannot be null");
        }
        this.valuesRef = array;
        this.hash = null;
        this.jsonTypeCache = 0;
    }

    @Override
    public void clear() {
        super.clear();
        this.keysRef = null;
        this.valuesRef = null;
    }

    @Override
    public boolean isMap() {
        if (target != null) {
            return target instanceof Map;
        }
        if (type != null) {
            return Map.class.isAssignableFrom(getRawType());
        }
        return true;
    }

    // ========== Map Interface Overrides ==========
    // Parent's Map operations iterate parent.keys[]/parent.data[], which are empty
    // on JsonObjectMap instances. These overrides redirect to the canonical
    // keysRef/valuesRef storage.

    @Override
    public int size() {
        if (keysRef == null || valuesRef == null) {
            return 0;
        }
        return Math.min(keysRef.length, valuesRef.length);
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Object get(Object key) {
        int idx = indexOfMapKey(key);
        if (idx < 0 || valuesRef == null || idx >= valuesRef.length) {
            return null;
        }
        return valuesRef[idx];
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOfMapKey(key) >= 0;
    }

    @Override
    public boolean containsValue(Object value) {
        if (valuesRef == null) {
            return false;
        }
        int len = size();
        for (int i = 0; i < len; i++) {
            if (Objects.equals(value, valuesRef[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object put(Object key, Object value) {
        // JsonObjectMap is built bulk via setKeys/setItems; per-entry mutation isn't supported.
        throw new JsonIoException("put is not supported on JsonObjectMap; use setKeys/setItems");
    }

    @Override
    public Object remove(Object key) {
        throw new JsonIoException("remove is not supported on JsonObjectMap");
    }

    private int indexOfMapKey(Object key) {
        if (keysRef == null) {
            return -1;
        }
        int len = size();
        for (int i = 0; i < len; i++) {
            if (Objects.equals(key, keysRef[i])) {
                return i;
            }
        }
        return -1;
    }

    // ========== Collection View Overrides ==========
    // Parent's KeySet/ValuesCollection/EntrySet inner classes index parent's keys[]/data[]
    // arrays directly, which are empty on JsonObjectMap. Provide views backed by keysRef/valuesRef.

    @Override
    public Set<Object> keySet() {
        return new MapKeySet();
    }

    @Override
    public Collection<Object> values() {
        return new MapValuesCollection();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return new MapEntrySet();
    }

    // Fast accessors used by JsonWriter hot paths; parent versions read keys[]/data[].
    @Override
    int fastEntryCount() {
        return size();
    }

    @Override
    Object fastKeyAt(int index) {
        return keysRef[index];
    }

    @Override
    Object fastValueAt(int index) {
        return valuesRef[index];
    }

    private final class MapKeySet extends AbstractSet<Object> {
        @Override
        public int size() {
            return JsonObjectMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObjectMap.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final int len = JsonObjectMap.this.size();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return keysRef[idx++];
                }
            };
        }
    }

    private final class MapValuesCollection extends AbstractSet<Object> {
        @Override
        public int size() {
            return JsonObjectMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObjectMap.this.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return containsValue(o);
        }

        @Override
        public Iterator<Object> iterator() {
            return new Iterator<Object>() {
                private final int len = JsonObjectMap.this.size();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Object next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    return valuesRef[idx++];
                }
            };
        }
    }

    private final class MapEntrySet extends AbstractSet<Map.Entry<Object, Object>> {
        @Override
        public int size() {
            return JsonObjectMap.this.size();
        }

        @Override
        public boolean isEmpty() {
            return JsonObjectMap.this.isEmpty();
        }

        @Override
        public Iterator<Map.Entry<Object, Object>> iterator() {
            return new Iterator<Map.Entry<Object, Object>>() {
                private final int len = JsonObjectMap.this.size();
                private int idx = 0;

                @Override
                public boolean hasNext() {
                    return idx < len;
                }

                @Override
                public Map.Entry<Object, Object> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    int i = idx++;
                    return new AbstractMap.SimpleEntry<>(keysRef[i], valuesRef[i]);
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            int idx = indexOfMapKey(entry.getKey());
            if (idx < 0) return false;
            return Objects.equals(valuesRef[idx], entry.getValue());
        }
    }

    // ========== Hash and Equals ==========

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof JsonObjectMap)) {
            return false;
        }
        JsonObjectMap other = (JsonObjectMap) obj;

        int len = size();
        if (len != other.size()) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (!Objects.equals(keysRef[i], other.keysRef[i])) {
                return false;
            }
            if (!Objects.equals(valuesRef[i], other.valuesRef[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (hash == null) {
            int result = 1;
            int len = size();
            for (int i = 0; i < len; i++) {
                result = 31 * result + (keysRef[i] == null ? 0 : keysRef[i].hashCode());
                result = 31 * result + hashCodeSafe(valuesRef[i]);
            }
            hash = result;
        }
        return hash;
    }

    // ========== Resolution Support ==========

    @Override
    Map.Entry<Object[], Object[]> asTwoArrays() {
        // Mirror parent's pre-refactor semantics for the absent/explicit-null cases:
        //   {@type:HashMap, @keys:null, @items:null}  -> empty map (both absent)
        //   {@type:HashMap, @keys:[..], @items:null}  -> error
        //   {@type:HashMap, @keys:null, @items:[..]}  -> error
        boolean keysSet = keysRef != null;
        boolean itemsSet = valuesRef != null;
        if (!keysSet && !itemsSet) {
            return new AbstractMap.SimpleImmutableEntry<>(EMPTY, EMPTY);
        }
        if (!keysSet) {
            throw new JsonIoException("Map with @items must also have @keys");
        }
        if (!itemsSet) {
            throw new JsonIoException("@keys cannot be set without @items");
        }
        if (keysRef.length != valuesRef.length) {
            throw new JsonIoException("@keys and @items must be same length");
        }
        return new AbstractMap.SimpleImmutableEntry<>(keysRef, valuesRef);
    }

    @Override
    @SuppressWarnings("unchecked")
    void rehashMaps() {
        if (!(target instanceof Map)) {
            return;
        }
        if (keysRef == null || valuesRef == null) {
            return;
        }

        int len = Math.min(keysRef.length, valuesRef.length);
        if (len == 0) {
            return;
        }

        hash = null;
        Map<Object, Object> targetMap = (Map<Object, Object>) target;

        for (int i = 0; i < len; i++) {
            Object key = keysRef[i];
            Object value = valuesRef[i];

            // Extract targets or values from nested JsonObjects (mirrors parent's logic).
            if (key instanceof JsonObject) {
                JsonObject jObj = (JsonObject) key;
                if (jObj.getTarget() != null) {
                    key = jObj.getTarget();
                } else if (jObj.hasValue()) {
                    key = jObj.getValue();
                }
            }
            if (value instanceof JsonObject) {
                JsonObject jObj = (JsonObject) value;
                if (jObj.getTarget() != null) {
                    value = jObj.getTarget();
                } else if (jObj.hasValue()) {
                    value = jObj.getValue();
                }
            }

            targetMap.put(key, value);
        }
    }
}
