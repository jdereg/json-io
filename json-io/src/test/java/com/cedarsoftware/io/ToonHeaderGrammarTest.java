package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The header grammar (TOON spec 5.2 and 6). A line with an unquoted bracket before its first unquoted colon is a
 * header, and must be one by the grammar: {@code key[N]:}, with an optional colon after the length for a keyed
 * table, an optional delimiter symbol (a tab or a pipe), an optional field list, and nothing else -- no sign or
 * space in the length, no space before the bracket, between bracket, field list and colon, or between a field
 * name and its nested group. A line that is not is an error <i>in strict and non-strict mode alike</i>.
 * <p>
 * json-io read several of these as headers in both modes ({@code items[2] : 1,2}, {@code x[+3]: a,b,c},
 * {@code foo [2]: a,b} with the key {@code "foo "}), and its non-strict mode read a header it could not parse as a
 * key that happens to hold a bracket: {@code items[abc]: 1,2,3} gave the key {@code items[abc]} with the string
 * {@code 1,2,3} for its value, and no error.
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
class ToonHeaderGrammarTest {

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions PLAIN_JSON =
            new WriteOptionsBuilder().showTypeInfoNever().cycleSupport(false).build();

    /** Spec 6: length = "0" / ( %x31-39 *DIGIT ). */
    private static final Pattern LENGTH = Pattern.compile("0|[1-9][0-9]*");

    /** What a refusal of a count says; any other message refuses the header itself. */
    private static final Pattern COUNT_REFUSED = Pattern.compile(".*(count mismatch|than declared count).*");

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

    private static String shown(String toon) {
        StringBuilder out = new StringBuilder();
        for (char c : toon.toCharArray()) {
            out.append(c == '\t' ? "<TAB>" : c == '\n' ? "<LF>" : c > 126 ? String.format("\\u%04x", (int) c)
                    : String.valueOf(c));
        }
        return out.toString();
    }

    private static void assertReads(Object expected, String toon) {
        for (ReadOptions options : BOTH_MODES) {
            assertEquals(json(expected), json(read(toon, options)),
                    (options.isStrictToon() ? "strict " : "non-strict ") + shown(toon));
        }
    }

    /** Refused as a header in strict and in non-strict mode. */
    private static void assertNoHeader(String toon) {
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read(toon, options))
                    .as((options.isStrictToon() ? "strict " : "non-strict ") + shown(toon))
                    .isInstanceOf(JsonIoException.class)
                    .hasMessageNotContaining("count mismatch").hasMessageNotContaining("than declared count");
        }
    }

    // ---------------------------------------------------------------- the bracket segment, every combination

    /**
     * 3,456 header lines: every combination of sixteen lengths, a keyed colon or none, six delimiter symbols, a
     * space or a tab or nothing before the bracket, a space or nothing after it, and no field list, one, or one
     * with a space before the colon. The line is a header exactly when the grammar says so. A header is read in
     * non-strict mode, and in strict mode either read or refused for its count; anything else is refused in
     * both modes, and not for its count.
     */
    @Test
    void everyCombinationOfTheBracketSegment() {
        String[] lengths = {"2", "0", "10", "02", "00", "+2", "-2", "2.0", "", "x", "٢", "２", " 2", "2 ",
                "2147483648", "99999999999999999999"};
        String[] keyedMarks = {"", ":"};
        String[] symbols = {"", "|", "\t", ",", ";", "||"};
        String[] beforeBracket = {"", " ", "\t"};
        String[] afterBracket = {"", " "};
        String[] fieldLists = {"", "{x}", "{x} "};
        int headers = 0;
        int refused = 0;
        for (String length : lengths) {
            for (String keyed : keyedMarks) {
                for (String symbol : symbols) {
                    for (String before : beforeBracket) {
                        for (String after : afterBracket) {
                            for (String fields : fieldLists) {
                                boolean valid = LENGTH.matcher(length).matches()
                                        && (symbol.isEmpty() || symbol.equals("|") || symbol.equals("\t"))
                                        && before.isEmpty() && after.isEmpty() && !fields.endsWith(" ")
                                        && (keyed.isEmpty() || !fields.isEmpty());
                                String line = "k" + before + "[" + length + keyed + symbol + "]" + after + fields
                                        + ":" + (fields.isEmpty() ? " a" : "");
                                if (valid) {
                                    Object value = ((Map<?, ?>) read(line, LENIENT)).get("k");
                                    String expected = !keyed.isEmpty() ? "{}" : fields.isEmpty() ? "[\"a\"]" : "[]";
                                    assertEquals(expected, json(value), shown(line));
                                    try {
                                        read(line, STRICT);
                                    } catch (JsonIoException e) {
                                        assertTrue(COUNT_REFUSED.matcher(e.getMessage()).matches(),
                                                shown(line) + ": " + e.getMessage());
                                    }
                                    headers++;
                                } else {
                                    assertNoHeader(line);
                                    refused++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // five lengths, three symbols, and a plain header, a table or a keyed table
        assertEquals(45, headers);
        assertEquals(3456 - 45, refused);
    }

    // ---------------------------------------------------------------- the field list

    @Test
    void theFieldListsThatAreFieldLists() {
        assertReads(map("t", listOf(map("x", 1L))), "t[1]{x}:\n  1");
        assertReads(map("t", listOf(map("x", 1L, "y", 2L))), "t[1]{ x , y }:\n  1,2");
        assertReads(map("t", listOf(map("a,b", 1L, "c", 2L))), "t[1]{\"a,b\",c}:\n  1,2");
        assertReads(map("t", listOf(map("x", map("y", 1L)))), "t[1]{x{y}}:\n  1");
        assertReads(map("t", listOf(map("x", map("y", 1L, "z", 2L), "w", 3L))), "t[1]{x{y,z},w}:\n  1,2,3");
        assertReads(map("t", listOf(map("x", map("y", map("z", 1L))))), "t[1]{x{y{z}}}:\n  1");
        assertReads(map("t", listOf(map("x y", map("z", 1L)))), "t[1]{\"x y\"{z}}:\n  1");
        assertReads(map("t", listOf(map("x", 1L, "y", 2L))), "t[1|]{x|y}:\n  1|2");
        assertReads(map("t", listOf(map("x", 1L, "y", 2L))), "t[1\t]{x\ty}:\n  1\t2");
        assertReads(map("t", listOf(map("x,y", 1L))), "t[1|]{\"x,y\"}:\n  1");
    }

    @Test
    void theFieldListsThatAreNot() {
        String[] lists = {"{}", "{ }", "{x,}", "{,x}", "{x,,y}", "{x, ,y}", "{x{}}", "{{y}}", "{x,{y}}", "{x {y}}",
                "{x{y}z}", "{x{y}{z}}", "{x", "{x{y}", "{x}}", "{x|y}", "{x\ty}", "{x{y|z}}"};
        for (String fields : lists) {
            assertNoHeader("t[0]" + fields + ":");
            assertNoHeader("items[1]:\n  - t[0]" + fields + ":");
            assertNoHeader("m[0:]" + fields + ":");
        }
        assertNoHeader("t[0|]{x,y}:");
        assertNoHeader("t[0\t]{x|y}:");
        assertNoHeader("[0]{}:");
    }

    /** The one thing the mode decides in a field list: a name that stands twice (spec 14.3, 14.4). */
    @Test
    void aFieldNameTwice() {
        assertThatThrownBy(() -> read("t[1]{x,x}:\n  1,2", STRICT)).isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Duplicate field name 'x'");
        Map<?, ?> table = (Map<?, ?>) read("t[1]{x,x}:\n  1,2", LENIENT);
        Map<?, ?> row = (Map<?, ?>) ((List<?>) table.get("t")).get(0);
        assertEquals(2L, row.get("x"), "the last one wins");
        assertReads(map("t", listOf(map("x", 1L, "n", map("x", 2L)))), "t[1]{x,n{x}}:\n  1,2");
    }

    // ---------------------------------------------------------------- what is a header line at all

    @Test
    void aBracketBeforeTheFirstColonMakesTheLineAHeader() {
        assertNoHeader("a[b]: 1");
        assertNoHeader("a[: 1");
        assertNoHeader("a[1: 2");
        assertNoHeader("a[2:]{x}");                 // its only colon stands inside the bracket: still a header line
        assertNoHeader("a[2:]:");                   // a keyed header needs its field list
        assertNoHeader("a[1][2]: x");
        assertNoHeader("a[2]extra: x,y");
        assertNoHeader("items[1]:\n  - k [1]: x");
        assertNoHeader("\"a\" [2]: x,y");
    }

    @Test
    void whatIsNoHeaderLine() {
        assertReads("a[2]", "a[2]");                                    // no colon: a scalar line
        assertReads(map("items", listOf("[2]")), "items[1]:\n  - [2]");
        assertReads(map("a[1]", "x", "b", "c[1]: d"), "\"a[1]\": x\nb: c[1]: d");
        assertReads(map("a]", 1L), "a]: 1");
        assertReads(map("n ", listOf("y")), "n [1]: y");      // any other character belongs to the key
        assertReads(map("a b", listOf("y")), "a b[1]: y");
        assertReads(map("", listOf("y")), "\"\"[1]: y");
        // the key of a keyed row is the text before its colon, whatever it holds (spec 9.5)
        assertReads(map("m", map("r[x]", map("v", 1L), "s", map("v", 2L))), "m[2:]{v}:\n  r[x]: 1\n  s: 2");
    }

    /** The refusal says what is wrong with the header. */
    @Test
    void theRefusalNamesTheDefect() {
        String[][] cases = {
                {"k[-2]: a", "Array count must not be negative"},
                {"k[02]: a,b", "Array count with leading zeros"},
                {"k[+2]: a,b", "Invalid array count"},
                {"k[2 ]: a,b", "Invalid array count"},
                {"k[2] : a,b", "unexpected content after bracket segment"},
                {"k[1]{x} :", "unexpected content after fields segment"},
                {"k [2]: a,b", "whitespace between the key and its bracket segment"},
                {"k[2:]: a", "Malformed keyed header (no field list)"},
                {"k[x:]{v}:", "Malformed keyed header (length)"},
                {"k[1]{}:", "Malformed array header (field list)"},
                {"k[1]{x|y}:", "Delimiter mismatch in tabular header"},
                {"k[2: a", "Malformed array syntax"}};
        for (String[] each : cases) {
            for (ReadOptions options : BOTH_MODES) {
                assertThatThrownBy(() -> read(each[0], options)).as(shown(each[0]))
                        .isInstanceOf(JsonIoException.class).hasMessageContaining(each[1]);
            }
        }
    }

    /** The lines json-io's non-strict mode read as a key and its value, with no error. */
    @Test
    void aMalformedHeaderIsNoKeyWithABracketInIt() {
        String[] lines = {"items[2] : 1,2", "a[1]{x} :\n  1", "x[+3]: a,b,c", "k[٢]: a,b", "foo [2]: bar,baz",
                "t\t[1]: x", "a[0]{x,}:", "items[0]{}:", "a[0|]{x\ty}:", "a[0]{x|y}:", "a[0]{b{c}d}:", "a[2:]{x}"};
        for (String line : lines) {
            assertNoHeader(line);
            try {
                Object value = read(line, LENIENT);
                fail(shown(line) + " was read as " + json(value));
            } catch (JsonIoException expected) {
                // refused, as the spec has it
            }
        }
    }
}
