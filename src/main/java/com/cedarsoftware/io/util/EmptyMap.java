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
public class EmptyMap<K, V> implements Map<K, V> {
    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return true;
    }

    public boolean containsKey(Object key) {
        return false;
    }

    public boolean containsValue(Object value) {
        return false;
    }

    public V get(Object key) {
        return null;
    }

    public V put(K key, V value) {
        throw new UnsupportedOperationException("This is an intentionally empty map, put() not supported.");
    }

    public V remove(Object key) {
        throw new UnsupportedOperationException("This is an intentionally empty map, remove() not supported.");
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        throw new UnsupportedOperationException("This is an intentionally empty map, putAll() not supported.");
    }

    public void clear() {
        // No-op
    }

    public Set<K> keySet() {
        return Collections.emptySet();
    }

    public Collection<V> values() {
        return Collections.emptyList();
    }

    public Set<Entry<K, V>> entrySet() {
        return Collections.emptySet();
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        return size() == ((Map<?, ?>) o).size();
    }

    public int hashCode() {
        return 0;
    }
}