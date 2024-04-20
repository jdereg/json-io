package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
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
public class SingletonMap<K, V> implements Map<K, V> {
    private static final Object UNINITIALIZED = new Object();
    private K key = (K) UNINITIALIZED;
    private V value = (V) UNINITIALIZED;

    public SingletonMap(K key, V value) {
        this.key = key == null ? (K) UNINITIALIZED : key;
        this.value = value == null ? (V) UNINITIALIZED : value;
    }

    // Serialization support constructor (no arguments)
    public SingletonMap() {
        super();
    }

    public int size() {
        return key == UNINITIALIZED ? 0 : 1;
    }

    public boolean isEmpty() {
        return key == UNINITIALIZED;
    }

    public boolean containsKey(Object key) {
        return this.key != UNINITIALIZED && this.key.equals(key);
    }

    public boolean containsValue(Object value) {
        return this.value != UNINITIALIZED && this.value.equals(value);
    }

    public V get(Object key) {
        return containsKey(key) ? this.value : null;
    }

    public V put(K key, V value) {
        if (this.key == UNINITIALIZED) {
            this.key = key;
            this.value = value;
            return null;
        } else if (this.key.equals(key)) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        } else {
            throw new UnsupportedOperationException("Cannot add more than one item to a SingletonMap");
        }
    }

    public V remove(Object key) {
        throw new UnsupportedOperationException("Cannot remove item from SingletonMap");
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("Cannot add items to SingletonMap");
    }

    public void clear() {
        throw new UnsupportedOperationException("Cannot clear SingletonMap");
    }

    public Set<K> keySet() {
        return key == UNINITIALIZED ? Collections.emptySet() : Collections.singleton(key);
    }

    public Collection<V> values() {
        return key == UNINITIALIZED ? Collections.emptyList() : Collections.singletonList(value);
    }

    public Set<Map.Entry<K, V>> entrySet() {
        if (key == UNINITIALIZED) {
            return Collections.emptySet();
        }
        return Collections.singleton(new Map.Entry<K, V>() {
            public K getKey() {
                return key;
            }

            public V getValue() {
                return value;
            }

            public V setValue(V value) {
                V oldVal = SingletonMap.this.value;
                SingletonMap.this.value = value;
                return oldVal;
            }
        });
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        Map<?, ?> m = (Map<?, ?>) o;
        return size() == m.size() && (key == UNINITIALIZED || (m.containsKey(key) && m.get(key).equals(value)));
    }

    public int hashCode() {
        return key == UNINITIALIZED ? 0 : key.hashCode() ^ (value == null ? 0 : value.hashCode());
    }
}