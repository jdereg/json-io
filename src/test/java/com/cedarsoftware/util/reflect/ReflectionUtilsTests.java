package com.cedarsoftware.util.reflect;

import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

class ReflectionUtilsTests {
    private static Stream<Arguments> createIsAccessor() {
        return Stream.of(
                Arguments.of("field", "isField"),
                Arguments.of("foo", "isFoo"),
                Arguments.of("treeMap", "isTreeMap"));
    }


}
