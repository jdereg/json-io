package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A list or map class written as the single value its {@code @IoValue} method returns -- {@code Tags extends
 * ArrayList<String>} as {@code "x,y"} -- is written so, and read back through its creator, in JSON and TOON, as
 * Jackson writes and reads it. JSON wrote the value but referenced the object when it was held twice, a
 * {@code {"@ref":1}} to an {@code @id} never written; TOON wrote the list or map; and neither read back (a map from
 * its value, or a list from its items, went to the creator that takes text, or to the Converter).
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
class IoValueContainerTest {
    public static class Tags extends ArrayList<String> {
        public Tags() {
        }

        @IoCreator
        public static Tags of(String joined) {
            Tags t = new Tags();
            t.addAll(Arrays.asList(joined.split(",")));
            return t;
        }

        @IoValue
        public String joined() {
            return String.join(",", this);
        }
    }

    public static class Dict extends LinkedHashMap<String, Integer> {
        public Dict() {
        }

        @IoCreator
        public static Dict of(String text) {
            Dict d = new Dict();
            for (String kv : text.split(";")) {
                String[] p = kv.split("=");
                d.put(p[0], Integer.parseInt(p[1]));
            }
            return d;
        }

        @IoValue
        public String text() {
            StringBuilder sb = new StringBuilder();
            forEach((k, v) -> sb.append(sb.length() == 0 ? "" : ";").append(k).append('=').append(v));
            return sb.toString();
        }
    }

    /** A map written as a list, its keys and values in turn: a value that is a list. */
    public static class Pairs extends LinkedHashMap<String, String> {
        public Pairs() {
        }

        @IoCreator
        public static Pairs of(List<String> flat) {
            Pairs p = new Pairs();
            for (int i = 0; i + 1 < flat.size(); i += 2) {
                p.put(flat.get(i), flat.get(i + 1));
            }
            return p;
        }

        @IoValue
        public List<String> flat() {
            List<String> flat = new ArrayList<>();
            forEach((k, v) -> {
                flat.add(k);
                flat.add(v);
            });
            return flat;
        }
    }

    /** A list written as a map of each element's count: a value that is an object, written on lines of its own. */
    public static class Counts extends ArrayList<String> {
        public Counts() {
        }

        @IoCreator
        public static Counts of(Map<String, Integer> counts) {
            Counts c = new Counts();
            counts.forEach((k, n) -> {
                for (int i = 0; i < n; i++) {
                    c.add(k);
                }
            });
            return c;
        }

        @IoValue
        public Map<String, Integer> counts() {
            Map<String, Integer> m = new LinkedHashMap<>();
            forEach(k -> m.merge(k, 1, Integer::sum));
            return m;
        }
    }

    /** A map written as a list of its entries, each a map: a value that is a list written on lines of its own. */
    public static class Entries extends LinkedHashMap<String, String> {
        public Entries() {
        }

        @IoCreator
        public static Entries of(List<Map<String, String>> entries) {
            Entries e = new Entries();
            for (Map<String, String> entry : entries) {
                e.put(entry.get("k"), entry.get("v"));
            }
            return e;
        }

        @IoValue
        public List<Map<String, String>> entries() {
            List<Map<String, String>> list = new ArrayList<>();
            forEach((k, v) -> {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("k", k);
                entry.put("v", v);
                list.add(entry);
            });
            return list;
        }
    }

    /** A list subclass without the method: a list, as before. */
    public static class Plain extends ArrayList<String> {
    }

    /** An object in a list, with more than plain values: written as a list item, its fields inline. */
    public static class Row {
        public Tags tags;
        public Dict dict;
        public Pairs pairs;
        public Counts counts;
        public Entries entries;
        public Plain other;
    }

    public static class Holder {
        public Tags a;
        public Tags b;
        public Dict d;
        public Dict d2;
        public List<Row> rows;
        public Object any;
        public List<Tags> list;
        public Map<String, Dict> map;
        public Tags[] array;
        public Plain plain;
    }

    private static Holder holder() {
        Holder h = new Holder();
        h.a = Tags.of("x,y");
        h.b = h.a;
        h.d = Dict.of("k=1;m=2");
        h.d2 = h.d;
        Row row = new Row();
        row.tags = Tags.of("r,s");
        row.dict = Dict.of("q=3");
        row.pairs = Pairs.of(Arrays.asList("a", "1", "b", "2"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("x", 2);
        counts.put("y", 1);
        row.counts = Counts.of(counts);
        row.entries = new Entries();
        row.entries.put("p", "q");
        row.entries.put("r", "s");
        row.other = new Plain();
        row.other.add("o");
        h.rows = new ArrayList<>(Collections.singletonList(row));
        h.any = Tags.of("p,q");
        h.list = new ArrayList<>(Collections.singletonList(Tags.of("u,v")));
        h.map = new LinkedHashMap<>();
        h.map.put("n", Dict.of("z=9"));
        h.array = new Tags[] {Tags.of("s,t")};
        h.plain = new Plain();
        h.plain.add("w");
        return h;
    }

    private static void assertRead(Holder h, Holder back, boolean typed) {
        assertEquals(h.a, back.a);
        assertInstanceOf(Tags.class, back.a);
        assertEquals(h.b, back.b);
        assertEquals(h.d, back.d);
        assertInstanceOf(Dict.class, back.d);
        assertEquals(h.d2, back.d2);
        assertInstanceOf(Dict.class, back.d2);
        Row row = back.rows.get(0);
        assertEquals(h.rows.get(0).tags, row.tags);
        assertInstanceOf(Tags.class, row.tags);
        assertEquals(h.rows.get(0).dict, row.dict);
        assertInstanceOf(Dict.class, row.dict);
        assertEquals(h.rows.get(0).pairs, row.pairs);
        assertInstanceOf(Pairs.class, row.pairs);
        assertEquals(h.rows.get(0).counts, row.counts);
        assertInstanceOf(Counts.class, row.counts);
        assertEquals(h.rows.get(0).entries, row.entries);
        assertInstanceOf(Entries.class, row.entries);
        assertEquals(h.rows.get(0).other, row.other);
        if (typed) {
            assertEquals(h.any, back.any);
            assertInstanceOf(Tags.class, back.any);
        } else {
            assertEquals("p,q", back.any);      // as Jackson reads it into an Object
        }
        assertEquals(h.list, back.list);
        assertInstanceOf(Tags.class, back.list.get(0));
        assertEquals(h.map, back.map);
        assertInstanceOf(Dict.class, back.map.get("n"));
        assertArrayEquals(h.array, back.array);
        assertEquals(h.plain, back.plain);
        assertInstanceOf(Plain.class, back.plain);
    }

    @Test
    void jsonWritesTheValueAndReadsItBack() {
        Holder h = holder();
        WriteOptions[] modes = {null, new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build()};
        for (WriteOptions options : modes) {
            String json = JsonIo.toJson(h, options);
            assertFalse(json.contains("@ref"), json);
            assertFalse(json.contains("@id"), json);
            assertTrue(json.contains("\"x,y\""), json);
            assertTrue(json.contains("\"k=1;m=2\""), json);
            assertRead(h, JsonIo.toJava(json, null).asClass(Holder.class), options != modes[2]);
        }
        String json = JsonIo.toJson(h, null);
        assertTrue(json.contains("\"a\":\"x,y\",\"b\":\"x,y\",\"d\":\"k=1;m=2\",\"d2\":\"k=1;m=2\""), json);
        assertTrue(json.contains("\"plain\":[\"w\"]"), json);
    }

    @Test
    void toonWritesTheValueAndReadsItBack() {
        Holder h = holder();
        WriteOptions[] modes = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (WriteOptions options : modes) {
            String toon = JsonIo.toToon(h, options);
            assertFalse(toon.contains("$ref"), toon);
            assertFalse(toon.contains("$items[2]: x,y"), toon);
            assertTrue(toon.contains("\"x,y\""), toon);
            assertTrue(toon.contains("k=1;m=2"), toon);
            assertRead(h, JsonIo.fromToon(toon, null).asClass(Holder.class), options != null);
        }
        String toon = JsonIo.toToon(h, null);
        assertTrue(toon.contains("a: \"x,y\"\nb: \"x,y\"\nd: k=1;m=2\nd2: k=1;m=2\n"), toon);
        assertTrue(toon.contains("  - tags: \"r,s\"\n    dict: q=3\n"), toon);
    }

    @Test
    void atTheRoot() {
        Tags t = Tags.of("x,y");
        Dict d = Dict.of("k=1");
        assertEquals(t, JsonIo.toJava(JsonIo.toJson(t, null), null).asClass(Tags.class));
        assertEquals(d, JsonIo.toJava(JsonIo.toJson(d, null), null).asClass(Dict.class));
        assertInstanceOf(Tags.class, JsonIo.toJava(JsonIo.toJson(t, null), null).asClass(null));
        assertEquals("\"x,y\"\n", JsonIo.toToon(t, null) + "\n");
        assertEquals(t, JsonIo.fromToon(JsonIo.toToon(t, null), null).asClass(Tags.class));
        assertEquals(d, JsonIo.fromToon(JsonIo.toToon(d, null), null).asClass(Dict.class));
    }

    /** Jackson's output, and the lists and maps 4.117.0's TOON wrote (which did not read back then), read. */
    @Test
    void otherFormsRead() {
        Holder back = JsonIo.toJava("{\"a\":\"x,y\",\"b\":\"x,y\",\"d\":\"k=1;m=2\",\"list\":[\"u,v\"],"
                + "\"map\":{\"n\":\"z=9\"},\"array\":[\"s,t\"]}", null).asClass(Holder.class);
        assertEquals(Tags.of("x,y"), back.b);
        assertEquals(Dict.of("k=1;m=2"), back.d);
        assertEquals(Dict.of("z=9"), back.map.get("n"));

        back = JsonIo.fromToon("a[2]: x,y\nd:\n  k: 1\n  m: 2\n", null).asClass(Holder.class);
        assertEquals(Tags.of("x,y"), back.a);
        assertInstanceOf(Tags.class, back.a);
        assertEquals(Dict.of("k=1;m=2"), back.d);
        assertInstanceOf(Dict.class, back.d);

        back = JsonIo.fromToon("$type: " + Holder.class.getName() + "\na:\n  $id: 1\n  $items[2]: x,y\nb:\n  $ref: 1\n",
                null).asClass(Holder.class);
        assertEquals(Tags.of("x,y"), back.a);
        assertTrue(back.a == back.b);

        back = JsonIo.toJava("{\"d\":{\"k\":1},\"list\":[[\"u\",\"v\"]]}", null).asClass(Holder.class);
        assertEquals(Dict.of("k=1"), back.d);
        assertInstanceOf(Tags.class, back.list.get(0));
    }
}
