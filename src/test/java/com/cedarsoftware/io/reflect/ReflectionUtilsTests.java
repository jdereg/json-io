package com.cedarsoftware.io.reflect;

import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

class ReflectionUtilsTests {
    private static Stream<Arguments> createIsAccessor() {
        return Stream.of(
                Arguments.of("field", "isField"),
                Arguments.of("foo", "isFoo"),
                Arguments.of("treeMap", "isTreeMap"));
    }


}
