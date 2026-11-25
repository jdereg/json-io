package com.cedarsoftware.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link JsonTokenizer#getBigIntegerValue()} on a decimal token takes the whole part without building the number an
 * exponent names (GHSA-8hw2-vgq3-jcx3, reported by Dickson Wu, SEAL and CertiK).
 * <p>
 * It called {@code BigDecimal.toBigInteger()}, which multiplies -- or, for a fraction, divides -- by 10 to the power
 * of the exponent: the 10 characters {@code 1e30000000} held the calling thread for 3.6 seconds, {@code 1e600000000}
 * for five minutes and 237 MB (JDK 25), and {@code 1e-30000000}, whose whole part is 0, cost the same as
 * {@code 1e30000000}. A decimal token is a
 * BigDecimal with {@code floatPointBigDecimal()}, and with {@code floatPointBoth()} when no double reads back as its
 * text. The conversion now goes through the read's Converter, as a tree read's conversions do: 0 at once for a
 * fraction, and a whole part that an exponent expands past 1,000 digits (ConverterOptions.getMaxPlainDigits()) is
 * refused from its digit count.
 * <p>
 * Every case runs through both public factories, the String one and the InputStream one. The class-level timeout is
 * what fails on the earlier code: it runs each test on its own thread and gives up on it.
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class TokenizerBigIntegerValueTest {
    private static final ReadOptions DOUBLE = new ReadOptionsBuilder().build();
    private static final ReadOptions BIG_DECIMAL = new ReadOptionsBuilder().floatPointBigDecimal().build();
    private static final ReadOptions BOTH = new ReadOptionsBuilder().floatPointBoth().build();
    private static final ReadOptions NAN_ALLOWED = new ReadOptionsBuilder().allowNanAndInfinity(true).build();

    /** A tokenizer standing on {@code number}, the one element of an array. */
    private static JsonTokenizer on(String number, ReadOptions options, boolean stream) throws IOException {
        String json = "[" + number + "]";
        JsonTokenizer tokenizer = stream
                ? JsonIo.createTokenizer(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), options)
                : JsonIo.createTokenizer(json, options);
        assertEquals(JsonToken.START_ARRAY, tokenizer.nextToken());
        tokenizer.nextToken();
        return tokenizer;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aWholePartAnExponentExpandsPastTheLimitIsRefusedNotBuilt(boolean stream) throws IOException {
        String[] numbers = {"1e30000000", "1E+600000000", "-1e600000000", "1.5e30000000", "1e999999999",
                "1e1000"};                                                 // 1e1000 is 1,001 digits
        for (ReadOptions options : new ReadOptions[]{BIG_DECIMAL, BOTH}) {
            for (String number : numbers) {
                try (JsonTokenizer tokenizer = on(number, options, stream)) {
                    assertEquals(NumberType.BIG_DECIMAL, tokenizer.getNumberType(), number);
                    assertThatThrownBy(tokenizer::getBigIntegerValue).as(number)
                            .isInstanceOf(JsonParseException.class)
                            .hasMessageContaining("whole number")
                            .hasMessageContaining("more than 1000");
                    // the token is not spent: it is still the decimal that was written
                    assertEquals(new BigDecimal(number), tokenizer.getDecimalValue(), number);
                    assertEquals(new BigDecimal(number), tokenizer.getNumberValue(), number);
                }
            }
        }
    }

    /** The reported payload: forty tokens in 401 bytes, about four seconds of CPU as it was. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anArrayOfFortyHugeExponentsIsRefusedAtItsFirstNumber(boolean stream) throws IOException {
        String json = "[" + String.join(",", Collections.nCopies(40, "1e9999999")) + "]";
        assertEquals(401, json.length());
        try (JsonTokenizer tokenizer = stream
                ? JsonIo.createTokenizer(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), BIG_DECIMAL)
                : JsonIo.createTokenizer(json, BIG_DECIMAL)) {
            assertEquals(JsonToken.START_ARRAY, tokenizer.nextToken());
            assertEquals(JsonToken.VALUE_NUMBER_FLOAT, tokenizer.nextToken());
            assertThatThrownBy(tokenizer::getBigIntegerValue).isInstanceOf(JsonParseException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFractionIsZeroWithoutDividingByItsScale(boolean stream) throws IOException {
        String[] numbers = {"1e-600000000", "-1e-30000000", "123456789e-600000000", "1e-999999999", "0.5", "-0.999",
                "0e600000000", "0E+2000", "-0e30"};                         // zero, however it is written
        for (ReadOptions options : new ReadOptions[]{BIG_DECIMAL, BOTH}) {
            for (String number : numbers) {
                try (JsonTokenizer tokenizer = on(number, options, stream)) {
                    assertEquals(BigInteger.ZERO, tokenizer.getBigIntegerValue(), number);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aDecimalWithinTheLimitGivesItsWholePart(boolean stream) throws IOException {
        StringBuilder written = new StringBuilder();                        // 1,500 digits of its own: no exponent
        for (int i = 0; i < 1500; i++) {
            written.append((char) ('1' + i % 9));
        }
        String[][] cases = {
                {"1e999", BigInteger.TEN.pow(999).toString()},              // 1,000 digits: the most an exponent names
                {"-1e999", BigInteger.TEN.pow(999).negate().toString()},
                {"1.5e3", "1500"},
                {"2.9", "2"},                                               // toward zero, as BigDecimal.toBigInteger()
                {"-2.9", "-2"},
                {"12345678901234567890.75", "12345678901234567890"},
                {"1.0", "1"},
                {written + ".5", written.toString()},
        };
        for (String[] each : cases) {
            try (JsonTokenizer tokenizer = on(each[0], BIG_DECIMAL, stream)) {
                assertEquals(NumberType.BIG_DECIMAL, tokenizer.getNumberType(), each[0]);
                assertEquals(new BigInteger(each[1]), tokenizer.getBigIntegerValue(), each[0]);
            }
        }
    }

    /**
     * A double token's whole part is that of the decimal the double prints, the same on every JDK. It was taken
     * from Double.toString(), which before JDK 19 does not always print the shortest decimal: 1e23 was
     * 99999999999999990000000 on JDK 8 through 18.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aDoubleGivesTheWholePartOfTheDecimalItPrints(boolean stream) throws IOException {
        String[][] cases = {
                {"1e23", "100000000000000000000000"},
                {"2e22", "20000000000000000000000"},
                {"2.9", "2"},
                {"-2.9", "-2"},
                {"0.5", "0"},
                {"1.0", "1"},
                {"1e308", BigInteger.TEN.pow(308).toString()},
                {"1.7976931348623157E308", "17976931348623157" + BigInteger.TEN.pow(292).toString().substring(1)},
        };
        for (String[] each : cases) {
            try (JsonTokenizer tokenizer = on(each[0], DOUBLE, stream)) {
                assertEquals(NumberType.DOUBLE, tokenizer.getNumberType(), each[0]);
                assertEquals(new BigInteger(each[1]), tokenizer.getBigIntegerValue(), each[0]);
            }
        }
    }

    /**
     * NaN and the infinities have no BigInteger and no BigDecimal: a JsonParseException, as getLongValue() and
     * getIntValue() throw for them. It was a NumberFormatException from the JDK, with no message on JDK 8. 1e400
     * is here because a double token past the double range reads as Infinity.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void notANumberAndInfinityAreAParseException(boolean stream) throws IOException {
        Object[][] cases = {{"NaN", NAN_ALLOWED}, {"Infinity", NAN_ALLOWED}, {"-Infinity", NAN_ALLOWED}, {"1e400", DOUBLE}};
        for (Object[] each : cases) {
            String number = (String) each[0];
            try (JsonTokenizer tokenizer = on(number, (ReadOptions) each[1], stream)) {
                assertEquals(NumberType.DOUBLE, tokenizer.getNumberType(), number);
                assertThatThrownBy(tokenizer::getBigIntegerValue).as(number)
                        .isInstanceOf(JsonParseException.class).hasMessageContaining("out of range of BigInteger");
                assertThatThrownBy(tokenizer::getDecimalValue).as(number)
                        .isInstanceOf(JsonParseException.class).hasMessageContaining("out of range of BigDecimal");
                assertThatThrownBy(tokenizer::getLongValue).as(number).isInstanceOf(JsonParseException.class);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anIntegerTokenIsItself(boolean stream) throws IOException {
        String past = "123456789012345678901234567890";
        for (ReadOptions options : new ReadOptions[]{DOUBLE, BIG_DECIMAL, BOTH}) {
            try (JsonTokenizer tokenizer = on("42", options, stream)) {
                assertEquals(BigInteger.valueOf(42), tokenizer.getBigIntegerValue());
            }
            try (JsonTokenizer tokenizer = on("-9223372036854775808", options, stream)) {
                assertEquals(BigInteger.valueOf(Long.MIN_VALUE), tokenizer.getBigIntegerValue());
            }
            try (JsonTokenizer tokenizer = on(past, options, stream)) {
                assertEquals(NumberType.BIG_INTEGER, tokenizer.getNumberType());
                assertEquals(new BigInteger(past), tokenizer.getBigIntegerValue());
            }
        }
    }

    /** What was never slow stays as it was: the tree keeps the decimal, and a copied token is written as read. */
    @Test
    void aTreeReadAndACopiedTokenKeepTheDecimal() throws IOException {
        BigDecimal huge = new BigDecimal("1e600000000");
        Object[] read = JsonIo.toJava("[1e600000000, 1e-600000000]", BIG_DECIMAL).asClass(Object[].class);
        assertEquals(huge, read[0]);
        assertEquals(new BigDecimal("1e-600000000"), read[1]);

        StringWriter out = new StringWriter();
        try (JsonTokenizer tokenizer = on("1e600000000", BIG_DECIMAL, false);
             JsonGenerator generator = JsonIo.createGenerator(out)) {
            generator.copyCurrentEvent(tokenizer);
        }
        assertEquals("1E+600000000", out.toString());
    }
}
