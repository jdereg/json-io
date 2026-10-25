package com.cedarsoftware.io;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code maxObjectGraphDepth} is one rule, in JSON and TOON, with and without {@code cycleSupport}: the nesting of
 * objects and containers (a list, an array, a map, an Optional), as Jackson's maxNestingDepth counts it -- the root
 * is level 1, each object or container inside another one more, and a scalar adds none (Issue424MaxDepthTest). Without
 * cycleSupport, JSON wrote a list's (or map's) objects past the path it counts, so a chain through lists counted half
 * the depth the trace does; TOON, by default, wrote what it lays out in place past its path (a list in a field, the
 * rows of a table, a keyed table, folded keys, an Optional's content) and counted none of it; and the trace, in both,
 * counted a scalar in a list or map one level below it, a scalar in a field not.
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
class GraphDepthCountedAlikeTest {
    public static class Node {
        public Node next;
        public List<Node> kids;
        public Node[] arr;
        public Map<String, Node> map;
        public Object any;
        public Optional<Node> opt;
    }

    public static class Flat {
        public int a;
        public String b;

        public Flat() {
        }

        Flat(int a, String b) {
            this.a = a;
            this.b = b;
        }
    }

    /** A row with an object in it: a table's group. */
    public static class Pair {
        public int a;
        public Flat f;

        public Pair() {
        }

        Pair(int a, Flat f) {
            this.a = a;
            this.f = f;
        }
    }

    public static class Holder {
        public List<Flat> rows;
        public List<Pair> pairs;
        public Map<String, Flat> keyed;
        public Map<String, Object> folded;
        public Map<Flat, String> complex;
        public List<List<String>> lists;
        public List<List<Flat>> tables;
        public List<Map<String, Object>> maps;
        public int[][] grid;
        public Object any;
        public List<Object> list;
        public String s;
    }

    private enum Link { FIELD, LIST, ARRAY, MAP, OBJECT_LIST, OPTIONAL, LIST_THEN_FIELDS }

    private static Node chain(int length, Link link) {
        Node head = new Node();
        Node cur = head;
        for (int i = 0; i < length; i++) {
            Node next = new Node();
            switch (link) {
                case FIELD:
                    cur.next = next;
                    break;
                case LIST:
                    cur.kids = new ArrayList<>(Collections.singletonList(next));
                    break;
                case ARRAY:
                    cur.arr = new Node[] {next};
                    break;
                case MAP:
                    cur.map = new LinkedHashMap<>(Collections.singletonMap("k", next));
                    break;
                case OBJECT_LIST:
                    cur.any = new ArrayList<>(Collections.singletonList(next));
                    break;
                case OPTIONAL:
                    cur.opt = Optional.of(next);
                    break;
                default:
                    // in a list first, then each in a field: below the list, counted by the path again
                    if (i == 0) {
                        cur.kids = new ArrayList<>(Collections.singletonList(next));
                    } else {
                        cur.next = next;
                    }
                    break;
            }
            cur = next;
        }
        return head;
    }

    private static Map<String, Object> map(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    /** The graphs, by name, with the depth each is: the least maxObjectGraphDepth that writes it. */
    private static Map<String, Object[]> graphs() {
        Map<String, Object[]> graphs = new LinkedHashMap<>();
        graphs.put("field chain", new Object[] {4, (Supplier<Object>) () -> chain(3, Link.FIELD)});
        graphs.put("list chain", new Object[] {7, (Supplier<Object>) () -> chain(3, Link.LIST)});
        graphs.put("array chain", new Object[] {7, (Supplier<Object>) () -> chain(3, Link.ARRAY)});
        graphs.put("map chain", new Object[] {7, (Supplier<Object>) () -> chain(3, Link.MAP)});
        graphs.put("Object-field list chain", new Object[] {7, (Supplier<Object>) () -> chain(3, Link.OBJECT_LIST)});
        graphs.put("Optional chain", new Object[] {7, (Supplier<Object>) () -> chain(3, Link.OPTIONAL)});
        graphs.put("list then fields", new Object[] {5, (Supplier<Object>) () -> chain(3, Link.LIST_THEN_FIELDS)});
        graphs.put("table", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.rows = new ArrayList<>(Arrays.asList(new Flat(1, "x"), new Flat(2, "y")));
            return h;
        }});
        graphs.put("table with groups", new Object[] {4, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.pairs = new ArrayList<>(Arrays.asList(new Pair(1, new Flat(1, "x")), new Pair(2, new Flat(2, "y"))));
            return h;
        }});
        graphs.put("table of maps", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.maps = new ArrayList<>(Arrays.asList(map("a", 1L), map("a", 2L)));
            return h;
        }});
        graphs.put("tables in a list", new Object[] {4, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.tables = new ArrayList<>(Collections.singletonList(
                    new ArrayList<>(Arrays.asList(new Flat(1, "x"), new Flat(2, "y")))));
            return h;
        }});
        graphs.put("keyed table", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.keyed = new LinkedHashMap<>();
            h.keyed.put("p", new Flat(1, "x"));
            h.keyed.put("q", new Flat(2, "y"));
            return h;
        }});
        graphs.put("folded keys", new Object[] {4, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.folded = map("b", map("c", map("v", 1L)));
            return h;
        }});
        graphs.put("object keys", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.complex = new LinkedHashMap<>(Collections.singletonMap(new Flat(1, "x"), "v"));
            return h;
        }});
        graphs.put("empty list in a list", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.lists = new ArrayList<>(Arrays.asList(new ArrayList<>(), new ArrayList<>(Arrays.asList("s"))));
            return h;
        }});
        graphs.put("array of arrays", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.grid = new int[][] {{1, 2}, {3}};
            return h;
        }});
        graphs.put("maps in a list", new Object[] {4, (Supplier<Object>) () -> {
            Holder h = new Holder();
            Map<String, Object> m = map("in", map("x", 1L));
            m.put("b", 2L);
            h.list = new ArrayList<>(Arrays.asList(m, "s"));
            return h;
        }});
        graphs.put("table in an Object field", new Object[] {3, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.any = new ArrayList<>(Arrays.asList(new Flat(1, "x"), new Flat(2, "y")));
            return h;
        }});
        graphs.put("a table at the root", new Object[] {2, (Supplier<Object>) () ->
                new ArrayList<>(Arrays.asList(new Flat(1, "x"), new Flat(2, "y")))});
        graphs.put("scalars in a list", new Object[] {2, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.list = new ArrayList<>(Arrays.asList("s", 1L, UUID.randomUUID()));
            return h;
        }});
        graphs.put("a scalar in an Object field", new Object[] {1, (Supplier<Object>) () -> {   // the root alone
            Holder h = new Holder();
            h.any = "s";
            h.s = "t";
            return h;
        }});
        graphs.put("an empty list at the end", new Object[] {4, (Supplier<Object>) () -> {
            Node root = chain(2, Link.FIELD);
            root.next.next.kids = new ArrayList<>();
            return root;
        }});
        graphs.put("an empty map at the end", new Object[] {4, (Supplier<Object>) () -> {
            Node root = chain(2, Link.FIELD);
            root.next.next.map = new LinkedHashMap<>();
            return root;
        }});
        graphs.put("an empty array at the end", new Object[] {4, (Supplier<Object>) () -> {
            Node root = chain(2, Link.FIELD);
            root.next.next.arr = new Node[0];
            return root;
        }});
        graphs.put("a keyed table of groups at the root", new Object[] {3, (Supplier<Object>) () -> {
            Map<String, Pair> root = new LinkedHashMap<>();
            root.put("p", new Pair(1, new Flat(1, "x")));
            root.put("q", new Pair(2, new Flat(2, "y")));
            return root;
        }});
        graphs.put("an Optional in a list element", new Object[] {5, (Supplier<Object>) () -> {
            Node root = new Node();
            Node element = new Node();
            element.opt = Optional.of(new Node());
            root.kids = new ArrayList<>(Collections.singletonList(element));
            return root;
        }});
        graphs.put("Optionals in a list", new Object[] {4, (Supplier<Object>) () -> {
            Node root = new Node();
            root.any = new ArrayList<>(Collections.singletonList(Optional.of(new Node())));
            return root;
        }});
        graphs.put("folded keys below a list", new Object[] {5, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.list = new ArrayList<>(Collections.singletonList(map("b", map("c", map("v", 1L)))));
            return h;
        }});
        graphs.put("a keyed table in a list element", new Object[] {5, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.keyed = new LinkedHashMap<>();
            h.keyed.put("p", new Flat(1, "x"));
            h.keyed.put("q", new Flat(2, "y"));
            Node root = new Node();
            // (a list of the Holder alone is a table with groups; after a String, a list, its keyed table written
            // inline, and the list is read for no table first, which counts what it reads)
            root.any = new ArrayList<>(Arrays.asList("x", h));
            return root;
        }});
        graphs.put("a keyed table of map rows with groups", new Object[] {4, (Supplier<Object>) () -> {
            Map<String, Object> p = map("a", 1L);
            p.put("g", map("x", 1L));
            Map<String, Object> q = map("a", 2L);
            q.put("g", map("x", 2L));
            Holder h = new Holder();
            h.folded = new LinkedHashMap<>();
            h.folded.put("p", p);
            h.folded.put("q", q);
            return h;
        }});
        graphs.put("folded keys ending in a list", new Object[] {4, (Supplier<Object>) () -> {
            Holder h = new Holder();
            h.folded = map("b", map("c", new ArrayList<>(Arrays.asList(1L, 2L))));
            return h;
        }});
        graphs.put("an Optional at the root", new Object[] {3, (Supplier<Object>) () ->
                Optional.of(chain(1, Link.FIELD))});
        // A leaf one level down, in an Object field and in a list: no level of its own, whatever its class (a UUID
        // was one, in a list two, without cycleSupport; a String in a list one with it)
        Object[] leaves = {"s", 5L, UUID.randomUUID(), LocalDate.of(2026, 1, 2), new BigDecimal("1.5"), new Date(0),
                String.class, new char[] {'h', 'i'}};
        for (Object leaf : leaves) {
            String name = leaf.getClass().getSimpleName();
            graphs.put(name + " in an Object field below the root", new Object[] {2, (Supplier<Object>) () -> {
                Node root = new Node();
                root.next = new Node();
                root.next.any = leaf;
                return root;
            }});
            graphs.put(name + " in a list below the root", new Object[] {3, (Supplier<Object>) () -> {
                Node root = new Node();
                root.next = new Node();
                root.next.any = new ArrayList<>(Collections.singletonList(leaf));
                return root;
            }});
        }
        return graphs;
    }

    /** The least limit that writes {@code graph}. */
    private static int depthOf(Object graph, boolean toon, boolean cycleSupport, boolean folding) {
        for (int limit = 1; limit < 40; limit++) {
            WriteOptions options = new WriteOptionsBuilder().cycleSupport(cycleSupport).maxObjectGraphDepth(limit)
                    .toonKeyFolding(folding).build();
            try {
                if (toon) {
                    JsonIo.toToon(graph, options);
                } else {
                    JsonIo.toJson(graph, options);
                }
                return limit;
            } catch (JsonIoException e) {
                // deeper
            }
        }
        return -1;
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneRuleInEveryFormatAndMode() {
        for (Map.Entry<String, Object[]> graph : graphs().entrySet()) {
            int depth = (Integer) graph.getValue()[0];
            Supplier<Object> make = (Supplier<Object>) graph.getValue()[1];
            boolean folding = graph.getKey().startsWith("folded keys") || graph.getKey().contains("keyed table of map");
            for (boolean toon : new boolean[] {false, true}) {
                for (boolean cycleSupport : new boolean[] {true, false}) {
                    assertEquals(depth, depthOf(make.get(), toon, cycleSupport, folding), graph.getKey() + " in "
                            + (toon ? "TOON" : "JSON") + (cycleSupport ? " with" : " without") + " cycleSupport");
                }
            }
        }
    }

    /**
     * A class declared non-referenceable is a leaf, as json-io's own (dates, UUID, ...) are: no level, in a field or
     * a list. (With cycleSupport, the trace does not look inside it, as before; without, what it holds is written,
     * and counted.)
     */
    @Test
    void aClassDeclaredNonReferenceable() {
        Node inList = chain(1, Link.FIELD);
        inList.next.any = new ArrayList<>(Collections.singletonList(new Flat(1, "x")));
        Node inField = chain(1, Link.FIELD);
        inField.next.any = new Flat(1, "x");
        for (boolean toon : new boolean[] {false, true}) {
            for (boolean cycleSupport : new boolean[] {true, false}) {
                String mode = (toon ? "TOON" : "JSON") + (cycleSupport ? " with" : " without") + " cycleSupport";
                assertEquals(3, depthOfNonRef(inList, toon, cycleSupport), "in a list, " + mode);
                assertEquals(2, depthOfNonRef(inField, toon, cycleSupport), "in a field, " + mode);
            }
        }
    }

    private static int depthOfNonRef(Object graph, boolean toon, boolean cycleSupport) {
        for (int limit = 1; limit < 40; limit++) {
            WriteOptions options = new WriteOptionsBuilder().cycleSupport(cycleSupport).maxObjectGraphDepth(limit)
                    .addNonReferenceableClass(Flat.class).build();
            try {
                if (toon) {
                    JsonIo.toToon(graph, options);
                } else {
                    JsonIo.toJson(graph, options);
                }
                return limit;
            } catch (JsonIoException e) {
                // deeper
            }
        }
        return -1;
    }

    /** A writer that refused a graph writes the next from the top: the count starts again. */
    @Test
    void aWriterUsedAgainAfterARefusal() throws Exception {
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(false).maxObjectGraphDepth(6).build();
        for (boolean toon : new boolean[] {false, true}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (toon) {
                ToonWriter writer = new ToonWriter(out, options);
                try {
                    writer.write(chain(10, Link.LIST));
                } catch (JsonIoException expected) {
                    // refused, within the third list
                }
                writer.write(chain(2, Link.LIST));
                writer.close();
            } else {
                JsonWriter writer = new JsonWriter(out, options);
                try {
                    writer.write(chain(10, Link.LIST));
                } catch (JsonIoException expected) {
                    // refused, within the third list
                }
                writer.write(chain(2, Link.LIST));
                writer.close();
            }
        }
    }

    /** The count follows the path: a wide list of short chains is as deep as one of them. */
    @Test
    void wideIsNotDeep() {
        Node root = new Node();
        root.kids = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            root.kids.add(chain(1, Link.LIST));
        }
        WriteOptions options = new WriteOptionsBuilder().cycleSupport(false).maxObjectGraphDepth(5).build();
        JsonIo.toJson(root, options);
        JsonIo.toToon(root, options);
    }
}
