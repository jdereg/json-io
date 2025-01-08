package com.cedarsoftware.io;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public abstract class SerializationDeserializationMinimumTests<T> {

    protected abstract T  provideT1();

    protected abstract T provideT2();

    protected abstract T provideT3();

    protected abstract T provideT4();

    protected abstract Class<T> getTestClass();

    protected List<String> getPossibleClassNamesForType() {
        String x = getTestClass().getSimpleName();
        return listOf(x);
    }

    protected boolean isReferenceable() {
        return true;
    }

    //  standalone
    @Test
    protected void testStandaloneT() {
        // arrange
        T expected = provideT1();

        // act
        T actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertStandalone(expected, actual);
    }

    protected void assertStandalone(T expected, T actual) {
        assert expected != actual;
        assert DeepEquals.deepEquals(actual, expected);
    }

    //  nested object
    @Test
    protected void testNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType() {
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
        T[] expected = extractNestedInObject_withMatchingFieldTypes(nestedExpected);
        T[] actual = extractNestedInObject_withMatchingFieldTypes(nestedActual);

        // Uncomment if we remove java.util.LocalDate/Time from nonRefs.txt
        assert DeepEquals.deepEquals(actual[0], expected[0]);
        assert actual[0] != expected[0];

        assert DeepEquals.deepEquals(actual[1], expected[1]);
    }

    protected abstract T[] extractNestedInObject_withMatchingFieldTypes(Object o);

    /**
     * @return Just needs to have two objects of type T nested in it and be unique objects
     */
    protected abstract Object provideNestedInObject_withNoDuplicates_andFieldTypeMatchesObjectType();

    // nested object with duplicate reference
    // we are really only testing the isReferenceable() and that the referential integrity
    // matches the flag isReferenceable() here for class T
    @Test
    protected void testNestedInObject_withDuplicates_andFieldTypeMatchesObjectType() {
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
        T[] expected = extractNestedInObject_withMatchingFieldTypes(nestedExpected);
        T[] actual = extractNestedInObject_withMatchingFieldTypes(nestedActual);

        // make sure test is setup correctly
        assertThat(expected[0]).isSameAs(expected[1]);

        // asserts
        if (actual[0] instanceof OffsetDateTime) {
            long differenceInNanos = Math.abs(Duration.between((OffsetDateTime) expected[0], (OffsetDateTime) actual[0]).toNanos());
            // Check if the difference is less than or equal to 1,000 nanoseconds (1 microsecond).
            // This is needed because the OffsetDateTime can be off by 1 nanosecond during conversions between types.
            assert differenceInNanos < 1_000;  // Allow for < 1 micro-second difference

            differenceInNanos = Math.abs(Duration.between((OffsetDateTime) expected[1], (OffsetDateTime) actual[1]).toNanos());
            // Check if the difference is less than or equal to 1,000 nanoseconds (1 microsecond).
            // This is needed because the OffsetDateTime can be off by 1 nanosecond during conversions between types.
            assert differenceInNanos < 1_000;  // Allow for < 1 micro-second difference

            assertReferentialIdentity(actual[0], actual[1]);
        } else {
            assertThat(actual[0]).isEqualTo(expected[0]);
            assertThat(actual[1]).isEqualTo(expected[1]);
            assertReferentialIdentity(actual[0], actual[1]);
        }
    }

    /**
     * @return Just needs to have two objects of type T nested and they both need to be same reference
     */
    protected abstract Object provideNestedInObject_withDuplicates_andFieldTypeMatchesObjectType();


    @Test
    protected void testNestedInObject_withNoDuplicates_andFieldTypeDoesNotMatchObjectType() {
        // arrange
        NestedWithObjectFields expected = provideNestedInObject_withNoDuplicates_andFieldTypeDoesNotMatchObjectType();

        // act
        String json = TestUtil.toJson(expected);

        assertNoReferencesInString(json);
        assertOneOfTheseTypesInString(json);

        NestedWithObjectFields actual = TestUtil.toObjects(json, null);

        assertNestedInObject_withNoDuplicates_andFieldTypeDoesNotMatchObjectType(expected, actual);
    }

    protected void assertNestedInObject_withNoDuplicates_andFieldTypeDoesNotMatchObjectType(NestedWithObjectFields expected, NestedWithObjectFields actual) {
        // make sure test is setup correctly
        assertThat(expected.one).isNotSameAs(expected.two);

        // asserts.
        assertThat(actual.one)
                .isEqualTo(expected.one);

        assertThat(actual.two)
                .isEqualTo(expected.two);

        assertThat(actual.one).isNotSameAs(actual.two);
    }

    protected NestedWithObjectFields provideNestedInObject_withNoDuplicates_andFieldTypeDoesNotMatchObjectType() {
        return new NestedWithObjectFields(provideT1(), provideT2());
    }

    protected void assertNestedInObject_withDuplicates_andFieldTypeDoesNotMatchObjectType(NestedWithObjectFields expected, NestedWithObjectFields actual) {
        // make sure test is setup correctly
        assertThat(expected.one).isSameAs(expected.two);

        // asserts.
        assertThat(actual.one)
                .isEqualTo(expected.one);

        assertThat(actual.two)
                .isEqualTo(expected.two);

        assertReferentialIdentity(actual.one, actual.two);
    }

    protected NestedWithObjectFields provideNestedInObject_withDuplicates_andFieldTypeDoesNotMatchObjectType() {
        T duplicate = provideT1();
        return new NestedWithObjectFields(duplicate, duplicate);

    }

    // object array unique
    @Test
    protected void testObjectArray_withNoDuplicates_butIncludingRandomObjects() {
        // arrange
        Object[] expected = new Object[]{provideT1(), "foo", 9L, provideT2()};

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

    @Test
    protected void testNestedInObject_withNoDuplicates_andFieldTypeIsGeneric() {
        // arrange
        Nested<T> expected = provideNestedInObject_withNoDuplicates_andFieldTypeIsGeneric();

        // act
        String json = TestUtil.toJson(expected);

        // should be no references
        assertNoReferencesInString(json);
        assertOneOfTheseTypesInString(json);

        Nested<T> actual = TestUtil.toObjects(json, null);

        assertNestedInObject_withNoDuplicates_andFieldTypeIsGeneric(expected, actual);
    }

    protected void assertNestedInObject_withNoDuplicates_andFieldTypeIsGeneric(Nested<T> expected, Nested<T> actual) {
        assertThat(actual.one)
                .isEqualTo(expected.one)
                .isNotSameAs(actual.two);

        assertThat(actual.two).isEqualTo(expected.two);
    }

    /**
     * @return Just needs to have two objects of type T nested in it and be unique objects
     */
    protected Nested<T> provideNestedInObject_withNoDuplicates_andFieldTypeIsGeneric() {
        return new Nested(provideT1(), provideT2());
    }


    // Object array with duplicates
    @Test
    protected void testObjectArray_withDuplicates() {
        T standalone = provideT1();

        Object[] expected = new Object[]{standalone, standalone, 5, "foo"};

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        Object[] actual = TestUtil.toObjects(json, null);

        // assert
        assertObjectArray_withDuplicates(expected, actual);
    }

    protected void assertObjectArray_withDuplicates(Object[] expected, Object[] actual) {
        assertEquals(actual.length, expected.length);
        assertReferentialIdentity(actual[0], actual[1]);
        assert DeepEquals.deepEquals(expected, actual);
    }
    
    @Test
    protected void testClassSpecificArray_withNoDuplicates() {
        T[] expected = ArrayUtilities.createArray(provideT1(), provideT2());
        // should be no references
        String json = TestUtil.toJson(expected);
        assertNoReferencesInString(json);
        T[] actual = TestUtil.toObjects(json, null);
        assertClassSpecificArray_withNoDuplicates(expected, actual);
    }

    protected void assertClassSpecificArray_withNoDuplicates(T[] expected, T[] actual) {
        assertEquals(actual.length, expected.length);
        assert DeepEquals.deepEquals(expected, actual);
        assert actual[0] != actual[1];
    }

    @Test
    protected void testClassSpecificArray_withDuplicates() {
        T one = provideT1();
        T two = provideT2();

        T[] expected = ArrayUtilities.createArray(one, two, one);

        // should be no references
        String json = TestUtil.toJson(expected);
        assertReferentialityInString(json);
        T[] actual = TestUtil.toObjects(json, null);
        assertClassSpecificArray_withDuplicates(expected, actual);
    }

    protected void assertClassSpecificArray_withDuplicates(T[] expected, T[] actual) {
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
    protected void testCollection_withNoDuplicates() {
        List expected = listOf(provideT1(), provideT2(), provideT3(), provideT4());

        // act
        List actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertCollection_withNoDuplicates(expected, actual);
        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    protected void assertCollection_withNoDuplicates(List<T> expected, List<T> actual) {
        assertThat(expected).hasSize(actual.size());

        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i)).isEqualTo(expected.get(i));
        }
    }

    @Test
    protected void testCollection_withDuplicates() {
        T instance = provideT1();
        List expected = listOf(instance, provideT2(), provideT3(), provideT4(), instance);

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        List<T> actual = TestUtil.toObjects(json, null);

        // assert
        assertCollection_withDuplicates(expected, actual);
    }

    protected void assertCollection_withDuplicates(List<T> expected, List<T> actual) {

        assertThat(actual).hasSize(expected.size());

        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i)).isEqualTo(expected.get(i));
        }

        assertReferentialIdentity(actual.get(0), actual.get(4));
    }

    @Test
    protected void testMap_withNoDuplicates() {
        // arrange
        Map<String, T> expected = mapOf("foo", provideT1(), "bar", provideT2());

        // act
        String json = TestUtil.toJson(expected);

        assertNoReferencesInString(json);

        Map<String, T> actual = TestUtil.toObjects(json, null);

        // assert
        assertMap_withNoDuplicates(expected, actual);
    }

    protected void assertMap_withNoDuplicates(Map<String, T> expected, Map<String, T> actual) {
        assertThat(expected).hasSize(2)
                .containsAllEntriesOf(actual);

        assertThat(actual.get("foo")).isNotSameAs(actual.get("bar"));
    }

    @SuppressWarnings("unchecked")
    @Test
    protected void testMap_withDuplicates() {
        T instance = provideT4();
        Map<String, T> expected = mapOf("foo", instance, "bar", instance);

        // act
        String json = TestUtil.toJson(expected);

        assertReferentialityInString(json);

        Map<String, T> actual = TestUtil.toObjects(json, null);

        // assert
        assertMap_withDuplicates(expected, actual);
    }

    protected void assertMap_withDuplicates(Map<String, T> expected, Map<String, T> actual) {
        assertThat(expected).hasSize(2)
                .containsAllEntriesOf(actual);

        // make sure test is setup correctly.
        assertThat(expected.get("foo")).isSameAs(expected.get("bar"));

        assertReferentialIdentity(actual.get("foo"), actual.get("bar"));
    }

    @Test
    protected void testT1_serializedWithoutType_parsedAsJsonTypes() {
        // arrange
        T expected = provideT4();

        // act
        Object actual = TestUtil.serializeDeserializeAsMaps(expected);

        // assert
        assertT1_serializedWithoutType_parsedAsJsonTypes(expected, actual);
    }

    protected abstract void assertT1_serializedWithoutType_parsedAsJsonTypes(T expected, Object actual);

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
            assert actual == expected;
        } else {
            assert actual != expected;
        }
    }

    private static void assertNoReferencesInString(String json) {
        assertThat(json).doesNotContain("\"@id\"").doesNotContain("\"@ref\"");
    }

    protected void assertNoTypeInString(String json) {
        assertThat(json).doesNotContain(buildPossibleClassNamesArray());
    }

    private void assertOneOfTheseTypesInString(String json) {
        String[] strings = buildPossibleClassNamesArray();
        boolean containsAny = Arrays.stream(strings).anyMatch(json::contains);
        assert containsAny : json + " should contain at least one of " + String.join(", ", strings);
    }

    protected String[] buildPossibleClassNamesArray() {

        List<String> possibleNamesForType = getPossibleClassNamesForType();
        return possibleNamesForType.stream()
                .map(this::buildTypeNameSearch)
                .collect(Collectors.toList())
                .toArray(new String[]{});
    }

    private String buildTypeNameSearch(String name) {
        return "\"@type\":\"" + name + "\"";
    }

    private void assertTypeIfReferenceable(String json) {
        if (isReferenceable()) {
            assertThat(json).contains("\"@type\"");
        }
    }

    private static class Nested<T> {
        private final T one;
        private final T two;

        public Nested(T one, T two) {
            this.one = one;
            this.two = two;
        }

        public T getOne() {
            return this.one;
        }

        public T getTwo() {
            return this.two;
        }
    }

    private static class NestedWithObjectFields {
        private final Object one;
        private final Object two;

        public NestedWithObjectFields(Object one, Object two) {
            this.one = one;
            this.two = two;
        }

        public Object getOne() {
            return this.one;
        }

        public Object getTwo() {
            return this.two;
        }
    }

}
