package com.cedarsoftware.util.io;

import static com.cedarsoftware.util.io.JsonWriter.CLASSLOADER;
import static com.cedarsoftware.util.io.JsonWriter.CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.DATE_FORMAT;
import static com.cedarsoftware.util.io.JsonWriter.WRITE_ENUMS_AS_PRIMITIVE;
import static com.cedarsoftware.util.io.JsonWriter.ENUM_PUBLIC_ONLY;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_NAME_BLACK_LIST;
import static com.cedarsoftware.util.io.JsonWriter.FIELD_SPECIFIERS;
import static com.cedarsoftware.util.io.JsonWriter.FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS;
import static com.cedarsoftware.util.io.JsonWriter.ISO_DATE_FORMAT;
import static com.cedarsoftware.util.io.JsonWriter.ISO_DATE_TIME_FORMAT;
import static com.cedarsoftware.util.io.JsonWriter.NOT_CUSTOM_WRITER_MAP;
import static com.cedarsoftware.util.io.JsonWriter.PRETTY_PRINT;
import static com.cedarsoftware.util.io.JsonWriter.SHORT_META_KEYS;
import static com.cedarsoftware.util.io.JsonWriter.SKIP_NULL_FIELDS;
import static com.cedarsoftware.util.io.JsonWriter.TYPE;
import static com.cedarsoftware.util.io.JsonWriter.TYPE_NAME_MAP;
import static com.cedarsoftware.util.io.JsonWriter.WRITE_LONGS_AS_STRINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

class WriteOptionsBuilderTest {

    @Test
    void withShortMetaKeys() {
        var options = new WriteOptionsBuilder()
                .withShortMetaKeys()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(SHORT_META_KEYS, Boolean.TRUE);
    }

    @Test
    void withDateFormat_usingPredefinedFormat() {
        var options = new WriteOptionsBuilder()
                .withDateFormat(ISO_DATE_TIME_FORMAT)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_TIME_FORMAT);
    }

    @Test
    void withDateFormat_usingCustomFormat() {
        var options = new WriteOptionsBuilder()
                .withDateFormat("yyyy")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, "yyyy");
    }

    @Test
    void withIsoDateTimeFormat() {
        var options = new WriteOptionsBuilder()
                .withIsoDateTimeFormat()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_TIME_FORMAT);
    }

    @Test
    void withIsoDateFormat() {
        var options = new WriteOptionsBuilder()
                .withIsoDateFormat()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(DATE_FORMAT, ISO_DATE_FORMAT);
    }

    @Test
    void skipNullFields() {
        var options = new WriteOptionsBuilder()
                .skipNullFields()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(SKIP_NULL_FIELDS, Boolean.TRUE);
    }

    @Test
    void withNoTypeInformation() {
        var options = new WriteOptionsBuilder()
                .withNoTypeInformation()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(TYPE, Boolean.FALSE);
    }

    @Test
    void forceTypeInformation() {
        var options = new WriteOptionsBuilder()
                .forceTypeInformation()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(TYPE, Boolean.TRUE);
    }

    @Test
    void withFieldNameBlackList() {
        var options = new WriteOptionsBuilder()
                .withFieldNameBlackList(URL.class, List.of("protocol"))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);

        var optionItem = (Map<Class, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .hasSize(1)
                .containsEntry(URL.class, List.of("protocol"));
    }

    @Test
    void withFieldNameBlackListMap() {
        var map = new HashMap();
        map.put(URL.class, List.of("protocol"));
        map.put(Date.class, List.of("month"));

        var options = new WriteOptionsBuilder()
                .withFieldNameBlackListMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);
        var optionItem = (Map<Class, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .hasSize(2)
                .containsEntry(URL.class, List.of("protocol"))
                .containsEntry(Date.class, List.of("month"));
    }

    @Test
    void withFieldNameBlackListMap_accumulates_andKeepsLastUnique() {

        Map<Class, List<String>> map = Map.of(
                URL.class, List.of("host", "port"),
                Date.class, List.of("month")
        );

        var options = new WriteOptionsBuilder()
                .withFieldNameBlackList(URL.class, List.of("protocol"))
                .withFieldNameBlackList(Locale.class, List.of("foo"))
                .withFieldNameBlackListMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_NAME_BLACK_LIST);

        Map<Class, List<String>> expected = Map.of(
                URL.class, List.of("host", "port"),
                Date.class, List.of("month"),
                Locale.class, List.of("foo")
        );

        var optionItem = (Map<Class, List<String>>)options.get(FIELD_NAME_BLACK_LIST);

        assertThat(optionItem)
                .containsAllEntriesOf(expected);
     }

    @Test
    void withFieldNameSpecifiers() {
        var options = new WriteOptionsBuilder()
                .withFieldSpecifier(URL.class, List.of("protocol"))
                .withFieldSpecifier(Date.class, List.of("month"))
                .withFieldSpecifier(URL.class, List.of("host", "port"))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(FIELD_SPECIFIERS);

        Map<Class, List<String>> map = new HashMap();
        map.put(URL.class, List.of("host", "port"));
        map.put(Date.class, List.of("month"));

        var optionItem = (Map<Class, List<String>>)options.get(FIELD_SPECIFIERS);

        assertThat(optionItem)
                .containsAllEntriesOf(map);
    }

    @Test
    void withFieldNameSpecifierMap() {
        Map<Class, List<String>> map = Map.of(URL.class, List.of("protocol"));

        var options = new WriteOptionsBuilder()
                .withFieldSpecifiersMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FIELD_SPECIFIERS, map);

        var specifiers = (Map<Class, List<String>>) options.get(FIELD_SPECIFIERS);

        assertThat(specifiers)
                .hasSize(1)
                .containsEntry(URL.class, List.of("protocol"));
    }
    @Test
    void withFieldNameSpecifierMap_accumulates_andKeepsUnique() {
        Map<Class, List<String>> map = Map.of(
                URL.class, List.of("protocol"),
                Date.class, List.of("month"));

        var options = new WriteOptionsBuilder()
                .withFieldSpecifiersMap(map)
                .withFieldSpecifier(URL.class, List.of("host", "ref"))
                .withFieldSpecifier(TimeZone.class, List.of("zone"))
                .build();

        var specifiers = (Map<Class, List<String>>) options.get(FIELD_SPECIFIERS);

        Map<Class, List<String>> expected = Map.of(
                URL.class, List.of("host", "ref"),
                TimeZone.class, List.of("zone"),
                Date.class, List.of("month"));

        assertThat(specifiers)
                .hasSize(3)
                .containsAllEntriesOf(expected);
    }

    @Test
    void withPrettyPrint() {
        var options = new WriteOptionsBuilder()
                .withPrettyPrint()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(PRETTY_PRINT, Boolean.TRUE);
    }

    @Test
    void withDefaultOptimizations() {
        var options = new WriteOptionsBuilder()
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
        var options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_WRITER_MAP);

        var customWriterMap = (Map<Class, JsonWriter.JsonClassWriter>)options.get(CUSTOM_WRITER_MAP);

        assertThat(customWriterMap).hasSize(1)
                .containsKey(Date.class);
    }

    @Test
    void withCustomWriterMap() {
        Map<Class, JsonWriter.JsonClassWriter> map = new HashMap<>();

        var options = new WriteOptionsBuilder()
                .withCustomWriterMap(map)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CUSTOM_WRITER_MAP, map);
    }

    @Test
    void withCustomWriterMap_whenCustomWriterMapAlreadyExists_throwsIllegalStateException() {
        var options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .withCustomWriter(TestCustomWriter.Person.class, new TestCustomWriter.CustomPersonWriter())
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(CUSTOM_WRITER_MAP);

        var map = (Map<Class, JsonWriter.JsonClassWriter>)options.get(CUSTOM_WRITER_MAP);

        assertThat(map)
                .containsOnlyKeys(Date.class, TestCustomWriter.Person.class);
    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        var value = "foobar";

        var options = new WriteOptionsBuilder()
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

        var options = new WriteOptionsBuilder()
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
        var options = new WriteOptionsBuilder()
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

        var options = new WriteOptionsBuilder()
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
    void withCustomTypeName_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        var value = "foobar";

        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .withNoTypeInformation()
                        .withCustomTypeName(Date.class, value)
                        .build())
                .withMessage("TYPE_NAME_MAP is not needed when types are not going to be output");
    }

    @Test
    void withCustomTypeNameMap_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .withNoTypeInformation()
                        .withCustomTypeNameMap(new HashMap())
                        .build())
                .withMessage("TYPE_NAME_MAP is not needed when types are not going to be output");
    }

    @Test
    void withCustomTypeNameMap_whenAddingNames_addsUniqueNames() {
        var options = new WriteOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(String.class, "bar2")
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(TYPE_NAME_MAP);

        var map = new HashMap();
        map.put(String.class.getName(), "bar2");

        assertThat(map)
                .containsAllEntriesOf(map);
    }

    private Map<String, String> expectedTypeNameMap() {
        var expectedMap = new HashMap();
        expectedMap.put(Date.class.getName(), "foo2");
        expectedMap.put(String.class.getName(), "bar2");
        return expectedMap;
    }

    @Test
    void writeLongsAsStrigs() {
        var options = new WriteOptionsBuilder()
                .writeLongsAsStrings()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(WRITE_LONGS_AS_STRINGS, Boolean.TRUE);
    }

    @Test
    void doNotWritePrivateEnumFields() {
        var options = new WriteOptionsBuilder()
                .doNotWritePrivateEnumFields()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(ENUM_PUBLIC_ONLY, Boolean.TRUE);
    }

    @Test
    void writeEnumsAsPrimitive() {
        var options = new WriteOptionsBuilder()
                .writeEnumsAsPrimitive()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(WRITE_ENUMS_AS_PRIMITIVE, Boolean.TRUE);
    }

    @Test
    void forceMapOutputAsKeysAndItems() {
        var options = new WriteOptionsBuilder()
                .forceMapOutputAsKeysAndItems()
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(FORCE_MAP_FORMAT_ARRAY_KEYS_ITEMS, Boolean.TRUE);
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        var options = new WriteOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsEntry(CLASSLOADER, classLoader);
    }

    @Test
    void doNotCustomizeClass() {
        var options = new WriteOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }

    @Test
    void doNotCustomizeClass_withTwoOfSameClass_isUsingSetUnderneath() {
        var options = new WriteOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(String.class)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .hasSize(1)
                .contains(String.class);
    }
    @Test
    void doNotCustomizeClass_addsAdditionalUniqueClasses() {
        Collection<Class> list = List.of(HashMap.class, String.class);

        var options = new WriteOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Map.class)
                .withNonCustomizableClasses(list)
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(List.of(String.class, HashMap.class, Map.class));
    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        var options = new WriteOptionsBuilder()
                .withNonCustomizableClass(String.class)
                .withNonCustomizableClass(Date.class)
                .withNonCustomizableClasses(List.of(String.class, List.class))
                .build();

        assertThat(options)
                .hasSize(1)
                .containsKey(NOT_CUSTOM_WRITER_MAP);

        var collection = (Collection<Class>)options.get(NOT_CUSTOM_WRITER_MAP);

        assertThat(collection)
                .containsExactlyInAnyOrderElementsOf(List.of(String.class, Date.class, List.class));
    }


}
