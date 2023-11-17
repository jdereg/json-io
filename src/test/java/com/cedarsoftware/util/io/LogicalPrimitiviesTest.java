package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrimitivesTests {

    private static Stream<Arguments> contains_true() {
        return Stream.of(
                Arguments.of(Byte.class),
                Arguments.of(Short.class),
                Arguments.of(Integer.class),
                Arguments.of(Long.class),
                Arguments.of(Float.class),
                Arguments.of(Double.class),
                Arguments.of(Boolean.class),
                Arguments.of(Character.class)
        );
    }

    @ParameterizedTest
    @MethodSource("contains_true")
    void testContains_trueCases(Class<?> c) {
        assertThat(Primitives.contains(c)).isTrue();
    }

    private static Stream<Arguments> contains_false() {
        return Stream.of(
                Arguments.of(Random.class),
                Arguments.of(List.class),
                Arguments.of(Collection.class)
        );
    }

    @ParameterizedTest
    @MethodSource("contains_false")
    void testContains_falseCases(Class<?> c) {
        assertThat(Primitives.contains(c)).isFalse();
    }

    private static Stream<Arguments> testLogicalPrimitives_true() {
        return Stream.of(
                Arguments.of(Byte.class),
                Arguments.of(Byte.TYPE),
                Arguments.of(Short.class),
                Arguments.of(Short.TYPE),
                Arguments.of(Integer.class),
                Arguments.of(Integer.TYPE),
                Arguments.of(Long.class),
                Arguments.of(Long.TYPE),
                Arguments.of(Float.class),
                Arguments.of(Float.TYPE),
                Arguments.of(Double.class),
                Arguments.of(Double.TYPE),
                Arguments.of(Boolean.class),
                Arguments.of(Boolean.TYPE),
                Arguments.of(Character.class),
                Arguments.of(Character.TYPE),
                Arguments.of(Date.class),
                Arguments.of(String.class),
                Arguments.of(BigInteger.class),
                Arguments.of(BigDecimal.class),
                Arguments.of(Number.class)
        );
    }

    @ParameterizedTest
    @MethodSource("testLogicalPrimitives_true")
    void testIsLogicalPrimitive_trueCases(Class<?> c) {
        assertThat(Primitives.isLogicalPrimitive(c)).isTrue();
    }


    @Test
    void testIsLogicalPrimitive_whenNullClass_throwsException() {
        assertThrows(NullPointerException.class, () -> {
            Primitives.isLogicalPrimitive(null);
        });
    }


}
