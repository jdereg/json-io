package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The only whitespace TOON knows is the space, U+0020 (spec 12). Around a token, and at the end of a line, spaces
 * are removed and nothing else: a tab, a no-break space or any other character is part of the token. json-io
 * removed every character up to the space, so a tab at either end of a value or a key was lost, the last cell of a
 * tab-delimited row went with it when it was empty, and a tab after a closing quote was let through.
 * <p>
 * Indentation is spaces as well. A tab in it is an error in strict mode; in non-strict mode it counts as one level
 * (spec 14.4, recovery 3), where json-io took a tab-indented line for one at the left margin.
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
class ToonSpacesOnlyTest {

    private static final String NBSP = "\u00a0";
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

    // ---------------------------------------------------------------- tokens

    @Test
    void aValueKeepsEverythingButTheSpacesAroundIt() {
        assertReads(map("a", "v\t"), "a: v\t");
        assertReads(map("a", "\tv"), "a: \tv");
        assertReads(map("a", NBSP + "v" + NBSP), "a: " + NBSP + "v" + NBSP);
        assertReads(map("a", "v\u000b"), "a: v\u000b");
        assertReads(map("a", "\u0001"), "a: \u0001");
        assertReads(map("a", "v"), "a:    v   ");
        assertReads(map("a", "v\t"), "a: v\t   ");          // the spaces after the tab end the line; the tab does not
        assertReads(map("a", "\t1"), "a:\t1");              // no number: the tab is part of the token
    }

    @Test
    void aKeyKeepsEverythingButTheSpacesAroundIt() {
        assertReads(map("a" + NBSP, 1L, "b\t", 2L, "c", 3L), "a" + NBSP + ": 1\nb\t: 2\nc  : 3");
        // (a tab in a field list is another delimiter than the header declares, and an error of its own)
        assertReads(map("t", listOf(map(NBSP + "c", 1L, "d" + NBSP, 2L, "e", 3L))),
                "t[1]{" + NBSP + "c,d" + NBSP + ", e }:\n  1,2,3");
        assertReads(map("m", map("x\t", map("v", 1L), "y", map("v", 2L))), "m[2:]{v}:\n  x\t: 1\n  y  : 2");
        assertReads(map("items", listOf(map("k\t", 1L))), "items[1]:\n  - k\t: 1");
    }

    @Test
    void theTokensOfAnArrayAndTheCellsOfARow() {
        assertReads(map("items", listOf(NBSP + "a" + NBSP, "\tb", "c")),
                "items[3]: " + NBSP + "a" + NBSP + ",\tb ,  c");
        assertReads(map("rows", listOf(map("a", "x\t", "b", "\ty"))), "rows[1]{a,b}:\n  x\t, \ty");
        assertReads(map("list", listOf("x\t", "\ty")), "list[2]:\n  - x\t\n  -  \ty");
    }

    /** A line that holds a quoted token is split another way; what is trimmed is the same. */
    @Test
    void aLineWithAQuotedTokenIsTrimmedTheSameWay() {
        assertReads(map("a", listOf("q", "\tx\t", "y")), "a[3]: \"q\", \tx\t , y");
        assertReads(map("rows", listOf(map("a", "q", "b", "\tx\t", "c", "y"))),
                "rows[1]{a,b,c}:\n  \"q\", \tx\t , y");
    }

    @Test
    void theFieldsOfAListItemAndItsInlineArrays() {
        assertReads(map("items", listOf(map("k", "\tv", "b", "\tw", "c", "x\t"))),
                "items[1]:\n  - k:\tv\n    b:\tw\n    c: x\t");
        assertReads(map("items", listOf(map("k", "v\t"))), "items[1]:\n  - k: v\t");
        assertReads(map("items", listOf(map("\tk", listOf(1L, 2L)))), "items[1]:\n  - \tk[2]: 1,2");
        assertReads(map("items", listOf(listOf("x", "y\t"))), "items[1]:\n  - [2]: x,y\t");
        // a tab before the bracket is no part of a key (spec 6: no whitespace between a key and its bracket), and
        // the item is no array either: the line is refused
        for (ReadOptions options : BOTH_MODES) {
            assertThatThrownBy(() -> read("items[1]:\n  - \t[2]: x,y", options)).isInstanceOf(JsonIoException.class);
        }
        assertReads(map("items", listOf(map("\tk", listOf("x", "y")))), "items[1]:\n  - \tk[2]: x,y");
        assertReads(map("m", map("x", map("v", "\t1"), "y", map("v", 2L))), "m[2:]{v}:\n  x:\t1\n  y: 2");
    }

    /** With a tab beside it, [] is no empty array and {} no empty object: each is the text it is. */
    @Test
    void aTokenThatOnlyResemblesAnEmptyArray() {
        assertReads(map("a", "\t[]", "b", "[]\t"), "a: \t[]\nb: []\t");
        assertReads(map("a", "\t{}", "b", "{}\t"), "a: \t{}\nb: {}\t");
        assertReads("\u000bx", "\u000bx");
    }

    /** Spaces go, and the character next to them stays, also when both stand at the edge of a name. */
    @Test
    void spacesBesideAnotherCharacterAtTheEdgeOfAName() {
        assertReads(map("b\t", 2L), "b\t : 2");
        assertReads(map("t", listOf(map("\u0001c", 1L, "d\u0001", 2L))), "t[1]{ \u0001c ,d\u0001 }:\n  1,2");
        assertReads(map("t", listOf(map("b\u0001", map("c", 1L)))), "t[1]{b\u0001{c}}:\n  1");
    }

    /** Where the header grammar allows nothing at all, a tab or a control character is not waved through. */
    @Test
    void noCharacterIsSkippedWhereTheHeaderAllowsNone() {
        String[] headers = {"a[2]\t: x,y", "t[1]{a{b}\u0001,c}:\n  1,2", "a[\u00012]: x,y", "a[2\u0001]: x,y"};
        for (String toon : headers) {
            assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class)
                    .hasMessageMatching("(Malformed array header|Invalid array count).*");
        }
        // Non-strict mode, whatever it makes of such a line: the control character is not trimmed off the
        // length, so the line is no header of an array of two under the key a.
        String[] lengths = {"a[\u00012]: x,y", "a[2\u0001]: x,y", "a[ \u00012]: x,y", "a[2\u0001 ]: x,y"};
        for (String toon : lengths) {
            Object doc;
            try {
                doc = read(toon, LENIENT);
            } catch (JsonIoException e) {
                continue;
            }
            assertFalse(doc instanceof Map && ((Map<?, ?>) doc).get("a") instanceof List, toon);
        }
    }

    /** With the tab as delimiter, a tab at the end of the line is a delimiter, and an empty token follows it. */
    @Test
    void theLastTokenOfATabDelimitedLineMayBeEmpty() {
        assertReads(map("a", listOf("x", "")), "a[2\t]: x\t");
        assertReads(map("a", listOf("x", "", "")), "a[3\t]: x\t\t");
        assertReads(map("a", listOf("", "x")), "a[2\t]: \tx");
        assertReads(map("a", listOf("x", "y")), "a[2\t]: x \t y ");
        assertEquals(2, ((Map<?, ?>) ((List<?>) ((Map<?, ?>) read("rows[1\t]{a\tb}:\n  x\t", STRICT)).get("rows"))
                .get(0)).size(), "the row has its two cells");
    }

    /** A token that opens with a quote ends with its closing quote: a tab after it is not trimmed away. */
    @Test
    void aTabAfterAClosingQuoteIsAnErrorInStrictMode() {
        for (String toon : new String[] {"a: \"x\"\t", "\"k\"\t: 1", "a[2]: \"x\"\t,y", "r[1]{c}:\n  \"x\"\t"}) {
            assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class);
        }
        assertReads(map("a", "x", "k", 1L), "a: \"x\"   \n\"k\"  : 1");     // spaces are trimmed, as ever
    }

    // ---------------------------------------------------------------- indentation

    @Test
    void aTabInTheIndentationIsAnErrorInStrictMode() {
        for (String toon : new String[] {"a:\n\tb: 1", "a:\n  b:\n \t c: 1", "a: 1\n\t\nb: 2", "a[1]:\n\t- x"}) {
            assertThatThrownBy(() -> read(toon, STRICT)).as(toon).isInstanceOf(JsonIoException.class)
                    .hasMessageContaining("Tabs are not allowed in indentation");
        }
    }

    /** Recovery 3 of spec 14.4: the depth is the tabs plus the spaces divided by the indent size. */
    @Test
    void inNonStrictModeATabIsOneLevelOfIndentation() {
        assertEquals(json(map("a", map("b", 1L))), json(read("a:\n\tb: 1", LENIENT)));
        assertEquals(json(map("a", map("b", map("c", 1L), "d", 2L), "e", 3L)),
                json(read("a:\n\tb:\n\t\tc: 1\n\td: 2\ne: 3", LENIENT)));
        assertEquals(json(map("a", map("b", map("c", 1L)))), json(read("a:\n  b:\n \t c: 1", LENIENT)),
                "one tab and two spaces are two levels, wherever the tab stands");
        assertEquals(json(map("a", map("b", map("c", 1L)))), json(read("a:\n  b:\n\t  c: 1", LENIENT)));
        assertEquals(json(map("a", map("b", 1L, "c", 2L))), json(read("a:\n  b: 1\n\tc: 2", LENIENT)),
                "a tab is one level, as two spaces are");
        assertEquals(json(map("a", listOf("x", "y"))), json(read("a[2]:\n  - x\n\t- y", LENIENT)));
        assertEquals(json(map("rows", listOf(map("id", 1L), map("id", 2L)))),
                json(read("rows[2]{id}:\n\t1\n\t2", LENIENT)));
    }

    /** A line of only spaces and tabs is blank in non-strict mode; a line of only spaces is blank in both. */
    @Test
    void aLineOfOnlySpacesAndTabsIsBlankInNonStrictMode() {
        assertEquals(json(map("a", 1L, "b", 2L)), json(read("a: 1\n\t\nb: 2", LENIENT)));
        assertEquals(json(map("a", 1L, "b", 2L)), json(read("a: 1\n \t \t\nb: 2", LENIENT)));
        assertReads(map("a", 1L, "b", 2L), "a: 1\n   \nb: 2");
    }

    // ---------------------------------------------------------------- what the writer writes

    /** A string with a tab, or with a space at either end, is written in quotes, so nothing is there to trim. */
    @Test
    void whatTheWriterWritesReadsBack() {
        Map<String, Object> doc = map(
                "tabs", new ArrayList<>(listOf("v\t", "\tv", "a\tb", "\t")),
                "spaces", new ArrayList<>(listOf(" v", "v ", " ", "a b")),
                "nbsp", new ArrayList<>(listOf(NBSP + "v", "v" + NBSP, NBSP)),
                "k\t", "x",
                " k", "y",
                NBSP + "k", "z");

        for (char delimiter : new char[] {',', '|', '\t'}) {
            String toon = JsonIo.toToon(doc, new WriteOptionsBuilder().toonDelimiter(delimiter).build());

            assertReads(doc, toon);
        }
        assertEquals("tabs[4]: \"v\\t\",\"\\tv\",\"a\\tb\",\"\\t\"\n"
                + "spaces[4]: \" v\",\"v \",\" \",a b\n"
                + "nbsp[3]: " + NBSP + "v,v" + NBSP + "," + NBSP + "\n"
                + "\"k\\t\": x\n"
                + "\" k\": y\n"
                + "\"" + NBSP + "k\": z", JsonIo.toToon(doc, null));
    }
}
