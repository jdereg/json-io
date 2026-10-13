package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Line endings (TOON spec 12): a document is split at LF, and a single CR at the end of a line belongs to the line
 * ending, so CRLF input reads like LF input. A CR anywhere else -- in the middle of a line, or a second one before
 * the LF -- is content. json-io ended a line at every CR: {@code a: x\r\r\n} read as {@code x} followed by a blank
 * line, and a value with a CR in it was cut in two lines.
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
class ToonLineEndingsTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

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

    /** Read from a String and from UTF-8 bytes, in both modes: all the same. */
    private static void assertReads(Object expected, String toon) {
        String shown = toon.length() > 300 ? toon.substring(0, 300) + "..." : toon;
        shown = shown.replace("\r", "\\r").replace("\n", "\\n");
        for (ReadOptions options : BOTH_MODES) {
            String mode = (options.isStrictToon() ? "strict: " : "non-strict: ") + shown;
            assertEquals(json(expected), json(JsonIo.fromToon(toon, options).asClass(Object.class)), mode);
            ByteArrayInputStream bytes = new ByteArrayInputStream(toon.getBytes(StandardCharsets.UTF_8));
            assertEquals(json(expected), json(JsonIo.fromToon(bytes, options).asClass(Object.class)), mode);
        }
    }

    private static Map<String, Object> sample() {
        return map(
                "name", "Ada",
                "tags", new ArrayList<>(listOf("a", "b c", 3L)),
                "address", map("city", "London", "geo", map("lat", 51.5, "lon", -0.1)),
                "orders", new ArrayList<>(listOf(map("id", 1L, "customer", map("name", "x", "country", "DK")),
                        map("id", 2L, "customer", map("name", "y", "country", "UK")))),
                "servers", map("alpha", map("host", "a", "port", 80L), "beta", map("host", "b", "port", 81L)),
                "mixed", new ArrayList<>(listOf(map("k", 1L, "more", new ArrayList<>(listOf(1L, 2L))), "s",
                        new ArrayList<>(listOf("p", "q")))),
                "empty", map(),
                "last", "end");
    }

    // ---------------------------------------------------------------- CRLF is a line ending

    @Test
    void aDocumentWithCrLfReadsLikeTheSameDocumentWithLf() {
        Map<String, Object> doc = sample();
        String toon = JsonIo.toToon(doc, null);

        assertFalse(toon.contains("\r"));
        assertReads(doc, toon);
        assertReads(doc, toon.replace("\n", "\r\n"));
        assertReads(doc, toon.replace("\n", "\r\n") + "\r\n");
        assertReads(doc, toon.replace("\n", "\r\n") + "\r");
        // some lines one way, some the other
        String[] lines = toon.split("\n");
        StringBuilder mixed = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            mixed.append(lines[i]).append(i % 2 == 0 ? "\r\n" : "\n");
        }
        assertReads(doc, mixed.toString());
    }

    @Test
    void blankLinesAndCommentsWithCrLf() {
        assertReads(map("a", 1L, "b", map("c", 2L)), "# note\r\n\r\na: 1\r\n   \r\nb:\r\n\r\n  # inner\r\n  c: 2\r\n");
        assertReads(map(), "\r\n\r\n");
        assertReads(8L, "8\r\n");
    }

    // ---------------------------------------------------------------- any other CR is content

    @Test
    void aCarriageReturnInsideALineIsContent() {
        assertReads(map("a", "x\ry", "b", 1L), "a: x\ry\nb: 1");
        assertReads(map("a", "1\rb: 2"), "a: 1\rb: 2");
        assertReads(map("k\ry", 1L), "k\ry: 1");
        assertReads(map("a", listOf("x\r", "y", "\rz")), "a[3]: x\r,y,\rz");
        assertReads(map("rows", listOf(map("p", "x\ry", "q", 1L))), "rows[1]{p,q}:\n  x\ry,1");
    }

    /** Only one CR goes with the LF: a second one before it stays with the value. */
    @Test
    void aSecondCarriageReturnBeforeTheLineFeedIsContent() {
        assertReads(map("a", "x\r", "b", 1L), "a: x\r\r\nb: 1");
        assertReads(map("a", "x\r\r", "b", 1L), "a: x\r\r\r\nb: 1\r\n");
        assertReads(map("a", "x\r"), "a: x\r\r");
        assertReads(map("a", "x"), "a: x\r");
    }

    // ---------------------------------------------------------------- long input

    /**
     * Lines of every length from 1 to 60, four thousand of them, and then a line far longer than any buffer: line
     * endings fall at every place of the reader's buffer, and lines cross from one fill of it to the next.
     */
    @Test
    void lineEndingsAtEveryPlaceOfTheBuffer() {
        Random random = new Random(20261012L);
        Map<String, Object> doc = new LinkedHashMap<>();
        for (int n = 0; n < 4000; n++) {
            StringBuilder value = new StringBuilder();
            for (int length = 1 + random.nextInt(60); length > 0; length--) {
                value.append((char) ('a' + random.nextInt(26)));
            }
            doc.put("k" + n, value.toString());
        }
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 50_000; i++) {
            huge.append((char) ('a' + i % 26));
        }
        doc.put("huge", huge.toString());
        doc.put("after", "x");
        String toon = JsonIo.toToon(doc, null);

        assertReads(doc, toon);
        assertReads(doc, toon.replace("\n", "\r\n"));
        assertReads(doc, toon.replace("\n", "\r\n") + "\r\n");

        // and with a carriage return inside the long line, and one more before its line feed
        Map<String, Object> withCr = new LinkedHashMap<>(doc);
        withCr.put("huge", huge.substring(0, 20_000) + "\r" + huge.substring(20_000) + "\r");
        String text = toon.replace("huge: " + huge, "huge: " + huge.substring(0, 20_000) + "\r"
                + huge.substring(20_000) + "\r").replace("\n", "\r\n");
        assertReads(withCr, text);
    }

    /**
     * A last line without a line feed, of every length around the powers of two up to 131,072: whatever room the
     * reader has for a line (it grows by doubling, and is kept from one document to the next), one of these fills
     * it exactly as the input ends.
     */
    @Test
    void aLastLineWithoutALineFeedOfEveryAwkwardLength() {
        for (int power = 2; power <= 17; power++) {
            for (int lineLength = (1 << power) - 2; lineLength <= (1 << power) + 2; lineLength++) {
                StringBuilder value = new StringBuilder();
                while (value.length() + "last: ".length() < lineLength) {
                    value.append((char) ('a' + value.length() % 26));
                }
                if (value.length() == 0) {
                    continue;
                }
                Map<String, Object> doc = map("first", 1L, "last", value.toString());

                assertReads(doc, "first: 1\nlast: " + value);
                assertReads(doc, "first: 1\r\nlast: " + value + "\r");
            }
        }
    }

    // ---------------------------------------------------------------- writing

    /** json-io writes LF and no CR: a CR in a string is written escaped, inside quotes, and read back. */
    @Test
    void theWriterWritesNoCarriageReturn() {
        Map<String, Object> doc = map("a", "x\ry", "b", "line1\r\nline2", "c", "\r", "k\rey", 1L,
                "list", new ArrayList<>(listOf("p\r", "\rq")));

        String toon = JsonIo.toToon(doc, null);

        assertFalse(toon.contains("\r"), toon);
        assertEquals("a: \"x\\ry\"\nb: \"line1\\r\\nline2\"\nc: \"\\r\"\n\"k\\rey\": 1\n"
                + "list[2]: \"p\\r\",\"\\rq\"", toon);
        assertReads(doc, toon);
        assertReads(doc, toon.replace("\n", "\r\n"));
    }
}
