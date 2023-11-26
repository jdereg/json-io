package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.DeepEquals;

public abstract class SerializationDeserializationMinimumTests<T> {

    protected abstract T provideT1();

    protected abstract T provideT2();

    protected abstract T provideT3();

    protected abstract T provideT4();

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
        assertThat(expected).isNotSameAs(actual);
        assertThat(actual).isEqualTo(expected);
    }

    //  nested object
    @Test
    protected void testNestedInObject() {
        // arrange
        Object initial = provideNestedInObject();

        // act
        Object actual = TestUtil.serializeDeserialize(initial);

        // assert
        assertNestedInObject(initial, actual);
    }

    protected abstract Object provideNestedInObject();

    protected abstract void assertNestedInObject(Object expected, Object actual);


    // nested object with duplicate reference
    @Test
    protected void testDuplicatesNestedInObject() {
        // arrange
        Object expected = provideDuplicatesNestedInObject();

        // act
        String json = TestUtil.toJson(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        Object actual = TestUtil.toObjects(json, null);

        // assert
        assertDuplicatesNestedInObject(expected, actual);
    }

    protected abstract Object provideDuplicatesNestedInObject();

    protected abstract void assertDuplicatesNestedInObject(Object expected, Object actual);

    // object array unique
    @Test
    protected void testInObjectArray() {
        List expected = MetaUtils.listOf(provideT1(), "foo", 9L, provideT2());

        // act
        List actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertInObjectArray(expected, actual);
        assert DeepEquals.deepEquals(expected, actual);
    }

    protected void assertInObjectArray(List expected, List actual) {
        assertThat(expected).isNotSameAs(actual);
        assertThat(actual).hasSameElementsAs(expected);
    }


    // Object array with duplicates
    @Test
    protected void testDuplicatesInObjectArray() {
        T standalone = provideT1();
        List expected = MetaUtils.listOf(standalone, standalone, 5, "foo");

        // act
        String json = TestUtil.toJson(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        List<Object> actual = TestUtil.toObjects(json, null);

        // assert
        assertThat(expected).isNotSameAs(actual);
        assertDuplicatesInObjectArray(expected, actual);
    }

    protected void assertDuplicatesInObjectArray(List<Object> expected, List<Object> actual) {
        assertThat(actual).hasSameElementsAs(expected);
        assertThat(actual.get(0)).isSameAs(actual.get(1));
    }

    @Test
    protected void testClassSpecificArray() {
        T[] expected = Arrays.array(provideT1(), provideT2(), provideT3(), provideT4());

        T[] actual = TestUtil.serializeDeserialize(expected);

        assertClassSpecificArray(expected, actual);
    }

    protected void assertClassSpecificArray(T[] expected, T[] actual) {
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    protected void testInCollection() {
        List expected = MetaUtils.listOf(provideT1(), provideT2(), provideT3(), provideT4());

        // act
        List actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertTestInCollection(expected, actual);
        assertThat(DeepEquals.deepEquals(expected, actual)).isTrue();
    }

    protected void assertTestInCollection(List<T> expected, List<T> actual) {
        assertThat(actual).isNotSameAs(expected);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    protected void testDuplicatesInCollection() {
        T instance = provideT1();
        List expected = MetaUtils.listOf(instance, provideT2(), provideT3(), provideT4(), instance);

        // act
        String json = TestUtil.toJson(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        List<T> actual = TestUtil.toObjects(json, null);

        // assert
        assertDuplicatesInCollection(expected, actual);
    }

    protected void assertDuplicatesInCollection(List<T> expected, List<T> actual) {
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.get(0)).isSameAs(actual.get(4));
    }

    @Test
    protected void testT1_and_T2_and_T3_providedAsValuesToMap() {
        Map expected = MetaUtilsHelper.mapOf("foo", provideT1(), "bar", provideT2(), "qux", provideT3());

        // act
        Map<String, T> actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertAsValueInMap(expected, actual);
    }

    protected void assertAsValueInMap(Map<String, T> expected, Map<String, T> actual) {
        assertThat(expected).containsAllEntriesOf(actual);
    }

    @Test
    protected void testAsDuplicateValuesInMap() {
        Object instance = provideT4();
        Map expected = MetaUtilsHelper.mapOf("foo", instance, "bar", provideT2(), "qux", instance);

        // act
        String json = TestUtil.toJson(expected);

        assertThat(json).contains("@i")
                .contains("@ref");

        Map<String, T> actual = TestUtil.toObjects(json, null);

        // assert
        assertAsDuplicateValuesInMap(expected, actual);
    }

    protected void assertAsDuplicateValuesInMap(Map<String, T> expected, Map<String, T> actual) {
        assertThat(expected).isNotSameAs(actual);
        assertThat(expected).containsAllEntriesOf(actual);
    }

    @Test
    protected void testT1_serializedWithoutType_parsedAsMaps() {
        // arrange
        T expected = provideT4();

        // act
        Object actual = TestUtil.serializeDeserializeAsMaps(expected);

        // assert
        assertT1_serializedWithoutType_parsedAsMaps(expected, actual);
    }

    protected abstract void assertT1_serializedWithoutType_parsedAsMaps(T expected, Object actual);
}
