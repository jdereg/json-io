package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.cedarsoftware.io.annotation.IoAnyGetter;
import com.cedarsoftware.io.annotation.IoAnySetter;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The empty object (TOON spec 8 and 9.4): {@code key:} with nothing after it in a field, the bare hyphen in a
 * list, an empty document at the root. {@code {}} is not an object in TOON; a reader takes it for a string.
 * <p>
 * json-io wrote an empty {@code Map} that way, but an object with no field to write as {@code {}}: after
 * {@code key:} on a line of its own at the left margin, which json-io itself could not read back ("Unexpected
 * content after root value"), as {@code - {}} in a list and as {@code {}} at the root.
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
class ToonEmptyObjectTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions SKIP_NULLS =
            new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).skipNullFields(true).build();

    public static class Empty {
    }

    /** With skipNullFields, an instance whose fields are all null has nothing to write either. */
    public static class Sparse {
        public String a;
        public Integer b;
        public Empty inner;
    }

    /** Its fields come from the map: written by another path than a plain object's. */
    public static class Bag {
        private final transient Map<String, Object> extra = new LinkedHashMap<>();

        @IoAnyGetter
        public Map<String, Object> extra() {
            return extra;
        }

        @IoAnySetter
        public void extra(String key, Object value) {
            extra.put(key, value);
        }
    }

    /** No field has an initial value: a field that is not in the text stays null when the object is read. */
    public static class Holder {
        public Empty first;
        public String s;
        public Sparse sparse;
        public List<Empty> empties;
        public Empty[] array;
        public Map<String, Empty> byName;
        public List<Wrapper> wrappers;
        public Bag bag;
        public Empty last;
    }

    public static class Wrapper {
        public Empty inner;
        public String name;
        public Empty tail;

        public Wrapper() {
        }

        Wrapper(String name) {
            this.inner = new Empty();
            this.name = name;
            this.tail = new Empty();
        }
    }

    private static Holder holder() {
        Holder holder = new Holder();
        holder.first = new Empty();
        holder.s = "x";
        holder.sparse = new Sparse();
        holder.empties = new ArrayList<>(listOf(new Empty(), new Empty()));
        holder.array = new Empty[] {new Empty()};
        holder.byName = new LinkedHashMap<>();
        holder.wrappers = new ArrayList<>();
        holder.bag = new Bag();
        holder.last = new Empty();
        holder.byName.put("one", new Empty());
        holder.byName.put("two", new Empty());
        holder.wrappers.add(new Wrapper("w1"));
        holder.wrappers.add(new Wrapper("w2"));
        return holder;
    }

    private static void assertWellFormed(String toon) {
        for (String line : toon.split("\n", -1)) {
            assertFalse(line.trim().isEmpty() && !toon.isEmpty(), "a blank line in\n" + toon);
            assertFalse(line.trim().equals("{}") || line.endsWith(" {}"), "{} written in\n" + toon);
            assertFalse(line.endsWith(" "), "a trailing space in\n" + toon);
        }
    }

    // ---------------------------------------------------------------- writing

    @Test
    void anObjectWithNoFieldsInEveryPosition() {
        String toon = JsonIo.toToon(holder(), SKIP_NULLS);

        assertEquals("first:\n"
                + "s: x\n"
                + "sparse:\n"
                + "empties[2]:\n"
                + "  -\n"
                + "  -\n"
                + "array[1]:\n"
                + "  -\n"
                + "byName:\n"
                + "  one:\n"
                + "  two:\n"
                + "wrappers[2]:\n"
                + "  - inner:\n"
                + "    name: w1\n"
                + "    tail:\n"
                + "  - inner:\n"
                + "    name: w2\n"
                + "    tail:\n"
                + "bag:\n"
                + "last:", toon);
        assertWellFormed(toon);
    }

    @Test
    void whatIsWrittenIsReadBack() {
        String toon = JsonIo.toToon(holder(), SKIP_NULLS);

        for (ReadOptions options : BOTH_MODES) {
            Holder back = JsonIo.fromToon(toon, options).asClass(Holder.class);

            assertNotNull(back.first);
            assertEquals("x", back.s);
            assertNotNull(back.sparse);
            assertNull(back.sparse.a);
            assertNull(back.sparse.inner);
            assertEquals(2, back.empties.size());
            assertSame(Empty.class, back.empties.get(1).getClass());
            assertEquals(1, back.array.length);
            assertNotNull(back.array[0]);
            assertEquals(listOf("one", "two"), new ArrayList<>(back.byName.keySet()));
            assertSame(Empty.class, back.byName.get("two").getClass());
            assertEquals(2, back.wrappers.size());
            assertEquals("w2", back.wrappers.get(1).name);
            assertNotNull(back.wrappers.get(1).inner);
            assertNotNull(back.wrappers.get(1).tail);
            assertNotNull(back.bag);
            assertNotNull(back.last);
            assertEquals(toon, JsonIo.toToon(back, SKIP_NULLS), "and written again, the same text");
        }
    }

    /**
     * The other places where an object follows "key:": the end of a folded key, and the key and the value of an
     * entry in json-io's own form for a map whose keys are not strings.
     */
    @Test
    void afterAFoldedKeyAndInAMapWithKeysThatAreNotStrings() {
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("b", new Wrapper("w"));
        Map<String, Object> cd = new LinkedHashMap<>();
        cd.put("d", new Empty());
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("a", ab);
        doc.put("c", cd);

        assertEquals("a.b:\n"
                + "  inner:\n"
                + "  name: w\n"
                + "  tail:\n"
                + "c.d:", JsonIo.toToon(doc, new WriteOptionsBuilder().toonKeyFolding(true).build()));

        Map<Object, Object> complex = new LinkedHashMap<>();
        complex.put(new ArrayList<>(listOf(1L, 2L)), new Wrapper("v"));
        complex.put(new Wrapper("k"), new Empty());
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("complex", complex);
        holder.put("z", 1L);

        String toon = JsonIo.toToon(holder, null);

        assertEquals("complex:\n"
                + "  [2]:\n"
                + "  - \n"
                + "    $key:\n"
                + "      [2]: 1,2\n"
                + "    $value:\n"
                + "      inner:\n"
                + "      name: v\n"
                + "      tail:\n"
                + "  - \n"
                + "    $key:\n"
                + "      inner:\n"
                + "      name: k\n"
                + "      tail:\n"
                + "    $value:\n"
                + "z: 1", toon);
        for (ReadOptions options : BOTH_MODES) {
            Map<String, Object> back = JsonIo.fromToon(toon, options).asClass(Map.class);
            assertEquals(1L, back.get("z"));
            assertEquals(2, ((List<?>) back.get("complex")).size());
        }
    }

    /** At the root: an empty document, as an empty Map is. */
    @Test
    void anEmptyObjectAtTheRoot() {
        assertEquals("", JsonIo.toToon(new Empty(), null));
        assertEquals("", JsonIo.toToon(new Sparse(), SKIP_NULLS));
        assertEquals("", JsonIo.toToon(new Bag(), null));
        assertEquals("", JsonIo.toToon(new LinkedHashMap<>(), null));
        assertSame(Empty.class, JsonIo.fromToon("", STRICT).asClass(Empty.class).getClass());
    }

    /** Type information or an id is something to write: the object is not empty then, and reads back as itself. */
    @Test
    void withItsTypeOrItsIdTheObjectIsNotEmpty() {
        WriteOptions typed = new WriteOptionsBuilder().showTypeInfoAlways().cycleSupport(false).build();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("e", new Empty());
        doc.put("list", new ArrayList<>(listOf(new Empty(), "y")));

        String toon = JsonIo.toToon(doc, typed);

        assertWellFormed(toon);
        assertTrue(toon.contains("e:\n  $type: " + Empty.class.getName()), toon);
        Map<String, Object> back = JsonIo.fromToon(toon, STRICT).asClass(Map.class);
        assertSame(Empty.class, back.get("e").getClass());
        assertSame(Empty.class, ((List<?>) back.get("list")).get(0).getClass());

        Empty shared = new Empty();
        Map<String, Object> twice = new LinkedHashMap<>();
        twice.put("a", shared);
        twice.put("b", shared);
        String refs = JsonIo.toToon(twice, new WriteOptionsBuilder().showTypeInfoAlways().cycleSupport(true).build());
        assertWellFormed(refs);
        Map<String, Object> same = JsonIo.fromToon(refs, STRICT).asClass(Map.class);
        assertSame(same.get("a"), same.get("b"));
        assertSame(Empty.class, same.get("a").getClass());
    }

    // ---------------------------------------------------------------- reading

    /** {} is a string wherever it stands (spec 7.4): only "key:" alone and the bare hyphen are empty objects. */
    @Test
    void bracesAreAString() {
        for (ReadOptions options : BOTH_MODES) {
            Map<String, Object> doc = JsonIo.fromToon("e: {}\nlist[2]:\n  - {}\n  -\na[2]: {},x\nt[1]{c}:\n  {}\nk:",
                    options).asClass(Map.class);

            assertEquals("{}", doc.get("e"));
            assertEquals("{}", ((List<?>) doc.get("list")).get(0));
            assertTrue(((Map<?, ?>) ((List<?>) doc.get("list")).get(1)).isEmpty());
            assertEquals(listOf("{}", "x"), doc.get("a"));
            assertEquals("{}", ((Map<?, ?>) ((List<?>) doc.get("t")).get(0)).get("c"));
            assertTrue(((Map<?, ?>) doc.get("k")).isEmpty());
            assertEquals("{}", JsonIo.fromToon("{}", options).asClass(Object.class));
        }
    }

    /** The string "{}" is written in quotes (it holds braces), so it comes back the string it was. */
    @Test
    void theStringOfTwoBracesKeepsItsQuotes() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("e", "{}");
        doc.put("list", new ArrayList<>(listOf("{}", new LinkedHashMap<>())));

        String toon = JsonIo.toToon(doc, null);

        assertEquals("e: \"{}\"\nlist[2]:\n  - \"{}\"\n  -", toon);
        assertEquals(doc, JsonIo.fromToon(toon, STRICT).asClass(Map.class));
        assertEquals("\"{}\"", JsonIo.toToon("{}", null));
    }

    // ---------------------------------------------------------------- many graphs

    private static Wrapper randomWrapper(Random random) {
        Wrapper wrapper = new Wrapper(random.nextBoolean() ? null : "n" + random.nextInt(9));
        wrapper.inner = random.nextBoolean() ? null : new Empty();
        wrapper.tail = random.nextBoolean() ? null : new Empty();
        return wrapper;
    }

    private static Holder randomHolder(Random random) {
        Holder holder = new Holder();
        holder.first = random.nextBoolean() ? null : new Empty();
        holder.s = random.nextBoolean() ? null : "s" + random.nextInt(9);
        holder.sparse = random.nextInt(3) == 0 ? null : new Sparse();
        if (holder.sparse != null) {
            holder.sparse.a = random.nextBoolean() ? null : "a";
            holder.sparse.b = random.nextBoolean() ? null : random.nextInt(9);
            holder.sparse.inner = random.nextBoolean() ? null : new Empty();
        }
        holder.empties = new ArrayList<>();
        holder.byName = new LinkedHashMap<>();
        holder.wrappers = new ArrayList<>();
        holder.bag = new Bag();
        for (int i = random.nextInt(4); i > 0; i--) {
            holder.empties.add(new Empty());
        }
        holder.array = new Empty[random.nextInt(3)];
        for (int i = 0; i < holder.array.length; i++) {
            holder.array[i] = new Empty();
        }
        for (int i = random.nextInt(3); i > 0; i--) {
            holder.byName.put("k" + i, new Empty());
        }
        for (int i = random.nextInt(4); i > 0; i--) {
            holder.wrappers.add(randomWrapper(random));
        }
        if (random.nextBoolean()) {
            holder.bag.extra.put("x", (long) random.nextInt(9));
        }
        holder.last = random.nextBoolean() ? null : new Empty();
        return holder;
    }

    /**
     * 400 graphs from a fixed seed in which objects with nothing to write stand in every position, with nulls
     * written and with nulls skipped: the text has no blank line, no {} and no trailing space, strict mode reads
     * it, and what is read writes the same text again.
     */
    @Test
    void manyGraphsOfEmptyAndNearlyEmptyObjects() {
        Random random = new Random(20261012L);
        WriteOptions withNulls = new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();
        int bareKeys = 0;
        for (int n = 0; n < 400; n++) {
            Holder holder = randomHolder(random);
            for (WriteOptions options : new WriteOptions[] {withNulls, SKIP_NULLS}) {
                String toon = JsonIo.toToon(holder, options);

                assertWellFormed(toon);
                Holder back = JsonIo.fromToon(toon, STRICT).asClass(Holder.class);
                assertEquals(toon, JsonIo.toToon(back, options), toon);
                for (String line : toon.split("\n")) {
                    if (line.endsWith(":")) {
                        bareKeys++;
                    }
                }
            }
        }
        assertTrue(bareKeys > 2_000, bareKeys + " empty objects written as a key alone");
    }
}
