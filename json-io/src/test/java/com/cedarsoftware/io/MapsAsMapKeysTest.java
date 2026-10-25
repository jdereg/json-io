package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
