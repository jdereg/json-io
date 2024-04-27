package com.cedarsoftware.io.util;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * UnmodifiableMap provides a Map that can be 'sealed' and 'unsealed'. When sealed,
 * the Map is read-only. Before sealing, it can be modified. The iterator,
 * keySet, entrySet, and values() return views that honor the original Map's
 * sealed state.
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
    private final Map<K, V> map = new LinkedHashMap<>();
    private volatile boolean sealed = false;

    public UnmodifiableMap() { }
    public UnmodifiableMap(Map<K, V> items) { map.putAll(items); }
    public void seal() { sealed = true; }
    public void unseal() { sealed = false; }
    private void throwIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This map has been sealed and is now immutable");
        }
    }

    public boolean equals(Object obj) { return map.equals(obj); }
    public int hashCode() { return map.hashCode(); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public boolean containsKey(Object key) { return map.containsKey(key); }
    public boolean containsValue(Object value) { return map.containsValue(value); }
    public V get(Object key) { return map.get(key); }
    public V put(K key, V value) { throwIfSealed(); return map.put(key, value); }
    public V remove(Object key) { throwIfSealed(); return map.remove(key); }
    public void putAll(Map<? extends K, ? extends V> m) { throwIfSealed(); map.putAll(m); }
    public void clear() { throwIfSealed(); map.clear(); }
    public Set<K> keySet() { return createSealHonoringSet(map.keySet()); }
    public Collection<V> values() { return createSealHonoringCollection(map.values()); }
    public Set<Map.Entry<K, V>> entrySet() { return createSealHonoringSet((Set)map.entrySet()); }

    private Set<K> createSealHonoringSet(Set<K> set) {
        return new LinkedHashSet<K>() {
            public int size() { return set.size(); }
            public boolean isEmpty() { return set.isEmpty(); }
            public boolean contains(Object o) { return set.contains(o); }
            public Iterator<K> iterator() {
                return new Iterator<K>() {
                    private final Iterator<K> it = set.iterator();
                    public boolean hasNext() { return it.hasNext(); }
                    public K next() {
                        K element = it.next();
                        if (element instanceof Map.Entry) {
                            Map.Entry entry = (Map.Entry)element;
                            return (K) new AbstractMap.SimpleImmutableEntry(entry.getKey(), entry.getValue());  // prevent .setValue() on the entry's value.
                        } else {
                            return element;
                        }
                    }
                    public void remove() { throwIfSealed(); it.remove(); }
                };
            }
            public Object[] toArray() { return set.toArray(); }
            public <T> T[] toArray(T[] a) { return set.toArray(a); }
            public boolean add(K k) { throwIfSealed(); return set.add(k); }
            public boolean remove(Object o) { throwIfSealed(); return set.remove(o); }
            public boolean containsAll(Collection<?> c) { return set.containsAll(c); }
            public boolean addAll(Collection<? extends K> c) { throwIfSealed(); return set.addAll(c); }
            public boolean retainAll(Collection<?> c) { throwIfSealed(); return set.retainAll(c); }
            public boolean removeAll(Collection<?> c) { throwIfSealed(); return set.removeAll(c); }
            public void clear() { throwIfSealed(); set.clear(); }
            public boolean equals(Object o) { return set.equals(o); }
            public int hashCode() { return set.hashCode(); }
        };
    }

    private Collection<V> createSealHonoringCollection(Collection<V> col) {
        return new Collection<V>() {
            public int size() { return col.size(); }
            public boolean isEmpty() { return col.isEmpty(); }
            public boolean contains(Object o) { return col.contains(o); }
            public Iterator<V> iterator() {
                return new Iterator<V>() {
                    private final Iterator<V> it = col.iterator();
                    public boolean hasNext() { return it.hasNext(); }
                    public V next() { return it.next(); }
                    public void remove() { throwIfSealed(); it.remove(); }
                };
            }
            public Object[] toArray() { return col.toArray(); }
            public <T> T[] toArray(T[] a) { return col.toArray(a); }
            public boolean add(V v) { throwIfSealed(); return col.add(v); }
            public boolean remove(Object o) { throwIfSealed(); return col.remove(o); }
            public boolean containsAll(Collection<?> c) { return col.containsAll(c); }
            public boolean addAll(Collection<? extends V> c) { throwIfSealed(); return col.addAll(c); }
            public boolean retainAll(Collection<?> c) { throwIfSealed(); return col.retainAll(c); }
            public boolean removeAll(Collection<?> c) { throwIfSealed(); return col.removeAll(c); }
            public void clear() { throwIfSealed(); col.clear(); }
            public boolean equals(Object o) { return col.equals(o); }
            public int hashCode() { return col.hashCode(); }
        };
    }
}
