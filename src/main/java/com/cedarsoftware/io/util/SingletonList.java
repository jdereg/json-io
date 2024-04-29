package com.cedarsoftware.io.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
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
public class SingletonList<T> implements List<T> {
    private static final Object UNINITIALIZED = new Object();
    private T element = (T) UNINITIALIZED;

    public SingletonList(T element) {
        this.element = element == null ? (T) UNINITIALIZED : element;
    }

    // Serialization support constructor (no arguments)
    public SingletonList() {
        super();
    }

    public int size() {
        return element == UNINITIALIZED ? 0 : 1;
    }

    public boolean isEmpty() {
        return element == UNINITIALIZED;
    }

    public boolean contains(Object o) {
        return element != UNINITIALIZED && element.equals(o);
    }

    public Iterator<T> iterator() {
        return listIterator();
    }

    public Object[] toArray() {
        return element == UNINITIALIZED ? new Object[0] : new Object[]{element};
    }

    public <T1> T1[] toArray(T1[] a) {
        if (element == UNINITIALIZED) {
            if (a.length > 0) {
                a[0] = null;
            }
            return a;
        }
        if (a.length > 0) {
            a[0] = (T1) element;
            if (a.length > 1) a[1] = null;
            return a;
        } else {
            T1[] newArray = (T1[]) Array.newInstance(a.getClass().getComponentType(), 1);
            newArray[0] = (T1) element;
            return newArray;
        }
    }

    public boolean add(T t) {
        if (element == UNINITIALIZED) {
            element = t;
            return true;
        } else {
            throw new UnsupportedOperationException("Cannot add item to singleton list");
        }
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Cannot remove item from singleton list");
    }

    public boolean containsAll(Collection<?> c) {
        if (c.size() == 1) {
            return contains(c.iterator().next());
        }
        return false;
    }

    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException("Cannot add items to SingletonList");
    }

    public boolean addAll(int index, Collection<? extends T> c) {
        throw new UnsupportedOperationException("Cannot add items to SingletonList");
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot remove items from SingletonList");
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot remove items from SingletonList");
    }

    public void clear() {
        throw new UnsupportedOperationException("Cannot clear SingletonList");
    }

    public T get(int index) {
        if (index != 0 || element == UNINITIALIZED) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        return element;
    }

    public T set(int index, T element) {
        if (index != 0) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }
        T oldElement = this.element;
        this.element = element;
        return oldElement;
    }

    public void add(int index, T element) {
        throw new UnsupportedOperationException("Cannot add items to SingletonList");
    }

    public T remove(int index) {
        throw new UnsupportedOperationException("Cannot remove items to SingletonList");
    }

    public int indexOf(Object o) {
        return element != UNINITIALIZED && element.equals(o) ? 0 : -1;
    }

    public int lastIndexOf(Object o) {
        return indexOf(o);
    }

    public ListIterator<T> listIterator() {
        return listIterator(0);
    }

    public ListIterator<T> listIterator(final int index) {
        if (index != 0 && (index != 1 || element == UNINITIALIZED)) {
            throw new IndexOutOfBoundsException("Index: " + index);
        }

        return new ListIterator<T>() {
            private int cursor = index;

            public boolean hasNext() {
                return cursor == 0 && element != UNINITIALIZED;
            }

            public T next() {
                if (cursor != 0 || element == UNINITIALIZED) {
                    throw new NoSuchElementException();
                }
                cursor = 1;  // Move cursor forward after returning element
                return element;
            }

            public boolean hasPrevious() {
                return cursor == 1;
            }

            public T previous() {
                if (cursor != 1) {
                    throw new NoSuchElementException();
                }
                cursor = 0;  // Move cursor back after returning element
                return element;
            }

            public int nextIndex() {
                return cursor;
            }

            public int previousIndex() {
                return cursor - 1;
            }

            public void remove() {
                throw new UnsupportedOperationException("Cannot remove items from SingletonList");
            }

            public void set(T e) {
                if (cursor != 1) {
                    throw new IllegalStateException();
                }
                SingletonList.this.element = e;
            }

            public void add(T e) {
                throw new UnsupportedOperationException("Cannot add items to SingletonList");
            }
        };
    }

    public List<T> subList(int fromIndex, int toIndex) {
        if (fromIndex == 0 && toIndex == 0 || element == UNINITIALIZED) {
            return new ArrayList<>();
        }
        if (fromIndex != 0 || toIndex != 1) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + ", toIndex: " + toIndex);
        }
        return Collections.singletonList(element);
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof List)) {
            return false;
        }
        List<?> that = (List<?>) o;
        return this.size() == that.size() && containsAll(that);
    }

    public int hashCode() {
        return element != UNINITIALIZED ? Arrays.hashCode(new Object[]{element}) : 1;
    }
}