package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

/**
 * This class provides an immutable, empty implementation of the NavigableMap interface.
 * It is designed to be a singleton as there is no internal state that varies between instances.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
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
public class EmptyNavigableMap<K, V> implements NavigableMap<K, V> {

    public Comparator<? super K> comparator() {
        return null;
    }

    public K firstKey() {
        throw new NoSuchElementException("The map is empty.");
    }

    public K lastKey() {
        throw new NoSuchElementException("The map is empty.");
    }

    public Entry<K, V> lowerEntry(K key) {
        return null;
    }

    public K lowerKey(K key) {
        return null;
    }

    public Entry<K, V> floorEntry(K key) {
        return null;
    }

    public K floorKey(K key) {
        return null;
    }

    public Entry<K, V> ceilingEntry(K key) {
        return null;
    }

    public K ceilingKey(K key) {
        return null;
    }

    public Entry<K, V> higherEntry(K key) {
        return null;
    }

    public K higherKey(K key) {
        return null;
    }

    public Entry<K, V> firstEntry() {
        return null;
    }

    public Entry<K, V> lastEntry() {
        return null;
    }

    public Entry<K, V> pollFirstEntry() {
        return null;
    }

    public Entry<K, V> pollLastEntry() {
        return null;
    }

    public NavigableMap<K, V> descendingMap() {
        return this;
    }

    public NavigableSet<K> navigableKeySet() {
        return Collections.emptyNavigableSet();
    }

    public NavigableSet<K> descendingKeySet() {
        return Collections.emptyNavigableSet();
    }

    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return this;
    }

    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return this;
    }

    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return this;
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return this.subMap(fromKey, true, toKey, false);
    }

    public SortedMap<K, V> headMap(K toKey) {
        return this.headMap(toKey, false);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return this.tailMap(fromKey, true);
    }

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
