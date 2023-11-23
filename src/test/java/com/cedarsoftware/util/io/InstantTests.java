package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.models.NestedInstant;

public class InstantTests extends SerializationDeserializationMinimumTests<Instant> {
    @Override
    protected Instant provideT1() {
        return Instant.ofEpochMilli(1700668272163L);
    }

    @Override
    protected Instant provideT2() {
        return Instant.ofEpochMilli(1700668346218L);

    }

    @Override
    protected Instant provideT3() {
        return Instant.ofEpochMilli(1700668473085L);

    }

    @Override
    protected Instant provideT4() {
        return Instant.ofEpochMilli(1700668572135L);
    }

    @Override
    protected Object provideNestedInObject() {
        return new NestedInstant(provideT1(), provideT4());
    }

    @Override
    protected void assertNestedInObject(Object expected, Object actual) {
        NestedInstant instant1 = (NestedInstant) expected;
        NestedInstant instant2 = (NestedInstant) actual;
        assertThat(instant2.getInstant1())
                .isEqualTo(instant1.getInstant1());
        assertThat(instant2.getInstant2()).isEqualTo(instant1.getInstant2());
    }

    @Override
    protected Object provideDuplicatesNestedInObject() {
        Instant instant = Instant.now();
        return new NestedInstant(instant, instant);
    }

    @Override
    protected void assertDuplicatesNestedInObject(Object expected, Object actual) {
        NestedInstant instant1 = (NestedInstant) expected;
        NestedInstant instant2 = (NestedInstant) actual;


        assertThat(instant2.getInstant1())
                .isEqualTo(instant1.getInstant1())
                .isSameAs(instant2.getInstant2());
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsMaps(Instant expected, Object actual) {
        assertThat(actual).isEqualTo("2023-11-22T15:56:12.135Z");
    }


    private static Stream<Arguments> roundTripInstants() {
        return Stream.of(
                Arguments.of(Instant.parse("2023-11-22T15:56:12.135Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12.1Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12.135976719Z")),
                Arguments.of(Instant.parse("9999-12-31T23:59:59.999999999Z")),
                Arguments.of(Instant.ofEpochMilli(1700668272163L)),
                Arguments.of(Instant.ofEpochSecond(1700668272163L)),
                Arguments.of(Instant.ofEpochSecond(((146097L * 5L) - (30L * 365L + 7L)) * 86400L, 999999999L))
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripInstants")
    void roundTripTests(Instant expected) {
        String json = JsonWriter.toJson(expected, new WriteOptions());

        Instant actual = JsonReader.toObjects(json, new ReadOptionsBuilder().build(), Instant.class);
        assertThat(expected).isEqualTo(actual);
    }
}
