package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

/**
 * NOTE: Please do not reformat this code. This is a very lambda-like implementation
 * and it makes it easy to see over all structure.  The individual methods are trivial
 * because this is about APIs and delegating.
 *  <br><br>
 * UnmodifiableNavigableMap provides a NavigableMap that can be 'sealed' and 'unsealed'.
 * When sealed, the map is read-only. Before sealing, it can be modified.
 * The iterator, keySet, entrySet, and navigableKeySet return views that honor the
 * original Map's sealed state.
 *
 * @author John DeRegnaucourt
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
public class UnmodifiableNavigableMap<K, V> implements NavigableMap<K, V> {
    private final NavigableMap<K, V> map;
    private final Supplier<Boolean> sealedSupplier;

    public UnmodifiableNavigableMap(Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.map = new ConcurrentSkipListMap<>();
    }
    public UnmodifiableNavigableMap(SortedMap<K, V> map, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.map = new ConcurrentSkipListMap<>(map);
    }
    public UnmodifiableNavigableMap(NavigableMap<K, V> map, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.map = map;
    }

    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This map has been sealed and is now immutable");
        }
    }

    // Immutable APIs
    public boolean equals(Object o) { return map.equals(o); }
    public int hashCode() { return map.hashCode(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public boolean containsKey(Object key) { return map.containsKey(key); }
    public boolean containsValue(Object value) { return map.containsValue(value); }
    public int size() { return map.size(); }
    public V get(Object key) { return map.get(key); }
    public Comparator<? super K> comparator() { return map.comparator(); }
    public K firstKey() { return map.firstKey(); }
    public K lastKey() { return map.lastKey(); }
    public Set<K> keySet() { return new UnmodifiableSet<>(map.keySet(), sealedSupplier); }
    public Collection<V> values() { return new UnmodifiableList<>(new ArrayList<>(map.values()), sealedSupplier); }
    public Set<Entry<K, V>> entrySet() { return new UnmodifiableSet<>(map.entrySet(), sealedSupplier); }
    public Map.Entry<K, V> lowerEntry(K key) { return map.lowerEntry(key); }
    public K lowerKey(K key) { return map.lowerKey(key); }
    public Map.Entry<K, V> floorEntry(K key) { return map.floorEntry(key); }
    public K floorKey(K key) { return map.floorKey(key); }
    public Map.Entry<K, V> ceilingEntry(K key) { return map.ceilingEntry(key); }
    public K ceilingKey(K key) { return map.ceilingKey(key); }
    public Map.Entry<K, V> higherEntry(K key) { return map.higherEntry(key); }
    public K higherKey(K key) { return map.higherKey(key); }
    public Map.Entry<K, V> firstEntry() { return map.firstEntry(); }
    public Map.Entry<K, V> lastEntry() { return map.lastEntry(); }
    public NavigableMap<K, V> descendingMap() { return new UnmodifiableNavigableMap<>(map.descendingMap(), sealedSupplier); }
    public NavigableSet<K> navigableKeySet() { return new UnmodifiableNavigableSet<>(map.navigableKeySet(), sealedSupplier); }
    public NavigableSet<K> descendingKeySet() { return new UnmodifiableNavigableSet<>(map.descendingKeySet(), sealedSupplier); }
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return new UnmodifiableNavigableMap<>(map.subMap(fromKey, fromInclusive, toKey, toInclusive), sealedSupplier);
    }
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new UnmodifiableNavigableMap<>(map.headMap(toKey, inclusive), sealedSupplier);
    }
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new UnmodifiableNavigableMap<>(map.tailMap(fromKey, inclusive), sealedSupplier);
    }
    public SortedMap<K, V> subMap(K fromKey, K toKey) { return subMap(fromKey, true, toKey, false); }
    public SortedMap<K, V> headMap(K toKey) { return headMap(toKey, false); }
    public SortedMap<K, V> tailMap(K fromKey) { return tailMap(fromKey, true); }

    // Mutable APIs
    public Map.Entry<K, V> pollFirstEntry() { throwIfSealed(); return map.pollFirstEntry(); }
    public Map.Entry<K, V> pollLastEntry() { throwIfSealed(); return map.pollLastEntry(); }
    public V put(K key, V value) { throwIfSealed(); return map.put(key, value); }
    public V remove(Object key) { throwIfSealed(); return map.remove(key); }
    public void putAll(Map<? extends K, ? extends V> m) { throwIfSealed(); map.putAll(m); }
    public void clear() { throwIfSealed(); map.clear(); }
}