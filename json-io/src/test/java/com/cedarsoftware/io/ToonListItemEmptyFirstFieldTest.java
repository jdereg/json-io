package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A list item that is an object carries its first field on the hyphen line, and that field stands one level below
 * the hyphen (TOON spec 10). When the first field is an empty object, {@code - a:}, the lines that follow at that
 * level are the item's other fields. The reader took them for the content of {@code a}: json-io wrote
 * {@code {"a": {}, "b": 1}} as a list item and read back {@code {"a": {"b": 1}}}, with no error, in both modes.
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
class ToonListItemEmptyFirstFieldTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    public static class Task {
        public Map<String, String> labels;
        public String name;
        public int rank;
    }

    public static class Board {
        public List<Task> tasks = new ArrayList<>();
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

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            String mode = options.isStrictToon() ? "strict" : "non-strict";
            assertEquals(json(expected), json(JsonIo.fromToon(toon, options).asClass(Object.class)),
                    mode + "\n" + toon);
        }
    }

    @Test
    void theLinesAfterAnEmptyFirstFieldAreTheItemsOtherFields() {
        String toon = "items[1]:\n"
                + "  - a:\n"
                + "    b: 1";

        assertReads(map("items", listOf(map("a", map(), "b", 1L))), toon);
    }

    /** What json-io writes for such an item, it reads back. */
    @Test
    void whatTheWriterWritesReadsBack() {
        Object doc = map("items", new ArrayList<>(listOf(
                map("a", map(), "b", 1L, "c", "x"),
                map("only", map()),
                map("first", map(), "second", map(), "third", map("deep", map(), "n", 2L)),
                "tail")));

        String toon = JsonIo.toToon(doc, null);

        assertEquals("items[4]:\n"
                + "  - a:\n"
                + "    b: 1\n"
                + "    c: x\n"
                + "  - only:\n"
                + "  - first:\n"
                + "    second:\n"
                + "    third:\n"
                + "      deep:\n"
                + "      n: 2\n"
                + "  - tail", toon);
        assertReads(doc, toon);
    }

    /** Two levels below the hyphen is the first field's own content, as it was. */
    @Test
    void contentTwoLevelsBelowTheHyphenIsStillTheFirstFieldsOwn() {
        String toon = "items[2]:\n"
                + "  - a:\n"
                + "      x: 1\n"
                + "      inner:\n"
                + "        y: 2\n"
                + "    b: 3\n"
                + "  - a:\n"
                + "      x: 4";

        assertReads(map("items", listOf(
                map("a", map("x", 1L, "inner", map("y", 2L)), "b", 3L),
                map("a", map("x", 4L)))), toon);
    }

    @Test
    void anEmptyFirstFieldMayHaveAQuotedKeyAndStandInANestedList() {
        String toon = "outer[1]:\n"
                + "  - \"my key\":\n"
                + "    rows[2]:\n"
                + "      - e:\n"
                + "        f: 1\n"
                + "      - g: 2\n"
                + "    last: true";

        assertReads(map("outer", listOf(map(
                "my key", map(),
                "rows", listOf(map("e", map(), "f", 1L), map("g", 2L)),
                "last", true))), toon);
    }

    /** An object whose first field is an empty map reads back with its other fields its own. */
    @Test
    void theOtherFieldsOfATypedItemAreFilled() {
        Board board = new Board();
        Task task = new Task();
        task.labels = new LinkedHashMap<>();
        task.name = "write";
        task.rank = 7;
        Task other = new Task();
        other.labels = new LinkedHashMap<>();
        other.labels.put("team", "a");
        other.name = "read";
        board.tasks.add(task);
        board.tasks.add(other);

        String toon = JsonIo.toToon(board, null);

        assertEquals("tasks[2]:\n"
                + "  - labels:\n"
                + "    name: write\n"
                + "    rank: 7\n"
                + "  - labels:\n"
                + "      team: a\n"
                + "    name: read\n"
                + "    rank: 0", toon);
        for (ReadOptions options : BOTH_MODES) {
            Board back = JsonIo.fromToon(toon, options).asClass(Board.class);
            assertEquals(2, back.tasks.size());
            assertTrue(back.tasks.get(0).labels.isEmpty());
            assertEquals("write", back.tasks.get(0).name);
            assertEquals(7, back.tasks.get(0).rank);
            assertEquals("a", back.tasks.get(1).labels.get("team"));
            assertEquals("read", back.tasks.get(1).name);
        }
    }
}
