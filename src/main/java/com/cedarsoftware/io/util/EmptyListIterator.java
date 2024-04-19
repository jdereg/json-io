package com.cedarsoftware.io.util;
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
public class EmptyListIterator<T> implements ListIterator<T> {

    /**
     * Returns {@code false} as there are no elements in any direction.
     */
    public boolean hasNext() {
        return false;
    }

    /**
     * Throws NoSuchElementException because there are no elements to return.
     */
    public T next() {
        throw new NoSuchElementException("No elements to iterate.");
    }

    /**
     * Returns {@code false} as there are no elements in any direction.
     */
    public boolean hasPrevious() {
        return false;
    }

    /**
     * Throws NoSuchElementException because there are no elements to return.
     */
    public T previous() {
        throw new NoSuchElementException("No elements to iterate.");
    }

    /**
     * Throws UnsupportedOperationException because this operation
     * is not supported by the empty list iterator.
     */
    public void remove() {
        throw new UnsupportedOperationException("Remove operation is not supported.");
    }

    /**
     * Throws UnsupportedOperationException because this operation
     * is not supported by the empty list iterator.
     */
    public void set(T e) {
        throw new UnsupportedOperationException("Set operation is not supported.");
    }

    /**
     * Throws UnsupportedOperationException because this operation
     * is not supported by the empty list iterator.
     */
    public void add(T e) {
        throw new UnsupportedOperationException("Add operation is not supported.");
    }

    /**
     * Always returns 0 because there are no elements.
     */
    public int nextIndex() {
        return 0;
    }

    /**
     * Always returns -1 because there are no elements.
     */
    public int previousIndex() {
        return -1;
    }
}
