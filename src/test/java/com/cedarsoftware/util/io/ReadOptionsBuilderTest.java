package com.cedarsoftware.util.io;

import com.cedarsoftware.util.io.factory.LocalDateFactory;
import com.cedarsoftware.util.io.factory.LocalTimeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static com.cedarsoftware.util.io.JsonReader.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReadOptionsBuilderTest {

    @Test
    void failOnUnknownType() {
        var options = new ReadOptionsBuilder()
                .failOnUnknownType()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FAIL_ON_UNKNOWN_TYPE, Boolean.TRUE);
    }

    @Test
    void setUnknownTypeClass() {
        var options = new ReadOptionsBuilder()
                .setUnknownTypeClass(LinkedHashMap.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(UNKNOWN_OBJECT, LinkedHashMap.class.getName());
    }

    @Test
    void returnAsMaps() {
        var options = new ReadOptionsBuilder()
                .returnAsMaps()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(USE_MAPS, Boolean.TRUE);
    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        var value = "foobar";

        var options = new ReadOptionsBuilder()
                .withCustomTypeName(Date.class, value)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(typeNameMap).hasSize(1)
                .containsEntry(Date.class.getName(), value);
    }

    @Test
    void withCustomTypeName_usingStringAsKey() {
        var key = "javax.sql.Date";
        var value = "foobar";

        var options = new ReadOptionsBuilder()
                .withCustomTypeName(key, value)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(typeNameMap).hasSize(1)
                .containsEntry(key, value);
    }

    @Test
    void withCustomTypeName_whenAddingNames_addsUniqueNames() {
        var options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(Date.class.getName(), "foo1")
                .withCustomTypeName(String.class, "bar2")
                .withCustomTypeName(Date.class.getName(), "foo2")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var map = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(map)
                .containsAllEntriesOf(expectedTypeNameMap());
    }

    @Test
    void withCustomTypeName_withMixedCustomTypeNameInitialization_accumulates() {
        Map<String, String> map = Map.of(
                String.class.getName(), "char1",
                "foo", "bar");

        var options = new ReadOptionsBuilder()
                .withCustomTypeNameMap(map)
                .withCustomTypeName(String.class, "char2")
                .withCustomTypeName(Date.class, "dt")
                .withCustomTypeName(TimeZone.class.getName(), "tz")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        Map<String, String> expected = Map.of(
                String.class.getName(), "char2",
                "foo", "bar",
                Date.class.getName(), "dt",
                TimeZone.class.getName(), "tz"
        );

        assertThat(typeNameMap)
                .containsAllEntriesOf(expected);
    }

    @Test
    void withCustomTypeNameMap_whenAddingNames_addsUniqueNames() {
        var options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(String.class, "bar2")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var map = new HashMap<>();
        map.put(String.class.getName(), "bar2");

        assertThat(map)
                .containsAllEntriesOf(map);
    }

    @Test
    void withCustomReader() {
        var options = new ReadOptionsBuilder()
                .withCustomReader(Date.class, new Readers.DateReader())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_READER_MAP);

        var customWriterMap = (Map<Class, JsonReader.JsonClassReader>)options.get(CUSTOM_READER_MAP);

        assertThat(customWriterMap).hasSize(1)
                .containsKey(Date.class);
    }

    @Test
    void withCustomReaderMap() {
        Map<Class<?>, JsonReader.JsonClassReader> map = new HashMap<>();

        var options = new ReadOptionsBuilder()
                .withCustomReaders(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CUSTOM_READER_MAP, map);
    }

    @Test
    void withCustomReaderMap_whenCustomWriterMapAlreadyExists_throwsIllegalStateException() {
        var options = new ReadOptionsBuilder()
                .withCustomReader(Date.class, new Readers.DateReader())
                .withCustomReader(TestCustomWriter.Person.class, new TestCustomWriter.CustomPersonReader())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_READER_MAP);

        var map = (Map<Class, JsonReader.JsonClassReader>)options.get(CUSTOM_READER_MAP);

        assertThat(map)
                .containsOnlyKeys(Date.class, TestCustomWriter.Person.class);
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        var options = new ReadOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CLASSLOADER, classLoader);
    }

    @Test
    void withNonCustomizableClass() {
        var options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }

    @Test
    void withNonCustomizableClass_withTwoOfSameClass_isUsingSetUnderneath() {
        var options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }
    @Test
    void withNonCustomizableClass_addsAdditionalUniqueClasses() {
        Collection<Class<?>> list = List.of(HashMap.class, String.class);

        var options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Map.class)
                .withNonCustomizableClasses(list)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        var collection = (Collection<Class<?>>) options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(List.of(String.class, HashMap.class, Map.class));
    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        var options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Date.class)
                .withNonCustomizableClasses(List.of(String.class, List.class))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(List.of(String.class, Date.class, List.class));
    }

    @Test
    void withClassFactory() {
        var localDateFactory = new LocalDateFactory();
        var options = new ReadOptionsBuilder()
                .withClassFactory(LocalDate.class, localDateFactory)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FACTORIES);

        var collection = (Map<String, JsonReader.ClassFactory>) options.get(FACTORIES);

        assertThat(collection)
                .containsAllEntriesOf(Map.of(LocalDate.class.getName(), localDateFactory));
    }

    private Map<String, JsonReader.ClassFactory> getClassFactoryMap() {
        return Map.of(
                LocalDate.class.getName(), new LocalDateFactory(),
                LocalTime.class.getName(), new LocalTimeFactory());
    }


    private Map<String, String> expectedTypeNameMap() {
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(Date.class.getName(), "foo2");
        expectedMap.put(String.class.getName(), "bar2");
        return expectedMap;
    }
}
