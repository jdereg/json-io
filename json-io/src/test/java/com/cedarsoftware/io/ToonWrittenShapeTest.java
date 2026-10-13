package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two shapes json-io wrote that TOON does not have, and what it writes in their place.
 * <ul>
 * <li><b>A field on a line of its own under a bare hyphen.</b> An object's first field stands on the hyphen line
 * (spec 10); a bare hyphen is the empty object, and a reader must refuse a field indented under it. json-io wrote a
 * collection or array that carries its {@code $type} or {@code $id}, and a map with keys that are not strings,
 * that way when it was a list item.</li>
 * <li><b>A header without a key under {@code key:}.</b> A keyless header stands only at the root and as a list
 * item (spec 6). json-io wrote a map with keys that are not strings, without type information, as {@code key:},
 * then {@code [N]:}, then one {@code $key} / {@code $value} pair per entry under hyphens at the header's own
 * depth. Such a map is now its keys beside its values, {@code $keys[N]} and {@code $items[N]}, as it always was
 * with type information on, and as json-io writes it in JSON.</li>
 * </ul>
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
class ToonWrittenShapeTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions ALWAYS =
            new WriteOptionsBuilder().showTypeInfoAlways().cycleSupport(false).build();
    private static final WriteOptions MINIMAL_REFS =
            new WriteOptionsBuilder().showTypeInfoMinimal().cycleSupport(true).build();
    private static final WriteOptions TYPED_JSON = new WriteOptionsBuilder().showTypeInfoAlways().build();

    public static class Point {
        public int x;
        public int y;

        public Point() {
        }

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Point && ((Point) other).x == x && ((Point) other).y == y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    public static class Holder {
        public List<Object> things = new ArrayList<>();
        public Map<Object, Object> byKey = new LinkedHashMap<>();
        public Map<Point, String> names = new LinkedHashMap<>();
        public Object any;
    }

    public static class Atlas {
        public List<Map<Point, String>> maps = new ArrayList<>();
    }

    /** A map whose keySet() goes the other way than its values(): the two must not be paired by position. */
    public static class Contrary extends LinkedHashMap<Object, Object> {
        @Override
        public Set<Object> keySet() {
            List<Object> keys = new ArrayList<>(super.keySet());
            Collections.reverse(keys);
            return new LinkedHashSet<>(keys);
        }
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static final Pattern BARE_KEY =
            Pattern.compile(" *(?:\"(?:[^\"\\\\]|\\\\.)*\"|[^:\\[\\]\" ][^:\\[\\]\"]*):");

    private static int indentOf(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    /**
     * What a reader of the spec refuses, looked for in the text itself: a line under a bare hyphen that stands
     * deeper than the hyphen; a header without a key right under "key:"; a list item at its header's own depth;
     * a blank line; a space at the end of a line.
     */
    private static void assertWellShaped(String toon) {
        String[] lines = toon.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            assertFalse(line.trim().isEmpty() && lines.length > 1, "a blank line in\n" + toon);
            assertFalse(line.endsWith(" "), "a space ends line " + (i + 1) + " of\n" + toon);
            if (i + 1 == lines.length) {
                break;
            }
            String next = lines[i + 1];
            if (line.trim().equals("-")) {
                assertTrue(indentOf(next) <= indentOf(line), "a line under the bare hyphen of line " + (i + 1)
                        + " of\n" + toon);
            }
            if (BARE_KEY.matcher(line).matches() && indentOf(next) > indentOf(line)) {
                assertFalse(next.trim().startsWith("["),
                        "a header without a key under line " + (i + 1) + " of\n" + toon);
            }
            if (line.trim().endsWith(":") && line.contains("[") && next.trim().startsWith("-")
                    && !line.trim().startsWith("-")) {
                assertTrue(indentOf(next) > indentOf(line), "a list item at its header's depth, line " + (i + 2)
                        + " of\n" + toon);
            }
        }
    }

    private static <T> void assertRoundTrip(T original, String toon, Class<T> type) {
        for (ReadOptions options : BOTH_MODES) {
            T back = JsonIo.fromToon(toon, options).asClass(type);
            assertEquals(JsonIo.toJson(original, TYPED_JSON), JsonIo.toJson(back, TYPED_JSON),
                    (options.isStrictToon() ? "strict" : "non-strict") + "\n" + toon);
        }
    }

    // ---------------------------------------------------------------- the first field stands on the hyphen line

    @Test
    void aCollectionWithItsTypeAsAListItem() {
        Holder holder = new Holder();
        holder.things.add(new ArrayList<>(listOf(1L, 2L)));
        holder.things.add(new long[] {3, 4});
        holder.things.add(new TreeSet<>(listOf("b", "a")));
        holder.things.add(new LinkedList<>(listOf(new Point(1, 2), "z")));

        String toon = JsonIo.toToon(holder, ALWAYS);

        assertEquals("$type: " + Holder.class.getName() + "\n"
                + "things:\n"
                + "  $type: ArrayList\n"
                + "  $items[4]:\n"
                + "    - $type: ArrayList\n"
                + "      $items[2]: 1,2\n"
                + "    - $type: \"long[]\"\n"
                + "      $items[2]: 3,4\n"
                + "    - $type: TreeSet\n"
                + "      $items[2]: a,b\n"
                + "    - $type: LinkedList\n"
                + "      $items[2]:\n"
                + "        - $type: " + Point.class.getName() + "\n"
                + "          x: 1\n"
                + "          y: 2\n"
                + "        - z\n"
                + "byKey:\n"
                + "  $type: LinkedHashMap\n"
                + "names:\n"
                + "  $type: LinkedHashMap\n"
                + "any: null", toon);
        assertWellShaped(toon);
        assertRoundTrip(holder, toon, Holder.class);
    }

    @Test
    void aSharedCollectionOrMapAsAListItem() {
        List<Object> shared = new ArrayList<>(listOf("s", 1L));
        Map<Object, Object> keyed = new LinkedHashMap<>();
        keyed.put(new Point(1, 1), "one");
        Holder holder = new Holder();
        holder.things.add(shared);
        holder.things.add(keyed);
        holder.things.add(shared);
        holder.things.add(keyed);

        String toon = JsonIo.toToon(holder, MINIMAL_REFS);

        assertTrue(toon.contains("things[4]:\n"
                + "  - $id: 2\n"
                + "    $type: ArrayList\n"
                + "    $items[2]: s,1\n"
                + "  - $id: 1\n"
                + "    $type: LinkedHashMap\n"
                + "    $keys[1]{\"$type\",x,y}:\n"
                + "      " + Point.class.getName() + ",1,1\n"
                + "    $items[1]: one\n"
                + "  - $ref: 2\n"
                + "  - $ref: 1\n"), toon);
        assertWellShaped(toon);
        for (ReadOptions options : BOTH_MODES) {
            Holder back = JsonIo.fromToon(toon, options).asClass(Holder.class);
            assertTrue(back.things.get(0) == back.things.get(2), "one list, twice");
            assertTrue(back.things.get(1) == back.things.get(3), "one map, twice");
            assertEquals(shared, back.things.get(0));
            assertEquals(keyed, back.things.get(1));
        }
    }

    // ---------------------------------------------------------------- a map with keys that are not strings

    @Test
    void aMapWithKeysThatAreNotStringsIsItsKeysBesideItsValues() {
        Holder holder = new Holder();
        holder.byKey.put(new ArrayList<>(listOf(1L, 2L)), "v");
        holder.byKey.put(map("p", 1L), map("q", 2L));
        holder.byKey.put(7L, "seven");
        holder.names.put(new Point(1, 2), "a");
        holder.names.put(new Point(3, 4), "b");
        holder.things.add(new LinkedHashMap<>(holder.names));
        holder.any = new LinkedHashMap<>(holder.names);

        String toon = JsonIo.toToon(holder, null);

        assertEquals("things[1]:\n"
                + "  - $keys[2]{x,y}:\n"
                + "      1,2\n"
                + "      3,4\n"
                + "    $items[2]: a,b\n"
                + "byKey:\n"
                + "  $keys[3]:\n"
                + "    - [2]: 1,2\n"
                + "    - p: 1\n"
                + "    - 7\n"
                + "  $items[3]:\n"
                + "    - v\n"
                + "    - q: 2\n"
                + "    - seven\n"
                + "names:\n"
                + "  $keys[2]{x,y}:\n"
                + "    1,2\n"
                + "    3,4\n"
                + "  $items[2]: a,b\n"
                + "any:\n"
                + "  $keys[2]{x,y}:\n"
                + "    1,2\n"
                + "    3,4\n"
                + "  $items[2]: a,b", toon);
        assertWellShaped(toon);
        for (ReadOptions options : BOTH_MODES) {
            Holder back = JsonIo.fromToon(toon, options).asClass(Holder.class);
            assertEquals(holder.names, back.names, "the declared Map<Point, String>");
            assertEquals(3, back.byKey.size());
            assertEquals("v", back.byKey.get(listOf(1L, 2L)));
            assertEquals("seven", back.byKey.get(7L));
            assertTrue(back.things.get(0) instanceof Map, "without type information, still a map");
            assertTrue(back.any instanceof Map);
        }
    }

    /**
     * In a list the old entry form did not come back a map even where the list declared one: a
     * List<Map<Point, String>> read back holding a list of the entries. It is the map now.
     */
    @Test
    void inADeclaredListOfMaps() {
        Atlas atlas = new Atlas();
        Map<Point, String> names = new LinkedHashMap<>();
        names.put(new Point(1, 2), "a");
        names.put(new Point(3, 4), "b");
        atlas.maps.add(names);
        atlas.maps.add(new LinkedHashMap<>());

        String toon = JsonIo.toToon(atlas, null);

        assertEquals("maps[2]:\n  - $keys[2]{x,y}:\n      1,2\n      3,4\n    $items[2]: a,b\n  -", toon);
        assertWellShaped(toon);
        for (ReadOptions options : BOTH_MODES) {
            Atlas back = JsonIo.fromToon(toon, options).asClass(Atlas.class);
            assertEquals(atlas.maps, back.maps);
            assertTrue(back.maps.get(0).keySet().iterator().next() instanceof Point);
        }
    }

    @Test
    void atTheRoot() {
        Map<Point, String> names = new LinkedHashMap<>();
        names.put(new Point(1, 2), "a");
        names.put(new Point(3, 4), "b");

        String toon = JsonIo.toToon(names, null);

        assertEquals("$keys[2]{x,y}:\n  1,2\n  3,4\n$items[2]: a,b", toon);
        for (ReadOptions options : BOTH_MODES) {
            Map<Point, String> back = JsonIo.fromToon(toon, options).asType(new TypeHolder<Map<Point, String>>() { });
            assertEquals(names, back);
            assertTrue(JsonIo.fromToon(toon, options).asClass(Object.class) instanceof Map);
        }
    }

    /** The n-th key stands beside the n-th value, whatever order the map's own keySet() and values() keep. */
    @Test
    void theKeysAndTheValuesComeFromOnePassOverTheEntries() {
        Contrary contrary = new Contrary();
        contrary.put(new Point(1, 1), "one");
        contrary.put(new Point(2, 2), "two");
        contrary.put(new Point(3, 3), "three");

        String toon = JsonIo.toToon(contrary, null);

        assertEquals("$keys[3]{x,y}:\n  1,1\n  2,2\n  3,3\n$items[3]: one,two,three", toon);
        Map<Point, String> back = JsonIo.fromToon(toon, STRICT).asType(new TypeHolder<Map<Point, String>>() { });
        assertEquals("one", back.get(new Point(1, 1)));
        assertEquals("three", back.get(new Point(3, 3)));
    }

    // ---------------------------------------------------------------- many graphs

    private static Object randomThing(Random random, int depth, List<Object> pool) {
        if (!pool.isEmpty() && random.nextInt(6) == 0) {
            return pool.get(random.nextInt(pool.size()));           // something already in the graph: shared
        }
        Object thing;
        switch (random.nextInt(depth > 2 ? 4 : 10)) {
            case 0:
                return (long) random.nextInt(50);
            case 1:
                return "s" + random.nextInt(20);
            case 2:
                return new Point(random.nextInt(5), random.nextInt(5));
            case 3:
                return random.nextBoolean();
            case 4: {
                List<Object> list = random.nextBoolean() ? new ArrayList<>() : new LinkedList<>();
                for (int i = random.nextInt(4); i > 0; i--) {
                    list.add(randomThing(random, depth + 1, pool));
                }
                thing = list;
                break;
            }
            case 5:
                thing = new TreeSet<>(Arrays.asList("b" + random.nextInt(3), "a" + random.nextInt(3)));
                break;
            case 6:
                thing = new long[] {random.nextInt(9), random.nextInt(9)};
                break;
            case 7: {
                Map<Object, Object> keyed = new LinkedHashMap<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    Object key = random.nextBoolean() ? new Point(i, random.nextInt(3))
                            : random.nextBoolean() ? (Object) (long) i : new ArrayList<>(listOf((long) i, "k"));
                    keyed.put(key, randomThing(random, depth + 1, pool));
                }
                thing = keyed;
                break;
            }
            case 8: {
                Map<String, Object> plain = new LinkedHashMap<>();
                for (int i = random.nextInt(3); i > 0; i--) {
                    plain.put("k" + i, randomThing(random, depth + 1, pool));
                }
                thing = plain;
                break;
            }
            default:
                thing = new Object[] {randomThing(random, depth + 1, pool), "arr"};
        }
        pool.add(thing);
        return thing;
    }

    /**
     * 400 graphs from a fixed seed -- lists, sets, arrays, maps with keys of every kind, some of them held twice --
     * written in six ways: with no type information, with it where the reader needs it and with it everywhere,
     * with references and without. The text has none of the shapes a reader refuses, both modes read it, and with
     * type information what is read is what was written.
     */
    @Test
    void manyGraphsAreWrittenInShapesAReaderAccepts() {
        Random random = new Random(20261012L);
        WriteOptions[] ways = {
                new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(true).build(),
                new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(true).toonLegacyForms(true).build(),
                MINIMAL_REFS,
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().cycleSupport(true).build(),
                new WriteOptionsBuilder().showTypeInfoAlways().cycleSupport(true).build(),
                new WriteOptionsBuilder().showTypeInfoAlways().cycleSupport(true).toonLegacyForms(true).build()};
        int hyphenFirstFields = 0;
        for (int n = 0; n < 400; n++) {
            Holder holder = new Holder();
            List<Object> pool = new ArrayList<>();
            for (int i = 1 + random.nextInt(5); i > 0; i--) {
                holder.things.add(randomThing(random, 0, pool));
            }
            holder.byKey.put(new Point(n, 0), randomThing(random, 1, pool));
            holder.byKey.put("plain", randomThing(random, 1, pool));
            holder.names.put(new Point(n, 1), "n" + n);
            holder.any = randomThing(random, 0, pool);
            String expected = JsonIo.toJson(holder, TYPED_JSON);

            for (int way = 0; way < ways.length; way++) {
                String toon = JsonIo.toToon(holder, ways[way]);

                assertWellShaped(toon);
                for (ReadOptions options : BOTH_MODES) {
                    Holder back = JsonIo.fromToon(toon, options).asClass(Holder.class);
                    if (way >= 4) {
                        assertEquals(expected, JsonIo.toJson(back, TYPED_JSON), toon);
                    } else {
                        assertEquals(holder.things.size(), back.things.size(), toon);
                        assertEquals(holder.names, back.names, toon);
                    }
                }
                if (toon.contains("- $type:") || toon.contains("- $id:") || toon.contains("- $keys[")) {
                    hyphenFirstFields++;
                }
            }
        }
        assertTrue(hyphenFirstFields > 800, hyphenFirstFields + " texts with a meta field on a hyphen line");
    }
}
