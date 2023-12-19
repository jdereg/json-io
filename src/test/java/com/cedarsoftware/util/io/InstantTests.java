package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.models.NestedInstant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class InstantTests extends SerializationDeserializationMinimumTests<Instant> {

    @Override
    protected boolean isReferenceable() {
        return false;
    }

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
    protected Object provideNestedInObject_withNoDuplicates() {
        return new NestedInstant(provideT1(), provideT4());
    }

    @Override
    protected Instant[] extractNestedInObject(Object o) {
        NestedInstant nested = (NestedInstant) o;

        return new Instant[]{
                nested.getInstant1(),
                nested.getInstant2()
        };
    }

    @Override
    protected Object provideNestedInObject_withDuplicates() {
        Instant instant = Instant.now();
        return new NestedInstant(instant, instant);
    }

    @Override
    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(Instant expected, Object actual) {
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
        String json = TestUtil.toJson(expected, new WriteOptionsBuilder().build());

        Instant actual = TestUtil.toObjects(json, new ReadOptions(), Instant.class);
        assertThat(expected).isEqualTo(actual);
    }

    private static Stream<Arguments> oldFormats() {
        return Stream.of(
                Arguments.of("old-format-basic.json", Instant.ofEpochSecond(1700668272, 163000000)),
                Arguments.of("old-format-missing-fields.json", Instant.ofEpochSecond(0, 0)),
                Arguments.of("old-format-missing-nanos.json", Instant.ofEpochSecond(1700668272, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("oldFormats")
    void testOldFormats(String fileName, Instant expected) {
        Instant instant = TestUtil.toObjects(loadJson(fileName), Instant.class);
        assertThat(instant).isEqualTo(expected);
    }

    private String loadJson(String fileName) {
        return MetaUtils.loadResourceAsString("instant/" + fileName);
    }
}
