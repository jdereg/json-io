package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.Collections;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A JSON number reads as the number it writes, however long its digits and its exponent (java-util 4.119.0).
 * <p>
 * java-util's fast number parser stopped reading an exponent at 1024, then added it to the position of the decimal
 * point: {@code 0.} + 1,005 zeros + {@code 12345e10240}, which is 1.2345E+9234, was read as 1.2345E18, here with
 * default options and with {@code floatPointBoth()}, and by the Converter. Each read below is compared with the
 * same number written without the padding, so what json-io does with a number past the double range is not
 * restated here: only that the padded text is that number.
 */
class LongNumberWithAHugeExponentTest {
    private static final String ZEROS = String.join("", Collections.nCopies(1005, "0"));
    private static final String HUGE = "0." + ZEROS + "12345e10240";                    // 1.2345E+9234
    private static final String TINY = "12345" + ZEROS + "00000000000000e-10240";       // 1.2345E-9217

    @Test
    void theDefaultReadIsTheNumberWritten() {
        ReadOptions defaults = new ReadOptionsBuilder().build();
        assertEquals(read("[1.2345e9234]", defaults), read("[" + HUGE + "]", defaults));
        assertEquals(read("[-1.2345e9234]", defaults), read("[-" + HUGE + "]", defaults));
        assertEquals(read("[1.2345e-9217]", defaults), read("[" + TINY + "]", defaults));
        assertEquals(read("{\"n\":1.2345e9234}", defaults).toString(),
                read("{\"n\":" + HUGE + "}", defaults).toString());
    }

    @Test
    void withBothTypesItIsTheBigDecimal() {
        ReadOptions both = new ReadOptionsBuilder().floatPointBoth().build();
        Object[] huge = JsonIo.toJava("[" + HUGE + "]", both).asClass(Object[].class);
        assertEquals(0, new BigDecimal("1.2345e9234").compareTo((BigDecimal) huge[0]));
        Object[] tiny = JsonIo.toJava("[" + TINY + "]", both).asClass(Object[].class);
        assertEquals(0, new BigDecimal("1.2345e-9217").compareTo((BigDecimal) tiny[0]));

        ReadOptions decimal = new ReadOptionsBuilder().floatPointBigDecimal().build();
        Object[] exact = JsonIo.toJava("[" + HUGE + "]", decimal).asClass(Object[].class);
        assertEquals(0, new BigDecimal("1.2345e9234").compareTo((BigDecimal) exact[0]));
    }

    /** Into a double field, and through the Converter: outside the range, as the plain spelling is. */
    @Test
    void aDoubleFieldAndTheConverterRefuseItAsTheyRefuseThePlainSpelling() {
        Converter converter = new Converter(new com.cedarsoftware.util.convert.DefaultConverterOptions());
        assertThrows(IllegalArgumentException.class, () -> converter.convert("1.2345e9234", Double.class));
        assertThrows(IllegalArgumentException.class, () -> converter.convert(HUGE, Double.class));
        assertEquals(converter.convert("1.2345e-9217", Double.class), converter.convert(TINY, Double.class));
        assertEquals(converter.convert("1.2345e-9217", Float.class), converter.convert(TINY, Float.class));
    }

    private static Object read(String json, ReadOptions options) {
        Object result = JsonIo.toJava(json, options).asClass(Object.class);
        return result instanceof Object[] ? java.util.Arrays.asList((Object[]) result) : result;
    }
}
