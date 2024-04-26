package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * UnmodifiableMap provides a toggle between a mutable and immutable map.
 * The map can be sealed to prevent further modifications.
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
public class UnmodifiableMap<K, V> implements Map<K, V>, Unmodifiable {
    private final Map<K, V> map;
    private boolean sealed = false;

    public UnmodifiableMap() {
        this.map = new LinkedHashMap<>();
    }

    public UnmodifiableMap(Map<K, V> map) {
        this.map = new LinkedHashMap<>(map);
    }

    public void seal() {
        sealed = true;
    }

    public void unseal() {
        sealed = false;
    }

    private void throwIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This map has been sealed and is now immutable");
        }
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public V get(Object key) {
        return map.get(key);
    }

    public V put(K key, V value) {
        throwIfSealed();
        return map.put(key, value);
    }

    public V remove(Object key) {
        throwIfSealed();
        return map.remove(key);
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        throwIfSealed();
        map.putAll(m);
    }

    public void clear() {
        throwIfSealed();
        map.clear();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    public Set<Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    public boolean equals(Object o) {
        return o == this || map.equals(o);
    }

    public int hashCode() {
        return map.hashCode();
    }
}
