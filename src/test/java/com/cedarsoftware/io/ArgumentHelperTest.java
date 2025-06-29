package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArgumentHelperTest
{
    @ParameterizedTest
    @MethodSource("argumentHelperTrueValues")
    void argumentHelper_truthyValues_returnTrue(Object input) {
        assertThat(ArgumentHelper.isTrue(input)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("argumentHelperFalseValues")
    void argumentHelper_nonTruthyValues_returnFalse(Object input) {
        assertThat(ArgumentHelper.isTrue(input)).isFalse();
    }

    private static Stream<Arguments> argumentHelperTrueValues() {
        return Stream.of(
                Arguments.of(Boolean.TRUE),
                Arguments.of("true"),
                Arguments.of(Long.valueOf(999)),
                Arguments.of(Integer.valueOf(5)),
                Arguments.of(BigInteger.valueOf(1)),
                Arguments.of(Byte.valueOf("1", 10)),
                Arguments.of(Double.valueOf(1.0)),
                Arguments.of(new BigDecimal(1.9)),
                Arguments.of(1.1d),
                Arguments.of(1.9f),
                Arguments.of(Short.valueOf((short)1),
                Arguments.of((short)1),
                Arguments.of(1),
                Arguments.of(1L),
                Arguments.of(Integer.MAX_VALUE),
                Arguments.of(Byte.valueOf((byte)1)))
        );
    }

    private static Stream<Arguments> argumentHelperFalseValues() {
        return Stream.of(
                Arguments.of(Boolean.FALSE),
                Arguments.of("false"),
                Arguments.of("foo"),
                Arguments.of(Long.valueOf(0)),
                Arguments.of(Integer.valueOf(0)),
                Arguments.of((byte)0),
                Arguments.of(Double.valueOf(0)),
                Arguments.of(BigInteger.valueOf(0)),
                Arguments.of(new BigDecimal(0.0)),
                Arguments.of(0.0d),
                Arguments.of(0.0f),
                Arguments.of(new ArgumentHelperTest()),
                Arguments.of(Short.valueOf((short)0),
                Arguments.of((short)0),
                Arguments.of(0),
                Arguments.of(0L),
                Arguments.of(Byte.valueOf((byte)0)))
        );
    }

    @ParameterizedTest
    @MethodSource("numberWithDefaultValues")
    void getNumberWithDefault_validInput_returnsExpected(Object input, Number def, Number expected) {
        assertThat(ArgumentHelper.getNumberWithDefault(input, def)).isEqualTo(expected);
    }

    @Test
    void getNumberWithDefault_nonNumber_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> ArgumentHelper.getNumberWithDefault("foo", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected Number but got: String");
    }

    private static Stream<Arguments> numberWithDefaultValues() {
        return Stream.of(
                Arguments.of(null, 42, 42),
                Arguments.of(5, 9, Integer.valueOf(5)),
                Arguments.of(3.14d, 0, 3.14d)
        );
    }
}
