package com.cedarsoftware.io;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TOON writes a whole double or float as the decimal it prints, in full -- the digits the reference encoder's
 * JavaScript String(value) gives: 2^60 is 1152921504606847000. Above 2^53 (2^24 for a float) json-io wrote the binary
 * value from a long cast instead (1152921504606846976, and 1.5e10f as 15000000512), and at 2^63 the cast clamped to
 * Long.MAX_VALUE, so 2^63 was written 9223372036854775807, a different number. JSON already wrote these as the decimal
 * they print (1.152921504606847E18).
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
class ToonWholeNumberTest {

    public static class Values {
        public double huge = 0x1p63;
        public double big = 0x1p60;
        public double floor = -0x1p63;
        public float wide = 1.5e10f;
        public double[] doubles = {0x1p63, 0x1p60};
        public float[] floats = {0x1p63f, 1.5e10f};
    }

    @Test
    void largeWholeNumbersAreTheDecimalTheyPrint() {
        String toon = JsonIo.toToon(Arrays.asList(0x1p63, 0x1p60, -0x1p63, 1.5e10f, 0x1p63f), null);
        assertEquals("[5]: 9223372036854776000,1152921504606847000,-9223372036854776000,15000000000,"
                + "9223372000000000000", toon);
    }

    /** Below 2^53 (2^24 for a float) a whole number is exact, and is written as it was. */
    @Test
    void exactWholeNumbersAreUnchanged() {
        String toon = JsonIo.toToon(Arrays.asList(0x1p53, 9007199254740991d, -9007199254740991d, 123d, 1e20, 16777215f,
                0x1p24f), null);
        assertEquals("[7]: 9007199254740992,9007199254740991,-9007199254740991,123,100000000000000000000,16777215,"
                + "16777216", toon);
    }

    /** Fields and primitive arrays take the same path, and read back as the same doubles and floats. */
    @Test
    void fieldsAndArraysRoundTrip() {
        Values values = new Values();
        String toon = JsonIo.toToon(values, null);
        assertTrue(toon.contains("huge: 9223372036854776000"), toon);
        assertTrue(toon.contains("big: 1152921504606847000"), toon);
        assertTrue(toon.contains("wide: 15000000000"), toon);
        assertTrue(toon.contains("doubles[2]: 9223372036854776000,1152921504606847000"), toon);
        assertTrue(toon.contains("floats[2]: 9223372000000000000,15000000000"), toon);
        Values back = JsonIo.fromToon(toon, null).asClass(Values.class);
        assertEquals(values.huge, back.huge);
        assertEquals(values.big, back.big);
        assertEquals(values.floor, back.floor);
        assertEquals(values.wide, back.wide);
        assertEquals(Arrays.toString(values.doubles), Arrays.toString(back.doubles));
        assertEquals(Arrays.toString(values.floats), Arrays.toString(back.floats));
    }
}
