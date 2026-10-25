package com.cedarsoftware.io;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * In Maps mode an object whose {@code @type} names a class on the classpath is not built, at the root as anywhere: its
 * class types its fields (an {@code int} field an Integer, a date field a date, an enum field its constant), as it
 * has since 2017, without an instance. Building one ran its constructor and static initializer and called its
 * {@link ClassFactory}, and a class that could not be built -- a constructor that validates -- failed the read.
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
class MapsModeRootNotBuiltTest {
    /** Set by the classes below: when one is initialized and when one is constructed. */
    static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    static final AtomicInteger CONSTRUCTED = new AtomicInteger();

    public enum Level { LOW, HIGH }

    public static class Counted {
        static {
            INITIALIZED.set(true);
        }

        public int age;
        public LocalDate born;
        public Level level;

        public Counted() {
            CONSTRUCTED.incrementAndGet();
        }
    }

    /** Its only constructor refuses what Maps mode would pass it. */
    public static class Strict {
        public final String number;
        public int count;

        public Strict(String number) {
            if (number == null || !number.startsWith("P-")) {
                throw new IllegalArgumentException("number must start with P-");
            }
            this.number = number;
        }
    }

    private static final String FIELDS = "\"age\":3,\"born\":\"2024-01-15\",\"level\":\"HIGH\"";

    private static void assertTyped(Map<?, ?> read) {
        assertEquals(3, read.get("age"));       // typed by its class, as before
        assertEquals(LocalDate.of(2024, 1, 15), read.get("born"));
        assertEquals(Level.HIGH, read.get("level"));
    }

    @Test
    void anObjectOfAClassIsNotBuilt() {
        int before = CONSTRUCTED.get();
        String json = "{\"@type\":\"" + Counted.class.getName() + "\"," + FIELDS + "}";
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
        assertInstanceOf(JsonObject.class, read);
        assertTyped(read);
        assertEquals(before, CONSTRUCTED.get());    // its constructor not run
        assertFalse(INITIALIZED.get());             // nor its static initializer
    }

    @Test
    void aClassThatCannotBeBuiltIsRead() {
        // It failed the read: "Unable to instantiate: ..Strict - Most recent error: number must start with P-"
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps("{\"@type\":\"" + Strict.class.getName() + "\",\"number\":\"X\",\"count\":2}", null)
                .asClass(null);
        assertEquals("X", read.get("number"));
        assertEquals(2, read.get("count"));
    }

    /** An object of no class holding @items is the array it holds, as before. */
    @Test
    void anUntypedObjectWithItemsIsAnArray() {
        assertArrayEquals(new Object[] {1L, 2L}, (Object[]) JsonIo.toMaps("{\"@items\":[1,2]}", null).asClass(null));
    }

    @Test
    void itsClassFactoryIsNotCalled() {
        AtomicInteger calls = new AtomicInteger();
        ReadOptions options = new ReadOptionsBuilder().addClassFactory(Counted.class, new ClassFactory() {
            @Override
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
                calls.incrementAndGet();
                return null;
            }
        }).build();
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps("{\"@type\":\"" + Counted.class.getName() + "\"," + FIELDS + "}", options)
                .asClass(null);
        assertTyped(read);
        assertEquals(0, calls.get());
    }
}
