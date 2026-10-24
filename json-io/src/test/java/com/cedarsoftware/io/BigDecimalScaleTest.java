package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BigDecimal is written as text that reads back as the same number with the same scale. Plain digits did that for
 * every scale but a negative one: {@code 1E+3} (what {@code new BigDecimal("1000").stripTrailingZeros()} is) was
 * written {@code "1000"}, read back with scale 0, and so was not {@code equals()} to what was written. Now a negative
 * scale is written in its exponent form, {@code "1E+3"}, as Jackson writes it; every other BigDecimal is written as
 * before. TOON without type info writes numbers, which have no scale, as before.
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
class BigDecimalScaleTest {
    public static class Amounts {
        public BigDecimal dec;
        public Object any;
        public List<BigDecimal> list;
        public Map<String, BigDecimal> map;
        public Map<BigDecimal, String> keyed;
        public BigDecimal[] array;
        public Optional<BigDecimal> opt;
    }

    /** Negative scales, and the text each is written as. */
    private static final String[][] NEGATIVE = {{"1E+3", "1E+3"}, {"1.2E+5", "1.2E+5"}, {"-5E+2", "-5E+2"},
            {"1E+30", "1E+30"}, {"12E+3", "1.2E+4"}, {"1.5E+2", "1.5E+2"}, {"1E+1", "1E+1"},
            {"1E+999999999", "1E+999999999"}};

    /** Scales of zero and more, written as plain digits as before (past the plain-digit limit, the exponent form). */
    private static final String[][] PLAIN = {{"1000", "1000"}, {"1.50", "1.50"}, {"0.0000001", "0.0000001"}, {"0", "0"},
            {"-2.50", "-2.50"}, {"1E-999999999", "1E-999999999"}};

    private static Amounts amounts(BigDecimal v) {
        Amounts a = new Amounts();
        a.dec = v;
        a.any = v;
        a.list = new ArrayList<>(Collections.singletonList(v));
        a.map = new LinkedHashMap<>();
        a.map.put("k", v);
        a.keyed = new LinkedHashMap<>();
        a.keyed.put(v, "x");
        a.array = new BigDecimal[] {v};
        a.opt = Optional.of(v);
        return a;
    }

    /** BigDecimal.equals: the same number and scale, everywhere (an Object field only where its type is written). */
    private static void assertExactly(Amounts expected, Amounts actual, String text) {
        assertEquals(expected.dec, actual.dec, text);
        if (text.contains("BigDecimal")) {
            assertEquals(expected.any, actual.any, text);
        }
        assertEquals(expected.list, actual.list, text);
        assertEquals(expected.map, actual.map, text);
        assertEquals(expected.keyed, actual.keyed, text);
        assertArrayEquals(expected.array, actual.array, text);
        assertEquals(expected.opt, actual.opt, text);
    }

    /** The field written as the text, bare or in its typed form (JSON5: its name unquoted). */
    private static void assertWritten(String json, String text) {
        assertTrue(json.contains("\"dec\":\"" + text + "\"") || json.contains("{dec:\"" + text + "\"")
                || json.contains("\"dec\":{\"@type\":\"BigDecimal\",\"value\":\"" + text + "\"}"), json);
    }

    private static final WriteOptions[] JSON = {null,
            new WriteOptionsBuilder().showTypeInfoAlways().build(),
            new WriteOptionsBuilder().showTypeInfoMinimal().build(),
            new WriteOptionsBuilder().stringifyMapKeys(true).build(),
            new WriteOptionsBuilder().json5().build()};

    private static final WriteOptions[] TOON = {new WriteOptionsBuilder().showTypeInfoMinimal().build(),
            new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
            new WriteOptionsBuilder().showTypeInfoAlways().build()};

    @Test
    void aNegativeScaleIsWrittenInItsExponentForm() {
        for (String[] c : NEGATIVE) {
            BigDecimal v = new BigDecimal(c[0]);
            assertTrue(v.scale() < 0, c[0]);
            Amounts a = amounts(v);
            for (WriteOptions options : JSON) {
                String json = JsonIo.toJson(a, options);
                assertWritten(json, c[1]);
                assertExactly(a, JsonIo.toJava(json, null).asClass(Amounts.class), json);
            }
            for (WriteOptions options : TOON) {
                String toon = JsonIo.toToon(a, options);
                assertTrue(toon.contains("dec: \"" + c[1] + "\"")
                        || toon.contains("dec:\n  $type: BigDecimal\n  value: \"" + c[1] + "\"\n"), toon);
                assertExactly(a, JsonIo.fromToon(toon, null).asClass(Amounts.class), toon);
            }
            assertEquals("{\"@type\":\"BigDecimal\",\"value\":\"" + c[1] + "\"}", JsonIo.toJson(v, null));
            assertEquals(v, JsonIo.toJava(JsonIo.toJson(v, null), null).asClass(BigDecimal.class));
            assertEquals(v, JsonIo.toJava(JsonIo.toJson(v, null), null).asClass(null));
        }
    }

    @Test
    void otherScalesAreWrittenAsBefore() {
        for (String[] c : PLAIN) {
            BigDecimal v = new BigDecimal(c[0]);
            Amounts a = amounts(v);
            for (WriteOptions options : JSON) {
                String json = JsonIo.toJson(a, options);
                assertWritten(json, c[1]);
                assertExactly(a, JsonIo.toJava(json, null).asClass(Amounts.class), json);
            }
            for (WriteOptions options : TOON) {
                String toon = JsonIo.toToon(a, options);
                assertTrue(toon.contains("dec: \"" + c[1] + "\"")
                        || toon.contains("dec:\n  $type: BigDecimal\n  value: \"" + c[1] + "\"\n"), toon);
                assertExactly(a, JsonIo.fromToon(toon, null).asClass(Amounts.class), toon);
            }
        }
    }

    /** A stringified map key keeps its scale too, where the Converter's text (1000) would not. */
    @Test
    void aStringifiedKey() {
        Map<BigDecimal, String> keyed = new LinkedHashMap<>();
        keyed.put(new BigDecimal("1E+3"), "a");
        keyed.put(new BigDecimal(BigInteger.valueOf(25), -2), "b");
        keyed.put(new BigDecimal("1.50"), "c");
        WriteOptions options = new WriteOptionsBuilder().stringifyMapKeys(true).showTypeInfoNever().build();
        String json = JsonIo.toJson(keyed, options);
        assertEquals("{\"1E+3\":\"a\",\"2.5E+3\":\"b\",\"1.50\":\"c\"}", json);
        Map<BigDecimal, String> back = JsonIo.toJava(json, null)
                .asType(new TypeHolder<LinkedHashMap<BigDecimal, String>>() {});
        assertEquals(keyed, back);
        assertEquals(new ArrayList<>(keyed.keySet()), new ArrayList<>(back.keySet()));
        assertEquals(-3, back.keySet().iterator().next().scale());
    }

    /** TOON without type info writes numbers, which have no scale: unchanged. */
    @Test
    void toonNumbersAreUnchanged() {
        String toon = JsonIo.toToon(amounts(new BigDecimal("1E+3")), null);
        assertTrue(toon.contains("dec: 1000\n"), toon);
        assertTrue(toon.contains("list[1]: 1000\n"), toon);
    }

    /** What json-io wrote for a negative scale through 4.117.0 reads as it did: the plain digits' number. */
    @Test
    void thePlainDigitsWrittenBeforeReadAsBefore() {
        Amounts back = JsonIo.toJava("{\"dec\":\"1000\",\"list\":[\"1000\"]}", null).asClass(Amounts.class);
        assertEquals(new BigDecimal("1000"), back.dec);
        assertEquals(0, back.dec.scale());
        assertEquals(Collections.singletonList(new BigDecimal("1000")), back.list);
    }
}
