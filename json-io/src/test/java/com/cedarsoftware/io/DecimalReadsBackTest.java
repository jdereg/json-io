package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A decimal read with {@code floatPointBoth()}, and every decimal the TOON reader reads, is a Double when the double
 * reads back as the number in the text, else a BigDecimal holding that number (java-util 4.118.0's
 * {@code MathUtilities.parseToMinimalNumericType}). With java-util 4.117.0 finite text could become Infinity, a
 * 16-digit value a different Double, and the 17 digits {@code Double.toString} writes a BigDecimal.
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
class DecimalReadsBackTest {
    private static final ReadOptions BOTH = new ReadOptionsBuilder().floatPointBoth().build();

    /** Doubles that Double.toString writes in the shortest form on every JDK, 8 included. */
    private static final double[] DOUBLES = {0.1 + 0.2, Math.PI, Math.E, 1.0 / 3, 1e23, 1e308, 9.9e307,
            Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_NORMAL, Double.MIN_VALUE, 0.1, -123.456};

    /**
     * Those TOON writes with a fractional part. TOON writes numbers without an exponent (spec section 2), so 1e23 is
     * written as an integer and read back as one; equal in value, not a Double.
     */
    private static final double[] TOON_DECIMALS = {0.1 + 0.2, Math.PI, Math.E, 1.0 / 3, Double.MIN_NORMAL,
            Double.MIN_VALUE, 0.1, -123.456, 1.5e-5};

    @Test
    void floatPointBothKeepsTheNumberInTheText() {
        Object[] read = JsonIo.toJava("[0.30000000000000004, 8.000000000000001, 99e307, 1.5, 1e308, "
                + "12345678901234567890.5, 0e400]", BOTH).asClass(Object[].class);
        assertEquals(0.1 + 0.2, read[0]);
        assertBigDecimal("8.000000000000001", read[1]);        // the nearest double reads back as 8.000000000000002
        assertBigDecimal("99e307", read[2]);                   // past the double range: was Infinity
        assertEquals(1.5, read[3]);
        assertEquals(1e308, read[4]);
        assertBigDecimal("12345678901234567890.5", read[5]);
        assertEquals(0.0, read[6]);
    }

    @Test
    void floatPointBothReadsAWrittenDoubleBackAsTheSameDouble() {
        String json = JsonIo.toJson(toObjects(DOUBLES), new WriteOptionsBuilder().showTypeInfoNever().build());
        Object[] read = JsonIo.toJava(json, BOTH).asClass(Object[].class);
        for (int i = 0; i < DOUBLES.length; i++) {
            assertInstanceOf(Double.class, read[i], json);
            assertEquals(DOUBLES[i], read[i], json);
        }
    }

    @Test
    void toonKeepsTheNumberInTheText() {
        Map<?, ?> read = JsonIo.fromToon("a: 0.30000000000000004\nb: 8.000000000000001\nc: 99e307\nd: 1.5",
                new ReadOptionsBuilder().build()).asClass(Map.class);
        assertEquals(0.1 + 0.2, read.get("a"));
        assertBigDecimal("8.000000000000001", read.get("b"));
        assertBigDecimal("99e307", read.get("c"));
        assertEquals(1.5, read.get("d"));
    }

    @Test
    void toonReadsAWrittenDoubleBackAsTheSameDouble() {
        String toon = JsonIo.toToon(toObjects(TOON_DECIMALS), null);
        Object[] read = JsonIo.fromToon(toon, new ReadOptionsBuilder().build()).asClass(Object[].class);
        for (int i = 0; i < TOON_DECIMALS.length; i++) {
            assertInstanceOf(Double.class, read[i], toon);
            assertEquals(TOON_DECIMALS[i], read[i], toon);
        }
    }

    private static void assertBigDecimal(String expected, Object actual) {
        assertInstanceOf(BigDecimal.class, actual, expected);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), expected + " read as " + actual);
    }

    private static Object[] toObjects(double[] doubles) {
        Object[] objects = new Object[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            objects[i] = doubles[i];
        }
        return objects;
    }
}
