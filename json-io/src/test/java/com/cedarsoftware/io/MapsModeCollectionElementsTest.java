package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * In Maps mode an object inside a typed collection ({"@type":"ArrayList","@items":[..]}) stays JSON, as an object in a
 * field does: its class is not built and its values stay as JSON gives them. From 4.31.0 such an object was built when
 * its class was on the classpath -- its constructor run, its fields typed by its class -- and a typed collection read
 * at the root as a List lost its objects: a null in place of each.
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
class MapsModeCollectionElementsTest {
    /** Counts its constructions. */
    public static class Built {
        static final AtomicInteger made = new AtomicInteger();
        public int count;

        public Built() {
            made.incrementAndGet();
        }
    }

    private static final String BUILT = Built.class.getName();

    private static String list(String items) {
        return "{\"@type\":\"ArrayList\",\"@items\":[" + items + "]}";
    }

    public enum Plain { A, B }

    /** A has a body: its class is an anonymous subclass of the enum, named Rich$1 in the JSON json-io writes. */
    public enum Rich {
        A {
            @Override
            int weight() {
                return 1;
            }
        },
        B;

        int weight() {
            return 0;
        }
    }

    @Test
    void anObjectInATypedCollectionStaysJson() {
        String json = "{\"a\":{\"@id\":1,\"z\":9},\"l\":" + list("{\"@type\":\"" + BUILT + "\",\"count\":3,\"b\":{\"@ref\":1}}") + "}";
        int before = Built.made.get();
        Map<?, ?> root = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
        JsonObject element = (JsonObject) ((JsonObject) root.get("l")).getItems()[0];
        assertEquals(3L, element.get("count"));         // as JSON gives it (it was the Integer its class declares)
        assertEquals(BUILT, element.getTypeString());
        assertSame(root.get("a"), element.get("b"));    // its @ref patched, as in any object
        assertEquals(before, Built.made.get());         // not built
        assertEquals(json, JsonIo.toJson(root, null));
    }

    @Test
    void aTypedCollectionAskedForAsAListAtTheRootKeepsItsObjects() {
        String json = list("{\"@type\":\"" + BUILT + "\",\"count\":3},{\"x\":1},{\"@type\":\"com.example.NoSuchClass\",\"y\":2},5,"
                + "{\"@type\":\"" + Plain.class.getName() + "\",\"name\":\"B\"},{\"@type\":\"Integer\",\"value\":7},"
                + "{\"@type\":\"" + Rich.class.getName() + "$1\",\"name\":\"A\"},{\"@type\":\"HashSet\",\"@items\":[8]}");
        List<?> read = JsonIo.toMaps(json, null).asClass(List.class);
        assertEquals(8, read.size());
        assertEquals(3L, ((Map<?, ?>) read.get(0)).get("count"));     // each was null
        assertEquals(1L, ((Map<?, ?>) read.get(1)).get("x"));
        assertEquals(2L, ((Map<?, ?>) read.get(2)).get("y"));           // a class not on the classpath: a map, as before
        assertEquals(5L, read.get(3));
        assertSame(Plain.B, read.get(4));           // an enum, a typed value and a container read as before
        assertEquals(7, read.get(5));
        assertEquals("A", ((Map<?, ?>) read.get(6)).get("name"));      // a constant with a body: JSON (it was a null)
        assertEquals(new HashSet<>(Arrays.asList(8L)), read.get(7));
    }
}
