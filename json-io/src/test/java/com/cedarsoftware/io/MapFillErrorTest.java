package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A map its class will not hold the entries of -- a TreeMap whose keys do not compare with one another -- is an error
 * naming the map, the key and where it stands. Maps are filled as the read ends, and the map's own ClassCastException
 * escaped the read unwrapped ("class com.cedarsoftware.io.JsonObject cannot be cast to class java.lang.Comparable"),
 * saying neither which map nor where.
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
class MapFillErrorTest {
    public static class Holder {
        public TreeMap<Object, Integer> m;
        public Map<Object, Object> other;
    }

    private static final String MAP_KEYS = "{\"@type\":\"TreeMap\",\"@keys\":[{\"x\":1},{\"y\":2}],\"@items\":[1,2]}";

    @Test
    void keysThatAreNotComparable() {
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"m\":" + MAP_KEYS + "}", null).asClass(Holder.class));
        assertEquals("Cannot fill a java.util.TreeMap with its entries: its keys must compare with one another, and a "
                + JsonObject.class.getName() + " is not Comparable (at $.m)", e.getMessage());
        assertInstanceOf(ClassCastException.class, e.getCause());
        e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(MAP_KEYS, null).asClass(null));
        assertTrue(e.getMessage().startsWith("Cannot fill a java.util.TreeMap"), e.getMessage());
        e = assertThrows(JsonIoException.class,
                () -> JsonIo.toMaps("{\"@keys\":[{\"x\":1},{\"y\":2}],\"@items\":[1,2]}", null).asClass(TreeMap.class));
        assertTrue(e.getMessage().startsWith("Cannot fill a java.util.TreeMap"), e.getMessage());
    }

    @Test
    void keysOfKindsThatDoNotCompare() {
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(
                "{\"m\":{\"@type\":\"TreeMap\",\"@keys\":[1,\"a\"],\"@items\":[1,2]}}", null).asClass(Holder.class));
        assertEquals("Cannot fill a java.util.TreeMap with its entries: its keys must compare with one another (at $.m)",
                e.getMessage());
        // beside a map whose keys are maps, filled first
        e = assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"m\":{\"@type\":\"TreeMap\",\"@keys\":[1,\"a\"],"
                + "\"@items\":[1,2]},\"other\":{\"@keys\":[{\"k\":1}],\"@items\":[1]}}", null).asClass(Holder.class));
        assertTrue(e.getMessage().endsWith("(at $.m)"), e.getMessage());
    }

    /** Keys that compare fill it as before; Maps mode, asked for nothing, keeps such a map as JSON. */
    @Test
    void whatIsReadAsBefore() {
        Holder h = JsonIo.toJava("{\"m\":{\"@type\":\"TreeMap\",\"@keys\":[\"b\",\"a\"],\"@items\":[1,2]}}", null)
                .asClass(Holder.class);
        assertEquals(Arrays.asList("a", "b"), Arrays.asList(h.m.keySet().toArray()));
        Map<?, ?> tree = (Map<?, ?>) JsonIo.toMaps("{\"m\":" + MAP_KEYS + "}", null).asClass(null);
        assertInstanceOf(Map.class, tree.get("m"));
    }
}
