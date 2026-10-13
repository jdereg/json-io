package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The elements of a TOON list are read wherever the list stands. TOON's parser hands a list over as a
 * {@code java.util.List}, and only a list that was the value of a field declared as a collection or an array (or the
 * root) had its elements read. Anywhere else -- an Object field, a Map's value, a list inside a list -- the list was
 * passed on as parsed: a {@code $ref} in it stayed an empty map, an element that names its {@code $type} was not
 * built, and the inner lists of a {@code List<List<Integer>>} held Longs. JSON read all of these correctly, so JSON
 * is what TOON is compared with here.
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
class ToonListElementsResolvedTest {

    public static class Point {
        public int x;
        public int y;

        public Point() { }
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Holder {
        public Object any;
        public Map<String, Object> map;
        public List<Object> list;
        public List<List<Object>> lists;
        public List<List<Integer>> numbers;
        public List<List<Point>> points;
        public List<List<Map<String, Point>>> pointMaps;
        public Map<String, List<Object>> byName;
        public Object[] array;
    }

    private static final WriteOptions REFERENCES = new WriteOptionsBuilder().cycleSupport(true).build();
    private static final WriteOptions PLAIN_JSON =
            new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static List<Object> list(Object... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }

    /** What was read, as JSON with no references and no types: equal text is equal content. */
    private static String content(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    /** Written as TOON with references, read back: the content written, in strict and non-strict mode. */
    private static Holder roundTrip(Holder holder, WriteOptions options) {
        String toon = JsonIo.toToon(holder, options);
        Holder strict = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(true).build()).asClass(Holder.class);
        Holder back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(false).build()).asClass(Holder.class);
        assertEquals(content(holder), content(strict), toon);
        assertEquals(content(holder), content(back), toon);
        return back;
    }

    // ---------------------------------------------------------------- references

    @Test
    void aReferenceInAListInAMapIsTheObjectItNames() {
        Map<String, Object> shared = map("p", 1L, "q", "x");
        Holder holder = new Holder();
        holder.map = map("first", shared, "list", list(shared, "tail"));

        String toon = JsonIo.toToon(holder, new WriteOptionsBuilder().cycleSupport(true).skipNullFields(true).build());
        Holder back = roundTrip(holder, REFERENCES);

        assertEquals("map:\n"
                + "  first:\n"
                + "    $id: 1\n"
                + "    p: 1\n"
                + "    q: x\n"
                + "  list[2]:\n"
                + "    - $ref: 1\n"
                + "    - tail", toon);
        assertSame(back.map.get("first"), ((List<?>) back.map.get("list")).get(0), "one map, held twice");
    }

    @Test
    void aReferenceInAListIsResolvedWhereverTheListStands() {
        Map<String, Object> shared = map("p", 1L, "q", "x");
        Point point = new Point(1, 2);

        Holder inAnObjectField = new Holder();
        inAnObjectField.any = list(shared, shared, "tail");
        Holder inAListInAList = new Holder();
        inAListInAList.map = map("first", shared, "list", list(list(shared, "x"), "tail"));
        Holder inTypedLists = new Holder();
        inTypedLists.lists = new ArrayList<>();
        inTypedLists.lists.add(list(shared, "a"));
        inTypedLists.lists.add(list(shared, "b"));
        Holder inAMapInAMap = new Holder();
        inAMapInAMap.map = map("inner", map("list", list(shared, shared)));
        Holder toAnObject = new Holder();
        toAnObject.list = list(point);
        toAnObject.map = map("list", list(point, "tail"));
        Holder inMapValuesAndArrays = new Holder();
        inMapValuesAndArrays.byName = new LinkedHashMap<>();
        inMapValuesAndArrays.byName.put("a", list(shared, list(shared)));
        inMapValuesAndArrays.array = new Object[] {list(shared), "z"};

        List<?> any = (List<?>) roundTrip(inAnObjectField, REFERENCES).any;
        assertSame(any.get(0), any.get(1));

        Holder back = roundTrip(inAListInAList, REFERENCES);
        assertSame(back.map.get("first"), ((List<?>) ((List<?>) back.map.get("list")).get(0)).get(0));

        back = roundTrip(inTypedLists, REFERENCES);
        assertSame(back.lists.get(0).get(0), back.lists.get(1).get(0));

        List<?> innermost = (List<?>) ((Map<?, ?>) roundTrip(inAMapInAMap, REFERENCES).map.get("inner")).get("list");
        assertSame(innermost.get(0), innermost.get(1));

        // with no type info an object in an untyped slot is a map; either way it is one, held twice
        back = roundTrip(toAnObject, REFERENCES);
        assertSame(back.list.get(0), ((List<?>) back.map.get("list")).get(0));
        back = roundTrip(toAnObject, new WriteOptionsBuilder().cycleSupport(true).showTypeInfoMinimal().build());
        assertInstanceOf(Point.class, back.list.get(0));
        assertSame(back.list.get(0), ((List<?>) back.map.get("list")).get(0));

        back = roundTrip(inMapValuesAndArrays, REFERENCES);
        Object first = back.byName.get("a").get(0);
        assertSame(first, ((List<?>) back.byName.get("a").get(1)).get(0));
        assertSame(first, ((List<?>) back.array[0]).get(0));
    }

    /** The $ref may come before the object it names. */
    @Test
    void aForwardReferenceInAListIsResolved() {
        String toon = "map:\n"
                + "  list[2]:\n"
                + "    - $ref: 1\n"
                + "    - tail\n"
                + "  first:\n"
                + "    $id: 1\n"
                + "    p: 1\n"
                + "any[2]:\n"
                + "  - $ref: 1\n"
                + "  - [1]:\n"
                + "    - $ref: 1";

        for (boolean strict : new boolean[] {true, false}) {
            Holder back = JsonIo.fromToon(toon, new ReadOptionsBuilder().strictToon(strict).build())
                    .asClass(Holder.class);
            Object first = back.map.get("first");

            assertEquals("{\"p\":1}", content(first));
            assertSame(first, ((List<?>) back.map.get("list")).get(0));
            assertSame(first, ((List<?>) back.any).get(0));
            assertSame(first, ((List<?>) ((List<?>) back.any).get(1)).get(0));
        }
    }

    @Test
    void aReferenceInAListInsideTheRootListIsResolved() {
        String toon = "[3]:\n"
                + "  - $id: 1\n"
                + "    p: 1\n"
                + "  - [2]:\n"
                + "    - $ref: 1\n"
                + "    - x\n"
                + "  - tail";

        List<?> asList = JsonIo.fromToon(toon, null).asClass(List.class);
        Object[] asObject = (Object[]) JsonIo.fromToon(toon, null).asClass(Object.class);

        assertEquals("[{\"p\":1},[{\"p\":1},\"x\"],\"tail\"]", content(asList));
        assertSame(asList.get(0), ((List<?>) asList.get(1)).get(0));
        assertSame(asObject[0], ((List<?>) asObject[1]).get(0));

        // the $id and the $ref both inside an inner list: the root's own elements carry neither
        String inner = "[2]:\n"
                + "  - [2]:\n"
                + "    - $id: 1\n"
                + "      p: 1\n"
                + "    - $ref: 1\n"
                + "  - tail";
        for (Class<?> type : new Class<?>[] {List.class, Object.class}) {
            Object root = JsonIo.fromToon(inner, null).asClass(type);
            assertEquals("[[{\"p\":1},{\"p\":1}],\"tail\"]", content(root));
        }
    }

    /** Maps mode: a $ref in a list is the very map it names. */
    @Test
    void mapsModeResolvesAReferenceInAList() {
        String toon = "map:\n"
                + "  list[2]:\n"
                + "    - $ref: 1\n"
                + "    - [1]:\n"
                + "      - $ref: 1\n"
                + "  first:\n"
                + "    $id: 1\n"
                + "    p: 1\n"
                + "rows[2]:\n"
                + "  - $ref: 1\n"
                + "  - q: 2";

        Map<?, ?> root = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        Map<?, ?> inner = (Map<?, ?>) root.get("map");
        List<?> list = (List<?>) inner.get("list");

        assertEquals("{\"map\":{\"list\":[{\"p\":1},[{\"p\":1}]],\"first\":{\"p\":1}},\"rows\":[{\"p\":1},{\"q\":2}]}",
                content(root));
        assertSame(inner.get("first"), list.get(0));
        assertSame(inner.get("first"), ((List<?>) list.get(1)).get(0));
        assertSame(inner.get("first"), ((List<?>) root.get("rows")).get(0));
    }

    /** Maps mode: a list among the values of a map written with its keys aside is patched as well. */
    @Test
    void mapsModeResolvesAReferenceInAListAmongAMapsItems() {
        String toon = "first:\n"
                + "  $id: 1\n"
                + "  p: 1\n"
                + "m:\n"
                + "  $keys[1]: k\n"
                + "  $items[1]:\n"
                + "    - [2]:\n"
                + "      - $ref: 1\n"
                + "      - x";

        Map<?, ?> root = JsonIo.fromToonToMaps(toon).asClass(Map.class);
        JsonObject m = (JsonObject) root.get("m");
        List<?> list = (List<?>) m.getItems()[0];

        assertSame(root.get("first"), list.get(0));
        assertEquals("x", list.get(1));
    }

    // ---------------------------------------------------------------- types

    /** A list inside a list is read as its declared type: Integers, not the Longs the parser made. */
    @Test
    void anInnerListHoldsItsDeclaredElements() {
        Holder holder = new Holder();
        holder.numbers = new ArrayList<>();
        holder.numbers.add(new ArrayList<>(Arrays.asList(1, 2)));
        holder.numbers.add(new ArrayList<>(Arrays.asList(3)));
        holder.points = new ArrayList<>();
        holder.points.add(new ArrayList<>(Arrays.asList(new Point(1, 2), new Point(3, 4))));
        holder.points.add(new ArrayList<>(Arrays.asList(new Point(5, 6), null)));

        Holder back = roundTrip(holder, new WriteOptionsBuilder().build());

        for (List<Integer> inner : back.numbers) {
            for (Object number : inner) {
                assertInstanceOf(Integer.class, number);
            }
        }
        int sum = 0;
        for (List<Integer> inner : back.numbers) {
            for (int number : inner) {          // a Long here was a ClassCastException
                sum += number;
            }
        }
        assertEquals(6, sum);
        assertInstanceOf(Point.class, back.points.get(0).get(1));
        assertEquals(4, back.points.get(0).get(1).y);
        assertEquals(5, back.points.get(1).get(0).x);
    }

    /** ... and what its own elements hold: the Points of a List<List<Map<String, Point>>>. */
    @Test
    void anInnerListOfMapsHoldsTheMapsDeclaredValues() {
        Holder holder = new Holder();
        holder.pointMaps = new ArrayList<>();
        Map<String, Point> byName = new LinkedHashMap<>();
        byName.put("a", new Point(1, 2));
        byName.put("b", new Point(3, 4));
        holder.pointMaps.add(new ArrayList<>(Arrays.asList(byName)));

        Holder back = roundTrip(holder, new WriteOptionsBuilder().build());

        assertInstanceOf(Point.class, back.pointMaps.get(0).get(0).get("a"));
        assertEquals(4, back.pointMaps.get(0).get(0).get("b").y);
    }

    /** An element that names its type is built as it, in a list inside a list too. */
    @Test
    void anElementThatNamesItsTypeIsBuilt() {
        Holder holder = new Holder();
        holder.lists = new ArrayList<>();
        holder.lists.add(list(new Point(1, 2), "a"));
        holder.map = map("mixed", "x");
        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().skipNullFields(true).build();

        String toon = JsonIo.toToon(holder, minimal);
        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);

        assertTrue(toon.contains("lists[1]:\n  - [2]:\n    - $type: " + Point.class.getName() + "\n"), toon);
        assertInstanceOf(Point.class, back.lists.get(0).get(0));
        assertEquals(2, ((Point) back.lists.get(0).get(0)).y);
        assertEquals("a", back.lists.get(0).get(1));
    }

    /** The rows of a table that carries a $type column are built too, where nothing declares the list. */
    @Test
    void aTableRowThatNamesItsTypeIsBuiltInAnUntypedList() {
        String toon = "map:\n"
                + "  pts[2]{\"$type\",x,y}:\n"
                + "    " + Point.class.getName() + ",1,2\n"
                + "    " + Point.class.getName() + ",3,4\n"
                + "any[1]:\n"
                + "  - pts[1]{\"$type\",x,y}:\n"
                + "      " + Point.class.getName() + ",5,6";

        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);
        List<?> points = (List<?>) back.map.get("pts");

        assertInstanceOf(Point.class, points.get(0));
        assertEquals(4, ((Point) points.get(1)).y);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) back.any).get(0);
        assertEquals(5, ((Point) ((List<?>) item.get("pts")).get(0)).x);
    }

    // ---------------------------------------------------------------- what did not change

    /** A list of scalars, and the list and map classes of plain data, are what they were. */
    @Test
    void plainListsAreWhatTheyWere() {
        String toon = "any[2]: 1,2\n"
                + "map:\n"
                + "  nums[2]: 1,2.5\n"
                + "  objs[2]:\n"
                + "    - a: 1\n"
                + "    - a: 2\n"
                + "  nested[2]:\n"
                + "    - [2]: x,true\n"
                + "    - [1]:\n"
                + "      - b: null";

        Holder back = JsonIo.fromToon(toon, null).asClass(Holder.class);

        assertEquals("{\"any\":[1,2],\"map\":{\"nums\":[1,2.5],\"objs\":[{\"a\":1},{\"a\":2}],"
                + "\"nested\":[[\"x\",true],[{\"b\":null}]]},\"list\":null,\"lists\":null,\"numbers\":null,"
                + "\"points\":null,\"pointMaps\":null,\"byName\":null,\"array\":null}", content(back));
        assertEquals(ArrayList.class, back.any.getClass());
        assertEquals(Long.class, ((List<?>) back.any).get(0).getClass());
        assertEquals(ArrayList.class, back.map.get("nums").getClass());
        assertEquals(ArrayList.class, back.map.get("objs").getClass());
        assertInstanceOf(Map.class, ((List<?>) back.map.get("objs")).get(0));
        assertEquals(ArrayList.class, ((List<?>) back.map.get("nested")).get(0).getClass());
    }

    // ---------------------------------------------------------------- JSON as the yardstick

    /**
     * Graphs of maps and lists in untyped slots, with maps and objects shared between them, from a fixed seed:
     * written with references and read back, TOON gives the content JSON gives, and as many distinct objects.
     */
    @Test
    void toonReadsSharedObjectsInListsAsJsonDoes() {
        Random random = new Random(20261012L);
        for (int n = 0; n < 300; n++) {
            List<Object> pool = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                pool.add(random.nextBoolean() ? map("id", (long) i, "name", "n" + i) : new Point(i, -i));
            }
            Holder holder = new Holder();
            holder.any = container(random, pool, 3);
            holder.map = map("a", container(random, pool, 3), "b", container(random, pool, 2));
            holder.list = list(container(random, pool, 2), pool.get(random.nextInt(3)));
            holder.lists = new ArrayList<>();
            holder.lists.add(list(pool.get(0), container(random, pool, 1)));
            holder.array = new Object[] {container(random, pool, 2), pool.get(1)};
            boolean typeInfo = n % 2 == 0;
            WriteOptionsBuilder toonOptions = new WriteOptionsBuilder().cycleSupport(true);
            WriteOptionsBuilder jsonOptions = new WriteOptionsBuilder().cycleSupport(true);
            if (typeInfo) {
                toonOptions.showTypeInfoMinimal();
                jsonOptions.showTypeInfoMinimal();
            } else {
                jsonOptions.showTypeInfoNever();
            }

            String toon = JsonIo.toToon(holder, toonOptions.build());
            Holder viaToon = JsonIo.fromToon(toon, null).asClass(Holder.class);
            Holder viaJson = JsonIo.toJava(JsonIo.toJson(holder, jsonOptions.build()), null).asClass(Holder.class);

            assertEquals(content(viaJson), content(viaToon), "graph " + n + "\n" + toon);
            assertEquals(content(holder), content(viaToon), "graph " + n + "\n" + toon);
            assertEquals(distinctObjects(viaJson), distinctObjects(viaToon), "graph " + n + "\n" + toon);
            if (typeInfo) {
                Map<String, Object> diff = new LinkedHashMap<>();
                assertTrue(DeepEquals.deepEquals(holder, viaToon, diff),
                        "graph " + n + ": " + diff.get("diff") + "\n" + toon);
            }
        }
    }

    /** A list or a map of what the pool holds, of scalars, and of further lists and maps. */
    private static Object container(Random random, List<Object> pool, int depth) {
        boolean asList = random.nextBoolean();
        List<Object> list = new ArrayList<>();
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 1 + random.nextInt(3); i > 0; i--) {
            Object value;
            int kind = random.nextInt(6);
            if (kind < 2) {
                value = pool.get(random.nextInt(pool.size()));
            } else if (kind < 4 && depth > 0) {
                value = container(random, pool, depth - 1);
            } else if (kind == 4) {
                value = "s" + random.nextInt(5);
            } else {
                value = (long) random.nextInt(100);
            }
            list.add(value);
            map.put("k" + i, value);
        }
        return asList ? list : map;
    }

    /** How many different maps and objects the graph holds: a shared one counts once. */
    private static int distinctObjects(Object root) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        collect(root, seen);
        return seen.size();
    }

    private static void collect(Object value, IdentityHashMap<Object, Boolean> seen) {
        if (value instanceof Holder) {
            Holder holder = (Holder) value;
            for (Object field : new Object[] {holder.any, holder.map, holder.list, holder.lists, holder.array}) {
                collect(field, seen);
            }
        } else if (value instanceof Map) {
            if (seen.put(value, true) == null) {
                for (Object v : ((Map<?, ?>) value).values()) {
                    collect(v, seen);
                }
            }
        } else if (value instanceof Collection) {
            for (Object v : (Collection<?>) value) {
                collect(v, seen);
            }
        } else if (value instanceof Object[]) {
            for (Object v : (Object[]) value) {
                collect(v, seen);
            }
        } else if (value instanceof Point) {
            seen.put(value, true);
        }
    }
}
