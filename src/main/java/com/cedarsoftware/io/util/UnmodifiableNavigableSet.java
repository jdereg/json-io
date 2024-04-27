package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
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
public class UnmodifiableNavigableSet<T> implements NavigableSet<T>, Unmodifiable {
    private final NavigableSet<T> navigableSet;
    private volatile boolean sealed = false;

    public UnmodifiableNavigableSet() {
        navigableSet = new TreeSet<>();
    }

    public UnmodifiableNavigableSet(Comparator<? super T> comparator) {
        navigableSet = new TreeSet<>(comparator);
    }

    public UnmodifiableNavigableSet(Collection<? extends T> c) {
        this();
        addAll(c);
    }

    public UnmodifiableNavigableSet(SortedSet<T> s) {
        navigableSet = new TreeSet<>(s);
    }

    public void seal() {
        sealed = true;
    }

    public void unseal() {
        sealed = false;
    }

    private void throwIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This set has been sealed and is now immutable");
        }
    }

    private Iterator<T> createSealHonoringIterator(Iterator<T> iterator) {
        return new Iterator<T>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public T next() {
                return iterator.next();
            }

            public void remove() {
                throwIfSealed();
                iterator.remove();
            }
        };
    }

    private NavigableSet<T> createSealHonoringView(NavigableSet<T> set) {
        return new NavigableSet<T>() {
            public T lower(T e) { return set.lower(e); }
            public T floor(T e) { return set.floor(e); }
            public T ceiling(T e) { return set.ceiling(e); }
            public T higher(T e) { return set.higher(e); }
            public T pollFirst() { throwIfSealed(); return set.pollFirst(); }
            public T pollLast() { throwIfSealed(); return set.pollLast();}
            public int size() { return set.size(); }
            public boolean isEmpty() { return set.isEmpty(); }
            public boolean contains(Object o) { return set.contains(o); }
            public Iterator<T> iterator() { return createSealHonoringIterator(set.iterator()); }
            public Object[] toArray() { return set.toArray(); }
            public <T1> T1[] toArray(T1[] a) { return set.toArray(a); }
            public boolean add(T e) { throwIfSealed(); return set.add(e); }
            public boolean remove(Object o) { throwIfSealed(); return set.remove(o); }
            public boolean containsAll(Collection<?> c) { return set.containsAll(c); }
            public boolean addAll(Collection<? extends T> c) { throwIfSealed(); return set.addAll(c); }
            public boolean retainAll(Collection<?> c) { throwIfSealed(); return retainAll(c); }
            public boolean removeAll(Collection<?> c) { throwIfSealed(); return set.removeAll(c); }
            public void clear() { throwIfSealed(); set.clear(); }
            public NavigableSet<T> descendingSet() { return createSealHonoringView(set.descendingSet()); }
            public Iterator<T> descendingIterator() { return createSealHonoringIterator(set.descendingIterator()); }
            public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
                return createSealHonoringView(set.subSet(fromElement, fromInclusive, toElement, toInclusive));
            }
            public NavigableSet<T> headSet(T toElement, boolean inclusive) {
                return createSealHonoringView(set.headSet(toElement, inclusive));
            }
            public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
                return createSealHonoringView(set.tailSet(fromElement, inclusive));
            }
            public SortedSet<T> subSet(T fromElement, T toElement) {
                return createSealHonoringView(set.subSet(fromElement, true, toElement, false));
            }
            public SortedSet<T> headSet(T toElement) {
                return createSealHonoringView(set.headSet(toElement, false));
            }
            public SortedSet<T> tailSet(T fromElement) {
                return createSealHonoringView(set.tailSet(fromElement, true));
            }
            public Comparator<? super T> comparator() { return set.comparator(); }
            public T first() { return set.first(); }
            public T last() { return set.last(); }
            public boolean equals(Object o) { return o == this || set.equals(o); }
            public int hashCode() { return set.hashCode(); }
        };
    }

    public boolean add(T e) {
        throwIfSealed();
        return navigableSet.add(e);
    }

    public boolean addAll(Collection<? extends T> c) {
        throwIfSealed();
        return navigableSet.addAll(c);
    }

    public void clear() {
        throwIfSealed();
        navigableSet.clear();
    }

    public boolean remove(Object o) {
        throwIfSealed();
        return navigableSet.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        throwIfSealed();
        return navigableSet.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        throwIfSealed();
        return navigableSet.retainAll(c);
    }

    public T lower(T e) {
        return navigableSet.lower(e);
    }

    public T floor(T e) {
        return navigableSet.floor(e);
    }

    public T ceiling(T e) {
        return navigableSet.ceiling(e);
    }

    public T higher(T e) {
        return navigableSet.higher(e);
    }

    public T pollFirst() {
        throwIfSealed();
        return navigableSet.pollFirst();
    }

    public T pollLast() {
        throwIfSealed();
        return navigableSet.pollLast();
    }

    public Iterator<T> iterator() {
        return createSealHonoringIterator(navigableSet.iterator());
    }

    public Iterator<T> descendingIterator() {
        return createSealHonoringIterator(navigableSet.descendingIterator());
    }

    public NavigableSet<T> descendingSet() {
        return createSealHonoringView(navigableSet.descendingSet());
    }

    public SortedSet<T> subSet(T fromElement, T toElement) {
        return createSealHonoringView((NavigableSet<T>) navigableSet.subSet(fromElement, toElement));
    }

    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        return createSealHonoringView(navigableSet.subSet(fromElement, fromInclusive, toElement, toInclusive));
    }

    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return createSealHonoringView(navigableSet.headSet(toElement, inclusive));
    }

    public SortedSet<T> headSet(T toElement) {
        return createSealHonoringView((NavigableSet<T>) navigableSet.headSet(toElement));
    }

    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return createSealHonoringView(navigableSet.tailSet(fromElement, inclusive));
    }

    public SortedSet<T> tailSet(T fromElement) {
        return createSealHonoringView((NavigableSet<T>) navigableSet.tailSet(fromElement));
    }
    
    public Comparator<? super T> comparator() {
        return navigableSet.comparator();
    }

    public T first() {
        return navigableSet.first();
    }

    public T last() {
        return navigableSet.last();
    }

    public boolean contains(Object o) {
        return navigableSet.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return navigableSet.containsAll(c);
    }

    public boolean isEmpty() {
        return navigableSet.isEmpty();
    }

    public Object[] toArray() {
        return navigableSet.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return navigableSet.toArray(a);
    }

    public int size() {
        return navigableSet.size();
    }

    public boolean equals(Object o) {
        return o == this || navigableSet.equals(o);
    }

    public int hashCode() {
        return navigableSet.hashCode();
    }
}
