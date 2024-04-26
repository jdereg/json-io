package com.cedarsoftware.io.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * UnmodifiableSet provides a toggle between a mutable and immutable set.
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
public class UnmodifiableSet<T> implements Set<T>, Unmodifiable {
    private final Set<T> set = new LinkedHashSet<>();
    private boolean sealed = false;

    public UnmodifiableSet() {
    }

    public UnmodifiableSet(Collection<T> items) {
        set.addAll(items);
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

    public int size() {
        return set.size();
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }

    public boolean contains(Object o) {
        return set.contains(o);
    }

    public Iterator<T> iterator() {
        return set.iterator();  // Direct delegation
    }

    public Object[] toArray() {
        return set.toArray();
    }

    public <T1> T1[] toArray(T1[] a) {
        return set.toArray(a);
    }

    public boolean add(T t) {
        throwIfSealed();
        return set.add(t);
    }

    public boolean remove(Object o) {
        throwIfSealed();
        return set.remove(o);
    }

    public boolean containsAll(Collection<?> c) {
        return set.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c) {
        throwIfSealed();
        return set.addAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        throwIfSealed();
        return set.retainAll(c);
    }

    public boolean removeAll(Collection<?> c) {
        throwIfSealed();
        return set.removeAll(c);
    }

    public void clear() {
        throwIfSealed();
        set.clear();
    }

    public boolean equals(Object o) {
        return o == this || set.equals(o);
    }

    public int hashCode() {
        return set.hashCode();
    }
}
