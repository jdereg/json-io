package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blank lines (TOON spec 12). A blank line never creates or closes structure and is never counted. Inside a header
 * span -- from an array's or keyed table's first item, row or entry line through the last line of its content -- it
 * is an error in strict mode and ignored in non-strict mode; anywhere else it is ignored in both.
 * <p>
 * json-io took a blank line after {@code key:} for the end of that object: non-strict mode read
 * {@code a:}, a blank line, {@code   b: 1} as {@code {"a": {}}} and dropped the rest, with no error. Strict mode
 * refused a blank line between a header and its first item, which lies before the span.
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
class ToonBlankLinesTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    public static class Address {
        public String city;
        public String zip;
    }

    public static class Person {
        public String name;
        public Address address;
        public List<String> tags;
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /** The value as JSON without type information: what was read, whatever Java classes hold it. */
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

    /** Strict mode names the blank line; non-strict mode reads the document as if the line were not there. */
    private static void assertBlankLineRefused(String toon, int blankLine, String where, Object lenient) {
        assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class)
                .hasMessage("Blank lines are not allowed " + where + " at line " + blankLine);
        assertEquals(json(lenient), json(read(toon, LENIENT)), toon);
    }

    // ---------------------------------------------------------------- outside every header span

    /** The defect: the blank line after "a:" ended the object, and what followed was dropped or refused. */
    @Test
    void aBlankLineBeforeAndBetweenTheFieldsOfANestedObject() {
        assertReads(map("a", map("b", 1L, "c", 2L)), "a:\n\n  b: 1\n\n  c: 2");
        assertReads(map("a", map("b", map("c", 1L)), "d", 2L), "a:\n\n  b:\n   \n    c: 1\n\n\nd: 2");
        assertReads(map("a", map("items", listOf(1L, 2L)), "z", 0L), "a:\n\n  items[2]: 1,2\n\nz: 0");
    }

    @Test
    void aBlankLineBetweenAHeaderAndItsFirstLine() {
        assertReads(map("items", listOf("a", "b")), "items[2]:\n\n  - a\n  - b");
        assertReads(map("items", listOf(map("id", 1L), map("id", 2L))), "items[2]{id}:\n\n\n  1\n  2");
        assertReads(map("m", map("a", map("v", 1L), "b", map("v", 2L))), "m[2:]{v}:\n  \n  a: 1\n  b: 2");
        assertReads(listOf("a", "b"), "[2]:\n\n  - a\n  - b");
    }

    @Test
    void aBlankLineAfterAScopeEndsAndAtTheEdgesOfTheDocument() {
        assertReads(map("items", listOf("a"), "b", 2L, "c", map("d", listOf("x"), "e", 3L), "t", listOf(map("a", 1L)),
                        "m", map("k", map("v", 1L), "l", map("v", 2L)), "z", 0L),
                "items[1]:\n  - a\n\nb: 2\nc:\n  d[1]:\n    - x\n\n  e: 3\nt[1]{a}:\n  1\n\n"
                        + "m[2:]{v}:\n  k: 1\n  l: 2\n\nz: 0");
        assertReads(map("a", 1L, "b", 2L), "\n\na: 1\n\n   \nb: 2\n\n\n");
        assertReads(map("items", listOf(map("b", 2L))), "items[1]:\n  - b: 2\n\n");
        assertReads(map(), "\n\n  \n");
    }

    @Test
    void aTypedReadWithBlankLines() {
        for (ReadOptions options : BOTH_MODES) {
            Person person = JsonIo.fromToon("name: Ada\n\naddress:\n\n  city: London\n\n  zip: N1\n\ntags[2]: a,b\n",
                    options).asClass(Person.class);

            assertEquals("Ada", person.name);
            assertEquals("London", person.address.city);
            assertEquals("N1", person.address.zip);
            assertEquals(listOf("a", "b"), person.tags);
        }
    }

    // ---------------------------------------------------------------- inside a header span

    @Test
    void aBlankLineBetweenItemsRowsOrEntries() {
        assertBlankLineRefused("items[2]:\n  - a\n\n  - b", 3, "inside list arrays", map("items", listOf("a", "b")));
        assertBlankLineRefused("items[2]:\n  - a\n  \n  - b", 3, "inside list arrays", map("items", listOf("a", "b")));
        assertBlankLineRefused("items[2]{id}:\n  1\n\n  2", 3, "inside tabular arrays",
                map("items", listOf(map("id", 1L), map("id", 2L))));
        assertBlankLineRefused("m[2:]{v}:\n  a: 1\n\n  b: 2", 3, "among entry rows",
                map("m", map("a", map("v", 1L), "b", map("v", 2L))));
        assertBlankLineRefused("[2]:\n  - a\n\n\n  - b", 3, "inside list arrays", listOf("a", "b"));
    }

    /** The span runs to the last line of the array's content, however deep that line stands. */
    @Test
    void aBlankLineInsideWhatAnItemHolds() {
        assertBlankLineRefused("items[1]:\n  - a: 1\n\n    b: 2", 3, "inside list arrays",
                map("items", listOf(map("a", 1L, "b", 2L))));
        assertBlankLineRefused("a[1]:\n  - k:\n\n      x: 1", 3, "inside list arrays",
                map("a", listOf(map("k", map("x", 1L)))));
        assertBlankLineRefused("a[2]:\n  - x:\n      y: 1\n\n  - 2", 4, "inside list arrays",
                map("a", listOf(map("x", map("y", 1L)), 2L)));
        // before the inner table's first row: outside the inner span, inside the outer one
        assertBlankLineRefused("outer[2]:\n  - inner[1]{a}:\n\n      1\n  - x", 3, "inside list arrays",
                map("outer", listOf(map("inner", listOf(map("a", 1L))), "x")));
        // after the inner table's last row: the inner span is over, the outer one is not
        assertBlankLineRefused("outer[2]:\n  - inner[2]{a}:\n      1\n      2\n\n  - x", 5, "inside list arrays",
                map("outer", listOf(map("inner", listOf(map("a", 1L), map("a", 2L))), "x")));
        assertBlankLineRefused("outer[2]:\n  - inner[2]:\n      - a\n\n      - b\n  - x", 4, "inside list arrays",
                map("outer", listOf(map("inner", listOf("a", "b")), "x")));
        assertBlankLineRefused("outer[1]:\n  - inner[2]{a}:\n      1\n\n      2", 4, "inside tabular arrays",
                map("outer", listOf(map("inner", listOf(map("a", 1L), map("a", 2L))))));
    }

    // ---------------------------------------------------------------- every position of many documents

    /** A header that opens a scope on the lines below it: nothing follows its colon. */
    private static final Pattern SCOPE_HEADER =
            Pattern.compile("(?:\"(?:[^\"\\\\]|\\\\.)*\"|[^:\\[\\]\"]*)\\[\\d+:?[|\\t]?](?:\\{.*})?:");

    private static int depthOf(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces / 2;
    }

    /**
     * The rule of spec 12, restated on the text of a document json-io wrote: does a blank line put after line
     * {@code after} (counted from 1) lie inside a header span? A span runs from the first line of a header's scope
     * through the last; the scope is the lines that stand deeper than the header does. A header on a hyphen line
     * stands one level below the hyphen when it has a key (it is the item's first field), and at the hyphen's
     * level when it has none (the item is the array).
     */
    private static boolean insideAHeaderSpan(String[] lines, int after) {
        for (int h = 0; h < lines.length; h++) {
            String content = lines[h].trim();
            int depth = depthOf(lines[h]);
            if (content.startsWith("- ")) {
                content = content.substring(2);
                if (!content.startsWith("[")) {
                    depth++;
                }
            }
            if (!SCOPE_HEADER.matcher(content).matches()) {
                continue;
            }
            int last = h;
            while (last + 1 < lines.length && depthOf(lines[last + 1]) > depth) {
                last++;
            }
            int firstLine = h + 2;      // counted from 1: the line after the header
            int lastLine = last + 1;
            if (after >= firstLine && after < lastLine) {
                return true;
            }
        }
        return false;
    }

    private static Object randomValue(Random random, int depth) {
        switch (random.nextInt(depth > 2 ? 3 : 9)) {
            case 0:
                return (long) random.nextInt(100);
            case 1:
                return "s" + random.nextInt(50);
            case 2:
                return random.nextBoolean();
            case 3:
            case 4:
                return randomObject(random, depth + 1);
            case 5: {
                List<Object> scalars = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    scalars.add((long) random.nextInt(10));
                }
                return scalars;                                   // an inline array: no lines below its header
            }
            case 6: {
                List<Object> rows = new ArrayList<>();
                boolean nested = random.nextBoolean();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    rows.add(nested ? map("id", (long) i, "at", map("x", (long) random.nextInt(9)))
                            : map("id", (long) i, "name", "n" + i));
                }
                return rows;                                      // a table
            }
            case 7: {
                List<Object> items = new ArrayList<>();
                for (int i = 1 + random.nextInt(4); i > 0; i--) {
                    items.add(randomValue(random, depth + 1));
                }
                return items;                                     // most often a list
            }
            default: {
                Map<String, Object> entries = new LinkedHashMap<>();
                for (int i = 2 + random.nextInt(2); i > 0; i--) {
                    entries.put("e" + i, map("v", (long) i, "w", "t" + i));
                }
                return entries;                                   // a keyed table
            }
        }
    }

    private static Map<String, Object> randomObject(Random random, int depth) {
        Map<String, Object> object = new LinkedHashMap<>();
        for (int i = 1 + random.nextInt(4); i > 0; i--) {
            object.put("k" + i, randomValue(random, depth));
        }
        return object;
    }

    /**
     * 300 documents from a fixed seed, written by json-io, each with a blank line put at every position in turn.
     * Non-strict mode always reads the document that was written. Strict mode does too, unless the oracle says
     * the position lies inside a header span; then it names that line.
     */
    @Test
    void aBlankLineAtEveryPositionOfManyDocuments() {
        Random random = new Random(20261012L);
        int refused = 0;
        int ignored = 0;
        for (int n = 0; n < 300; n++) {
            Object doc = randomObject(random, 0);
            String toon = JsonIo.toToon(doc, null);
            String expected = json(doc);
            assertEquals(expected, json(read(toon, STRICT)), toon);
            String[] lines = toon.split("\n");

            for (int after = 0; after <= lines.length; after++) {
                String blank = (n + after) % 3 == 0 ? "   \n" : (n + after) % 3 == 1 ? "\n" : "\n\n";
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < lines.length; i++) {
                    if (i == after) {
                        text.append(blank);
                    }
                    text.append(lines[i]).append('\n');
                }
                if (after == lines.length) {
                    text.append(blank);
                }
                String withBlank = text.toString();

                assertEquals(expected, json(read(withBlank, LENIENT)), withBlank);
                if (insideAHeaderSpan(lines, after)) {
                    int blankLine = after + 1;
                    assertThatThrownBy(() -> read(withBlank, STRICT)).as(withBlank)
                            .isInstanceOf(JsonIoException.class)
                            .hasMessageStartingWith("Blank lines are not allowed ")
                            .hasMessageEndingWith(" at line " + blankLine);
                    refused++;
                } else {
                    assertEquals(expected, json(read(withBlank, STRICT)), withBlank);
                    ignored++;
                }
            }
        }
        assertTrue(refused > 500 && ignored > 1500, refused + " refused, " + ignored + " ignored");
    }
}
