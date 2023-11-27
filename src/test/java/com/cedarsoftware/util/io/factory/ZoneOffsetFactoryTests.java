package com.cedarsoftware.util.io.factory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.JsonObject;

class ZoneOffsetFactoryTests {
    private static Stream<Arguments> zoneOffsets() {
        return Stream.of(
                Arguments.of("Z"),
                Arguments.of("+3"),
                Arguments.of("+03"),
                Arguments.of("-3"),
                Arguments.of("-03"),
                Arguments.of("+03:20"),
                Arguments.of("-03:20"),
                Arguments.of("+0320"),
                Arguments.of("-0320"),
                Arguments.of("+032006"),
                Arguments.of("-032006"),
                Arguments.of("-03:20:06"),
                Arguments.of("-03:20:06")
        );
    }

    @ParameterizedTest
    @MethodSource("zoneOffsets")
    void newInstance_withVariousOffsets(String zoneOffset) {
        ZoneOffset initial = ZoneOffset.of(zoneOffset);
        ZoneOffsetFactory factory = new ZoneOffsetFactory();
        JsonObject jsonObject = new JsonObject();
        jsonObject.setValue(zoneOffset);
        ZoneOffset actual = factory.newInstance(ZoneOffset.class, jsonObject, null);
        assertThat(actual).isEqualTo(initial);
    }
}
