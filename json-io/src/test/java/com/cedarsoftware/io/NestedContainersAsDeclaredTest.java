package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A container held where a parameterized container is declared ({@code List<Point>} in a {@code List<List<Point>>},
 * a {@code Map<String, List<Point>>}'s values) is written as one of that declared class -- an ArrayList, its natural
 * default, without its @type -- and what it holds as the type arguments declare, as a field's container is, and as
 * Jackson writes it: {@code [[{"x":1}]]}. Both had their @type ({"@type":"ArrayList","@items":[{"@type":"Point",..}]}).
 * Every json-io reader from 4.111.0 reads the bare form back as these classes. A container among a map's keys keeps
 * its types (no reader reads a key container's elements by its type arguments), as does one where Object is declared,
 * and showTypeInfoMinimalLegacy() keeps 4.112.0's text.
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
class NestedContainersAsDeclaredTest {
    public static class P {
        public int x = 1;

        @Override
        public boolean equals(Object o) {
            return o instanceof P && ((P) o).x == x;
        }

        @Override
        public int hashCode() {
            return x;
        }
    }

    public enum Plain { A, B }

    private static List<P> ps() {
        return new ArrayList<>(Arrays.asList(new P()));
    }

    private static Map<String, P> mapOfP() {
        Map<String, P> map = new LinkedHashMap<>();
        map.put("a", new P());
        return map;
    }

    /** Written as Jackson writes them. */
    public static class Nested {
        public List<List<P>> lists = new ArrayList<>();
        public Map<String, List<P>> listsInMap = new LinkedHashMap<>();
        public List<Map<String, P>> mapsInList = new ArrayList<>();
        public Map<String, Map<String, P>> mapsInMap = new LinkedHashMap<>();
        public List<Set<Plain>> setsOfEnums = new ArrayList<>();
        public List<List<List<P>>> deep = new ArrayList<>();

        {
            lists.add(ps());
            listsInMap.put("k", ps());
            mapsInList.add(mapOfP());
            mapsInMap.put("m", mapOfP());
            setsOfEnums.add(new LinkedHashSet<>(Arrays.asList(Plain.B)));
            deep.add(new ArrayList<>(Arrays.asList(ps())));
        }
    }

    /** What keeps its types. */
    public static class Kept {
        public Map<List<P>, List<P>> keyedByLists = new LinkedHashMap<>();
        public List<List<Object>> objects = new ArrayList<>();
        public List<LinkedList<P>> linked = new ArrayList<>();
        public List<Map<Plain, P>> enumKeyed = new ArrayList<>();

        {
            keyedByLists.put(ps(), ps());
            objects.add(new ArrayList<>(Arrays.asList(new P())));
            linked.add(new LinkedList<>(Arrays.asList(new P())));
            Map<Plain, P> map = new LinkedHashMap<>();
            map.put(Plain.A, new P());
            enumKeyed.add(map);
        }
    }

    @Test
    void writtenAsJacksonWritesThem() throws Exception {
        String json = JsonIo.toJson(new Nested(), new WriteOptionsBuilder().omitRootTypeInfo().build());
        assertEquals(new ObjectMapper().writeValueAsString(new Nested()), json);
        assertTrue(json.contains("\"lists\":[[{\"x\":1}]]"), json);
    }

    private static void assertNested(Nested back, String text) {
        assertSame(P.class, back.lists.get(0).get(0).getClass(), text);
        assertSame(P.class, back.listsInMap.get("k").get(0).getClass(), text);
        assertSame(P.class, back.mapsInList.get(0).get("a").getClass(), text);
        assertSame(P.class, back.mapsInMap.get("m").get("a").getClass(), text);
        assertSame(Plain.B, back.setsOfEnums.get(0).iterator().next(), text);
        assertSame(P.class, back.deep.get(0).get(0).get(0).getClass(), text);
    }

    @Test
    void readBackAsDeclared() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().cycleSupport(false).build(),
                new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(new Nested(), options);
            assertNested(JsonIo.toJava(json, null).asClass(Nested.class), json);
        }
    }

    @Test
    void whatKeepsItsTypes() {
        String json = JsonIo.toJson(new Kept(), null);
        String p = P.class.getName();
        assertTrue(json.contains("\"keyedByLists\":{\"@keys\":[{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"" + p
                + "\",\"x\":1}]}],\"@items\":[[{\"x\":1}]]}"), json);
        assertTrue(json.contains("\"objects\":[[{\"@type\":\"" + p + "\",\"x\":1}]]"), json);
        assertTrue(json.contains("\"linked\":[[{\"x\":1}]]"), json);       // the declared class itself
        assertTrue(json.contains("\"enumKeyed\":[{\"@keys\":[\"A\"],\"@items\":[{\"x\":1}]}]"), json);
        Kept back = JsonIo.toJava(json, null).asClass(Kept.class);
        assertSame(P.class, back.keyedByLists.keySet().iterator().next().get(0).getClass());
        assertSame(P.class, back.keyedByLists.values().iterator().next().get(0).getClass());
        assertSame(P.class, back.objects.get(0).get(0).getClass());
        assertSame(LinkedList.class, back.linked.get(0).getClass());
        assertSame(P.class, back.enumKeyed.get(0).get(Plain.A).getClass());
    }

    /** 4.112.0's text, byte for byte. */
    @Test
    void theLegacyTextKeepsItsTypes() {
        String json = JsonIo.toJson(new Nested(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build());
        assertTrue(json.contains("\"lists\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"ArrayList\",\"@items\":"
                + "[{\"@type\":\"" + P.class.getName() + "\",\"x\":1}]}]}"), json);
    }

    public static class Box<T> {
        public T item;
        public Object any;
    }

    public static class Boxes {
        public List<Box<P>> boxes = new ArrayList<>();

        {
            Box<P> box = new Box<>();
            box.item = new P();
            List<List<P>> lists = new ArrayList<>();
            lists.add(ps());
            box.any = lists;
            boxes.add(box);
        }
    }

    /**
     * A generic class among the elements ({@code List<Box<P>>}): its {@code Object} field's list of lists is not of
     * the declaring the elements' type arguments make, and keeps its types, as before.
     */
    @Test
    void anObjectFieldInsideKeepsItsTypes() {
        String json = JsonIo.toJson(new Boxes(), null);
        assertTrue(json.contains("\"any\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"ArrayList\",\"@items\":"
                + "[{\"@type\":\"" + P.class.getName()), json);
        Boxes back = JsonIo.toJava(json, null).asClass(Boxes.class);
        Object inner = ((List<?>) ((List<?>) back.boxes.get(0).any).get(0)).get(0);
        assertSame(P.class, inner.getClass());
    }

    public static class Shared {
        public List<List<P>> lists = new ArrayList<>();

        {
            List<P> inner = ps();
            lists.add(inner);
            lists.add(inner);
        }
    }

    /** A list held twice keeps its identity: its @id beside its items, its @type left off. */
    @Test
    void aSharedListKeepsItsIdentity() {
        String json = JsonIo.toJson(new Shared(), null);
        assertTrue(json.contains("\"lists\":[{\"@id\":1,\"@items\":[{\"x\":1}]},{\"@ref\":1}]"), json);
        Shared back = JsonIo.toJava(json, null).asClass(Shared.class);
        assertSame(back.lists.get(0), back.lists.get(1));
        assertSame(P.class, back.lists.get(1).get(0).getClass());
    }
}
