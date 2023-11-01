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
        Map options = new ReadOptionsBuilder()
                .failOnUnknownType()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FAIL_ON_UNKNOWN_TYPE, Boolean.TRUE);
    }

    @Test
    void setUnknownTypeClass() {
        Map options = new ReadOptionsBuilder()
                .setUnknownTypeClass(LinkedHashMap.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(UNKNOWN_OBJECT, LinkedHashMap.class.getName());
    }

    @Test
    void returnAsMaps() {
        Map options = new ReadOptionsBuilder()
                .returnAsMaps()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(USE_MAPS, Boolean.TRUE);
    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        String value = "foobar";

        Map options = new ReadOptionsBuilder()
                .withCustomTypeName(Date.class, value)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(typeNameMap).hasSize(1)
                .containsEntry(Date.class.getName(), value);
    }

    @Test
    void withCustomTypeName_usingStringAsKey() {
        String key = "javax.sql.Date";
        String value = "foobar";

        Map options = new ReadOptionsBuilder()
                .withCustomTypeName(key, value)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(typeNameMap).hasSize(1)
                .containsEntry(key, value);
    }

    @Test
    void withCustomTypeName_whenAddingNames_addsUniqueNames() {
        Map options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(Date.class.getName(), "foo1")
                .withCustomTypeName(String.class, "bar2")
                .withCustomTypeName(Date.class.getName(), "foo2")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map map = (Map<String, String>)options.get(TYPE_NAME_MAP);

        assertThat(map)
                .containsAllEntriesOf(expectedTypeNameMap());
    }

    @Test
    void withCustomTypeName_withMixedCustomTypeNameInitialization_accumulates() {
        Map<String, String> map = MetaUtils.mapOf(
                String.class.getName(), "char1",
                "foo", "bar");

        Map options = new ReadOptionsBuilder()
                .withCustomTypeNameMap(map)
                .withCustomTypeName(String.class, "char2")
                .withCustomTypeName(Date.class, "dt")
                .withCustomTypeName(TimeZone.class.getName(), "tz")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map typeNameMap = (Map<String, String>)options.get(TYPE_NAME_MAP);

        Map<String, String> expected = MetaUtils.mapOf(
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
        Map options = new ReadOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(String.class, "bar2")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map map = new HashMap<>();
        map.put(String.class.getName(), "bar2");

        assertThat(map)
                .containsAllEntriesOf(map);
    }

    @Test
    void withCustomReader() {
        Map options = new ReadOptionsBuilder()
                .withCustomReader(Date.class, new Readers.DateReader())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_READER_MAP);

        Map customWriterMap = (Map<Class, JsonReader.JsonClassReader>)options.get(CUSTOM_READER_MAP);

        assertThat(customWriterMap).hasSize(1)
                .containsKey(Date.class);
    }

    @Test
    void withCustomReaderMap() {
        Map<Class<?>, JsonReader.JsonClassReader> map = new HashMap<>();

        Map options = new ReadOptionsBuilder()
                .withCustomReaders(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CUSTOM_READER_MAP, map);
    }

    @Test
    void withCustomReaderMap_whenCustomWriterMapAlreadyExists_throwsIllegalStateException() {
        Map options = new ReadOptionsBuilder()
                .withCustomReader(Date.class, new Readers.DateReader())
                .withCustomReader(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonReader())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_READER_MAP);

        Map map = (Map<Class, JsonReader.JsonClassReader>)options.get(CUSTOM_READER_MAP);

        assertThat(map)
                .containsOnlyKeys(Date.class, CustomWriterTest.Person.class);
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        Map options = new ReadOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CLASSLOADER, classLoader);
    }

    @Test
    void withNonCustomizableClass() {
        Map options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        Collection collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }

    @Test
    void withNonCustomizableClass_withTwoOfSameClass_isUsingSetUnderneath() {
        Map options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        Collection collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }
    @Test
    void withNonCustomizableClass_addsAdditionalUniqueClasses() {
        Collection<Class<?>> list = MetaUtils.listOf(HashMap.class, String.class);

        Map options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Map.class)
                .withNonCustomizableClasses(list)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        Collection collection = (Collection<Class<?>>) options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(MetaUtils.listOf(String.class, HashMap.class, Map.class));
    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        Map options = new ReadOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Date.class)
                .withNonCustomizableClasses(MetaUtils.listOf(String.class, List.class))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_READER_MAP);

        Collection collection = (Collection<Class>)options.get(NOT_CUSTOM_READER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(MetaUtils.listOf(String.class, Date.class, List.class));
    }

    @Test
    void withClassFactory() {
        LocalDateFactory localDateFactory = new LocalDateFactory();
        Map options = new ReadOptionsBuilder()
                .withClassFactory(LocalDate.class, localDateFactory)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FACTORIES);

        Map collection = (Map<String, JsonReader.ClassFactory>) options.get(FACTORIES);

        assertThat(collection)
                .containsAllEntriesOf(MetaUtils.mapOf(LocalDate.class.getName(), localDateFactory));
    }

    private Map<String, JsonReader.ClassFactory> getClassFactoryMap() {
        return MetaUtils.mapOf(
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
