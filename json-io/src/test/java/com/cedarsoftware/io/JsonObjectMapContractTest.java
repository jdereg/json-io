package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JsonObject follows the Map contract: it equals any Map with the same entries, in whatever order, and hashes as
 * AbstractMap does, so a HashSet or HashMap finds it by an equal LinkedHashMap and the other way round. It equalled
 * only a JsonObject of its own class with its keys in the same order, and hashed shallowly: {a,b} was not {b,a}, a
 * JsonObject was never its own LinkedHashMap copy (though the copy was the JsonObject), and a HashSet holding one did
 * not find the other.
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
class JsonObjectMapContractTest {
    private static Object maps(String json) {
        return JsonIo.toMaps(json, null).asClass(null);
    }

    private static Map<Object, Object> map(Object... keysAndValues) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static void assertSameMap(Object a, Object b) {
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
        Set<Object> holdsA = new HashSet<>();
        holdsA.add(a);
        assertTrue(holdsA.contains(b));
        Set<Object> holdsB = new HashSet<>();
        holdsB.add(b);
        assertTrue(holdsB.contains(a));
    }

    @Test
    void keyOrderDoesNotMatter() {
        assertSameMap(maps("{\"a\":1,\"b\":2}"), maps("{\"b\":2,\"a\":1}"));
    }

    @Test
    void itEqualsAnEqualLinkedHashMap() {
        Map<Object, Object> read = (Map<Object, Object>) maps("{\"a\":1,\"b\":\"x\",\"n\":{\"c\":true,\"d\":null}}");
        assertSameMap(read, map("a", 1L, "b", "x", "n", map("c", true, "d", null)));
        // a list and a set (a TOON list is one), hashed as theirs are
        read.put("list", new ArrayList<>(Arrays.asList(1L, 2L)));
        read.put("set", new LinkedHashSet<>(Arrays.asList("p", "q")));
        assertSameMap(read, map("a", 1L, "b", "x", "n", map("c", true, "d", null), "list", Arrays.asList(1L, 2L),
                "set", new HashSet<>(Arrays.asList("q", "p"))));
    }

    @Test
    void aMapWithKeysThatAreNotText() {
        Object read = ((Map<?, ?>) maps("{\"m\":{\"@type\":\"LinkedHashMap\",\"@keys\":[1,2],\"@items\":[\"a\",\"b\"]}}")).get("m");
        assertSameMap(read, map(1L, "a", 2L, "b"));
    }

    @Test
    void whatIsNotEqual() {
        assertNotEquals(maps("{\"a\":1}"), maps("{\"a\":2}"));
        assertNotEquals(maps("{\"a\":null}"), maps("{}"));
        assertNotEquals(maps("{\"a\":null}"), maps("{\"b\":null}"));
        assertNotEquals(maps("{\"a\":1}"), maps("{\"a\":1,\"b\":2}"));
        assertNotEquals(maps("{\"a\":1}"), "a=1");
        // an object holding @items is no map, though it has no entries
        assertNotEquals(((Map<?, ?>) maps("{\"l\":{\"@type\":\"ArrayList\",\"@items\":[1]}}")).get("l"), new LinkedHashMap<>());
    }

    /** As in any map, a value is compared by its equals(): an array by identity. DeepEquals compares by content. */
    @Test
    void anArrayIsComparedAsAnyMapComparesIt() {
        String json = "{\"a\":[1,2]}";
        Map<?, ?> first = (Map<?, ?>) maps(json);
        assertNotEquals(first, maps(json));
        assertTrue(DeepEquals.deepEquals(first, maps(json)));
        assertSameMap(first, new LinkedHashMap<>(first));      // its copy holds the same array
    }

    @Test
    void aNestedChangeChangesTheHash() {
        Map<?, ?> read = (Map<?, ?>) maps("{\"n\":{\"c\":1}}");
        int before = read.hashCode();
        ((Map<Object, Object>) read.get("n")).put("c", 2L);
        assertNotEquals(before, read.hashCode());       // it was cached, a nested change unseen
        assertSameMap(read, map("n", map("c", 2L)));
    }

    /**
     * A loop that closes on the first object, and one that closes a step later, holding the same values: equal, as
     * their reads are, so hashed alike -- by the objects' own entries, where a loop is reachable.
     */
    @Test
    void equalLoopsOfDifferentLengthsHashAlike() {
        Object self = maps("{\"@id\":1,\"x\":1,\"next\":{\"@ref\":1}}");
        Object ring = maps("{\"@id\":1,\"x\":1,\"next\":{\"@id\":2,\"x\":1,\"next\":{\"@ref\":1}}}");
        assertEquals(self, ring);
        assertEquals(ring, self);
        assertEquals(self.hashCode(), ring.hashCode());
        // its own values still tell one loop's hash from another's
        assertNotEquals(self.hashCode(), maps("{\"@id\":1,\"x\":2,\"next\":{\"@ref\":1}}").hashCode());
    }

    /** A graph of more maps than a hash walks unguarded is walked guarded, to the same hash. */
    @Test
    void aLargeGraphHashesAsAnEqualMapDoes() {
        Map<Object, Object> read = (Map<Object, Object>) maps("{\"n\":{\"m\":{\"k\":1}}}");
        List<Object> list = new ArrayList<>();
        Set<Object> set = new LinkedHashSet<>();
        List<Object> listCopy = new ArrayList<>();
        Set<Object> setCopy = new HashSet<>();
        for (long i = 0; i < 300; i++) {
            list.add(maps("{\"i\":" + i + "}"));
            listCopy.add(map("i", i));
            set.add(maps("{\"s\":" + i + "}"));
            setCopy.add(map("s", i));
        }
        read.put("list", list);
        read.put("set", set);
        assertSameMap(read, map("n", map("m", map("k", 1L)), "list", listCopy, "set", setCopy));
    }
}
