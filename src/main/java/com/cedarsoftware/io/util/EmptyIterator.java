package com.cedarsoftware.io.util;

import java.util.Iterator;
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
public class EmptyIterator<T> implements Iterator<T> {

    /**
     * Returns {@code false} as there are no elements to iterate.
     *
     * @return {@code false} always.
     */
    public boolean hasNext() {
        return false;
    }

    /**
     * Throws NoSuchElementException because there are no elements to return.
     *
     * @return nothing.
     * @throws NoSuchElementException always thrown.
     */
    public T next() {
        throw new NoSuchElementException("No elements to iterate.");
    }

    /**
     * Throws UnsupportedOperationException because this operation
     * is not supported by the empty iterator.
     *
     * @throws UnsupportedOperationException always thrown.
     */
    public void remove() {
        throw new UnsupportedOperationException("Remove operation is not supported.");
    }
}
