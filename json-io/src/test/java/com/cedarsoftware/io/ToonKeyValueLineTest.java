package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The key-value line (TOON spec 5.2, 7.4, 9.1): the first unquoted colon ends the key, and what follows it is one
 * value.
 * <ul>
 * <li>The key may be empty: {@code : 1} is {@code {"": 1}}. json-io failed on such a line with an internal error
 * ("Error parsing TOON value") in a field, and took it for a string at the root and in a list.</li>
 * <li>The value has no structure of its own. Only the token {@code []} means something there, the empty array;
 * {@code a: [2]: x} is the string {@code [2]: x}. json-io read an array from it (and failed on this one in strict
 * mode, for holding one value where two were declared). A header belongs before the colon: {@code a[2]: x,y}.</li>
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
class ToonKeyValueLineTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    // (no references: the expected values use one empty list in several places)
    private static final WriteOptions PLAIN_JSON =
            new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();

    public static class Counts {
        public Map<String, Integer> byName;
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static String json(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    private static Object read(String toon, ReadOptions options) {
        return JsonIo.fromToon(toon, options).asClass(Object.class);
    }

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            assertEquals(json(expected), json(read(toon, options)),
                    (options.isStrictToon() ? "strict" : "non-strict") + "\n" + toon);
        }
    }

    // ---------------------------------------------------------------- the empty key

    @Test
    void theEmptyKeyAtTheRoot() {
        assertReads(map("", 1L), ": 1");
        assertReads(map("x", 1L, "", 2L, "y", 3L), "x: 1\n: 2\ny: 3");
        assertReads(map("", map()), ":");
        assertReads(map("", "a:b"), ": a:b");
        assertReads(map("", listOf(1L, 2L)), "\"\"[2]: 1,2");
    }

    @Test
    void theEmptyKeyInANestedObject() {
        assertReads(map("a", map("", 2L)), "a:\n  : 2");
        assertReads(map("a", map("b", 1L, "", map("c", 3L))), "a:\n  b: 1\n  :\n    c: 3");
    }

    @Test
    void theEmptyKeyInAListItem() {
        assertReads(map("items", listOf(map("", 3L))), "items[1]:\n  - : 3");
        assertReads(map("items", listOf(map("a", 1L, "", 2L), map("", map()))), "items[2]:\n  - a: 1\n    : 2\n  - :");
        assertReads(map("items", listOf(map("", "smile:"))), "items[1]:\n  - :smile:");
    }

    @Test
    void theEmptyKeyOfAKeyedRow() {
        assertReads(map("m", map("", map("v", 4L), "k", map("v", 5L))), "m[2:]{v}:\n  : 4\n  k: 5");
    }

    /** The official case, whole. */
    @Test
    void theEmptyKeyInEveryPositionAtOnce() {
        assertReads(map("", 1L, "a", map("", 2L), "items", listOf(map("", 3L)), "m", map("", map("v", 4L))),
                ": 1\na:\n  : 2\nitems[1]:\n  - : 3\nm[1:]{v}:\n  : 4");
    }

    /** Quoted or not, it is one key: twice in an object is a duplicate. */
    @Test
    void theEmptyKeyTwice() {
        String toon = "x: 1\n: 2\n\"\": 3";

        assertThatThrownBy(() -> read(toon, STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Duplicate key ''");
        assertEquals(json(map("x", 1L, "", 3L)), json(read(toon, LENIENT)));
    }

    /** json-io writes the empty key in quotes (spec 7.3), and reads either form. */
    @Test
    void theEmptyKeyIsWrittenInQuotesAndReadBack() {
        Counts counts = new Counts();
        counts.byName = new LinkedHashMap<>();
        counts.byName.put("", 7);
        counts.byName.put("a", 8);
        Map<String, Object> doc = map("", 1L,
                "table", new ArrayList<>(listOf(map("", "x", "k", "y"))),
                "list", new ArrayList<>(listOf(map("", "x"), map("k", "y", "", "z"))));

        String toon = JsonIo.toToon(doc, null);
        String typed = JsonIo.toToon(counts, null);

        assertEquals("\"\": 1\n"
                + "table[1]{\"\",k}:\n"
                + "  x,y\n"
                + "list[2]:\n"
                + "  - \"\": x\n"
                + "  - k: y\n"
                + "    \"\": z", toon);
        assertReads(doc, toon);
        assertEquals("byName:\n  \"\": 7\n  a: 8", typed);
        for (ReadOptions options : BOTH_MODES) {
            assertEquals(counts.byName, JsonIo.fromToon(typed, options).asClass(Counts.class).byName);
            Counts bare = JsonIo.fromToon("byName:\n  : 7\n  a: 8", options).asClass(Counts.class);
            assertEquals(counts.byName, bare.byName);
        }
    }

    /** At row depth a line with a colon and no delimiter is no row (spec 9.3), also when its key is empty. */
    @Test
    void aLineWithAnEmptyKeyIsNoTableRow() {
        assertThatThrownBy(() -> read("t[2]{a}:\n  1\n  : 2", STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("expected 2 rows, got 1");
        assertThatThrownBy(() -> read("t[1]{a}:\n  1\n  : 2", STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageNotContaining("more rows than declared");
    }

    // ---------------------------------------------------------------- the value is one value

    @Test
    void aValueThatStartsWithABracketIsAString() {
        assertReads(map("a", "[2]: x"), "a: [2]: x");
        assertReads(map("a", "[2]: x,y", "b", "[2]{x}:", "c", "[0]:", "d", "[ ]", "e", "[x", "f", "[2]", "g", "]"),
                "a: [2]: x,y\nb: [2]{x}:\nc: [0]:\nd: [ ]\ne: [x\nf: [2]\ng: ]");
        assertReads(map("items", listOf(map("a", "[2]: x", "b", "[1]: z"))), "items[1]:\n  - a: [2]: x\n    b: [1]: z");
        assertReads(map("o", map("a", "[1]: z")), "o:\n  a: [1]: z");
    }

    @Test
    void onlyTheEmptyArrayTokenIsAnArrayThere() {
        assertReads(map("a", listOf(), "b", listOf(), "c", map("d", listOf())), "a: []\nb:   []   \nc:\n  d: []");
        assertReads(map("items", listOf(map("a", listOf(), "b", listOf()))), "items[1]:\n  - a: []\n    b: []");
    }

    /** Where a header may stand it still does: before the colon, at the root, and as a list item. */
    @Test
    void aHeaderWhereAHeaderBelongs() {
        assertReads(map("a", listOf("x", "y")), "a[2]: x,y");
        assertReads(listOf("x", "y"), "[2]: x,y");
        assertReads(listOf(), "[]");
        assertReads(map("items", listOf(listOf(), listOf("x", "y"), "z")), "items[3]:\n  - []\n  - [2]: x,y\n  - z");
    }

    /**
     * As a field of an object a header needs its key (spec 6): one without is an error in both modes, not an
     * array under the empty key. That array is written with its key in quotes.
     */
    @Test
    void aHeaderWithoutAKeyIsNoField() {
        String[] documents = {"a: 1\n[2]: x,y", "a[1]:\n  - b: 1\n    [2]: x,y", "o:\n  a: 1\n  [1]{c}:\n    1"};
        for (String toon : documents) {
            for (ReadOptions options : BOTH_MODES) {
                assertThatThrownBy(() -> read(toon, options)).as(toon).isInstanceOf(JsonIoException.class)
                        .hasMessageContaining("An array header without a key cannot be a field");
            }
        }
        assertReads(map("a", 1L, "", listOf("x", "y")), "a: 1\n\"\"[2]: x,y");
        assertReads(map("a", listOf(map("b", 1L, "", listOf("x", "y")))), "a[1]:\n  - b: 1\n    \"\"[2]: x,y");
    }

    /** In a row or an inline array [] is a cell like another: the string of two brackets (spec 9.3). */
    @Test
    void inACellTheEmptyArrayTokenIsAString() {
        assertReads(map("t", listOf(map("a", "[]", "b", "x"))), "t[1]{a,b}:\n  [],x");
        assertReads(map("a", listOf("[]", "x")), "a[2]: [],x");
        assertReads(map("m", map("k", map("v", "[]"), "l", map("v", 1L))), "m[2:]{v}:\n  k: []\n  l: 1");
    }

    /** What json-io writes for an array in a field has its header before the colon, and reads back. */
    @Test
    void whatTheWriterWritesForArraysInFields() {
        Map<String, Object> doc = map(
                "empty", new ArrayList<>(),
                "scalars", new ArrayList<>(listOf(1L, "two")),
                "nested", new ArrayList<>(listOf(new ArrayList<>(listOf(1L, 2L)), new ArrayList<>())),
                "text", "[2]: x",
                "brackets", "[]");

        String toon = JsonIo.toToon(doc, null);

        assertEquals("empty: []\n"
                + "scalars[2]: 1,two\n"
                + "nested[2]:\n"
                + "  - [2]: 1,2\n"
                + "  - [0]:\n"
                + "text: \"[2]: x\"\n"
                + "brackets: \"[]\"", toon);
        assertReads(doc, toon);
    }
}
