package com.cedarsoftware.io.util;

import java.util.Collections;
import java.util.Iterator;
import java.util.NavigableSet;

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
public class EmptyNavigableSet<E> extends EmptySortedSet<E> implements NavigableSet<E> {
    public E lower(E e) {
        return null;
    }

    public E floor(E e) {
        return null;
    }

    public E ceiling(E e) {
        return null;
    }

    public E higher(E e) {
        return null;
    }

    public E pollFirst() {
        return null;
    }

    public E pollLast() {
        return null;
    }

    public NavigableSet<E> descendingSet() {
        return new EmptyNavigableSet<>();
    }

    public Iterator<E> descendingIterator() {
        return Collections.emptyIterator();
    }

    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        return new EmptyNavigableSet<>();
    }

    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        return new EmptyNavigableSet<>();
    }

    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        return new EmptyNavigableSet<>();
    }
}
