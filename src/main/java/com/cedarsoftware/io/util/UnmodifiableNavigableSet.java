package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * UnmodifiableNavigableSet provides a toggle between a mutable and immutable navigable set.
 * The set can be sealed to prevent further modifications.
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
    private boolean sealed = false;

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

    private void checkIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This set has been sealed and is now immutable");
        }
    }

    public boolean add(T e) {
        checkIfSealed();
        return navigableSet.add(e);
    }

    public boolean addAll(Collection<? extends T> c) {
        checkIfSealed();
        return navigableSet.addAll(c);
    }

    public void clear() {
        checkIfSealed();
        navigableSet.clear();
    }

    public boolean remove(Object o) {
        checkIfSealed();
        return navigableSet.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        checkIfSealed();
        return navigableSet.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        checkIfSealed();
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
        checkIfSealed();
        return navigableSet.pollFirst();
    }

    public T pollLast() {
        checkIfSealed();
        return navigableSet.pollLast();
    }

    public Iterator<T> iterator() {
        return navigableSet.iterator();
    }

    public NavigableSet<T> descendingSet() {
        return navigableSet.descendingSet();
    }

    public Iterator<T> descendingIterator() {
        return navigableSet.descendingIterator();
    }

    public SortedSet<T> subSet(T fromElement, T toElement) {
        return navigableSet.subSet(fromElement, true, toElement, false);
    }

    public NavigableSet<T> subSet(T fromElement, boolean fromInclusive, T toElement, boolean toInclusive) {
        return navigableSet.subSet(fromElement, fromInclusive, toElement, toInclusive);
    }

    public NavigableSet<T> headSet(T toElement, boolean inclusive) {
        return navigableSet.headSet(toElement, inclusive);
    }

    public SortedSet<T> headSet(T toElement) {
        return navigableSet.headSet(toElement, false);  // exclusive
    }

    public NavigableSet<T> tailSet(T fromElement, boolean inclusive) {
        return navigableSet.tailSet(fromElement, inclusive);
    }

    public SortedSet<T> tailSet(T fromElement) {
        return navigableSet.tailSet(fromElement, true);  // inclusive
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
