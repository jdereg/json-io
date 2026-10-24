package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BigDecimal in TOON keeps its scale when type info is on: it is written as its text ({@code "1.50"}), as JSON writes
 * it, where TOON's number form drops the scale ({@code 1.5}). Without type info TOON writes numbers, as before. A
 * BigDecimal read from a TOON number -- what json-io wrote through 4.117.0 with type info on, and still writes with it
 * off -- is the number its text is ({@code 0.0000000000000000000000000001} is {@code 1E-28}), not the Double's own
 * spelling of it ({@code 1.0E-28}). JSON is unchanged.
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
class ToonBigDecimalTest {
    public static class Prices {
        public BigDecimal dec;
        public Object any;
        public List<BigDecimal> list;
        public Map<String, BigDecimal> map;
        public BigDecimal[] array;
        public Optional<BigDecimal> opt;
    }

    private static final String[] VALUES = {"1.50", "0.000", "-2.50", "123.4560", "0.0000000000000000000000000001",
            "-0.0000000000000003", "100", "7"};

    private static Prices prices(BigDecimal v) {
        Prices p = new Prices();
        p.dec = v;
        p.any = v;
        p.list = new ArrayList<>(Arrays.asList(v, v));
        p.map = new LinkedHashMap<>();
        p.map.put("k", v);
        p.array = new BigDecimal[] {v};
        p.opt = Optional.of(v);
        return p;
    }

    /** BigDecimal.equals: the same number and scale. */
    private static void assertExactly(Prices expected, Prices actual, boolean typed, String text) {
        assertEquals(expected.dec, actual.dec, text);
        if (typed) {
            assertEquals(expected.any, actual.any, text);
        }
        assertEquals(expected.list, actual.list, text);
        assertEquals(expected.map, actual.map, text);
        assertArrayEquals(expected.array, actual.array, text);
        assertEquals(expected.opt, actual.opt, text);
    }

    @Test
    void withTypeInfoTheTextKeepsTheScale() {
        WriteOptions[] typed = {new WriteOptionsBuilder().showTypeInfoMinimal().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().showTypeInfoAlways().build()};
        for (String value : VALUES) {
            Prices p = prices(new BigDecimal(value));
            for (WriteOptions options : typed) {
                String toon = JsonIo.toToon(p, options);
                assertTrue(toon.contains("dec: \"" + value + "\"") || toon.contains("value: \"" + value + "\""), toon);
                assertExactly(p, JsonIo.fromToon(toon, null).asClass(Prices.class), true, toon);
            }
        }
    }

    /** As JSON writes it: plain digits, and a negative scale (1E+3) in its exponent form, which keeps it. */
    @Test
    void theTextIsWhatJsonWrites() {
        Prices p = prices(new BigDecimal("1E+3"));
        String toon = JsonIo.toToon(p, new WriteOptionsBuilder().showTypeInfoMinimal().build());
        assertTrue(toon.contains("dec: \"1E+3\""), toon);
        assertTrue(JsonIo.toJson(p, null).contains("\"dec\":\"1E+3\""));
        assertExactly(p, JsonIo.fromToon(toon, null).asClass(Prices.class), true, toon);
    }

    /** Without type info TOON writes numbers, as before; a BigDecimal read from one is the number its text is. */
    @Test
    void withoutTypeInfoTheNumberStays() {
        for (String value : VALUES) {
            BigDecimal v = new BigDecimal(value);
            String toon = JsonIo.toToon(prices(v), null);
            String number = v.stripTrailingZeros().toPlainString();
            assertTrue(toon.contains("dec: " + number + "\n"), toon);
            assertFalse(toon.contains("\""), toon);
            Prices back = JsonIo.fromToon(toon, null).asClass(Prices.class);
            BigDecimal fromText = new BigDecimal(number);
            assertEquals(fromText, back.dec, toon);
            assertEquals(Arrays.asList(fromText, fromText), back.list, toon);
            assertEquals(fromText, back.map.get("k"), toon);
            assertEquals(fromText, back.array[0], toon);
            assertEquals(Optional.of(fromText), back.opt, toon);
        }
    }

    /** What json-io wrote through 4.117.0 with type info on: numbers, read as the numbers their text is. */
    @Test
    void numbersWrittenWithTypeInfoReadAsTheirText() {
        String toon = "$type: " + Prices.class.getName() + "\n"
                + "dec: 0.0000000000000000000000000001\n"
                + "any:\n  $type: BigDecimal\n  value: -0.0000000000000003\n"
                + "list[2]: 0.00001,2.5\n"
                + "map:\n  k: 123.456\n"
                + "array[1]: 0.000001\n"
                + "opt: 0.0000001\n";
        Prices back = JsonIo.fromToon(toon, null).asClass(Prices.class);
        assertEquals(new BigDecimal("0.0000000000000000000000000001"), back.dec);
        assertEquals(new BigDecimal("-0.0000000000000003"), back.any);
        assertEquals(Arrays.asList(new BigDecimal("0.00001"), new BigDecimal("2.5")), back.list);
        assertEquals(new BigDecimal("123.456"), back.map.get("k"));
        assertEquals(new BigDecimal("0.000001"), back.array[0]);
        assertEquals(Optional.of(new BigDecimal("0.0000001")), back.opt);
        assertEquals(new BigDecimal("0.0000000000000000000000000001"), JsonIo.fromToon("0.0000000000000000000000000001", null)
                .asClass(BigDecimal.class));
    }

    /** An array holding a typed element among plain ones, and the same read from a stream. */
    @Test
    void anArrayWithTypedElementsAndAStreamToo() {
        String toon = "array[2]:\n  - $type: BigDecimal\n    value: 0.0000001\n  - 0.000001\n";
        Prices back = JsonIo.fromToon(toon, null).asClass(Prices.class);
        assertEquals(new BigDecimal("0.0000001"), back.array[0]);
        assertEquals(new BigDecimal("0.000001"), back.array[1]);
        back = JsonIo.fromToon(new java.io.ByteArrayInputStream("dec: 0.0000000000000000000000000001\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)), null).asClass(Prices.class);
        assertEquals(new BigDecimal("0.0000000000000000000000000001"), back.dec);
    }

    /** JSON numbers read into a BigDecimal as before: 1.0 keeps its scale of 1. */
    @Test
    void jsonIsUnchanged() {
        Prices back = JsonIo.toJava("{\"dec\":1.0,\"list\":[1.0e-5]}", null).asClass(Prices.class);
        assertEquals(new BigDecimal("1.0"), back.dec);
        assertEquals(BigDecimal.valueOf(1.0e-5), back.list.get(0));
    }
}
