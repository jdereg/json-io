package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * NOTE: Please do not reformat this code. This is a very lambda-like implementation and it
 * makes it easy to see the overall structure.  The individual methods are trivial because
 * this is APIs, scope, and delegation.
 * <br><br>
 * UnmodifiableSet provides a toggle between a mutable and immutable set.
 * The set can be sealed to prevent further modifications.
 * <br><br>
 * @author John DeRegnaucourt
 *         <br>
 *         Copyright Cedar Software LLC
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
public class UnmodifiableSet<T> implements Set<T> {
    private final Set<T> set;
    private final Supplier<Boolean> sealedSupplier;

    public UnmodifiableSet(Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.set = new LinkedHashSet<>();
    }
    public UnmodifiableSet(Collection<T> items, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.set = new LinkedHashSet<>(items);
    }
    public UnmodifiableSet(Set<T> items, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        this.set = items;
    }

    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This set has been sealed and is now immutable");
        }
    }

    // Immutable APIs
    public int size() { return set.size(); }
    public boolean isEmpty() { return set.isEmpty(); }
    public boolean contains(Object o) { return set.contains(o); }
    public Object[] toArray() { return set.toArray(); }
    public <T1> T1[] toArray(T1[] a) { return set.toArray(a); }
    public boolean containsAll(Collection<?> col) { return set.containsAll(col); }
    public boolean equals(Object o) { return set.equals(o); }
    public int hashCode() { return set.hashCode(); }
    public Iterator<T> iterator() { return createSealHonoringIterator(set.iterator()); }

    // Mutable APIs
    public boolean add(T t) { throwIfSealed(); return set.add(t); }
    public boolean remove(Object o) { throwIfSealed(); return set.remove(o); }
    public boolean addAll(Collection<? extends T> col) { throwIfSealed(); return set.addAll(col); }
    public boolean removeAll(Collection<?> col) { throwIfSealed(); return set.removeAll(col); }
    public boolean retainAll(Collection<?> col) { throwIfSealed(); return set.retainAll(col); }
    public void clear() { throwIfSealed(); set.clear(); }

    private Iterator<T> createSealHonoringIterator(Iterator<T> iterator) {
        return new Iterator<T>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public T next() {
                T item = iterator.next();
                if (item instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                    return (T) new SealAwareEntry<>(entry, sealedSupplier);
                }
                return item;
            }
            public void remove() { throwIfSealed(); iterator.remove(); }
        };
    }

    // Must enforce immutability after the Map.Entry was "handed out" because
    // it could have been handed out when the map was unsealed or sealed.
    static class SealAwareEntry<K, V> implements Map.Entry<K, V> {
        private final Map.Entry<K, V> entry;
        private final Supplier<Boolean> sealedSupplier;

        SealAwareEntry(Map.Entry<K, V> entry, Supplier<Boolean> sealedSupplier) {
            this.entry = entry;
            this.sealedSupplier = sealedSupplier;
        }

        public K getKey() { return entry.getKey(); }
        public V getValue() { return entry.getValue(); }
        public V setValue(V value) {
            if (sealedSupplier.get()) {
                throw new UnsupportedOperationException("Cannot modify, set is sealed");
            }
            return entry.setValue(value);
        }

        public boolean equals(Object o) { return entry.equals(o); }
        public int hashCode() { return entry.hashCode(); }
    }
}