package com.cedarsoftware.io.util;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
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
public class EmptySet<E> implements Set<E> {
    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return true;
    }

    public boolean contains(Object o) {
        return false;
    }

    public Iterator<E> iterator() {
        return Collections.emptyIterator();
    }

    public Object[] toArray() {
        return new Object[0];
    }

    public <T> T[] toArray(T[] a) {
        // Create a new array of the specified type (T[]) with length 0
        if (a.length == 0) {
            return a;
        }
        T[] result = (T[]) Array.newInstance(a.getClass().getComponentType(), 0);
        return result;
    }

    public boolean add(E e) {
        throw new UnsupportedOperationException("Cannot add element to an empty set");
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException("Cannot remove element from an empty set");
    }

    public boolean containsAll(Collection<?> c) {
        return false;
    }

    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException("Cannot add elements to an empty set");
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot retain elements from an empty set");
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("Cannot remove elements from an empty set");
    }

    public void clear() {
    }
}
