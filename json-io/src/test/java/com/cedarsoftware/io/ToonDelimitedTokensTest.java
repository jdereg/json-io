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
 * How a line is split into tokens (TOON spec 5.2, 7.4 and 11.2), in an inline array, a table row and a keyed row:
 * <ul>
 * <li>at every unquoted delimiter, keeping empty tokens, which are the empty string -- N delimiters make N + 1
 * tokens;</li>
 * <li>a quote opens a quoted span wherever it stands, also in the middle of a token, and the next quote that is
 * not part of an escape sequence closes it; a delimiter or colon inside a span is part of the token;</li>
 * <li>outside a span a backslash is a character like another;</li>
 * <li>a token that <i>opens</i> with a quote is a complete quoted token, ending at its closing quote, in strict
 * and non-strict mode alike; any other token is taken as it stands.</li>
 * </ul>
 * json-io read an empty table cell as null, let a delimiter at the end of a line pass without a token after it,
 * took a backslash outside quotes for an escape (so {@code C:\,D:\} was one token), refused a quote in the middle of
 * a token in strict mode, and in non-strict mode returned a token with text after its closing quote, quotes and all.
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
class ToonDelimitedTokensTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON = new WriteOptionsBuilder().showTypeInfoNever().build();

    public static class Row {
        public Long id;
        public String text;
        public Integer boxed;
        public int primitive;
    }

    public static class Table {
        public List<Row> rows;
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

    private static void assertRefused(String toon, String message) {
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read(toon, options))
                    .as((options.isStrictToon() ? "strict" : "non-strict") + "\n" + toon)
                    .isInstanceOf(JsonIoException.class).hasMessageContaining(message);
        }
    }

    // ---------------------------------------------------------------- empty tokens

    @Test
    void anEmptyTokenIsTheEmptyString() {
        assertReads(map("rows", listOf(map("a", "x", "b", ""), map("a", "", "b", "y"), map("a", "", "b", ""))),
                "rows[3]{a,b}:\n  x,\n  ,y\n  ,");
        assertReads(map("rows", listOf(map("a", "x", "b", "", "c", "z"))), "rows[1]{a,b,c}:\n  \"x\",,z");
        assertReads(map("rows", listOf(map("a", "x", "w", map("b", "", "c", "")))), "rows[1]{a,w{b,c}}:\n  x,,");
        assertReads(map("m", map("k", map("a", "", "b", "y"), "l", map("a", "x", "b", ""))),
                "m[2:]{a,b}:\n  k: ,y\n  l: x,");
        assertReads(map("a", listOf("x", "", "z"), "b", listOf("", ""), "c", listOf("q", "")),
                "a[3]: x,,z\nb[2]: ,\nc[2]: \"q\",");
        assertReads(map("a", listOf("x", "")), "a[2|]: x|");
    }

    /** A delimiter at the end of a line has a token after it: one more than was declared is a mismatch. */
    @Test
    void theTokenAfterTheLastDelimiterCounts() {
        assertThatThrownBy(() -> read("a[2]: x,y,", STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("expected 2 values, got 3");
        assertThatThrownBy(() -> read("rows[1]{a,b}:\n  x,y,", STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("expected 2 values, got 3");
        // non-strict mode does not hold an array to its declared count (spec 14.4): every value is read
        assertEquals(json(map("a", listOf("x", "y", ""))), json(read("a[2]: x,y,", LENIENT)));
    }

    /** In a declared class an empty cell is what the empty string is anywhere in json-io. */
    @Test
    void anEmptyCellInATypedTable() {
        Table table = JsonIo.fromToon("rows[2]{id,text,boxed,primitive}:\n  1,a,5,6\n  2,,,", STRICT)
                .asClass(Table.class);
        Row quoted = JsonIo.fromToon("id: 2\ntext: \"\"\nboxed: \"\"\nprimitive: \"\"", STRICT).asClass(Row.class);

        Row row = table.rows.get(1);
        assertEquals(2L, row.id);
        assertEquals("", row.text);
        assertEquals(quoted.boxed, row.boxed);
        assertEquals(quoted.primitive, row.primitive);
        assertEquals(5, table.rows.get(0).boxed);
    }

    // ---------------------------------------------------------------- quoted spans

    @Test
    void aQuoteInTheMiddleOfATokenOpensASpan() {
        assertReads(map("k", listOf("a\"b,c\"", "d"), "u", listOf("x\"y,z"), "v", listOf("x\\\"y,z")),
                "k[2]: a\"b,c\",d\nu[1]: x\"y,z\nv[1]: x\\\"y,z");
        assertReads(map("rows", listOf(map("a", "a\"b,c\"", "b", "d"))), "rows[1]{a,b}:\n  a\"b,c\",d");
        assertReads(map("k", "abc\"", "l", "a\"b\"c", "m", "it\"s"), "k: abc\"\nl: a\"b\"c\nm: it\"s");
        assertReads(map("m", map("x,y", map("v", 1L), "z", map("v", "p,q"))), "m[2:]{v}:\n  \"x,y\": 1\n  z: \"p,q\"");
        assertReads(map("a:b", listOf(1L, 2L), "c", "d:e"), "\"a:b\"[2]: 1,2\nc: d:e");
    }

    @Test
    void outsideASpanABackslashIsACharacterLikeAnother() {
        assertReads(map("paths", listOf("C:\\", "D:\\")), "paths[2]: C:\\,D:\\");
        assertReads(map("rows", listOf(map("a", "C:\\", "b", "D:\\"))), "rows[1]{a,b}:\n  C:\\,D:\\");
        assertReads(map("a\\", "b", "k", "x\\y", "l", "\\"), "a\\: b\nk: x\\y\nl: \\");
        assertReads(map("a", listOf("x\\", "\"")), "a[2]: x\\,\"\\\"\"");
    }

    /** The same rules find the colon that ends a key, and the bracket that opens its header. */
    @Test
    void theColonAndTheBracketAfterAKey() {
        // a span in mid-key, closed, then a backslash: the colon and the bracket after it are not escaped
        assertReads(map("a\"b\"\\", 1L), "a\"b\"\\: 1");
        assertReads(map("a\"b\"\\", listOf(1L, 2L)), "a\"b\"\\[2]: 1,2");
        // inside a quoted key an escaped quote does not close it: the colon and the bracket are part of the key
        assertReads(map("a\":b", 1L, "c\"[d", listOf(1L, 2L)), "\"a\\\":b\": 1\n\"c\\\"[d\"[2]: 1,2");
    }

    // ---------------------------------------------------------------- a token that opens with a quote

    @Test
    void aTokenThatOpensWithAQuoteEndsAtItsClosingQuote() {
        assertRefused("k: \"abc\" def", "Characters after the closing quote");
        assertRefused("a[2]: \"x\"y,z", "Characters after the closing quote");
        assertRefused("rows[1]{a,b}:\n  \"x\"y,z", "Characters after the closing quote");
        assertRefused("\"a\"b: 1", "Characters after the closing quote");
        assertRefused("k: \"a\"\"", "Characters after the closing quote");
        assertRefused("items[1]:\n  - \"x\" y", "Characters after the closing quote");
    }

    @Test
    void anUnterminatedStringIsAnError() {
        assertRefused("k: \"abc", "Unclosed quoted string");
        assertRefused("k: \"", "Unclosed quoted string");
        assertRefused("k: \"abc\\\"", "Unclosed quoted string");        // its last quote is escaped
        assertRefused("a[2]: x,\"y", "Unclosed quoted string");
        assertRefused("rows[1]{a,b}:\n  x,\"y", "Unclosed quoted string");
        assertRefused("\"k: 1", "Unclosed quoted string");
        assertRefused("k: \"a\\", "Unclosed quoted string");            // it ends in a backslash
    }

    @Test
    void whatAQuotedTokenHolds() {
        assertReads(map("k", "", "l", "a\"b", "m", "x\\", "n", "a,b: c", "o", "A\tB\nC", "p", " pad "),
                "k: \"\"\nl: \"a\\\"b\"\nm: \"x\\\\\"\nn: \"a,b: c\"\no: \"\\u0041\\tB\\nC\"\np: \" pad \"");
        assertRefused("k: \"a\\x\"", "Invalid escape sequence");
        assertRefused("k: \"\\u12\"", "Invalid escape sequence");
    }

    // ---------------------------------------------------------------- many lines, four contexts

    /** A cell as it stands in the line, and the value it is. */
    private static final class Cell {
        final String text;
        final Object value;

        Cell(String text, Object value) {
            this.text = text;
            this.value = value;
        }
    }

    private static Cell randomCell(Random random, char d, boolean last) {
        switch (random.nextInt(last ? 19 : 18)) {
            case 0:
                return new Cell("abc", "abc");
            case 1:
                return new Cell("12", 12L);
            case 2:
                return new Cell("-3.5", -3.5d);
            case 3:
                return new Cell("true", Boolean.TRUE);
            case 4:
                return new Cell("null", null);
            case 5:
                return new Cell("", "");
            case 6:
                return new Cell(" x ", "x");
            case 7:
                return new Cell("\"\"", "");
            case 8:
                return new Cell("\"a" + d + "b\"", "a" + d + "b");                  // the delimiter, quoted
            case 9:
                return new Cell("\"q\\\"r\"", "q\"r");                              // an escaped quote
            case 10:
                return new Cell(" \"sp ace\" ", "sp ace");
            case 11:
                return new Cell("C:\\", "C:\\");                                    // a backslash, not an escape
            case 12:
                return new Cell("a\\b", "a\\b");
            case 13:
                return new Cell("x\"y" + d + "z\"w", "x\"y" + d + "z\"w");          // a span in mid-token
            case 14:
                return new Cell("it's", "it's");
            case 15:
                return new Cell("\"12\"", "12");
            case 16:
                return new Cell("\"\\\\\"", "\\");
            case 17:
                return new Cell("  ", "");
            default:
                return new Cell("t\"open" + d + "more", "t\"open" + d + "more");    // unclosed: to the line's end
        }
    }

    private static String header(String key, int count, char d, String fields, boolean keyed) {
        String mark = d == ',' ? "" : String.valueOf(d);
        return key + "[" + count + (keyed ? ":" : "") + mark + "]" + (fields == null ? "" : "{" + fields + "}") + ":";
    }

    /**
     * 3,000 lines of four to seven cells from a fixed seed, each cell drawn from the kinds above with its value
     * known in advance, joined by each of the three delimiters and read as an inline array, a table row, a row
     * under a nested field group and a keyed row, in both modes: every reading gives the same list of values.
     */
    @Test
    @SuppressWarnings("unchecked")
    void manyLinesReadTheSameInEveryContext() {
        Random random = new Random(20261012L);
        char[] delimiters = {',', '|', '\t'};
        for (int n = 0; n < 3000; n++) {
            char d = delimiters[n % 3];
            int count = 4 + random.nextInt(4);
            List<Object> expected = new ArrayList<>();
            StringBuilder line = new StringBuilder("c0");
            StringBuilder fields = new StringBuilder("f0");
            StringBuilder grouped = new StringBuilder("f0").append(d).append("w{");
            expected.add("c0");
            for (int i = 1; i < count; i++) {
                Cell cell = randomCell(random, d, i == count - 1);
                line.append(d).append(cell.text);
                expected.add(cell.value);
                fields.append(d).append('f').append(i);
                grouped.append(i == 1 ? "" : String.valueOf(d)).append('f').append(i);
            }
            grouped.append('}');
            String toon = header("a", count, d, null, false) + " " + line + "\n"
                    + header("t", 1, d, fields.toString(), false) + "\n  " + line + "\n"
                    + header("g", 1, d, grouped.toString(), false) + "\n  " + line + "\n"
                    + header("m", 2, d, fields.toString(), true) + "\n  k1: " + line + "\n  k2: " + line;

            for (ReadOptions options : BOTH_MODES) {
                Map<String, Object> doc = JsonIo.fromToon(toon, options).asClass(Map.class);
                Map<String, Object> grouping = ((List<Map<String, Object>>) doc.get("g")).get(0);
                List<Object> flat = new ArrayList<>();
                flat.add(grouping.get("f0"));
                flat.addAll(((Map<String, Object>) grouping.get("w")).values());
                Map<String, Map<String, Object>> keyed = (Map<String, Map<String, Object>>) doc.get("m");

                assertEquals(expected, doc.get("a"), toon);
                Map<String, Object> row = ((List<Map<String, Object>>) doc.get("t")).get(0);
                assertEquals(expected, new ArrayList<>(row.values()), toon);
                assertEquals(expected, flat, toon);
                assertEquals(expected, new ArrayList<>(keyed.get("k1").values()), toon);
                assertEquals(expected, new ArrayList<>(keyed.get("k2").values()), toon);
            }
        }
    }

    /** What json-io writes for strings made of quotes, backslashes, delimiters and colons, it reads back. */
    @Test
    void stringsOfQuotesBackslashesAndDelimitersRoundTrip() {
        Random random = new Random(7L);
        char[] alphabet = "ab\"\\,|:\t ".toCharArray();
        int quoted = 0;
        for (int n = 0; n < 2000; n++) {
            List<Object> strings = new ArrayList<>();
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                StringBuilder string = new StringBuilder();
                for (int length = random.nextInt(6); length > 0; length--) {
                    string.append(alphabet[random.nextInt(alphabet.length)]);
                }
                strings.add(string.toString());
                rows.add(map("id", (long) i, "s", string.toString()));
            }
            Map<String, Object> doc = map("strings", strings, "rows", rows, "last", strings.get(2));
            WriteOptions options = new WriteOptionsBuilder().toonDelimiter(",|\t".charAt(n % 3)).build();

            String toon = JsonIo.toToon(doc, options);

            assertReads(doc, toon);
            if (toon.contains("\"")) {
                quoted++;
            }
        }
        assertTrue(quoted > 1500, quoted + " documents with a quoted string");
    }
}
