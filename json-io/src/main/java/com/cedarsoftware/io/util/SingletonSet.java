package com.cedarsoftware.io.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

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
public class SingletonSet<T> implements Set<T> {
    private static final Object UNINITIALIZED = new Object();
    private T element = (T)UNINITIALIZED;

    public SingletonSet(T element) {
        this.element = element == null ? (T) UNINITIALIZED : element;
    }

    // Serialization support constructor (no arguments)
    public SingletonSet() {
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
        return new Iterator<T>() {
            private boolean hasNext = element != UNINITIALIZED;

            public boolean hasNext() {
                return hasNext;
            }

            public T next() {
                if (!hasNext) throw new java.util.NoSuchElementException();
                hasNext = false;
                return element;
            }
        };
    }

    public Object[] toArray() {
        return element == UNINITIALIZED ? new Object[0] : new Object[] { element };
    }

    public <T1> T1[] toArray(T1[] a) {
        if (element == UNINITIALIZED) return a;
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
            throw new UnsupportedOperationException("Cannot add item to singleton set");
        }
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Cannot remove item from singleton set");
    }

    public boolean containsAll(Collection<?> c) {
        return c.size() == 1 && c.contains(element);
    }

    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException("Cannot add items to singleton set");
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot modify singleton set");
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot remove items from singleton set");
    }

    public void clear() {
        throw new UnsupportedOperationException("Cannot clear singleton set");
    }
}
