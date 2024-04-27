package com.cedarsoftware.io.util;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

/**
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
public class UnmodifiableNavigableMap<K, V> implements NavigableMap<K, V>, Unmodifiable {
    private final NavigableMap<K, V> map;
    private volatile boolean sealed = false;

    public UnmodifiableNavigableMap() { this.map = new TreeMap<>(); }
    public UnmodifiableNavigableMap(NavigableMap<K, V> map) { this.map = new TreeMap<>(map); }
    public void seal() { sealed = true; }
    public void unseal() { sealed = false; }
    private void throwIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This map has been sealed and is now immutable");
        }
    }

    public Comparator<? super K> comparator() { return map.comparator(); }
    public K firstKey() { return map.firstKey(); }
    public K lastKey() { return map.lastKey(); }
    public Set<K> keySet() { return createSealHonoringNavigableSet((NavigableSet<K>)map.keySet()); }
    public Collection<V> values() { return createSealHonoringCollection(map.values()); }
    public Set<Entry<K, V>> entrySet() { return (Set<Entry<K, V>>) createSealHonoringSet((Set<K>) map.entrySet()); }
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
    public Map.Entry<K, V> pollFirstEntry() { throwIfSealed(); return map.pollFirstEntry(); }
    public Map.Entry<K, V> pollLastEntry() { throwIfSealed(); return map.pollLastEntry(); }
    public NavigableMap<K, V> descendingMap() { return createSealHonoringNavigableMap(map.descendingMap()); }
    public NavigableSet<K> navigableKeySet() { return createSealHonoringNavigableSet(map.navigableKeySet()); }
    public NavigableSet<K> descendingKeySet() { return createSealHonoringNavigableSet(map.descendingKeySet()); }
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        return createSealHonoringNavigableMap(map.subMap(fromKey, fromInclusive, toKey, toInclusive));
    }
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) { return createSealHonoringNavigableMap(map.headMap(toKey, inclusive)); }
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) { return createSealHonoringNavigableMap(map.tailMap(fromKey, inclusive)); }
    public SortedMap<K, V> subMap(K fromKey, K toKey) { return subMap(fromKey, true, toKey, false); }
    public SortedMap<K, V> headMap(K toKey) { return headMap(toKey, false); }
    public SortedMap<K, V> tailMap(K fromKey) { return tailMap(fromKey, true); }
    public boolean equals(Object o) { return map.equals(o); }
    public int hashCode() { return map.hashCode(); }
    public V get(Object key) { return map.get(key); }
    public V put(K key, V value) { throwIfSealed(); return map.put(key, value); }
    public V remove(Object key) { throwIfSealed(); return map.remove(key); }
    public void putAll(Map<? extends K, ? extends V> m) { throwIfSealed(); map.putAll(m); }
    public void clear() { throwIfSealed(); map.clear(); }
    public boolean containsKey(Object key) { return map.containsKey(key); }
    public boolean containsValue(Object value) { return map.containsValue(value); }
    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }

    private UnmodifiableNavigableMap<K, V> createSealHonoringNavigableMap(NavigableMap<K, V> original) {
        return new UnmodifiableNavigableMap<K, V>(original) {
            public void throwIfSealed() { UnmodifiableNavigableMap.this.throwIfSealed(); }
            public void seal() { UnmodifiableNavigableMap.this.seal(); }
            public void unseal() { UnmodifiableNavigableMap.this.unseal(); }
        };
    }

    interface Combo<V> extends Collection<V>, Unmodifiable { }
    private Collection<V> createSealHonoringCollection(Collection<V> col) {
        return new Combo<V>()  {
            public void seal() { UnmodifiableNavigableMap.this.seal(); }    // allow sealing from view to flip main
            public void unseal() { UnmodifiableNavigableMap.this.unseal(); }
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
            public boolean add(V o) { throwIfSealed(); return col.add(o); }
            public boolean remove(Object o) { throwIfSealed(); return col.remove(o); }
            public boolean addAll(Collection c) { throwIfSealed(); return col.addAll(c); }
            public void clear() { throwIfSealed(); col.clear(); }
            public boolean retainAll(Collection c) { throwIfSealed(); return col.retainAll(c); }
            public boolean removeAll(Collection c) { throwIfSealed(); return col.removeAll(c); }
            public boolean containsAll(Collection c) { return col.containsAll(c); }
            public Object[] toArray(Object[] a) { return col.toArray(a); }
            public boolean equals(Object o) { return col.equals(o); }
            public int hashCode() { return col.hashCode(); }
        };
    }

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

    private NavigableSet<K> createSealHonoringNavigableSet(NavigableSet<K> set) {
        return new NavigableSet<K>() {
            public int size() { return set.size(); }
            public boolean isEmpty() { return set.isEmpty(); }
            public boolean contains(Object o) { return set.contains(o); }
            public Iterator<K> iterator() {
                return new Iterator<K>() {
                    private final Iterator<K> it = set.iterator();
                    public boolean hasNext() { return it.hasNext(); }
                    public K next() { return it.next(); }
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
            public K lower(K k) { return set.lower(k); }
            public K floor(K k) { return set.floor(k); }
            public K ceiling(K k) { return set.ceiling(k); }
            public K higher(K k) { return set.higher(k); }
            public K pollFirst() { throwIfSealed(); return set.pollFirst(); }
            public K pollLast() { throwIfSealed(); return set.pollLast(); }
            public NavigableSet<K> descendingSet() { return createSealHonoringNavigableSet(set.descendingSet()); }
            public Iterator<K> descendingIterator() {
                return new Iterator<K>() {
                    private final Iterator<K> it = set.descendingIterator();
                    public boolean hasNext() { return it.hasNext(); }
                    public K next() { return it.next(); }
                    public void remove() { throwIfSealed(); it.remove(); }
                };
            }
            public NavigableSet<K> subSet(K fromElement, boolean fromInclusive, K toElement, boolean toInclusive) {
                return createSealHonoringNavigableSet(set.subSet(fromElement, fromInclusive, toElement, toInclusive));
            }
            public NavigableSet<K> headSet(K toElement, boolean inclusive) { return createSealHonoringNavigableSet(set.headSet(toElement, inclusive)); }
            public NavigableSet<K> tailSet(K fromElement, boolean inclusive) { return createSealHonoringNavigableSet(set.tailSet(fromElement, inclusive)); }
            public SortedSet<K> subSet(K fromElement, K toElement) { return subSet(fromElement, true, toElement, false); }
            public SortedSet<K> headSet(K toElement) { return headSet(toElement, false); }
            public SortedSet<K> tailSet(K fromElement) { return tailSet(fromElement, true); }
            public Comparator<? super K> comparator() { return set.comparator(); }
            public K first() { return set.first(); }
            public K last() { return set.last(); }
            public boolean equals(Object o) { return set.equals(o); }
            public int hashCode() { return set.hashCode(); }
        };
    }
}