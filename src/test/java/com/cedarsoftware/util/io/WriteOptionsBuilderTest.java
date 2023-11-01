package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.*;

import static com.cedarsoftware.util.io.JsonWriter.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class WriteOptionsBuilderTest {

    @Test
    void withShortMetaKeys() {
        Map options = new WriteOptionsBuilder()
                .withShortMetaKeys()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(SHORT_META_KEYS, Boolean.TRUE);
    }

    @Test
    void withDateFormat_usingPredefinedFormat() {
        Map options = new WriteOptionsBuilder()
                .withDateFormat(ISO_DATE_TIME_FORMAT)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_TIME_FORMAT);
    }

    @Test
    void withDateFormat_usingCustomFormat() {
        Map options = new WriteOptionsBuilder()
                .withDateFormat("yyyy")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, "yyyy");
    }

    @Test
    void withIsoDateTimeFormat() {
        Map options = new WriteOptionsBuilder()
                .withIsoDateTimeFormat()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_TIME_FORMAT);
    }

    @Test
    void withIsoDateFormat() {
        Map options = new WriteOptionsBuilder()
                .withIsoDateFormat()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_FORMAT);
    }

    @Test
    void skipNullFields() {
        Map options = new WriteOptionsBuilder()
                .skipNullFields()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(SKIP_NULL_FIELDS, Boolean.TRUE);
    }

    @Test
    void withNoTypeInformation() {
        Map options = new WriteOptionsBuilder()
                .noTypeInfo()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(TYPE, Boolean.FALSE);
    }

    @Test
    void forceTypeInformation() {
        Map options = new WriteOptionsBuilder()
                .forceTypeInfo()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(TYPE, Boolean.TRUE);
    }

    @Test
    void withFieldNameBlackList() {
        Map options = new WriteOptionsBuilder()
                .withFieldNameBlackList(URL.class, MetaUtils.listOf("protocol"))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);

        Map optionItem = (Map<Class, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .hasSize(1)
                .containsEntry(URL.class, MetaUtils.listOf("protocol"));
    }

    @Test
    void withFieldNameBlackListMap() {
        Map<Class<?>, List<String>> map = new HashMap<>();
        map.put(URL.class, MetaUtils.listOf("protocol"));
        map.put(Date.class, MetaUtils.listOf("month"));

        Map options = new WriteOptionsBuilder()
                .withFieldNameBlackListMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);
        Map optionItem = (Map<Class<?>, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .hasSize(2)
                .containsEntry(URL.class, MetaUtils.listOf("protocol"))
                .containsEntry(Date.class, MetaUtils.listOf("month"));
    }

    @Test
    void withFieldNameBlackListMap_accumulates_andKeepsLastUnique() {

        Map<Class<?>, List<String>> map = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("host", "port"),
                Date.class, MetaUtils.listOf("month")
        );

        Map options = new WriteOptionsBuilder()
                .withFieldNameBlackList(URL.class, MetaUtils.listOf("protocol"))
                .withFieldNameBlackList(Locale.class, MetaUtils.listOf("foo"))
                .withFieldNameBlackListMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);

        Map<Class, List<String>> expected = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("host", "port"),
                Date.class, MetaUtils.listOf("month"),
                Locale.class, MetaUtils.listOf("foo")
        );

        Map optionItem = (Map<Class, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .containsAllEntriesOf(expected);
     }

    @Test
    void withFieldNameSpecifiers() {
        Map options = new WriteOptionsBuilder()
                .withFieldSpecifier(URL.class, MetaUtils.listOf("protocol"))
                .withFieldSpecifier(Date.class, MetaUtils.listOf("month"))
                .withFieldSpecifier(URL.class, MetaUtils.listOf("host", "port"))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_SPECIFIERS);

        Map<Class, List<String>> map = new HashMap<>();
        map.put(URL.class, MetaUtils.listOf("host", "port"));
        map.put(Date.class, MetaUtils.listOf("month"));

        Map optionItem = (Map<Class, List<String>>)options.get(FIELD_SPECIFIERS);

        assertThat(optionItem)
                .containsAllEntriesOf(map);
    }

    @Test
    void withFieldNameSpecifierMap() {
        Map<Class<?>, List<String>> map = MetaUtils.mapOf(URL.class, MetaUtils.listOf("protocol"));

        Map options = new WriteOptionsBuilder()
                .withFieldSpecifiersMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FIELD_SPECIFIERS, map);

        Map specifiers = (Map<Class<?>, List<String>>) options.get(FIELD_SPECIFIERS);

        assertThat(specifiers)
                .hasSize(1)
                .containsEntry(URL.class, MetaUtils.listOf("protocol"));
    }
    @Test
    void withFieldNameSpecifierMap_accumulates_andKeepsUnique() {
        Map<Class<?>, List<String>> map = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("protocol"),
                Date.class, MetaUtils.listOf("month"));

        Map options = new WriteOptionsBuilder()
                .withFieldSpecifiersMap(map)
                .withFieldSpecifier(URL.class, MetaUtils.listOf("host", "ref"))
                .withFieldSpecifier(TimeZone.class, MetaUtils.listOf("zone"))
                .build();

        Map specifiers = (Map<Class<?>, List<String>>) options.get(FIELD_SPECIFIERS);

        Map<Class<?>, List<String>> expected = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("host", "ref"),
                TimeZone.class, MetaUtils.listOf("zone"),
                Date.class, MetaUtils.listOf("month"));

        assertThat(specifiers)
                .hasSize(3)
                .containsAllEntriesOf(expected);
    }

    @Test
    void withPrettyPrint() {
        Map options = new WriteOptionsBuilder()
                .withPrettyPrint()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(PRETTY_PRINT, Boolean.TRUE);
    }

    @Test
    void withDefaultOptimizations() {
        Map options = new WriteOptionsBuilder()
                .withDefaultOptimizations()
                .build();

        assertThat(options)
                .hasSize(3)
                .containsEntry(SKIP_NULL_FIELDS, Boolean.TRUE)
                .containsEntry(SHORT_META_KEYS, Boolean.TRUE)
                .containsEntry(DATE_FORMAT, ISO_DATE_TIME_FORMAT);
    }

    @Test
    void withCustomWriter() {
        Map options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_WRITER_MAP);

        Map customWriterMap = (Map<Class<?>, JsonWriter.JsonClassWriter>)options.get(CUSTOM_WRITER_MAP);

        assertThat(customWriterMap).hasSize(1)
                .containsKey(Date.class);
    }

    @Test
    void withCustomWriterMap() {
        Map<Class<?>, JsonWriter.JsonClassWriter> map = new HashMap<>();

        Map options = new WriteOptionsBuilder()
                .withCustomWriterMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CUSTOM_WRITER_MAP, map);
    }

    @Test
    void withCustomWriterMap_whenCustomWriterMapAlreadyExists_throwsIllegalStateException() {
        Map options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .withCustomWriter(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonWriter())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_WRITER_MAP);

        Map map = (Map<Class<?>, JsonWriter.JsonClassWriter>)options.get(CUSTOM_WRITER_MAP);

        assertThat(map)
                .containsOnlyKeys(Date.class, CustomWriterTest.Person.class);
    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        String value = "foobar";

        Map options = new WriteOptionsBuilder()
                .withCustomTypeName(Date.class, value)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        Map<String, String> typeNameMap = (Map)options.get(TYPE_NAME_MAP);

        assertThat(typeNameMap).hasSize(1)
                .containsEntry(Date.class.getName(), value);
    }

    @Test
    void withCustomTypeName_usingStringAsKey() {
        String key = "javax.sql.Date";
        String value = "foobar";

        Map options = new WriteOptionsBuilder()
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
        Map options = new WriteOptionsBuilder()
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

        Map options = new WriteOptionsBuilder()
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
    void withCustomTypeName_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        String value = "foobar";

        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .noTypeInfo()
                        .withCustomTypeName(Date.class, value)
                        .build())
                .withMessage("TYPE_NAME_MAP is not needed when types are not going to be output");
    }

    @Test
    void withCustomTypeNameMap_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .noTypeInfo()
                        .withCustomTypeNameMap(new HashMap<>())
                        .build())
                .withMessage("TYPE_NAME_MAP is not needed when types are not going to be output");
    }

    @Test
    void withCustomTypeNameMap_whenAddingNames_addsUniqueNames() {
        Map options = new WriteOptionsBuilder()
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

    private Map<String, String> expectedTypeNameMap() {
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(Date.class.getName(), "foo2");
        expectedMap.put(String.class.getName(), "bar2");
        return expectedMap;
    }

    @Test
    void writeLongsAsStrigs() {
        Map options = new WriteOptionsBuilder()
                .writeLongsAsStrings()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(WRITE_LONGS_AS_STRINGS, Boolean.TRUE);
    }

    @Test
    void doNotWritePrivateEnumFields() {
        Map options = new WriteOptionsBuilder()
                .doNotWritePrivateEnumFields()
                .build();

        assertThat(options)
                .hasSize(2)
                .containsEntry(ENUM_PUBLIC_ONLY, Boolean.TRUE)
                .containsEntry(WRITE_ENUMS_AS_OBJECTS, Boolean.TRUE);
    }

    @Test
    void writeEnumsAsObjects() {
        Map options = new WriteOptionsBuilder()
                .writeEnumsAsObjects()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(WRITE_ENUMS_AS_OBJECTS, Boolean.TRUE);
    }

    @Test
    void forceMapOutputAsKeysAndItems() {
        Map options = new WriteOptionsBuilder()
                .forceMapOutputAsKeysAndItems()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, Boolean.TRUE);
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        Map options = new WriteOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CLASSLOADER, classLoader);
    }

    @Test
    void doNotCustomizeClass() {
        Map options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        Collection collection = (Collection<Class<?>>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }

    @Test
    void doNotCustomizeClass_withTwoOfSameClass_isUsingSetUnderneath() {
        Map options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        Collection collection = (Collection<Class<?>>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }
    @Test
    void doNotCustomizeClass_addsAdditionalUniqueClasses() {
        Collection<Class<?>> list = MetaUtils.listOf(HashMap.class, String.class);

        Map options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(Map.class)
                .withNoCustomizationsFor(list)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        Collection collection = (Collection<Class<?>>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(MetaUtils.listOf(String.class, HashMap.class, Map.class));
    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        Map options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(Date.class)
                .withNoCustomizationsFor(MetaUtils.listOf(String.class, List.class))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        Collection collection = (Collection<Class<?>>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(MetaUtils.listOf(String.class, Date.class, List.class));
    }
}
