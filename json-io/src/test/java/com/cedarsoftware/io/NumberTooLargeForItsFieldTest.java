package com.cedarsoftware.io;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JSON number that does not fit the field, array element, list element or map value it is read into is refused,
 * naming the place and the range. It was cast.
 * <p>
 * A cast wraps a whole number: 2147483648 read into an {@code int} was -2147483648, -2147483649 was 2147483647, 70000
 * into a {@code short} was 4464, 128 into a {@code byte} was -128. And it pins a decimal to the nearest end: 3.9e9
 * into an {@code int} was 2147483647, 1e300 into a {@code long} was Long.MAX_VALUE, 1e39 into a {@code float} was
 * Infinity, NaN into an {@code int} was 0. All silently: the read succeeded with a number the JSON does not hold.
 * java-util's Converter has refused each of these since 4.113.0; json-io reached it for a BigInteger or a String but
 * took a short cut for the Long and Double its parser makes. The short cut now takes only what fits.
 * <p>
 * What is unchanged: a number that fits; a fraction dropped toward zero (1.9 into an int is 1), as the Converter
 * drops it; a long into a float or double (rounded to the nearest, never out of range); NaN and the infinities in a
 * float or double.
 */
class NumberTooLargeForItsFieldTest {
    private static final ReadOptions OPTIONS = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

    public static class Numbers {
        public int i;
        public short s;
        public byte b;
        public long l;
        public float f;
        public double d;
        public char c;
        public Integer iw;
        public Short sw;
        public Byte bw;
        public Long lw;
        public Float fw;
        public int[] ia;
        public short[] sa;
        public byte[] ba;
        public long[] la;
        public float[] fa;
        public Integer[] iwa;
        public List<Integer> il;
        public Map<String, Short> sm;
    }

    private static Numbers read(String json) {
        return JsonIo.toJava(json, OPTIONS).asClass(Numbers.class);
    }

    private static void refused(String json, String where, String range) {
        assertThatThrownBy(() -> read(json)).as(json)
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unable to convert")
                .hasMessageContaining(range)
                .hasMessageContaining("(at " + where + ")");
    }

    @Test
    void aWholeNumberPastTheFieldIsRefusedNotWrapped() {
        refused("{\"i\":2147483648}", "$.i", "outside -2147483648 to 2147483647");          // was -2147483648
        refused("{\"i\":-2147483649}", "$.i", "outside -2147483648 to 2147483647");         // was 2147483647
        refused("{\"iw\":2147483648}", "$.iw", "outside -2147483648 to 2147483647");
        refused("{\"s\":70000}", "$.s", "outside -32768 to 32767");                         // was 4464
        refused("{\"sw\":-32769}", "$.sw", "outside -32768 to 32767");
        refused("{\"b\":128}", "$.b", "outside -128 to 127");                               // was -128
        refused("{\"bw\":-129}", "$.bw", "outside -128 to 127");
        assertTrue(assertThatThrownBy(() -> read("{\"i\":2147483648}")).actual().getMessage().contains("Numbers.i"));
    }

    @Test
    void aDecimalPastTheFieldIsRefusedNotPinnedToItsEnd() {
        refused("{\"i\":3.9e9}", "$.i", "outside -2147483648 to 2147483647");               // was 2147483647
        refused("{\"i\":-2147483649.0}", "$.i", "outside -2147483648 to 2147483647");
        refused("{\"l\":9.3e18}", "$.l", "outside -9223372036854775808 to 9223372036854775807");   // was Long.MAX_VALUE
        refused("{\"l\":1e300}", "$.l", "outside -9223372036854775808");
        refused("{\"lw\":-1e300}", "$.lw", "outside -9223372036854775808");
        refused("{\"f\":1e39}", "$.f", "outside -3.4028235E38 to 3.4028235E38");            // was Infinity
        refused("{\"fw\":-1e39}", "$.fw", "outside -3.4028235E38 to 3.4028235E38");
        refused("{\"i\":NaN}", "$.i", "not a number");                                      // was 0
        refused("{\"l\":Infinity}", "$.l", "outside -9223372036854775808");                 // was Long.MAX_VALUE
    }

    @Test
    void theSameInArraysListsAndMaps() {
        refused("{\"ia\":[1,2147483648]}", "$.ia[1]", "outside -2147483648 to 2147483647"); // was [1, -2147483648]
        refused("{\"ia\":[3.9e9]}", "$.ia[0]", "outside -2147483648 to 2147483647");
        refused("{\"sa\":[70000]}", "$.sa[0]", "outside -32768 to 32767");
        refused("{\"ba\":[1.5,300]}", "$.ba[1]", "outside -128 to 127");
        refused("{\"la\":[1e300]}", "$.la[0]", "outside -9223372036854775808");
        refused("{\"fa\":[1e39]}", "$.fa[0]", "outside -3.4028235E38");
        refused("{\"iwa\":[2147483648]}", "$.iwa[0]", "outside -2147483648 to 2147483647");
        refused("{\"il\":[1,2147483648]}", "$.il[1]", "outside -2147483648 to 2147483647");
        refused("{\"sm\":{\"k\":70000}}", "$.sm.k", "outside -32768 to 32767");
    }

    @Test
    void whatFitsIsReadAsBefore() {
        Numbers ends = read("{\"i\":2147483647,\"s\":-32768,\"b\":127,\"l\":9223372036854775807,\"iw\":-2147483648,"
                + "\"sw\":32767,\"bw\":-128,\"lw\":-9223372036854775808,\"c\":65}");
        assertEquals(Integer.MAX_VALUE, ends.i);
        assertEquals(Short.MIN_VALUE, ends.s);
        assertEquals(Byte.MAX_VALUE, ends.b);
        assertEquals(Long.MAX_VALUE, ends.l);
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), ends.iw);
        assertEquals(Short.valueOf(Short.MAX_VALUE), ends.sw);
        assertEquals(Byte.valueOf(Byte.MIN_VALUE), ends.bw);
        assertEquals(Long.valueOf(Long.MIN_VALUE), ends.lw);
        assertEquals('A', ends.c);

        // a fraction is dropped toward zero, at the ends of the range too
        Numbers fractions = read("{\"i\":2147483647.9,\"s\":-1.9,\"b\":1.9,\"l\":1.9,\"iw\":-2147483648.9,\"lw\":-1.9}");
        assertEquals(Integer.MAX_VALUE, fractions.i);
        assertEquals(-1, fractions.s);
        assertEquals(1, fractions.b);
        assertEquals(1L, fractions.l);
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), fractions.iw);
        assertEquals(Long.valueOf(-1), fractions.lw);

        // floats and doubles: rounded, NaN and the infinities kept
        Numbers floats = read("{\"f\":3.4e38,\"fw\":16777217,\"d\":1e300,\"fa\":[1.5,NaN,Infinity,-Infinity,7]}");
        assertEquals(3.4e38f, floats.f);
        assertEquals(Float.valueOf(16777216f), floats.fw);
        assertEquals(1e300, floats.d);
        assertArrayEquals(new float[]{1.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 7f}, floats.fa);

        Numbers containers = read("{\"ia\":[1.9,-1.9,2147483647],\"ba\":[127,-128],\"il\":[1,1.9],\"sm\":{\"k\":32767}}");
        assertArrayEquals(new int[]{1, -1, Integer.MAX_VALUE}, containers.ia);
        assertArrayEquals(new byte[]{127, -128}, containers.ba);
        assertEquals(java.util.Arrays.asList(1, 1), containers.il);
        assertEquals(Short.valueOf(Short.MAX_VALUE), containers.sm.get("k"));
    }
}
