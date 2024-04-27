package com.cedarsoftware.io.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * UnmodifiableList provides a toggle between a mutable and immutable list.
 * The list can be sealed to prevent further modifications.
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
public class UnmodifiableList<T> implements List<T>, Unmodifiable {
    private final List<T> list = new ArrayList<>();
    private boolean sealed = false;

    public UnmodifiableList() {
    }
    
    public UnmodifiableList(List<T> items) {
        list.addAll(items);
    }

    public void seal() {
        sealed = true;
    }

    public void unseal() {
        sealed = false;
    }

    private void throwIfSealed() {
        if (sealed) {
            throw new UnsupportedOperationException("This list has been sealed and is now immutable");
        }
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public boolean contains(Object o) {
        return list.contains(o);
    }

    public Object[] toArray() {
        return list.toArray();
    }

    public <T1> T1[] toArray(T1[] a) {
        return list.toArray(a);
    }

    public boolean add(T t) {
        throwIfSealed();
        return list.add(t);
    }

    public boolean remove(Object o) {
        throwIfSealed();
        return list.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return list.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c) {
        throwIfSealed();
        return list.addAll(c);
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        throwIfSealed();
        return list.addAll(index, c);
    }

    public boolean removeAll(Collection<?> c) {
        throwIfSealed();
        return list.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        throwIfSealed();
        return list.retainAll(c);
    }

    public void clear() {
        throwIfSealed();
        list.clear();
    }

    public T get(int index) {
        return list.get(index);
    }

    public T set(int index, T element) {
        throwIfSealed();
        return list.set(index, element);
    }

    public void add(int index, T element) {
        throwIfSealed();
        list.add(index, element);
    }

    public T remove(int index) {
        throwIfSealed();
        return list.remove(index);
    }

    public int indexOf(Object o) {
        return list.indexOf(o);
    }

    public int lastIndexOf(Object o) {
        return list.lastIndexOf(o);
    }

    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private final Iterator<T> i = list.iterator();

            public boolean hasNext() {
                return i.hasNext();
            }

            public T next() {
                return i.next();
            }

            public void remove() {
                throwIfSealed();
                i.remove();
            }
        };
    }

    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    public ListIterator<T> listIterator(final int index) {
        return new ListIterator<T>() {
            private final ListIterator<T> i = list.listIterator(index);

            public boolean hasNext() {
                return i.hasNext();
            }

            public T next() {
                return i.next();
            }

            public boolean hasPrevious() {
                return i.hasPrevious();
            }

            public T previous() {
                return i.previous();
            }

            public int nextIndex() {
                return i.nextIndex();
            }

            public int previousIndex() {
                return i.previousIndex();
            }

            public void remove() {
                throwIfSealed();
                i.remove();
            }

            public void set(T e) {
                throwIfSealed();
                i.set(e);
            }

            public void add(T e) {
                throwIfSealed();
                i.add(e);
            }
        };
    }

    public List<T> subList(int fromIndex, int toIndex) {
        UnmodifiableList<T> sublist = new UnmodifiableList<>(list.subList(fromIndex, toIndex));
        sublist.sealed = this.sealed;  // Set the sealed state of the sublist to match the original list
        return sublist;
    }
}