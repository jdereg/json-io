package com.cedarsoftware.io.util;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * NOTE: Please do not reformat this code. This is a very lambda-like implementation
 * and it makes it easy to see over all structure.  The individual methods are trivial
 * because this is about APIs and delegating.
 * <br><br>
 * UnmodifiableNavigableSet provides a toggle between a mutable and immutable navigable set.
 * The set can be sealed to prevent further modifications. Iterators and views (and iterators
 * on views, and so on) respect the orginal enclosing Sets sealed state.
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
public class UnmodifiableNavigableSet<T> implements NavigableSet<T> {
    private final NavigableSet<T> navigableSet;
    private final Supplier<Boolean> sealedSupplier;

    public UnmodifiableNavigableSet(Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        navigableSet = new TreeSet<>();
    }
    public UnmodifiableNavigableSet(Comparator<? super T> comparator, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        navigableSet = new TreeSet<>(comparator);
    }
    public UnmodifiableNavigableSet(Collection<? extends T> col, Supplier<Boolean> sealedSupplier) {
        this(sealedSupplier);
        addAll(col);
    }
    public UnmodifiableNavigableSet(SortedSet<T> set, Supplier<Boolean> sealedSupplier) {
        this.sealedSupplier = sealedSupplier;
        navigableSet = new TreeSet<>(set);
    }

    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This set has been sealed and is now immutable");
        }
    }

    // Immutable APIs
    public boolean equals(Object o) { return o == this || navigableSet.equals(o); }
    public int hashCode() { return navigableSet.hashCode(); }
    public int size() { return navigableSet.size(); }
    public boolean isEmpty() { return navigableSet.isEmpty(); }
    public boolean contains(Object o) { return navigableSet.contains(o); }
    public boolean containsAll(Collection<?> col) { return navigableSet.containsAll(col);}
    public Comparator<? super T> comparator() { return navigableSet.comparator(); }
    public T first() { return navigableSet.first(); }
    public T last() { return navigableSet.last(); }
    public Object[] toArray() { return navigableSet.toArray(); }
    public <T> T[] toArray(T[] a) { return navigableSet.toArray(a); }
    public T lower(T e) { return navigableSet.lower(e); }
    public T floor(T e) { return navigableSet.floor(e); }
    public T ceiling(T e) { return navigableSet.ceiling(e); }
    public T higher(T e) { return navigableSet.higher(e); }
    public Iterator<T> iterator() {
        return createSealHonoringIterator(navigableSet.iterator());
    }
    public Iterator<T> descendingIterator() {
        return createSealHonoringIterator(navigableSet.descendingIterator());
    }
    public NavigableSet<T> descendingSet() {
        return new UnmodifiableNavigableSet<>(navigableSet.descendingSet(), sealedSupplier);
    }
    public SortedSet<T> subSet(T fromElement, T toElement) {
        return new UnmodifiableNavigableSet<>(navigableSet.subSet(fromElement, toElement), sealedSupplier);
    }
    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        return new UnmodifiableNavigableSet<>(navigableSet.subSet(fromElement, fromInclusive, toElement, toInclusive), sealedSupplier);
    }
    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return new UnmodifiableNavigableSet<>(navigableSet.headSet(toElement, inclusive), sealedSupplier);
    }
    public SortedSet<T> headSet(T toElement) {
        return new UnmodifiableNavigableSet<>(navigableSet.headSet(toElement), sealedSupplier);
    }
    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return new UnmodifiableNavigableSet<>(navigableSet.tailSet(fromElement, inclusive), sealedSupplier);
    }
    public SortedSet<T> tailSet(T fromElement) {
        return new UnmodifiableNavigableSet<>(navigableSet.tailSet(fromElement), sealedSupplier);
    }

    // Mutable APIs
    public boolean add(T e) { throwIfSealed(); return navigableSet.add(e); }
    public boolean addAll(Collection<? extends T> col) { throwIfSealed(); return navigableSet.addAll(col); }
    public void clear() { throwIfSealed(); navigableSet.clear(); }
    public boolean remove(Object o) { throwIfSealed(); return navigableSet.remove(o); }
    public boolean removeAll(Collection<?> col) { throwIfSealed(); return navigableSet.removeAll(col); }
    public boolean retainAll(Collection<?> col) { throwIfSealed(); return navigableSet.retainAll(col); }
    public T pollFirst() { throwIfSealed(); return navigableSet.pollFirst(); }
    public T pollLast() { throwIfSealed(); return navigableSet.pollLast(); }

    private Iterator<T> createSealHonoringIterator(Iterator<T> iterator) {
        return new Iterator<T>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public T next() {
                // Doing a 'Solid' for Maps that use UnmodifiableNavigableSet for entrySet() implementation. Before just
                // blindly returning the entry, see if we are in sealed mode, and if so, return an ImmutableEntry
                // so that user cannot modify the entry via entry.setValue() API.
                T item = iterator.next();
                if (item instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
                    if (sealedSupplier.get()) {
                        return (T) new AbstractMap.SimpleImmutableEntry(entry.getKey(), entry.getValue());  // prevent .setValue() on the entry's value.
                    }
                }
                return item;
            }
            public void remove() { throwIfSealed(); iterator.remove(); }
        };
    }
}