package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * UnmodifiableNavigableMap provides a toggle between a mutable and immutable navigable map.
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
public class UnmodifiableNavigableMap<K, V> implements NavigableMap<K, V>, Unmodifiable {
    private final NavigableMap<K, V> map;
    private boolean sealed = false;

    public UnmodifiableNavigableMap() {
        this.map = new TreeMap<>();
    }

    public UnmodifiableNavigableMap(Map<K, V> map) {
        this.map = new TreeMap<>(map);
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

    public Comparator<? super K> comparator() {
        return map.comparator();
    }

    public K firstKey() {
        return map.firstKey();
    }

    public K lastKey() {
        return map.lastKey();
    }

    public Entry<K, V> lowerEntry(K key) {
        return map.lowerEntry(key);
    }

    public K lowerKey(K key) {
        return map.lowerKey(key);
    }

    public Entry<K, V> floorEntry(K key) {
        return map.floorEntry(key);
    }

    public K floorKey(K key) {
        return map.floorKey(key);
    }

    public Entry<K, V> ceilingEntry(K key) {
        return map.ceilingEntry(key);
    }

    public K ceilingKey(K key) {
        return map.ceilingKey(key);
    }

    public Entry<K, V> higherEntry(K key) {
        return map.higherEntry(key);
    }

    public K higherKey(K key) {
        return map.higherKey(key);
    }

    public Entry<K, V> firstEntry() {
        return map.firstEntry();
    }

    public Entry<K, V> lastEntry() {
        return map.lastEntry();
    }

    public Entry<K, V> pollFirstEntry() {
        throwIfSealed();
        return map.pollFirstEntry();
    }

    public Entry<K, V> pollLastEntry() {
        throwIfSealed();
        return map.pollLastEntry();
    }

    public NavigableMap<K, V> descendingMap() {
        return Collections.unmodifiableNavigableMap(map.descendingMap());
    }

    public NavigableSet<K> navigableKeySet() {
        return Collections.unmodifiableNavigableSet(map.navigableKeySet());
    }

    public NavigableSet<K> descendingKeySet() {
        return Collections.unmodifiableNavigableSet(map.descendingKeySet());
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return Collections.unmodifiableNavigableMap(map.subMap(fromKey, fromInclusive, toKey, toInclusive));
    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return Collections.unmodifiableNavigableMap(map.headMap(toKey, inclusive));
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return Collections.unmodifiableNavigableMap(map.tailMap(fromKey, inclusive));
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return Collections.unmodifiableSortedMap(map.subMap(fromKey, toKey));
    }

    public SortedMap<K, V> headMap(K toKey) {
        return Collections.unmodifiableSortedMap(map.headMap(toKey));
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return Collections.unmodifiableSortedMap(map.tailMap(fromKey));
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
        return Collections.unmodifiableSet(map.keySet());
    }

    public Collection<V> values() {
        return Collections.unmodifiableCollection(map.values());
    }

    public Set<Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(map.entrySet());
    }
}
