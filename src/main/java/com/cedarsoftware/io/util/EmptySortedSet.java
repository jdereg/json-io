package com.cedarsoftware.io.util;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.SortedSet;

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
public class EmptySortedSet<E> extends EmptySet<E> implements SortedSet<E> {
    public Comparator<? super E> comparator() {
        return null;
    }

    public SortedSet<E> subSet(E fromElement, E toElement) {
        return new EmptySortedSet<>();
    }

    public SortedSet<E> headSet(E toElement) {
        return new EmptySortedSet<>();
    }

    public SortedSet<E> tailSet(E fromElement) {
        return new EmptySortedSet<>();
    }

    public E first() {
        throw new NoSuchElementException("This is an EmptySortedSet, first() will always return null.");
    }

    public E last() {
        throw new NoSuchElementException("This is an EmptySortedSet, last() will always return null.");
    }
}
