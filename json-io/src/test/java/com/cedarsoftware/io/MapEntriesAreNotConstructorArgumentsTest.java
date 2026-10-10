package com.cedarsoftware.io;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A map's JSON entries are its contents, not its constructor's arguments. A map class was built by matching them to
 * constructor parameters -- by name, or (a class compiled without parameter names, as java-util's LRUCache) by
 * position -- so {"capacity":7,"b":2} read as a class with a (capacity) constructor was built with capacity 7, and
 * {"a":7,"b":2} read as an LRUCache was a cache of capacity 7. It is now built with no arguments, as a map read for
 * an unknown type always was, and filled with its entries afterward.
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
class MapEntriesAreNotConstructorArgumentsTest {

    /** The constructors a cache has: none, and a capacity. */
    public static class Sized<K, V> extends LinkedHashMap<K, V> {
        public final String madeBy;

        public Sized() {
            madeBy = "()";
        }

        public Sized(int capacity) {
            madeBy = "(" + capacity + ")";
        }
    }

    /** A map class whose only constructor needs a positive bound. */
    public static class Bounded<K, V> extends LinkedHashMap<K, V> {
        public final int bound;

        public Bounded(int bound) {
            if (bound < 1) {
                throw new IllegalArgumentException("bound must be at least 1");
            }
            this.bound = bound;
        }
    }

    @Test
    void aMapIsBuiltWithoutItsEntries() {
        // an entry named as the constructor's parameter
        Sized<?, ?> plain = JsonIo.toJava("{\"capacity\":7,\"b\":2}", null).asClass(Sized.class);
        assertEquals("()", plain.madeBy);
        assertEquals(7L, plain.get("capacity"));
        assertEquals(2, plain.size());

        Sized<String, Integer> sized = new Sized<>();
        sized.put("capacity", 7);
        sized.put("b", 2);
        Sized<?, ?> back = JsonIo.toJava(JsonIo.toJson(sized, null), null).asClass(null);
        assertEquals("()", back.madeBy);
        assertEquals(sized, back);

        Sized<?, ?> fromToon = JsonIo.fromToon("capacity: 7\nb: 2", null).asClass(Sized.class);
        assertEquals("()", fromToon.madeBy);
        assertEquals(2, fromToon.size());
    }

    /** One no constructor of which takes json-io's defaults is still built, as before, and holds its entries. */
    @Test
    void aMapThatNeedsAnArgumentIsStillBuilt() {
        Bounded<?, ?> bounded = JsonIo.toJava("{\"a\":7,\"b\":2}", null).asClass(Bounded.class);
        assertEquals(2, bounded.size());
        assertEquals(2L, bounded.get("b"));
    }
}
