package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

/**
 * UnmodifiableList provides a List that can be 'sealed' and 'unsealed'. When sealed,
 * the List is read-only. The iterator, listIterator, and subList() return views that honor
 * the original List's sealed state, controlled by a Supplier.
 *
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
public class UnmodifiableList<T> implements List<T> {
    private final List<T> list;
    private final Supplier<Boolean> sealedSupplier;

    public UnmodifiableList(Supplier<Boolean> sealedSupplier) {
        this.list = new ArrayList<>();
        this.sealedSupplier = sealedSupplier;
    }

    public UnmodifiableList(List<T> items, Supplier<Boolean> sealedSupplier) {
        this.list = new ArrayList<>(items);
        this.sealedSupplier = sealedSupplier;
    }

    private void throwIfSealed() {
        if (sealedSupplier.get()) {
            throw new UnsupportedOperationException("This list has been sealed and is now immutable");
        }
    }

    public boolean add(T element) { throwIfSealed(); return list.add(element); }
    public T get(int index) { return list.get(index); }
    public T remove(int index) { throwIfSealed(); return list.remove(index); }
    public T set(int index, T element) { throwIfSealed(); return list.set(index, element); }
    public void add(int index, T element) { throwIfSealed(); list.add(index, element); }
    public boolean addAll(Collection<? extends T> c) { throwIfSealed(); return list.addAll(c); }
    public boolean addAll(int index, Collection<? extends T> c) { throwIfSealed(); return list.addAll(index, c); }
    public void clear() { throwIfSealed(); list.clear(); }
    public List<T> subList(int fromIndex, int toIndex) {
        return new UnmodifiableList<>(list.subList(fromIndex, toIndex), sealedSupplier);
    }
    public int size() { return list.size(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public boolean contains(Object o) { return list.contains(o); }
    public Object[] toArray() { return list.toArray(); }
    public <T1> T1[] toArray(T1[] a) { return list.toArray(a); }
    public boolean containsAll(Collection<?> c) { return list.containsAll(c); }
    public boolean removeAll(Collection<?> c) { throwIfSealed(); return list.removeAll(c); }
    public boolean retainAll(Collection<?> c) { throwIfSealed(); return list.retainAll(c); }
    public boolean remove(Object o) { throwIfSealed(); return list.remove(o); }
    public int indexOf(Object o) { return list.indexOf(o); }
    public int lastIndexOf(Object o) { return list.lastIndexOf(o); }

    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Iterator<T> it = list.iterator();
            public boolean hasNext() { return it.hasNext(); }
            public T next() { return it.next(); }
            public void remove() { throwIfSealed(); it.remove(); }
        };
    }

    public ListIterator<T> listIterator() {
        return createSealHonoringListIterator(list.listIterator());
    }

    public ListIterator<T> listIterator(final int index) {
        return createSealHonoringListIterator(list.listIterator(index));
    }

    private ListIterator<T> createSealHonoringListIterator(ListIterator<T> iterator) {
        return new ListIterator<T>() {
            public boolean hasNext() { return iterator.hasNext(); }
            public T next() { return iterator.next(); }
            public boolean hasPrevious() { return iterator.hasPrevious(); }
            public T previous() { return iterator.previous(); }
            public int nextIndex() { return iterator.nextIndex(); }
            public int previousIndex() { return iterator.previousIndex(); }
            public void remove() { throwIfSealed(); iterator.remove(); }
            public void set(T e) { throwIfSealed(); iterator.set(e); }
            public void add(T e) { throwIfSealed(); iterator.add(e); }
        };
    }
}
