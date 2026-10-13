package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which lines belong to what (TOON spec 5, 6, 8, 9, 10, 14). An array's or table's scope is the lines one level
 * below its header; an object's is the lines one level below its key. Two things follow.
 * <ul>
 * <li><b>A declared count never ends a scope or cuts it short</b> (14.1, 14.4). The reader reads every item, row
 * and value the scope holds; strict mode then compares the count, non-strict mode does not. json-io stopped at the
 * count: in non-strict mode the items beyond it were dropped with no error.</li>
 * <li><b>Every line belongs to the value.</b> A line that no scope takes -- indented under a field that holds a
 * primitive, standing among list items without being one, following a root array, a second primitive at the root
 * -- is an error in strict and non-strict mode alike. Non-strict mode has five recoveries (14.4), and none of them
 * drops a line. json-io's non-strict mode dropped every such line, and everything after it, with no error.</li>
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
class ToonScopeTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON =
            new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();

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

    /** Refused in strict and in non-strict mode. */
    private static void assertRefused(String toon) {
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read(toon, options))
                    .as((options.isStrictToon() ? "strict" : "non-strict") + "\n" + toon)
                    .isInstanceOf(JsonIoException.class);
        }
    }

    /** Strict mode refuses the count; non-strict mode reads what the scope holds. */
    private static void assertCountIgnoredOnlyInNonStrictMode(Object expected, String toon, String message) {
        assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class)
                .hasMessageContaining(message);
        assertEquals(json(expected), json(read(toon, LENIENT)), toon);
    }

    // ---------------------------------------------------------------- a count never cuts a scope

    @Test
    void everyItemOfAListIsReadWhateverTheCount() {
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(1L, 2L), "b", listOf("x")),
                "a[1]:\n  - 1\n  - 2\nb[3]:\n  - x", "more elements than declared count 1");
        assertCountIgnoredOnlyInNonStrictMode(map("b", listOf("x"), "z", 1L),
                "b[3]:\n  - x\nz: 1", "expected 3 elements, got 1");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf("x")), "a[0]:\n  - x", "more elements than declared");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(listOf(1L, 2L), map("k", "v"), map())),
                "a[1]:\n  - [2]: 1,2\n  - k: v\n  -", "more elements than declared count 1");
    }

    @Test
    void everyRowOfATableIsReadWhateverTheCount() {
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(map("x", 1L), map("x", 2L))),
                "a[1]{x}:\n  1\n  2", "more rows than declared count 1");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(map("x", 1L, "y", 2L)), "z", 0L),
                "a[5]{x,y}:\n  1,2\nz: 0", "expected 5 rows, got 1");
        assertCountIgnoredOnlyInNonStrictMode(
                map("a", listOf(map("x", 1L, "w", map("y", 2L)), map("x", 3L, "w", map("y", 4L)))),
                "a[1]{x,w{y}}:\n  1,2\n  3,4", "more rows than declared count 1");
        assertCountIgnoredOnlyInNonStrictMode(map("m", map("a", map("v", 1L), "b", map("v", 2L))),
                "m[1:]{v}:\n  a: 1\n  b: 2", "count mismatch");
    }

    @Test
    void everyValueOfAnInlineArrayIsReadWhateverTheCount() {
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(1L, 2L)), "a[0]: 1,2", "expected 0 values, got 2");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(1L, 2L, 3L)), "a[2]: 1,2,3", "expected 2 values, got 3");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(1L)), "a[4]: 1", "expected 4 values, got 1");
    }

    /** A count has no upper bound (spec 6), and is only a claim: it costs no memory. */
    @Test
    void aCountLargerThanAnyTextIsStillACount() {
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(1L)), "a[99999999999999999999]: 1", "count mismatch");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf("x")), "a[2147483647]:\n  - x", "count mismatch");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(map("x", 1L))), "a[2000000000]{x}:\n  1",
                "count mismatch");
        assertCountIgnoredOnlyInNonStrictMode(map("a", listOf(), "b", 1L), "a[2000000000]:\nb: 1", "count mismatch");
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            many.append("k").append(i).append("[2000000000]:\n");
        }
        assertEquals(2000, ((Map<?, ?>) read(many.toString(), LENIENT)).size());
    }

    /** A row's width is another matter than the count of rows: it is checked whatever the mode (14.1). */
    @Test
    void theWidthOfARowIsCheckedInBothModes() {
        assertRefused("items[1]{a}:\n  1,2");
        assertRefused("items[1]{a,b,c}:\n  1,2");
        assertRefused("items[2]{a,b}:\n  1,2\n  3");
        assertRefused("items[1]{a,w{b,c}}:\n  1,2");
        assertRefused("m[1:]{a,b}:\n  k: 1");
    }

    // ---------------------------------------------------------------- every line belongs to the value

    @Test
    void aLineIndentedUnderAPrimitiveBelongsToNothing() {
        assertRefused("a: 1\n  b: 2");
        assertRefused("a: 1\n  b: 2\nc: 3");
        assertRefused("o:\n  a: 1\n    b: 2");
        assertRefused("a[2]: 1,2\n  b: 3");
        assertRefused("items[2]:\n  - a\n    b: 1\n  - c");
        assertRefused("items[1]:\n  - a: 1\n      b: 2");
        assertRefused("items[1]:\n  - a[2]: 1,2\n      b: 2");
        assertRefused("u[1]{a}:\n  1\n    junk: 9");
    }

    @Test
    void aLineAmongListItemsThatIsNoItem() {
        assertRefused("items[1]:\n  - a\n  x: 1");
        assertRefused("k[1]:\n  - x: 1\n  bare");
        assertRefused("items[2]:\n  - a\n  -b");            // a hyphen without its space is no marker
        assertRefused("items[1]:\n  -\tx");
        assertRefused("items[1]:\n  - a: 1\n    b");        // nor a bare token among an item's fields
    }

    /** Items and rows stand below their header, each list's at one depth: not at the header's own, not deeper. */
    @Test
    void itemsAndRowsStandOneLevelBelowTheirHeader() {
        assertRefused("items[1]:\n- a");
        assertRefused("rows[1]{a}:\n1");
        assertRefused("o:\n  items[1]:\n  - a");
        assertRefused("items[2]:\n  - x\n    - y");
        assertRefused("rows[2]{a}:\n  1\n    2");
        assertReads(map("o", map("items", listOf("a"), "k", 1L)), "o:\n  items[1]:\n    - a\n  k: 1");
    }

    /** A bare hyphen is the empty object; a field never stands under it (the first one is on the hyphen line). */
    @Test
    void aFieldUnderABareHyphen() {
        assertReads(map("items", listOf(map(), map("b", 1L), map())), "items[3]:\n  -\n  - b: 1\n  -");
        assertRefused("items[1]:\n  -\n    b: 1");
        assertRefused("items[1]:\n  -\n    $type: ArrayList\n    $items[2]: 1,2");
        assertRefused("items[1]:\n  -\n    [2]: 1,2");
    }

    @Test
    void theRootIsOneValueAtTheLeftMargin() {
        assertRefused("hello\nworld");
        assertRefused("[2]: 1,2\njunk: 3");
        assertRefused("[]\njunk: 3");
        assertRefused("[2:]{v}:\n  a: 1\n  b: 2\nc: 3");
        assertRefused("  hello");
        assertRefused("  [2]: 1,2");
        assertRefused("  a: 1\n  b: 2");
        assertRefused("\n  a: 1");
        assertReads(listOf(1L, 2L), "[2]: 1,2\n\n");
        assertReads("hello", "\nhello\n");
    }

    // ---------------------------------------------------------------- where a header may stand

    @Test
    void aHeaderWithoutAKey() {
        assertRefused("a:\n  [2]: 1,2");
        assertRefused("a:\n  [2]:\n  - x\n  - y");                  // json-io 4.113.0's own map form: see the changelog
        assertRefused("items[1]:\n  - [0]{x}:");
        assertRefused("items[1]:\n  - [1]{x}:\n    1");
        assertRefused("items[1]:\n  - [2:]{v}:\n    a: 1\n    b: 2");
        assertReads(listOf(map("x", 1L)), "[1]{x}:\n  1");
        assertReads(map("items", listOf(listOf(1L, 2L), listOf("a"))), "items[2]:\n  - [2]: 1,2\n  - [1]:\n    - a");
        assertReads(map("items", listOf(map("rows", listOf(map("x", 1L))))), "items[1]:\n  - rows[1]{x}:\n      1");
    }

    /** A header with a field list has nothing after its colon: its rows stand on the lines below. */
    @Test
    void nothingFollowsTheColonOfATableHeader() {
        assertRefused("b[1]{c}: x");
        assertRefused("items[1]:\n  - k[1]{a}: 1");
        assertRefused("m[1:]{v}: a");
        assertReads(map("b", listOf(map("c", "x"))), "b[1]{c}:   \n  x");
    }

    /** Under a list item's first field the rows stand two levels below the hyphen (spec 10). */
    @Test
    void theRowsOfAListItemsFirstField() {
        assertReads(map("x", listOf(map("a", listOf(map("p", 1L)), "b", 2L))),
                "x[1]:\n  - a[1]{p}:\n      1\n    b: 2");
        assertRefused("x[1]:\n  - a[1]{p}:\n    1");
    }

    // ---------------------------------------------------------------- which lines are rows

    @Test
    void aLineAtRowDepthIsARowUnlessAColonComesFirst() {
        assertThatThrownBy(() -> read("items[2]{a,b}:\n  1,2\n  c: 3,4", STRICT)).isInstanceOf(JsonIoException.class);
        assertRefused("items[1]{a,b}:\n  1,2\n  c: 3,4");
        assertReads(map("items", listOf(map("a", "x:y", "b", 1L), map("a", "p", "b", "q:r"))),
                "items[2]{a,b}:\n  \"x:y\",1\n  p,q:r");
        assertReads(map("items", listOf(map("a", "x"), map("a", "y z"))), "items[2]{a}:\n  x\n  y z");
        assertReads(map("items", listOf(map("a", "x")), "k", 1L), "items[1]{a}:\n  x\nk: 1");
    }

    // ---------------------------------------------------------------- the one recovery about depth

    /**
     * Non-strict mode takes the depth of a scope's first line for the depth of the scope (14.4, recovery 5). A
     * later line that stands shallower than that, but not shallow enough to leave the scope, is still an error.
     */
    @Test
    void anAdoptedDepthIsKeptToTheEndOfTheScope() {
        assertEquals(json(map("a", map("b", 1L, "c", 2L), "d", 3L)),
                json(read("a:\n    b: 1\n    c: 2\nd: 3", LENIENT)));
        assertEquals(json(map("rows", listOf(map("a", 1L), map("a", 2L)))),
                json(read("rows[2]{a}:\n    1\n    2", LENIENT)));
        assertEquals(json(map("items", listOf("x", "y"))), json(read("items[2]:\n      - x\n      - y", LENIENT)));
        assertRefused("a:\n    b: 1\n  c: 2");
        assertRefused("rows[2]{a}:\n    1\n  x: 9\n    2");
        assertRefused("items[2]:\n      - x\n    - y");
    }

    // ---------------------------------------------------------------- many documents, every count wrong

    private static final Pattern COUNT = Pattern.compile("\\[(\\d+)(:?[|\\t]?)]");

    private static Object randomValue(Random random, int depth) {
        switch (random.nextInt(depth > 2 ? 2 : 7)) {
            case 0:
                return (long) random.nextInt(100);
            case 1:
                return "s" + random.nextInt(50);
            case 2: {
                Map<String, Object> object = new LinkedHashMap<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    object.put("k" + i, randomValue(random, depth + 1));
                }
                return object;
            }
            case 3: {
                List<Object> scalars = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    scalars.add((long) random.nextInt(10));
                }
                return scalars;
            }
            case 4: {
                List<Object> rows = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    rows.add(map("id", (long) i, "name", "n" + i));
                }
                return rows;
            }
            case 5: {
                List<Object> items = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    items.add(randomValue(random, depth + 1));
                }
                return items;
            }
            default: {
                Map<String, Object> entries = new LinkedHashMap<>();
                for (int i = 2 + random.nextInt(2); i > 0; i--) {
                    entries.put("e" + i, map("v", (long) i, "w", "t" + i));
                }
                return entries;
            }
        }
    }

    /**
     * 300 documents from a fixed seed, written by json-io. Each declared count in turn is made wrong -- one more,
     * one less, zero, or far too many. Non-strict mode still reads the document that was written; strict mode
     * refuses every one of them.
     */
    @Test
    void everyCountOfManyDocumentsMadeWrong() {
        Random random = new Random(20261012L);
        int headers = 0;
        for (int n = 0; n < 300; n++) {
            Map<String, Object> doc = new LinkedHashMap<>();
            for (int i = 1 + random.nextInt(4); i > 0; i--) {
                doc.put("f" + i, randomValue(random, 0));
            }
            String toon = JsonIo.toToon(doc, null);
            String expected = json(doc);
            assertEquals(expected, json(read(toon, STRICT)), toon);

            Matcher header = COUNT.matcher(toon);
            while (header.find()) {
                long declared = Long.parseLong(header.group(1));
                long[] wrong = {declared + 1, declared - 1, 0, 2_000_000_000L};
                long count = wrong[(n + headers) % wrong.length];
                if (count == declared || count < 0) {
                    count = declared + 2;
                }
                String altered = toon.substring(0, header.start(1)) + count + toon.substring(header.end(1));

                assertEquals(expected, json(read(altered, LENIENT)), altered);
                assertThatThrownBy(() -> read(altered, STRICT)).as(altered).isInstanceOf(JsonIoException.class)
                        .hasMessageMatching(".*(count mismatch|more (elements|rows) than declared).*");
                headers++;
            }
        }
        assertTrue(headers > 600, headers + " headers");
    }
}
