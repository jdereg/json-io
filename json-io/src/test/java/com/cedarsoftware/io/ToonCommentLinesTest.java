package com.cedarsoftware.io;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A line whose first character, after its leading spaces, is {@code #} is a comment: the reader drops it before it
 * looks at anything else (TOON spec 5.1, new in spec v4.0).
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
class ToonCommentLinesTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    static class Person {
        long id;
        String name;
    }

    static class Team {
        String title;
        List<Person> people;
    }

    private static Object read(String toon, ReadOptions options) {
        return JsonIo.fromToon(toon, options).asClass(Object.class);
    }

    /** The value as JSON without type information: what was read, whatever Java classes hold it. */
    private static String json(Object value) {
        return JsonIo.toJson(value, PLAIN_JSON);
    }

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            Object actual = read(toon, options);
            String mode = options.isStrictToon() ? "strict" : "non-strict";
            assertEquals(json(expected), json(actual), mode + "\n" + toon);
        }
    }

    @Test
    void aCommentIsDroppedWhereverItStands() {
        String toon = "# a heading with \"an open quote and [3]{braces\n"
                + "name: Ada\n"
                + "address:\n"
                + "  # inside the nested object\n"
                + "  city: Oslo\n"
                + "# outdented: it does not close the nested object\n"
                + "  zip: N-0150\n"
                + "tags[2]: a,b\n"
                + "rows[2]{id,name}:\n"
                + "  # before the first row\n"
                + "  1,Ada\n"
                + "  # between the rows: it is not a row\n"
                + "  2,Bob\n"
                + "items[2]:\n"
                + "  - x\n"
                + "  # between the items: it is not an item\n"
                + "  - y\n"
                + "# the last line";

        Object expected = mapOf(
                "name", "Ada",
                "address", mapOf("city", "Oslo", "zip", "N-0150"),
                "tags", listOf("a", "b"),
                "rows", listOf(mapOf("id", 1L, "name", "Ada"), mapOf("id", 2L, "name", "Bob")),
                "items", listOf("x", "y"));
        assertReads(expected, toon);
    }

    /** The indentation of a comment is not checked: three spaces are not a multiple of two. */
    @Test
    void aCommentMayBeIndentedByAnyNumberOfSpaces() {
        assertReads(mapOf("a", 1L, "b", 2L), "a: 1\n   # three spaces\n # one space\nb: 2");
    }

    @Test
    void aDocumentOfOnlyCommentsIsAnEmptyObject() {
        for (ReadOptions options : BOTH_MODES) {
            assertTrue(((Map<?, ?>) read("# one\n\n  # two\n", options)).isEmpty());
            assertTrue(((Map<?, ?>) read("#hello", options)).isEmpty());
        }
    }

    @Test
    void aHashThatDoesNotStartItsLineIsContent() {
        String toon = "a: #x\n"
                + "b: see #12\n"
                + "tags[2]: #a,b\n"
                + "rows[2]{id,color}:\n"
                + "  1,#ff0000\n"
                + "  \"#2\",#00ff00\n"
                + "items[1]:\n"
                + "  - #y";

        Object expected = mapOf(
                "a", "#x",
                "b", "see #12",
                "tags", listOf("#a", "b"),
                "rows", listOf(mapOf("id", 1L, "color", "#ff0000"), mapOf("id", "#2", "color", "#00ff00")),
                "items", listOf("#y"));
        assertReads(expected, toon);
    }

    @Test
    void commentsAreReadWithWindowsLineEndings() {
        assertReads(mapOf("a", 1L, "b", 2L), "# first\r\na: 1\r\n  # second\r\nb: 2\r\n");
    }

    @Test
    void commentsAmongTheRowsOfATypedList() {
        String toon = "title: core\n"
                + "people[2]{id,name}:\n"
                + "  # the founder\n"
                + "  1,Ada\n"
                + "  # joined later\n"
                + "  2,Bob";

        for (ReadOptions options : BOTH_MODES) {
            Team team = JsonIo.fromToon(toon, options).asClass(Team.class);

            assertEquals("core", team.title);
            assertEquals(2, team.people.size());
            assertEquals("Ada", team.people.get(0).name);
            assertEquals(2L, team.people.get(1).id);
        }
    }

    /**
     * Only spaces may come before the #. In strict mode a tab in the indentation is an error of its own; in
     * non-strict mode the tab is one level of indentation (spec 14.4), and the line is a field one level down.
     */
    @Test
    void aLineIndentedWithATabIsNotAComment() {
        String toon = "a:\n\t#x: 2";

        assertThatThrownBy(() -> read(toon, STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Tabs are not allowed in indentation");
        assertEquals(2L, ((Map<?, ?>) ((Map<?, ?>) read(toon, LENIENT)).get("a")).get("#x"));
    }

    /** An error still names the line of the file, comment lines included. */
    @Test
    void anErrorCountsTheCommentLines() {
        String toon = "# one\n# two\nrows[2]{a,b}:\n  1,2\n  # four\n  3";

        assertThatThrownBy(() -> read(toon, STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("width mismatch at line 6");
    }

    /**
     * What changes for stored text: json-io through 4.113.0 wrote a string that starts with # without quotes. Where
     * that string started its line - the first cell of a table row - the line is now a comment. Strict mode reports
     * the missing rows; non-strict mode has no row count to hold it to.
     */
    @Test
    void aRowThatJsonIo4113StartedWithABareHashIsNowAComment() {
        String written4113 = "palette[2]{color,name}:\n  #ff0000,red\n  00ff00,green";

        assertThatThrownBy(() -> read(written4113, STRICT))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("count mismatch");
        assertEquals(json(mapOf("palette", listOf(mapOf("color", "00ff00", "name", "green")))),
                json(read(written4113, LENIENT)));
    }

    /** The writer quotes such a string, so what 4.114.0 writes is never read as a comment. */
    @Test
    void whatTheWriterWritesIsNeverAComment() {
        Object palette = mapOf("palette", listOf(
                mapOf("color", "#ff0000", "name", "red"),
                mapOf("color", "#00ff00", "name", "green")));

        assertReads(palette, JsonIo.toToon(palette, null));
        for (ReadOptions options : BOTH_MODES) {
            assertEquals("#hello", JsonIo.fromToon(JsonIo.toToon("#hello", null), options).asClass(String.class));
        }
    }
}
