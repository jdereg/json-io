package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three minimal type info modes. MINIMAL, the default, leaves {@code @type} off a List, Set or Map whose class
 * is the one the reader builds for its declared type -- the natural default -- and everything reads back exactly.
 * MINIMAL_LEGACY writes what MINIMAL wrote before 4.113.0, byte for byte: for json-io readers older than 4.88.0 and
 * for text that is hashed or compared. MINIMAL_PLUS is MINIMAL and also lossy where values sit in Object slots.
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
class MinimalLegacyTest {

    public static class Address {
        public String street;
        public int zip;
    }

    public static class Person {
        public String name;
        public List<String> tags = new ArrayList<>();
        public List<Address> addresses = new ArrayList<>();
        public Map<String, Object> attrs = new LinkedHashMap<>();
        public Set<String> roles = new LinkedHashSet<>();
        public SortedSet<String> sorted = new TreeSet<>();
    }

    private static Person person() {
        Person p = new Person();
        p.name = "ann";
        p.tags.add("t1");
        Address a = new Address();
        a.street = "main";
        a.zip = 45202;
        p.addresses.add(a);
        p.attrs.put("count", 3);
        p.attrs.put("label", "x");
        p.roles.add("admin");
        p.sorted.add("b");
        p.sorted.add("a");
        return p;
    }

    private static List<Object> list() {
        List<Object> list = new ArrayList<>();
        list.add(person());
        list.add(7);
        return list;
    }

    private static final String PERSON = "{\"@type\":\"com.cedarsoftware.io.MinimalLegacyTest$Person\",\"name\":\"ann\",";

    /** person() and list() written by json-io 4.112.0 with its default options (MINIMAL), run on that release. */
    private static final String PERSON_4_112 = PERSON
            + "\"tags\":{\"@type\":\"ArrayList\",\"@items\":[\"t1\"]},"
            + "\"addresses\":{\"@type\":\"ArrayList\",\"@items\":[{\"street\":\"main\",\"zip\":45202}]},"
            + "\"attrs\":{\"@type\":\"LinkedHashMap\",\"count\":{\"@type\":\"Integer\",\"value\":3},\"label\":\"x\"},"
            + "\"roles\":{\"@type\":\"java.util.LinkedHashSet\",\"@items\":[\"admin\"]},"
            + "\"sorted\":{\"@type\":\"TreeSet\",\"@items\":[\"a\",\"b\"]}}";
    private static final String LIST_4_112 = "{\"@type\":\"ArrayList\",\"@items\":["
            + PERSON_4_112 + ",{\"@type\":\"Integer\",\"value\":7}]}";

    @Test
    void legacyWritesWhatMinimalWroteBefore() {
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build();
        assertEquals(PERSON_4_112, JsonIo.toJson(person(), legacy));
        assertEquals(LIST_4_112, JsonIo.toJson(list(), legacy));
        assertTrue(DeepEquals.deepEquals(person(), JsonIo.toJava(PERSON_4_112, null).asClass(Person.class)));
    }

    @Test
    void theDefaultLeavesTypeOffNaturalDefaultsAndReadsBackExactly() {
        String json = JsonIo.toJson(person(), null);
        assertEquals(PERSON + "\"tags\":[\"t1\"],\"addresses\":[{\"street\":\"main\",\"zip\":45202}],"
                + "\"attrs\":{\"count\":{\"@type\":\"Integer\",\"value\":3},\"label\":\"x\"},"
                + "\"roles\":[\"admin\"],\"sorted\":[\"a\",\"b\"]}", json);
        assertTrue(json.length() < PERSON_4_112.length() * 3 / 4, json);

        Person back = JsonIo.toJava(json, null).asClass(Person.class);
        assertTrue(DeepEquals.deepEquals(person(), back));
        assertEquals(Integer.class, back.attrs.get("count").getClass());     // an Object slot keeps its type
        assertEquals(TreeSet.class, back.sorted.getClass());
        assertEquals(LinkedHashSet.class, back.roles.getClass());
        assertEquals(Address.class, back.addresses.get(0).getClass());
    }

    @Test
    void minimalPlusIsSmallerAndLossy() {
        String json = JsonIo.toJson(person(), new WriteOptionsBuilder().showTypeInfoMinimalPlus().build());
        assertTrue(json.contains("\"count\":3"), json);
        Person back = JsonIo.toJava(json, null).asClass(Person.class);
        assertEquals(3L, back.attrs.get("count"));                           // read back a Long
    }

    @Test
    void legacyIsAMinimalMode() {
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().omitRootTypeInfo().build();
        assertTrue(legacy.isMinimalShowingType());
        assertTrue(legacy.isMinimalLegacyShowingType());
        assertTrue(JsonIo.toJson(person(), legacy).startsWith("{\"name\""));

        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().build();
        assertEquals(JsonIo.toToon(person(), minimal),
                JsonIo.toToon(person(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build()));

        try {
            WriteOptionsBuilder.addPermanentShowTypeInfoMinimalLegacy();
            assertEquals(PERSON_4_112, JsonIo.toJson(person(), new WriteOptionsBuilder().build()));
        } finally {
            WriteOptionsBuilder.addPermanentShowTypeInfoMinimal();
        }
        assertTrue(new WriteOptionsBuilder().build().isMinimalShowingType());
        assertTrue(!new WriteOptionsBuilder().build().isMinimalLegacyShowingType());
    }

    // ---------- More shapes, pinned against json-io 4.112.0's own text ----------
    // format/minimal-legacy-4.112.0.tsv: each graph below written with json-io 4.112.0's default options (MINIMAL),
    // by running that release on these classes (same names, same fields) -- one "NAME<tab>JSON" line per graph.

    public static class Node {
        public String name;
        public Node next;
        public List<Node> children = new ArrayList<>();
        public Object tag;
    }

    /** Shared and cyclic: a node referenced twice in a list, a cycle through next, an Object field back to the root. */
    static Node cyclic() {
        Node a = new Node();
        a.name = "a";
        Node b = new Node();
        b.name = "b";
        a.next = b;
        b.next = a;
        a.children.add(b);
        a.children.add(b);
        b.tag = a;
        return a;
    }

    public enum Color { RED, GREEN }

    public abstract static class Shape {
        public Color color;
    }

    public static class Circle extends Shape {
        public double radius;
    }

    public static class Square extends Shape {
        public int side;
    }

    public static class Drawing {
        public Shape main;
        public List<Shape> shapes = new ArrayList<>();
        public Object anything;
        public Color[] palette;
        public Map<Color, Integer> counts = new LinkedHashMap<>();
    }

    /** Polymorphic fields and lists, enums in a field, an array, an Object slot and as map keys. */
    static Drawing drawing() {
        Drawing d = new Drawing();
        Circle c = new Circle();
        c.color = Color.RED;
        c.radius = 1.5;
        Square s = new Square();
        s.color = Color.GREEN;
        s.side = 3;
        d.main = c;
        d.shapes.add(c);
        d.shapes.add(s);
        d.anything = Color.GREEN;
        d.palette = new Color[] {Color.GREEN, Color.RED};
        d.counts.put(Color.RED, 2);
        d.counts.put(Color.GREEN, 1);
        return d;
    }

    public static class Arrays1 {
        public String[] names;
        public int[] counts;
        public Object[] mixed;
        public Address[] places;
        public long[][] grid;
        public List<Object> withArrays = new ArrayList<>();
    }

    /** Typed and Object arrays, a 2D primitive array, arrays inside an Object list. */
    static Arrays1 arrays() {
        Arrays1 a = new Arrays1();
        a.names = new String[] {"x", "y"};
        a.counts = new int[] {1, 2, 3};
        Address home = new Address();
        home.street = "elm";
        home.zip = 45202;
        a.mixed = new Object[] {"s", 7, 2.5, true, null, home};
        a.places = new Address[] {home};
        a.grid = new long[][] {{1, 2}, {3}};
        a.withArrays.add(new String[] {"z"});
        a.withArrays.add(new int[] {9});
        return a;
    }

    public static class Maps1 {
        public Map<Integer, String> byNumber = new LinkedHashMap<>();
        public Map<Address, String> byAddress = new LinkedHashMap<>();
        public Map<String, List<Address>> nested = new LinkedHashMap<>();
        public List<Map<String, Integer>> rows = new ArrayList<>();
        public SortedMap<String, Object> sorted = new TreeMap<>();
    }

    /** Maps keyed by numbers and by objects, nested generics, a sorted map with Object values. */
    static Maps1 maps() {
        Maps1 m = new Maps1();
        m.byNumber.put(1, "one");
        m.byNumber.put(2, "two");
        Address home = new Address();
        home.street = "oak";
        home.zip = 10001;
        m.byAddress.put(home, "home");
        m.nested.put("k", new ArrayList<>(Arrays.asList(home)));
        Map<String, Integer> row = new LinkedHashMap<>();
        row.put("a", 1);
        m.rows.add(row);
        m.sorted.put("b", 2L);
        m.sorted.put("a", "x");
        return m;
    }

    public static class Values {
        public Date date;
        public LocalDate day;
        public ZonedDateTime when;
        public BigDecimal amount;
        public UUID id;
        public Object anyDay;
        public List<Object> things = new ArrayList<>();
    }

    /** Dates and other value types, in fields and in Object slots. */
    static Values values() {
        Values v = new Values();
        v.date = new Date(1700000000000L);
        v.day = LocalDate.of(2024, 1, 15);
        v.when = ZonedDateTime.parse("2023-10-22T12:03:01+03:00[Asia/Aden]");
        v.amount = new BigDecimal("12.50");
        v.id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        v.anyDay = LocalDate.of(2025, 6, 1);
        v.things.add(5L);
        v.things.add(2.5);
        v.things.add(new BigInteger("123456789012345678901234567890"));
        v.things.add('c');
        v.things.add((short) 3);
        return v;
    }

    public static class Containers1 {
        public List<String> linked;
        public Map<String, Integer> tree;
        public Set<String> hash;
        public List<String> copyOnWrite;
        public Collection<Integer> anyCollection;
    }

    /** Containers that are not the natural default for their declared type: their class is always written. */
    static Containers1 containers() {
        Containers1 c = new Containers1();
        c.linked = new LinkedList<>(Arrays.asList("a", "b"));
        c.tree = new TreeMap<>();
        c.tree.put("z", 26);
        c.tree.put("a", 1);
        c.hash = new HashSet<>(Arrays.asList("q"));
        c.copyOnWrite = new CopyOnWriteArrayList<>(Arrays.asList("w"));
        c.anyCollection = new ArrayDeque<>(Arrays.asList(4, 5));
        return c;
    }

    /** The graphs above, by the name their line in the golden file starts with. */
    private static Map<String, Object> graphs() {
        Map<String, Object> graphs = new LinkedHashMap<>();
        graphs.put("CYCLIC", cyclic());
        graphs.put("DRAWING", drawing());
        graphs.put("ARRAYS", arrays());
        graphs.put("MAPS", maps());
        graphs.put("VALUES", values());
        graphs.put("CONTAINERS", containers());
        return graphs;
    }

    private static Map<String, String> golden() {
        Map<String, String> golden = new LinkedHashMap<>();
        String text = ClassUtilities.loadResourceAsString("format/minimal-legacy-4.112.0.tsv");
        for (String line : text.split("\n")) {
            if (!line.isEmpty()) {
                int tab = line.indexOf('\t');
                golden.put(line.substring(0, tab), line.substring(tab + 1));
            }
        }
        return golden;
    }

    @Test
    void legacyWritesWhatMinimalWroteBeforeForEveryShape() {
        Map<String, String> golden = golden();
        Map<String, Object> graphs = graphs();
        assertEquals(graphs.keySet(), golden.keySet());
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build();
        for (Map.Entry<String, Object> graph : graphs.entrySet()) {
            assertEquals(golden.get(graph.getKey()), JsonIo.toJson(graph.getValue(), legacy), graph.getKey());
        }
    }

    /** Each shape reads back as it was, from the text 4.112.0 wrote and from what the default writes now. */
    @Test
    void everyShapeReadsBackExactlyFromLegacyAndFromTheDefault() {
        Map<String, String> golden = golden();
        for (Map.Entry<String, Object> graph : graphs().entrySet()) {
            Object original = graph.getValue();
            for (String json : new String[] {golden.get(graph.getKey()), JsonIo.toJson(original, null)}) {
                Object back = JsonIo.toJava(json, null).asClass(original.getClass());
                Map<String, Object> options = new HashMap<>();
                assertTrue(DeepEquals.deepEquals(original, back, options), graph.getKey() + ": " + options.get("diff"));
            }
        }
    }
}
