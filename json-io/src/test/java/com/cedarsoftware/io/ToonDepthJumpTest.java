package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Depth jumps (TOON spec 8, 9, 12, 14.4). The first line of a nested scope stands exactly one level below the key
 * or header that opens it. A first line deeper than that is a depth jump: an error in strict mode, which json-io
 * read without one; in non-strict mode that line's depth becomes the depth of the scope (recovery 5), and the
 * whole scope reads as if it stood where it should.
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
class ToonDepthJumpTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
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

    /** Strict mode names the line that jumps; non-strict mode reads the scope from where it stands. */
    private static void assertJump(Object expected, String toon, int line) {
        assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("more than one level").hasMessageContaining("at line " + line);
        assertEquals(json(expected), json(read(toon, LENIENT)), toon);
    }

    @Test
    void theFirstFieldOfANestedObject() {
        assertJump(map("a", map("b", 1L)), "a:\n    b: 1", 2);
        assertJump(map("a", map("b", 1L, "c", 2L), "d", 3L), "a:\n      b: 1\n      c: 2\nd: 3", 2);
        assertJump(map("a", map("b", map("c", 1L))), "a:\n  b:\n      c: 1", 3);
        assertJump(map("items", listOf(map("k", map("x", 1L), "n", 2L))),
                "items[1]:\n  - k:\n        x: 1\n    n: 2", 3);
    }

    @Test
    void theFirstItemOfAList() {
        assertJump(map("items", listOf("a", "b")), "items[2]:\n    - a\n    - b", 2);
        assertJump(map("o", map("items", listOf("a")), "z", 1L), "o:\n  items[1]:\n        - a\nz: 1", 3);
        assertJump(listOf("a", "b"), "[2]:\n    - a\n    - b", 2);
        assertJump(map("items", listOf(listOf("a", "b"))), "items[1]:\n  - [2]:\n      - a\n      - b", 3);
    }

    @Test
    void theFirstRowOfATableAndOfAKeyedTable() {
        assertJump(map("rows", listOf(map("a", 1L), map("a", 2L))), "rows[2]{a}:\n    1\n    2", 2);
        assertJump(listOf(map("a", 1L)), "[1]{a}:\n      1", 2);
        assertJump(map("m", map("x", map("v", 1L), "y", map("v", 2L))), "m[2:]{v}:\n    x: 1\n    y: 2", 2);
        assertJump(map("items", listOf(map("rows", listOf(map("a", 1L))))),
                "items[1]:\n  - rows[1]{a}:\n        1", 3);
    }

    /** One level below is where the line belongs: no jump, in either mode. */
    @Test
    void oneLevelBelowIsNoJump() {
        String toon = "a:\n  b:\n    c: 1\nitems[2]:\n  - x\n  - k:\n      y: 2\nrows[1]{a}:\n  1\nm[2:]{v}:\n  p: 1\n  q: 2";
        Object expected = map("a", map("b", map("c", 1L)), "items", listOf("x", map("k", map("y", 2L))),
                "rows", listOf(map("a", 1L)), "m", map("p", map("v", 1L), "q", map("v", 2L)));

        assertEquals(json(expected), json(read(toon, STRICT)));
        assertEquals(json(expected), json(read(toon, LENIENT)));
    }

    // ---------------------------------------------------------------- many documents, every scope pushed deeper

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
                List<Object> rows = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    rows.add(map("id", (long) i, "name", "n" + i));
                }
                return rows;
            }
            case 4: {
                List<Object> items = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    items.add(randomValue(random, depth + 1));
                }
                return items;
            }
            case 5: {
                Map<String, Object> entries = new LinkedHashMap<>();
                for (int i = 2 + random.nextInt(2); i > 0; i--) {
                    entries.put("e" + i, map("v", (long) i, "w", "t" + i));
                }
                return entries;
            }
            default: {
                List<Object> scalars = new ArrayList<>();
                for (int i = 1 + random.nextInt(3); i > 0; i--) {
                    scalars.add((long) random.nextInt(10));
                }
                return scalars;
            }
        }
    }

    private static int depthOf(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces / 2;
    }

    /**
     * 300 documents from a fixed seed, written by json-io. For each line that opens a scope -- a key or a header
     * that is not on a hyphen line, with deeper lines below it -- those deeper lines are pushed one, two or three
     * levels further in, all by the same amount. Non-strict mode reads the document that was written. Strict mode
     * names the first pushed line as a jump.
     */
    @Test
    void everyScopeOfManyDocumentsPushedDeeper() {
        Random random = new Random(20261012L);
        int scopes = 0;
        for (int n = 0; n < 300; n++) {
            Map<String, Object> doc = new LinkedHashMap<>();
            for (int i = 1 + random.nextInt(4); i > 0; i--) {
                doc.put("f" + i, randomValue(random, 0));
            }
            String toon = JsonIo.toToon(doc, null);
            String expected = json(doc);
            String[] lines = toon.split("\n");

            for (int opener = 0; opener + 1 < lines.length; opener++) {
                int depth = depthOf(lines[opener]);
                if (lines[opener].trim().startsWith("-") || !lines[opener].endsWith(":")
                        || depthOf(lines[opener + 1]) <= depth) {
                    continue;
                }
                String push = "      ".substring(0, 2 * (1 + (n + opener) % 3));
                StringBuilder pushed = new StringBuilder();
                boolean inScope = true;
                for (int i = 0; i < lines.length; i++) {
                    if (i > opener && inScope && depthOf(lines[i]) <= depth) {
                        inScope = false;
                    }
                    pushed.append(i > opener && inScope ? push : "").append(lines[i]).append('\n');
                }
                String text = pushed.toString();
                int firstPushed = opener + 2;

                assertEquals(expected, json(read(text, LENIENT)), text);
                assertThatThrownBy(() -> read(text, STRICT)).as(text).isInstanceOf(JsonIoException.class)
                        .hasMessageContaining("more than one level").hasMessageContaining("at line " + firstPushed);
                scopes++;
            }
        }
        assertTrue(scopes > 500, scopes + " scopes");
    }
}
