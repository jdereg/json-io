package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cedarsoftware.util.io.TestUtil.toObjects;

import java.time.ZoneOffset;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ZoneOffsetTests extends SerializationDeserializationMinimumTests<ZoneOffset> {

    @Override
    protected ZoneOffset provideT1() {
        return ZoneOffset.of("+09:08");
    }

    @Override
    protected ZoneOffset provideT2() {
        return ZoneOffset.of("-06");
    }

    @Override
    protected ZoneOffset provideT3() {
        return ZoneOffset.of("+5");
    }

    @Override
    protected ZoneOffset provideT4() {
        return ZoneOffset.of("-04:25:33");
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedZoneOffset(
                provideT1(),
                provideT2());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedZoneOffset expectedDate = (NestedZoneOffset) expected;
        NestedZoneOffset actualDate = (NestedZoneOffset) actual;

        assertThat(actualDate.one)
                .isEqualTo(expectedDate.one)
                .isNotSameAs(actualDate.two);

        assertThat(actualDate.one).isEqualTo(expectedDate.one);
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        return new NestedZoneOffset(provideT1());
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedZoneOffset expectedDate = (NestedZoneOffset) expected;
        NestedZoneOffset actualDate = (NestedZoneOffset) actual;

        assertThat(actualDate.one)
                .isEqualTo(expectedDate.one)
                .isSameAs(actualDate.two);

        assertThat(actualDate.one).isEqualTo(expectedDate.one);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(ZoneOffset expected, Object actual) {
        assertThat(actual).isEqualTo(expected.toString());
    }

    private static Stream<Arguments> argumentsForOldFormat() {
        return Stream.of(
                Arguments.of("{\"@type\":\"java.time.ZoneOffset\",\"value\":\"+9\"}"),
                Arguments.of("{\"@type\":\"java.time.ZoneOffset\",\"value\":\"+09\"}")
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsForOldFormat")
    void testOldFormat_objectType(String json) {
        ZoneOffset zone = toObjects(json, null);
        assertThat(zone).isEqualTo(ZoneOffset.of("+9"));
    }

    @Test
    void testOldFormat_nestedObject() {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.ZoneOffsetTests$NestedZoneOffset\",\"one\":{\"@id\":1,\"value\":\"+05:30\"},\"two\":{\"@ref\":1}}";
        NestedZoneOffset date = toObjects(json, null);
        assertThat(date.one)
                .isEqualTo(ZoneOffset.of("+05:30"))
                .isSameAs(date.two);
    }

    @Test
    void testTopLevel_serializesAsISODate() {
        ZoneOffset initial = ZoneOffset.of("-0908");
        ZoneOffset result = TestUtil.serializeDeserialize(initial);
        assertThat(result).isEqualTo(initial);
    }

    @Test
    void testZoneOffset_inArray() {
        ZoneOffset[] initial = new ZoneOffset[]{
                ZoneOffset.of("+06"),
                ZoneOffset.of("+06:02")
        };

        ZoneOffset[] actual = TestUtil.serializeDeserialize(initial);

        assertThat(actual).isEqualTo(initial);
    }

    private static class NestedZoneOffset {
        public ZoneOffset one;
        public ZoneOffset two;

        public NestedZoneOffset(ZoneOffset one, ZoneOffset two) {
            this.one = one;
            this.two = two;
        }

        public NestedZoneOffset(ZoneOffset date) {
            this(date, date);
        }
    }
}
