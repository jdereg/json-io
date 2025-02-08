package com.cedarsoftware.io;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstantTests {

    protected boolean isReferenceable() {
        return false;
    }

    protected Instant[] extractNestedInObject_withMatchingFieldTypes(Object o) {
        NestedInstant instant = (NestedInstant) o;
        return new Instant[]{instant.instant1, instant.instant2};
    }

    protected Instant provideT1() {
        return Instant.ofEpochMilli(1700668272163L);
    }

    protected Instant provideT2() {
        return Instant.ofEpochMilli(1700668346218L);

    }

    protected Instant provideT3() {
        return Instant.ofEpochMilli(1700668473085L);

    }

    protected Instant provideT4() {
        return Instant.ofEpochMilli(1700668572135L);
    }

    protected Class<Instant> getTestClass() {
        return Instant.class;
    }

    protected Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        return new NestedInstant(provideT1(), provideT4());
    }

    protected Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        Instant instant = Instant.parse("2024-02-12T04:35:20.221118300Z");
        return new NestedInstant(instant, instant);
    }

    protected void assertT1_serializedWithoutType_parsedAsJsonTypes(Instant expected, Object actual) {
        assertThat(actual).isEqualTo("2023-11-22T15:56:12.135Z");
    }


    private static Stream<Arguments> roundTripInstants() {
        return Stream.of(
                Arguments.of(java.time.Instant.parse("2023-11-22T15:56:12.135Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12.1Z")),
                Arguments.of(Instant.parse("2023-11-22T15:56:12.135976719Z")),
                Arguments.of(Instant.parse("9999-12-31T23:59:59.999999999Z")),
                Arguments.of(Instant.ofEpochMilli(1700668272163L)),
                Arguments.of(Instant.ofEpochSecond(((146097L * 5L) - (30L * 365L + 7L)) * 86400L, 999999999L))
        );
    }

    @ParameterizedTest
    @MethodSource("roundTripInstants")
    void roundTripTests(Instant expected) {
        String json = TestUtil.toJson(expected, new WriteOptionsBuilder().build());

        Instant actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Instant.class);
        assertThat(expected).isEqualTo(actual);
    }

    private static Stream<Arguments> parsingErrors() {
        return Stream.of(
                Arguments.of(1700668272163L, "+55862-01-12T13:22:43Z", "{\"@type\":\"Instant\",\"instant\":\"+55862-01-12T13:22:43Z\"}"),
                Arguments.of(-1700668272163L, "-51923-12-19T10:37:17Z", "{\"@type\":\"Instant\",\"instant\":\"-51923-12-19T10:37:17Z\"}")
        );
    }

    @ParameterizedTest
    @MethodSource("parsingErrors")
    void testInstantParsing(long epochSeconds, String isoFormat, String expectedFormat) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);

        String instantFormat = DateTimeFormatter.ISO_INSTANT.format(instant);
        assertThat(instantFormat).isEqualTo(isoFormat);

        Instant actual = DateTimeFormatter.ISO_INSTANT.parse(instantFormat, Instant::from);
        assertThat(actual).isEqualTo(instant);


        String json = TestUtil.toJson(instant, new WriteOptionsBuilder().build());
        assertThat(json).isEqualTo(expectedFormat);

        Instant actualFromJson = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), Instant.class);
        assertThat(actualFromJson).isEqualTo(actual);
    }

    private static Stream<Arguments> oldFormats() {
        return Stream.of(
                Arguments.of("old-format-basic.json", Instant.ofEpochSecond(1700668272, 163000000)),
                Arguments.of("old-format-missing-nanos.json", Instant.ofEpochSecond(1700668272, 0))
        );
    }

    @ParameterizedTest
    @MethodSource("oldFormats")
    void testOldFormats(String fileName, Instant expected) {
        Instant instant = TestUtil.toObjects(loadJson(fileName), Instant.class);
        assertThat(instant).isEqualTo(expected);
    }

    @Test
    void testOldFormatWithNothing() {
        assertThatThrownBy(() -> TestUtil.toObjects(loadJson("old-format-missing-fields.json"), Instant.class))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Map to 'Instant' the map must include: [instant], [value], or [_v] as key with associated value");
    }

    private String loadJson(String fileName) {
        return ClassUtilities.loadResourceAsString("instant/" + fileName);
    }


    protected List<String> getPossibleClassNamesForType() {
        return listOf(getTestClass().getName());
    }

    //  standalone
    @Test
    void testStandaloneT() {
        // arrange
        Instant expected = provideT1();

        // act
        Instant actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertStandalone(expected, actual);
    }

    protected void assertStandalone(Instant expected, Instant actual) {
        assertThat(expected).isNotSameAs(actual);
        assertThat(actual).isEqualTo(expected);
    }

    //  nested object
    @Test
    void testNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
        // arrange
        Object expected = provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType();

        // act
        String json = TestUtil.toJson(expected);

        // should be no references
        assertNoReferencesInString(json);
        assertNoTypeInString(json);

        Object actual = TestUtil.toObjects(json, null);

        assertNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType(expected, actual);
    }

    protected void assertNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType(Object nestedExpected, Object nestedActual) {
        Instant[] expected = extractNestedInObject_withMatchingFieldTypes(nestedExpected);
        Instant[] actual = extractNestedInObject_withMatchingFieldTypes(nestedActual);

        // Uncomment if we remove java.util.LocalDate/Time from nonRefs.txt
        assertThat(actual[0])
                .isEqualTo(expected[0])
                .isNotSameAs(actual[1]);

        assertThat(actual[1]).isEqualTo(expected[1]);
    }

    // nested object with duplicate reference
    // we are really only testing the isReferenceable() and that the referential integrity
    // matches the flag isReferenceable() here for class T
    @Test
    void testNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
        // arrange
        Object expected = provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType();

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);
        assertNoTypeInString(json);

        Object actual = TestUtil.toObjects(json, null);

        assertNestedInObject_withDuplicates_andFieldTypeMatchesObjectType(expected, actual);
    }

    protected void assertNestedInObject_withDuplicates_andFieldTypeMatchesObjectType(Object nestedExpected, Object nestedActual) {
        Instant[] expected = extractNestedInObject_withMatchingFieldTypes(nestedExpected);
        Instant[] actual = extractNestedInObject_withMatchingFieldTypes(nestedActual);

        // make sure test is setup correctly
        assertThat(expected[0]).isSameAs(expected[1]);

        // asserts.
        assertThat(actual[0])
                .isEqualTo(expected[0]);

        assertThat(actual[1])
                .isEqualTo(expected[1]);

        assertReferentialIdentity(actual[0], actual[1]);
    }

    // object array unique
    @Test
    void testObjectArray_withNoDuplicates_butIncludingRandomObjects() {
        // arrange
        Object[] expected = new Object[] { provideT1(), "foo", 9L, provideT2() };

        // act
        Object[] actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertObjectArray_withNoDuplicates_butIncludingRandomObjects(expected, actual);
    }

    protected void assertObjectArray_withNoDuplicates_butIncludingRandomObjects(Object[] expected, Object[] actual) {
        assertThat(actual).hasSize(expected.length);
        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(actual[1]).isEqualTo(expected[1]);
        assertThat(actual[2]).isEqualTo(expected[2]);
        assertThat(actual[3]).isEqualTo(expected[3]);

        assertThat(actual[0]).isNotSameAs(actual[3]);

        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    // Object array with duplicates
    @Test
    void testObjectArray_withDuplicates() {
        Instant standalone = provideT1();

        Object[] expected = new Object[] { standalone, standalone, 5, "foo" };

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        Object[] actual = TestUtil.toObjects(json, null);

        // assert
        assertObjectArray_withDuplicates(expected, actual);
    }

    protected void assertObjectArray_withDuplicates(Object[] expected, Object[] actual) {
        assertThat(actual).hasSize(expected.length);
        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(actual[1]).isEqualTo(expected[1]);
        assertThat(actual[2]).isEqualTo(expected[2]);
        assertThat(actual[3]).isEqualTo(expected[3]);

        assertReferentialIdentity(actual[0], actual[1]);
        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }


    @Test
    void testClassSpecificArray_withNoDuplicates() {
        Instant[] expected = Arrays.array(provideT1(), provideT2());

        // should be no references
        String json = TestUtil.toJson(expected);

        assertNoReferencesInString(json);

        Instant[] actual = TestUtil.toObjects(json, null);

        assertClassSpecificArray_withNoDuplicates(expected, actual);
    }

    protected void assertClassSpecificArray_withNoDuplicates(Instant[] expected, Instant[] actual) {
        assertThat(actual).hasSize(expected.length);
        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(actual[1]).isEqualTo(expected[1]);

        assertThat(actual[0])
                .isNotSameAs(actual[1]);

        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    @Test
    void testClassSpecificArray_withDuplicates() {
        Instant one = provideT1();
        Instant two = provideT2();

        Instant[] expected = Arrays.array(one, two, one);

        // should be no references
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        Instant[] actual = TestUtil.toObjects(json, null);

        assertClassSpecificArray_withDuplicates(expected, actual);
    }

    protected void assertClassSpecificArray_withDuplicates(Instant[] expected, Instant[] actual) {
        assertThat(actual).hasSize(expected.length);
        assertThat(actual[0]).isEqualTo(expected[0]);
        assertThat(actual[1]).isEqualTo(expected[1]);
        assertThat(actual[2]).isEqualTo(expected[2]);

        assertReferentialIdentity(actual[0], actual[2]);

        assertThat(actual[0])
                .isNotSameAs(actual[1]);

        assertThat(actual[1])
                .isNotSameAs(actual[2]);

        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    @Test
    void testCollection_withNoDuplicates() {
        List expected = listOf(provideT1(), provideT2(), provideT3(), provideT4());

        // act
        List actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertCollection_withNoDuplicates(expected, actual);
        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    protected void assertCollection_withNoDuplicates(List<Instant> expected, List<Instant> actual) {
        assertThat(expected).hasSize(actual.size());

        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i)).isEqualTo(expected.get(i));
        }
    }

    @Test
    void testCollection_withDuplicates() {
        Instant instance = provideT1();
        List expected = listOf(instance, provideT2(), provideT3(), provideT4(), instance);

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        List<Instant> actual = TestUtil.toObjects(json, null);

        // assert
        assertCollection_withDuplicates(expected, actual);
    }

    protected void assertCollection_withDuplicates(List<Instant> expected, List<Instant> actual) {

        assertThat(actual).hasSize(expected.size());

        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i)).isEqualTo(expected.get(i));
        }

        assertReferentialIdentity(actual.get(0), actual.get(4));
    }

    @Test
    void testMap_withNoDuplicates() {
        // arrange
        Map<String, Instant> expected = mapOf("foo", provideT1(), "bar", provideT2());

        // act
        String json = TestUtil.toJson(expected);

        assertNoReferencesInString(json);

        Map<String, Instant> actual = TestUtil.toObjects(json, null);

        // assert
        assertMap_withNoDuplicates(expected, actual);
    }

    protected void assertMap_withNoDuplicates(Map<String, Instant> expected, Map<String, Instant> actual) {
        assertThat(expected).hasSize(2)
                .containsAllEntriesOf(actual);

        assertThat(actual.get("foo")).isNotSameAs(actual.get("bar"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testMap_withDuplicates() {
        Instant instance = provideT4();
        Map<String, Instant> expected = mapOf("foo", instance, "bar", instance);

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        Map<String, Instant> actual = TestUtil.toObjects(json, null);

        // assert
        assertMap_withDuplicates(expected, actual);
    }

    protected void assertMap_withDuplicates(Map<String, Instant> expected, Map<String, Instant> actual) {
        assertThat(expected).hasSize(2)
                .containsAllEntriesOf(actual);

        // make sure test is setup correctly.
        assertThat(expected.get("foo")).isSameAs(expected.get("bar"));

        assertReferentialIdentity(actual.get("foo"), actual.get("bar"));
    }

    @Test
    void testT1_serializedWithoutType_parsedAsJsonTypes() {
        // arrange
        Instant expected = provideT4();

        // act
        Object actual = TestUtil.serializeDeserializeAsMaps(expected);

        // assert
        assertT1_serializedWithoutType_parsedAsJsonTypes(expected, actual);
    }

    private void assertReferentialityInString(String json) {
        if (isReferenceable()) {
            assertThat(json)
                    .contains("\"@id\"")
                    .contains("\"@ref\"");
        } else {
            assertNoReferencesInString(json);
        }
    }

    private void assertReferentialIdentity(Object actual, Object expected) {
        if (isReferenceable()) {
            assertThat(actual).isSameAs(expected);
        } else {
            assertThat(actual).isNotSameAs(expected);
        }
    }

    private static void assertNoReferencesInString(String json) {
        assertThat(json).doesNotContain("\"@id\"").doesNotContain("\"@ref\"");
    }

    protected void assertNoTypeInString(String json) {
        assertThat(json).doesNotContain(buildPossibleClassNamesArray());
    }

    private void assertOneOfTheseTypesInString(String json) {
        assertThat(json).containsAnyOf(buildPossibleClassNamesArray());
    }

    protected String[] buildPossibleClassNamesArray() {
        return getPossibleClassNamesForType().stream()
                .map(this::buildTypeNameSearch)
                .collect(Collectors.toList())
                .toArray(new String[] {});
    }

    private String buildTypeNameSearch(String name) {
        return "\"@type\":\"" + name + "\"";
    }

    private void assertTypeIfReferenceable(String json) {
        if (isReferenceable()) {
            assertThat(json).contains("\"@type\"");
        }
    }

    public static class NestedInstant {
        private final Instant instant1;
        private final Instant instant2;

        public NestedInstant(Instant instant1, Instant instant2) {
            this.instant1 = instant1;
            this.instant2 = instant2;
        }

        public Instant getInstant1() {
            return this.instant1;
        }

        public Instant getInstant2() {
            return this.instant2;
        }
    }
}
