package com.cedarsoftware.util.io;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        String json = TestUtil.getJsonString(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        Object actual = TestUtil.readJsonObject(json);

        // assert
        assertDuplicatesNestedInObject(expected, actual);
    }

    protected abstract Object provideDuplicatesNestedInObject();

    protected abstract void assertDuplicatesNestedInObject(Object expected, Object actual);

    // object array unique
    @Test
    protected void testInObjectArray() {
        var expected = List.of(provideT1(), "foo", 9L, provideT2());

        // act
        var actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertInObjectArray(expected, actual);
    }

    protected void assertInObjectArray(List expected, List actual) {
        assertThat(actual).hasSameElementsAs(expected);
    }


    // Object array with duplicates
    @Test
    protected void testDuplicatesInObjectArray() {
        T standalone = provideT1();
        var expected = List.of(standalone, standalone, 5, "foo");

        // act
        var json = TestUtil.getJsonString(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        List<Object> actual = TestUtil.readJsonObject(json);

        // assert
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
        var expected = List.of(provideT1(), provideT2(), provideT3(), provideT4());

        // act
        var actual = TestUtil.serializeDeserialize(expected);

        // assert
        assertTestInCollection(expected, actual);
    }

    protected void assertTestInCollection(List<T> expected, List<T> actual) {
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    protected void testDuplicatesInCollection() {
        T instance = provideT1();
        var expected = List.of(instance, provideT2(), provideT3(), provideT4(), instance);

        // act
        var json = TestUtil.getJsonString(expected);

        assertThat(json)
                .contains("@i")
                .contains("@ref");

        List<T> actual = TestUtil.readJsonObject(json);

        // assert
        assertDuplicatesInCollection(expected, actual);
    }

    protected void assertDuplicatesInCollection(List<T> expected, List<T> actual) {
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.get(0)).isSameAs(actual.get(4));
    }

    @Test
    protected void testAsValueInMap() {
        var expected = Map.of("foo", provideT1(), "bar", provideT2(), "qux", provideT3());

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
        var instance = provideT4();
        var expected = Map.of("foo", instance, "bar", provideT2(), "qux", instance);

        // act
        var json = TestUtil.getJsonString(expected);

        assertThat(json).contains("@i")
                .contains("@ref");

        Map<String, T> actual = TestUtil.readJsonObject(json);

        // assert
        assertAsDuplicateValuesInMap(expected, actual);
    }

    protected void assertAsDuplicateValuesInMap(Map<String, T> expected, Map<String, T> actual) {
        assertThat(expected).containsAllEntriesOf(actual);
    }
}
