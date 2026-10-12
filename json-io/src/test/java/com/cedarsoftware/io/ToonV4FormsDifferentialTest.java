package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whatever form TOON is written in, the same data comes back. Documents and object graphs are made from a fixed
 * seed, leaning towards lists and objects of like-shaped objects -- with a missing key, a null, an extra field or a
 * list thrown in now and then, so that both sides of every rule are met -- and each is written with the forms of
 * spec v4 (keyed tables, nested field groups) and with the legacy forms, then read back in strict and non-strict
 * mode. All four readings must equal what was written.
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
class ToonV4FormsDifferentialTest {

    private static final long SEED = 20261012L;
    private static final String[] NAMES = {"id", "name", "x", "y", "geo", "the key", "a-b", "n1", "v", "tags", "$x"};
    private static final String[] TEXTS = {"", "plain", "two words", "a,b", "x|y", "tab\there", "#hash", "-dash",
            "true", "null", "42", "4.50", " lead", "trail ", "quo\"te", "co:lon", "[1]", "{b}", "\u00e9\u00a0\u00fc",
            "line\nbreak"};

    // ---------------------------------------------------------------- documents of maps and lists

    @Test
    void aDocumentReadsBackTheSameInEitherForm() {
        Random random = new Random(SEED);
        int differing = 0;
        int keyed = 0;
        int grouped = 0;
        long v4Size = 0;
        long legacySize = 0;

        for (int n = 0; n < 400; n++) {
            Object doc = document(random);
            char delimiter = ",|\t".charAt(n % 3);
            boolean references = n % 2 == 0;
            String v4 = JsonIo.toToon(doc, new WriteOptionsBuilder().toonDelimiter(delimiter)
                    .cycleSupport(references).build());
            String legacy = JsonIo.toToon(doc, new WriteOptionsBuilder().toonDelimiter(delimiter)
                    .cycleSupport(references).toonLegacyForms(true).build());

            for (String toon : new String[] {v4, legacy}) {
                for (boolean strict : new boolean[] {true, false}) {
                    Object back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(strict).build())
                            .asClass(Object.class);
                    Map<String, Object> diff = new LinkedHashMap<>();
                    assertTrue(DeepEquals.deepEquals(doc, back, diff), "document " + n
                            + (toon == v4 ? ", v4 forms" : ", legacy forms") + (strict ? ", strict" : ", non-strict")
                            + ": " + diff.get("diff") + "\n" + toon);
                }
            }
            assertFalse(legacy.contains(":]{") || legacy.contains(":|]{") || legacy.contains(":\t]{"),
                    "a keyed header in the legacy forms:\n" + legacy);
            if (!v4.equals(legacy)) {
                differing++;
            }
            if (v4.contains(":]{") || v4.contains(":|]{") || v4.contains(":\t]{")) {
                keyed++;
            }
            if (hasNestedGroup(v4)) {
                grouped++;
            }
            v4Size += v4.length();
            legacySize += legacy.length();
        }

        // The corpus meets the new forms often enough to mean something, and they are the smaller ones
        assertTrue(differing > 150, "documents written differently: " + differing);
        assertTrue(keyed > 60, "documents with a keyed table: " + keyed);
        assertTrue(grouped > 60, "documents with a nested field group: " + grouped);
        assertTrue(v4Size < legacySize, v4Size + " characters in the v4 forms, " + legacySize + " in the legacy forms");
    }

    /** A brace inside a header's braces, outside quotes: a nested field group. */
    private static boolean hasNestedGroup(String toon) {
        for (String line : toon.split("\n")) {
            int open = line.indexOf("]{");
            if (open < 0 || !line.endsWith("}:")) {
                continue;
            }
            boolean quoted = false;
            for (int i = open + 2; i < line.length() - 2; i++) {
                char c = line.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    quoted = !quoted;
                } else if (c == '{' && !quoted) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Object document(Random random) {
        Map<String, Object> root = new LinkedHashMap<>();
        int fields = 1 + random.nextInt(4);
        for (int i = 0; i < fields; i++) {
            root.put("f" + i, value(random, 3));
        }
        // now and then the root itself is an object of like objects
        return random.nextInt(6) == 0 ? family(random, 2, true) : root;
    }

    private static Object value(Random random, int depth) {
        int kind = random.nextInt(10);
        if (depth == 0 || kind < 2) {
            return leaf(random);
        }
        if (kind < 5) {
            return family(random, depth - 1, false);      // a list of like objects
        }
        if (kind < 8) {
            return family(random, depth - 1, true);       // an object of like objects
        }
        if (kind == 8) {
            List<Object> list = new ArrayList<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                list.add(value(random, depth - 1));
            }
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = random.nextInt(4); i > 0; i--) {
            map.put(NAMES[random.nextInt(NAMES.length)], value(random, depth - 1));
        }
        return map;
    }

    /** Objects of one shape, as a list or as the values of an object; one in three families has an odd one out. */
    private static Object family(Random random, int depth, boolean keyed) {
        Shape shape = new Shape(random, depth);
        int size = 1 + random.nextInt(4);
        int oddOne = random.nextInt(3) == 0 ? random.nextInt(size) : -1;
        List<Object> list = new ArrayList<>();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            Object member = shape.instance(random, i == oddOne);
            list.add(member);
            map.put(random.nextInt(5) == 0 ? "key " + i : "k" + i, member);
        }
        return keyed ? map : list;
    }

    private static final class Shape {
        final List<String> names = new ArrayList<>();
        final List<Shape> groups = new ArrayList<>();       // per name: null for a leaf

        Shape(Random random, int depth) {
            int fields = 1 + random.nextInt(4);
            for (int i = 0; i < fields; i++) {
                String name = NAMES[random.nextInt(NAMES.length)];
                if (names.contains(name)) {
                    continue;
                }
                names.add(name);
                groups.add(depth > 0 && random.nextInt(3) == 0 ? new Shape(random, depth - 1) : null);
            }
        }

        Object instance(Random random, boolean odd) {
            Map<String, Object> map = new LinkedHashMap<>();
            int oddField = odd ? random.nextInt(names.size()) : -1;
            int oddKind = random.nextInt(5);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                order.add(random.nextInt(order.size() + 1), i);     // the fields in any order
            }
            for (int i : order) {
                Shape group = groups.get(i);
                Object value = group == null ? leaf(random) : group.instance(random, false);
                if (i == oddField) {
                    switch (oddKind) {
                        case 0:
                            continue;                               // the field is missing
                        case 1:
                            value = group == null ? new LinkedHashMap<>() : null;      // {} or null for an object
                            break;
                        case 2:
                            value = new ArrayList<>(java.util.Arrays.asList(1L, "two"));
                            break;
                        case 3:
                            map.put("extra", leaf(random));
                            break;
                        default:
                            value = group == null ? group(random) : leaf(random);       // an object for a leaf
                            break;
                    }
                }
                map.put(names.get(i), value);
            }
            return map;
        }

        private static Object group(Random random) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("inner", leaf(random));
            return map;
        }
    }

    private static Object leaf(Random random) {
        switch (random.nextInt(8)) {
            case 0:
                return null;
            case 1:
                return random.nextBoolean();
            case 2:
                return (long) random.nextInt(2000) - 1000;
            case 3:
                return random.nextLong();
            case 4:
                return random.nextInt(1000) + 0.5;
            case 5:
                return random.nextInt(100) / 8.0 + 0.0625;
            default:
                return TEXTS[random.nextInt(TEXTS.length)] + (random.nextInt(3) == 0 ? random.nextInt(50) : "");
        }
    }

    // ---------------------------------------------------------------- object graphs

    public static class Point {
        public int x;
        public long y;
        public Double z;
    }

    public static class Customer {
        public String name;
        public Point home;
        public Point work;
    }

    public static class Order {
        public int id;
        public Customer customer;
        public Point at;
        public double total;
        public String note;
    }

    public static class Store {
        public String name;
        public List<Order> orders;
        public Order[] array;
        public Map<String, Point> places;
        public Map<Long, Customer> customers;
        public Customer owner;
        public Order last;
        public Map<String, Object> extra;
    }

    @Test
    void anObjectGraphReadsBackTheSameInEitherFormAndEveryTypeMode() {
        Random random = new Random(SEED + 1);
        int differing = 0;

        for (int n = 0; n < 240; n++) {
            Store store = store(random);
            for (int mode = 0; mode < 4; mode++) {
                boolean references = (n + mode) % 2 == 0;
                boolean skipNulls = (n / 2 + mode) % 2 == 0;
                String what = "graph " + n + ", "
                        + new String[] {"no type info", "minimal", "minimal plus", "always"}[mode]
                        + (references ? ", references" : "") + (skipNulls ? ", nulls left out" : "");
                String v4 = JsonIo.toToon(store, options(mode, references, skipNulls).build());
                String legacy = JsonIo.toToon(store,
                        options(mode, references, skipNulls).toonLegacyForms(true).build());

                for (String toon : new String[] {v4, legacy}) {
                    for (boolean strict : new boolean[] {true, false}) {
                        Store back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(strict).build())
                                .asClass(Store.class);
                        Map<String, Object> diff = new LinkedHashMap<>();
                        assertTrue(DeepEquals.deepEquals(store, back, diff), what
                                + (toon == v4 ? ", v4 forms" : ", legacy forms")
                                + (strict ? ", strict" : ", non-strict") + ": " + diff.get("diff") + "\n" + toon);
                    }
                }
                if (!v4.equals(legacy)) {
                    differing++;
                }
            }
        }
        assertTrue(differing > 300, "graphs written differently: " + differing);
    }

    /** Written twice, the same text: detection reads the objects and leaves nothing behind in the writer's state. */
    @Test
    void writingIsRepeatable() {
        Random random = new Random(SEED + 2);
        for (int n = 0; n < 60; n++) {
            Store store = store(random);
            for (int mode = 0; mode < 4; mode++) {
                WriteOptions options = options(mode, n % 2 == 0, n % 3 == 0).build();
                assertEquals(JsonIo.toToon(store, options), JsonIo.toToon(store, options));
            }
        }
    }

    private static WriteOptionsBuilder options(int mode, boolean references, boolean skipNulls) {
        WriteOptionsBuilder builder = new WriteOptionsBuilder().cycleSupport(references).skipNullFields(skipNulls);
        switch (mode) {
            case 1:
                return builder.showTypeInfoMinimal();
            case 2:
                return builder.showTypeInfoMinimalPlus();
            case 3:
                return builder.showTypeInfoAlways();
            default:
                return builder;
        }
    }

    private static Store store(Random random) {
        Store store = new Store();
        store.name = random.nextInt(4) == 0 ? null : TEXTS[random.nextInt(TEXTS.length)];
        boolean alike = random.nextInt(3) != 0;         // two families in three have no odd one out
        if (random.nextInt(5) != 0) {
            store.orders = new ArrayList<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                store.orders.add(order(random, alike));
            }
        }
        if (random.nextInt(3) == 0) {
            store.array = new Order[1 + random.nextInt(3)];
            for (int i = 0; i < store.array.length; i++) {
                store.array[i] = order(random, alike);
            }
        }
        if (random.nextInt(4) != 0) {
            store.places = new LinkedHashMap<>();
            for (int i = random.nextInt(4); i > 0; i--) {
                store.places.put(random.nextInt(4) == 0 ? "place " + i : "p" + i, point(random, alike));
            }
        }
        if (random.nextInt(3) == 0) {
            store.customers = new LinkedHashMap<>();
            for (int i = 1 + random.nextInt(3); i > 0; i--) {
                store.customers.put((long) random.nextInt(1000), customer(random, alike));
            }
        }
        store.owner = random.nextInt(3) == 0 ? null : customer(random, alike);
        store.last = random.nextInt(3) == 0 ? null : order(random, alike);
        if (random.nextInt(3) == 0) {
            store.extra = new LinkedHashMap<>();
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("p", "x" + random.nextInt(9));
            a.put("q", TEXTS[random.nextInt(TEXTS.length)]);
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("q", "z");
            b.put("p", random.nextBoolean() ? "y" : null);
            store.extra.put("first", a);
            store.extra.put("second", b);
            List<Object> rows = new ArrayList<>();
            rows.add(new LinkedHashMap<>(a));
            rows.add(new LinkedHashMap<>(b));
            store.extra.put("rows", rows);
        }
        return store;
    }

    private static Order order(Random random, boolean alike) {
        Order order = new Order();
        order.id = random.nextInt(10_000);
        order.customer = alike || random.nextInt(3) != 0 ? customer(random, alike) : null;
        order.at = alike || random.nextBoolean() ? point(random, alike) : null;
        order.total = random.nextInt(5000) / 4.0;
        order.note = alike || random.nextBoolean() ? TEXTS[random.nextInt(TEXTS.length)] : null;
        return order;
    }

    private static Customer customer(Random random, boolean alike) {
        Customer customer = new Customer();
        customer.name = TEXTS[random.nextInt(TEXTS.length)];
        customer.home = point(random, alike);
        customer.work = alike || random.nextBoolean() ? point(random, alike) : null;
        return customer;
    }

    private static Point point(Random random, boolean alike) {
        Point point = new Point();
        point.x = random.nextInt(200) - 100;
        point.y = random.nextLong();
        point.z = alike || random.nextBoolean() ? random.nextInt(100) + 0.25 : null;
        return point;
    }
}
