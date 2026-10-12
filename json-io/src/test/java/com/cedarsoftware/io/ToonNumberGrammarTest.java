package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which unquoted token is a number, and which string is written in quotes because it resembles one (TOON spec 4
 * and 7.2). A token is a number if and only if it matches JSON's number grammar; {@code .5}, {@code 1.} and
 * {@code +1} are strings. json-io read them as numbers (it left the decision to Java's number parsers, which accept
 * more), so such a string written by another TOON encoder came back a number. The oracle here is the spec's own two
 * patterns, applied to tokens generated from the characters numbers are made of.
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
class ToonNumberGrammarTest {

    /** Spec 4: "an unquoted token decodes as a number if and only if it matches the JSON number grammar". */
    private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    /** Spec 7.2: a string value that is "numeric-like" is quoted. */
    private static final Pattern NUMERIC_LIKE = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

    private static final ReadOptions STRICT = new ReadOptionsBuilder().strictToon(true).build();
    private static final ReadOptions LENIENT = new ReadOptionsBuilder().strictToon(false).build();
    private static final ReadOptions[] BOTH_MODES = {STRICT, LENIENT};
    private static final WriteOptions LEGACY = new WriteOptionsBuilder().toonLegacyForms(true).build();

    public static class Holder {
        public String text;
        public double real;
        public int whole;
        public Object any;
        public List<String> texts;
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    /**
     * Tokens made of the characters numbers are made of: every one of up to four characters from a small alphabet,
     * and 20,000 longer ones from a wider alphabet and a fixed seed. About one in six is a number.
     */
    private static Set<String> tokens() {
        Set<String> tokens = new LinkedHashSet<>();
        char[] small = "019.e+-".toCharArray();
        List<String> level = new ArrayList<>(listOf(""));
        for (int length = 1; length <= 4; length++) {
            List<String> next = new ArrayList<>();
            for (String prefix : level) {
                for (char c : small) {
                    next.add(prefix + c);
                }
            }
            tokens.addAll(next);
            level = next;
        }
        char[] wide = "0123456789.eE+-x_dfLp".toCharArray();     // 1d, 1f, 0x1p3: numbers to Java, not to TOON
        Random random = new Random(20261012L);
        for (int n = 0; n < 20_000; n++) {
            StringBuilder token = new StringBuilder();
            int length = 1 + random.nextInt(9);
            for (int i = 0; i < length; i++) {
                // mostly digits, so that many tokens come close to a number
                token.append(random.nextInt(3) == 0
                        ? wide[random.nextInt(wide.length)] : (char) ('0' + random.nextInt(10)));
            }
            tokens.add(token.toString());
        }
        return tokens;
    }

    private static void assertNumber(String token, Object read, String where) {
        assertTrue(read instanceof Number, where + ": " + token + " is a number, read " + describe(read));
        if (read instanceof Double) {
            // The nearest double, -0.0 being 0. Not compared as text: before JDK 19 Double.toString does not print
            // the shortest form, and the double that 4e23 is prints as 3.9999999999999997E23.
            assertEquals(Double.parseDouble(token), ((Double) read).doubleValue(), 0.0,
                    where + ": " + token + " read as " + describe(read));
        } else {
            assertEquals(0, new BigDecimal(token).compareTo(new BigDecimal(read.toString())),
                    where + ": " + token + " read as " + describe(read));
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName() + " " + value;
    }

    // ---------------------------------------------------------------- reading

    /** The examples of spec 4, as it gives them. */
    @Test
    void theSpecsOwnExamples() {
        for (String string : new String[] {"05", "-05", ".5", "1.", "+5", "Infinity", "NaN", "0x10", "1_000"}) {
            for (ReadOptions options : BOTH_MODES) {
                assertEquals(string, JsonIo.fromToon("v: " + string, options).asClass(Map.class).get("v"), string);
            }
        }
        String[][] numbers = {{"1.5000", "1.5"}, {"-1E+03", "-1000"}, {"0e1", "0"}, {"-0", "0"}, {"0", "0"},
                {"-0.0", "0"}, {"42", "42"}, {"-7", "-7"}, {"1e2", "100"}, {"1E-2", "0.01"}, {"0.5", "0.5"},
                {"9223372036854775807", "9223372036854775807"}, {"9223372036854775808", "9223372036854775808"},
                {"-9223372036854775809", "-9223372036854775809"}};
        for (String[] number : numbers) {
            for (ReadOptions options : BOTH_MODES) {
                Object read = JsonIo.fromToon("v: " + number[0], options).asClass(Map.class).get("v");
                assertNumber(number[1], read, number[0]);
            }
        }
    }

    /**
     * For every generated token: a number exactly when the grammar says so, with the token's value; otherwise the
     * token itself, as a string. In a field, an inline array, a table cell and a list item, in both modes.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aTokenIsANumberExactlyWhenTheGrammarSaysSo() {
        int numbers = 0;
        int strings = 0;
        for (String token : tokens()) {
            boolean number = NUMBER.matcher(token).matches();
            String toon = "k: " + token + "\n"
                    + "a[2]: " + token + "," + token + "\n"
                    + "t[1]{c,d}:\n"
                    + "  " + token + "," + token + "\n"
                    + "l[2]:\n"
                    + "  - " + token + "\n"
                    + "  - x";
            for (ReadOptions options : BOTH_MODES) {
                Map<String, Object> doc = JsonIo.fromToon(toon, options).asClass(Map.class);
                Map<String, Object> row = (Map<String, Object>) ((List<Object>) doc.get("t")).get(0);
                List<Object> cells = (List<Object>) doc.get("a");
                Object item = ((List<Object>) doc.get("l")).get(0);
                Object[] read = {doc.get("k"), cells.get(0), cells.get(1), row.get("c"), row.get("d"), item};
                String[] where = {"field", "array", "array", "table", "table", "list item"};
                for (int i = 0; i < read.length; i++) {
                    if (number) {
                        assertNumber(token, read[i], where[i]);
                    } else {
                        assertEquals(token, read[i], where[i] + ": " + token + " is a string");
                    }
                }
            }
            if (number) {
                numbers++;
            } else {
                strings++;
            }
        }
        assertTrue(numbers > 2_000 && strings > 10_000, numbers + " numbers, " + strings + " strings");
    }

    /** A declared type still gets its value: the string is converted, as a quoted one always was. */
    @Test
    void aDeclaredNumberStillReadsATokenThatIsNoNumberInTheGrammar() {
        Holder holder = JsonIo.fromToon("text: .5\nreal: .5\nwhole: +1\nany: 1.\ntexts[3]: +1,.5,1.", STRICT)
                .asClass(Holder.class);

        assertEquals(".5", holder.text);
        assertEquals(0.5, holder.real);
        assertEquals(1, holder.whole);
        assertEquals("1.", holder.any);
        assertEquals(listOf("+1", ".5", "1."), holder.texts);
    }

    // ---------------------------------------------------------------- writing

    /**
     * A string is quoted for resembling a number exactly when it is numeric-like (or starts with a hyphen, which
     * is quoted whatever follows). Either way it is read back as the string it was.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aStringIsQuotedExactlyWhenItIsNumericLike() {
        int quoted = 0;
        int bare = 0;
        for (String token : tokens()) {
            boolean expectQuotes = NUMERIC_LIKE.matcher(token).matches() || token.startsWith("-");
            String written = expectQuotes ? '"' + token + '"' : token;

            String toon = JsonIo.toToon(map("k", token, "a", listOf(token, "x")), null);

            assertEquals("k: " + written + "\na[2]: " + written + ",x", toon);
            for (ReadOptions options : BOTH_MODES) {
                Map<String, Object> back = JsonIo.fromToon(toon, options).asClass(Map.class);
                assertEquals(token, back.get("k"), toon);
                assertEquals(listOf(token, "x"), back.get("a"), toon);
            }
            if (expectQuotes) {
                quoted++;
            } else {
                bare++;
            }
        }
        assertTrue(quoted > 2_000 && bare > 5_000, quoted + " quoted, " + bare + " bare");
    }

    /** The strings the spec names, in a table cell and as a field of an object too. */
    @Test
    void numericLikeStringsInEveryPosition() {
        Holder holder = new Holder();
        holder.text = ".5";
        holder.any = "1.";
        holder.texts = new ArrayList<>(listOf("+1", "05", "1e", "5.", "1e-6", "-3.14", ".5"));

        String toon = JsonIo.toToon(holder, null);

        assertEquals("text: .5\nreal: 0\nwhole: 0\nany: 1.\ntexts[7]: \"+1\",\"05\",1e,5.,\"1e-6\",\"-3.14\",.5",
                toon);
        Holder back = JsonIo.fromToon(toon, STRICT).asClass(Holder.class);
        assertEquals(".5", back.text);
        assertEquals("1.", back.any);
        assertEquals(holder.texts, back.texts);

        List<Object> rows = new ArrayList<>(listOf(map("id", ".5", "n", 1L), map("id", "5.", "n", 2L)));
        String table = JsonIo.toToon(map("rows", rows), null);
        assertEquals("rows[2]{id,n}:\n  .5,1\n  5.,2", table);
        assertEquals(map("rows", rows), JsonIo.fromToon(table, STRICT).asClass(Map.class));
    }

    /**
     * Under toonLegacyForms the text is what json-io 4.113.0 wrote, whose reader makes a number of .5: there the
     * quotes stay. The writers of both kinds share a cache of quoting decisions, so each is asked in turn, twice.
     */
    @Test
    void legacyFormsKeepTheQuotesThatAnOlderReaderNeeds() {
        String[] legacyOnly = {".5", "5.", "1e", "1.e5", "+.5", "12.e", "1e+"};
        for (int round = 0; round < 2; round++) {
            for (String string : legacyOnly) {
                assertFalse(NUMERIC_LIKE.matcher(string).matches(), string);
                assertEquals("k: \"" + string + "\"", JsonIo.toToon(map("k", string), LEGACY), "legacy " + string);
                assertEquals("k: " + string, JsonIo.toToon(map("k", string), null), "spec " + string);
            }
        }
        for (String string : new String[] {"42", "+1", "05", "1e5", "-1", "0.5"}) {
            assertEquals("k: \"" + string + "\"", JsonIo.toToon(map("k", string), LEGACY), string);
            assertEquals("k: \"" + string + "\"", JsonIo.toToon(map("k", string), null), string);
        }
        for (String string : new String[] {"1.2.3", "2026-10-12", "10:30", "e5", "x1", "1x", "..5", "+", "."}) {
            String quotes = string.contains(":") ? "\"" : "";
            assertEquals("k: " + quotes + string + quotes, JsonIo.toToon(map("k", string), LEGACY), string);
            assertEquals("k: " + quotes + string + quotes, JsonIo.toToon(map("k", string), null), string);
        }
    }
}
