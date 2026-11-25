package com.cedarsoftware.io;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A JSON zero written with an exponent ({@code 0e30}, {@code 0E+2000}) reads as zero, in every number mode.
 * <p>
 * With {@code floatPointBigDecimal()} such a token is the BigDecimal 0E+30, and java-util's Converter counted its
 * whole part as 31 digits (precision minus scale), so reading it into a {@code long} field failed with "Unable to
 * convert 0E+30 to long: it is outside -9223372036854775808 to 9223372036854775807", into a Date with "outside the
 * range of Instant", and 0E+2000 into a BigInteger as a whole number of 2,001 digits. java-util 4.119.0 counts no
 * digits for zero ({@code MathUtilities.wholeDigits}). The other two modes read the token as the double 0.0 and were
 * always right; they are here so the three modes stay alike.
 */
class ZeroWithExponentTest {
    private static final ReadOptions[] MODES = {
            new ReadOptionsBuilder().floatPointBigDecimal().build(),
            new ReadOptionsBuilder().floatPointBoth().build(),
            new ReadOptionsBuilder().build()};
    private static final String[] ZEROS = {"0e30", "0E+2000", "-0e30", "0e1", "0e600000000", "0e-8", "0.000"};

    public static class Whole {
        public long primitive;
        public int small;
        public Long wrapper;
        public BigInteger big;
        public Date date;
    }

    @Test
    void aZeroWithAnExponentReadsAsZeroIntoEveryWholeField() {
        for (ReadOptions mode : MODES) {
            for (String zero : ZEROS) {
                String json = "{\"primitive\":" + zero + ",\"small\":" + zero + ",\"wrapper\":" + zero
                        + ",\"big\":" + zero + ",\"date\":" + zero + "}";
                Whole whole = JsonIo.toJava(json, mode).asClass(Whole.class);
                assertEquals(0L, whole.primitive, zero);
                assertEquals(0, whole.small, zero);
                assertEquals(Long.valueOf(0), whole.wrapper, zero);
                assertEquals(BigInteger.ZERO, whole.big, zero);
                assertEquals(new Date(0), whole.date, zero);
            }
        }
    }

    @Test
    void aZeroWithAnExponentIsZeroFromAMapModeTreeAndFromTheTokenizer() throws IOException {
        for (ReadOptions mode : MODES) {
            for (String zero : ZEROS) {
                JsonObject tree = JsonIo.toMaps("{\"v\":" + zero + "}", mode).asClass(JsonObject.class);
                assertEquals(Long.valueOf(0), tree.get("v", Long.class), zero);
                assertEquals(BigInteger.ZERO, tree.get("v", BigInteger.class), zero);
                assertEquals(0, BigDecimal.ZERO.compareTo(tree.get("v", BigDecimal.class)), zero);

                try (JsonTokenizer tokenizer = JsonIo.createTokenizer("[" + zero + "]", mode)) {
                    tokenizer.nextToken();
                    tokenizer.nextToken();
                    assertEquals(BigInteger.ZERO, tokenizer.getBigIntegerValue(), zero);
                    assertEquals(0L, tokenizer.getLongValue(), zero);
                    assertEquals(0, tokenizer.getIntValue(), zero);
                    assertEquals(0, BigDecimal.ZERO.compareTo(tokenizer.getDecimalValue()), zero);
                }
            }
        }
    }
}
