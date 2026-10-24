package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode, asking for {@code Object.class} at the root is asking for whatever the JSON holds, as asking for no
 * class is ({@code asClass(null)}), and as Jackson's {@code readValue(json, Object.class)} is. Up to now it threw
 * "the rootType 'java.lang.Object' is not supported" for every JSON object at the root, typed or not.
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
class MapsModeObjectRootTest {
    public static class Point {
        public int x = 1;
        public int y = 2;
    }

    public static class Email {
        private final String v;

        @IoCreator
        public Email(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    public enum Code {
        RED("r"), GREEN("g");

        private final String code;

        Code(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }
    }

    public enum Plain { A, B }

    private static Object[] roots() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1L);
        map.put("b", new ArrayList<>(Arrays.asList("x", 2L)));
        List<Object> list = new ArrayList<>(Arrays.asList(1L, "two", new Point()));
        return new Object[] {new Point(), map, list, new Object[] {1L, "s"},
                UUID.fromString("12345678-1234-1234-1234-123456789abc"), new Email("a@x.com"), Code.GREEN, Plain.B,
                "text", 42L, new HashMap<>()};
    }

    private static void assertSame(Object byNull, Object byObject, String text) {
        Map<String, Object> options = new HashMap<>();
        assertTrue(DeepEquals.deepEquals(byNull, byObject, options), () -> text + "\n" + options.get("diff"));
        assertEquals(byNull == null ? null : byNull.getClass(), byObject == null ? null : byObject.getClass(), text);
    }

    @Test
    void objectAtTheRootIsWhateverTheJsonHolds() {
        for (Object root : roots()) {
            for (WriteOptions options : new WriteOptions[] {null,
                    new WriteOptionsBuilder().showTypeInfoAlways().build(),
                    new WriteOptionsBuilder().showTypeInfoNever().build()}) {
                String json = JsonIo.toJson(root, options);
                Object byNull = JsonIo.toMaps(json, null).asClass(null);
                Object byObject = JsonIo.toMaps(json, null).asClass(Object.class);
                assertSame(byNull, byObject, json);
            }
        }
    }

    @Test
    void toonToo() {
        for (Object root : roots()) {
            for (WriteOptions options : new WriteOptions[] {null,
                    new WriteOptionsBuilder().showTypeInfoMinimal().build()}) {
                String toon = JsonIo.toToon(root, options);
                Object byNull = JsonIo.fromToonToMaps(toon, null).asClass(null);
                Object byObject = JsonIo.fromToonToMaps(toon, null).asClass(Object.class);
                assertSame(byNull, byObject, toon);
            }
        }
    }

    /** A TOON list at the root whose elements name their type: each element was read as an Object, which threw. */
    @Test
    void aToonListOfTypedElements() {
        String toon = "[2]:\n  - $type: " + Point.class.getName() + "\n    x: 5\n"
                + "  - $type: java.util.UUID\n    value: 12345678-1234-1234-1234-123456789abc\n";
        for (Class<?> asked : new Class<?>[] {null, Object.class, List.class}) {
            List<?> back = (List<?>) JsonIo.fromToonToMaps(toon, null).asClass(asked);
            assertEquals(2, back.size());
            assertInstanceOf(JsonObject.class, back.get(0));
            assertEquals(Point.class.getName(), ((JsonObject) back.get(0)).getTypeString());
            assertEquals(5, ((JsonObject) back.get(0)).get("x"));     // as its class declares it
            assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789abc"), back.get(1));
        }
    }

    @Test
    void aPlainObjectReadsAsAMap() {
        Object back = JsonIo.toMaps("{\"a\":1,\"b\":{\"c\":true}}", null).asClass(Object.class);
        assertInstanceOf(Map.class, back);
        assertEquals(1L, ((Map<?, ?>) back).get("a"));
        back = JsonIo.toMaps(JsonIo.toJson(new Point(), null), null).asClass(Object.class);
        assertInstanceOf(JsonObject.class, back);
        assertEquals(Point.class.getName(), ((JsonObject) back).getTypeString());
        assertEquals(2, ((JsonObject) back).get("y"));
        back = JsonIo.toMaps("{\"a\":[1,2]}", null).asType(new TypeHolder<Object>() {});
        assertEquals(Arrays.asList(1L, 2L), Arrays.asList((Object[]) ((Map<?, ?>) back).get("a")));
    }
}
