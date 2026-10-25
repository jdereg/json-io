package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A map whose keys are maps reads back with every key, each found by {@code get}. A map is filled at the end of the
 * read, and the maps it holds as keys are read inside it, so queued after it: filled first, it hashed them while they
 * were still empty. Two such keys were equal then -- one entry for both, the first key with the last value -- and a
 * single one was filed under an empty map's hash, so {@code get} did not find it (4.117.0 too). Each map is now filled
 * after the maps it holds as keys, wherever they are defined.
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
class MapsAsMapKeysTest {
    public static class First {
        public Map<String, String> first;
        public Map<Map<String, String>, String> keyed;
    }

    public static class Last {
        public Map<Map<String, String>, String> keyed;
        public Map<String, String> first;
    }

    /** Given its map at once (read before the end of the read, its maps filled then). */
    public static class Copied {
        private final Map<Map<String, String>, String> keyed;

        @IoCreator
        public Copied(@IoProperty("keyed") Map<Map<String, String>, String> keyed) {
            this.keyed = new LinkedHashMap<>(keyed);
        }
    }

    private static final WriteOptions ALWAYS = new WriteOptionsBuilder().showTypeInfoAlways().build();

    private static Map<String, String> map(String v) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("v", v);
        return map;
    }

    private static Map<Map<String, String>, String> twoKeys() {
        Map<Map<String, String>, String> keyed = new LinkedHashMap<>();
        keyed.put(map("k1"), "a");
        keyed.put(map("k2"), "b");
        return keyed;
    }

    private static void assertTwoKeys(Map<?, ?> keyed) {
        assertEquals(2, keyed.size());
        assertEquals("a", keyed.get(map("k1")));
        assertEquals("b", keyed.get(map("k2")));
    }

    @Test
    void twoMapKeys() {
        for (WriteOptions options : new WriteOptions[] {null, ALWAYS}) {
            Map<?, ?> back = JsonIo.toJava(JsonIo.toJson(twoKeys(), options), null).asClass(Map.class);
            assertEquals(twoKeys(), back);
            assertTwoKeys(back);
        }
        assertTwoKeys(JsonIo.fromToon(JsonIo.toToon(twoKeys(), ALWAYS), null).asClass(Map.class));
    }

    @Test
    void mapKeysWithMapKeys() {
        Map<Map<Map<String, String>, String>, String> nested = new LinkedHashMap<>();
        Map<Map<String, String>, String> one = new LinkedHashMap<>();
        one.put(map("x1"), "p");
        Map<Map<String, String>, String> two = new LinkedHashMap<>();
        two.put(map("x2"), "q");
        nested.put(one, "one");
        nested.put(two, "two");
        for (WriteOptions options : new WriteOptions[] {null, ALWAYS}) {
            Map<?, ?> back = JsonIo.toJava(JsonIo.toJson(nested, options), null).asClass(Map.class);
            assertEquals(nested, back);
            assertEquals("two", back.get(two));
        }
    }

    /** Untyped keys, as hand-written JSON or showTypeInfoNever gives them: each kept. */
    @Test
    void untypedMapKeys() {
        String json = "{\"@keys\":[{\"v\":\"k1\"},{\"v\":\"k2\"}],\"@items\":[\"a\",\"b\"]}";
        Map<?, ?> back = JsonIo.toJava(json, null).asClass(Map.class);
        assertEquals(2, back.size());
        List<Object> values = Arrays.asList(back.values().toArray());
        assertEquals(Arrays.asList("a", "b"), values);
    }

    /**
     * An untyped key is a LinkedHashMap, as Jackson reads it, so an equal map finds its entry: as a JsonObject (shallow
     * hash, equal only to JsonObjects) it was not found. Its untyped objects too. In JSON and TOON, in a field and at
     * the root, written by json-io without types or by hand.
     */
    @Test
    void untypedMapKeysAreFoundByAnEqualMap() {
        String json = "{\"@keys\":[{\"v\":\"k1\"},{\"v\":{\"in\":\"k2\"}}],\"@items\":[\"a\",\"b\"]}";
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("v", Collections.singletonMap("in", "k2"));
        Map<?, ?> back = JsonIo.toJava(json, null).asClass(Map.class);
        assertEquals("a", back.get(map("k1")));
        assertEquals("b", back.get(nested));
        for (Object key : back.keySet()) {
            assertSame(LinkedHashMap.class, key.getClass());
        }
        assertSame(LinkedHashMap.class, ((Map<?, ?>) back.keySet().toArray()[1]).get("v").getClass());

        Map<Object, String> written = new LinkedHashMap<>();
        written.put(map("k1"), "a");
        written.put(nested, "b");
        Holder holder = new Holder();
        holder.keyed = written;
        WriteOptions untyped = new WriteOptionsBuilder().showTypeInfoNever().build();
        Holder fromJson = JsonIo.toJava(JsonIo.toJson(holder, untyped), null).asClass(Holder.class);
        assertEquals("a", fromJson.keyed.get(map("k1")));
        assertEquals("b", fromJson.keyed.get(nested));
        Holder fromToon = JsonIo.fromToon(JsonIo.toToon(holder, null), null).asClass(Holder.class);
        assertEquals("a", fromToon.keyed.get(map("k1")));
        assertEquals("b", fromToon.keyed.get(nested));
    }

    public static class Holder {
        public Map<Object, String> keyed;
    }

    public static class Point {
        public int x;

        @Override
        public boolean equals(Object o) {
            return o instanceof Point && ((Point) o).x == x;
        }

        @Override
        public int hashCode() {
            return x;
        }
    }

    /** An object shared twice in a key (no cycle) is a map both times; a typed object in a key is that object. */
    @Test
    void sharedAndTypedObjectsInAnUntypedKey() {
        String shared = "{\"@keys\":[{\"a\":{\"@id\":2,\"x\":1},\"b\":{\"@ref\":2}}],\"@items\":[\"s\"]}";
        Map<String, Object> inner = Collections.singletonMap("x", 1L);
        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("a", inner);
        probe.put("b", inner);
        Map<?, ?> back = JsonIo.toJava(shared, null).asClass(Map.class);
        assertSame(LinkedHashMap.class, back.keySet().iterator().next().getClass());
        assertEquals("s", back.get(probe));

        String typed = "{\"@keys\":[{\"p\":{\"@type\":\"" + Point.class.getName() + "\",\"x\":3}}],\"@items\":[\"t\"]}";
        Point point = new Point();
        point.x = 3;
        back = JsonIo.toJava(typed, null).asClass(Map.class);
        assertEquals("t", back.get(Collections.singletonMap("p", point)));
    }

    /** A key that holds itself keeps its JsonObject (a LinkedHashMap cannot hash a cycle); Maps mode keeps them all. */
    @Test
    void aCyclicKeyAndMapsModeKeepTheirObjects() {
        String cyclic = "{\"@keys\":[{\"@id\":1,\"self\":{\"@ref\":1}}],\"@items\":[\"x\"]}";
        Map<?, ?> back = JsonIo.toJava(cyclic, null).asClass(Map.class);
        Object key = back.keySet().iterator().next();
        assertSame(JsonObject.class, key.getClass());
        assertSame(key, ((Map<?, ?>) key).get("self"));
        assertEquals("x", back.get(key));

        String json = "{\"@type\":\"" + Holder.class.getName()
                + "\",\"keyed\":{\"@keys\":[{\"v\":\"k1\"}],\"@items\":[\"a\"]}}";
        Map<?, ?> keyed = (Map<?, ?>) ((Map<?, ?>) JsonIo.toMaps(json, null).asClass(null)).get("keyed");
        assertTrue(keyed.keySet().iterator().next() instanceof JsonObject);
    }

    /** A key that refers to a map defined elsewhere, before or after the map holding it: filled before that map. */
    @Test
    void aKeyThatIsAMapDefinedElsewhere() {
        String k2 = "{\"@type\":\"LinkedHashMap\",\"v\":\"k2\"}";
        String before = "{\"@type\":\"" + First.class.getName() + "\",\"first\":{\"@id\":1,\"v\":\"k1\"},"
                + "\"keyed\":{\"@keys\":[{\"@ref\":1}," + k2 + "],\"@items\":[\"a\",\"b\"]}}";
        First first = JsonIo.toJava(before, null).asClass(First.class);
        assertTwoKeys(first.keyed);
        assertSame(first.first, first.keyed.keySet().iterator().next());

        // Defined in a later field: read, and so queued, before the map holding it
        String later = "{\"@type\":\"" + Last.class.getName() + "\",\"keyed\":{\"@keys\":[{\"@ref\":1}," + k2 + "],"
                + "\"@items\":[\"a\",\"b\"]},\"first\":{\"@id\":1,\"v\":\"k1\"}}";
        Last last = JsonIo.toJava(later, null).asClass(Last.class);
        assertTwoKeys(last.keyed);
        assertSame(last.first, last.keyed.keySet().iterator().next());

        // Defined among the keys, referred to after
        String inKeys = "{\"@type\":\"" + Last.class.getName() + "\",\"keyed\":{\"@keys\":[{\"@id\":1,"
                + "\"@type\":\"LinkedHashMap\",\"v\":\"k1\"}," + k2 + "],\"@items\":[\"a\",\"b\"]},"
                + "\"first\":{\"@ref\":1}}";
        last = JsonIo.toJava(inKeys, null).asClass(Last.class);
        assertTwoKeys(last.keyed);
        assertSame(last.first, last.keyed.keySet().iterator().next());
    }

    @Test
    void aCreatorGivenMapKeys() {
        Copied copied = new Copied(twoKeys());
        for (WriteOptions options : new WriteOptions[] {null, ALWAYS}) {
            Copied back = JsonIo.toJava(JsonIo.toJson(copied, options), null).asClass(Copied.class);
            assertTwoKeys(back.keyed);
        }
    }

    /** A map that is its own key, or a ring of them, ends (there is no right order to fill it in). */
    @Test
    void aMapThatIsItsOwnKey() {
        String json = "{\"@id\":1,\"@type\":\"java.util.LinkedHashMap\",\"@keys\":[{\"@ref\":1},\"k\"],"
                + "\"@items\":[\"x\",\"y\"]}";
        Map<?, ?> back = JsonIo.toJava(json, null).asClass(Map.class);
        assertEquals(2, back.size());
        assertEquals("y", back.get("k"));
    }
}
