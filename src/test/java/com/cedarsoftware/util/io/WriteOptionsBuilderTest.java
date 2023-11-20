package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

class WriteOptionsBuilderTest {

    @Test
    void withShortMetaKeys() {
        WriteOptions options = new WriteOptionsBuilder()
                .withShortMetaKeys()
                .build();

        assertThat(options.isUsingShortMetaKeys()).isTrue();
    }

    @Test
    void skipNullFields() {
        WriteOptions options = new WriteOptionsBuilder()
                .skipNullFields()
                .build();

        assertThat(options.isSkippingNullFields()).isTrue();
    }

    @Test
    void withNoTypeInformation() {
        WriteOptions options = new WriteOptionsBuilder()
                .neverShowTypeInfo()
                .build();

        assertThat(options.isNeverShowingType()).isTrue();
    }

    @Test
    void forceTypeInformation() {
        WriteOptions options = new WriteOptionsBuilder()
                .alwaysShowTypeInfo()
                .build();

        assertThat(options.isAlwaysShowingType()).isTrue();
    }

    @Test
    void withFieldNameBlackList() {
        WriteOptions options = new WriteOptionsBuilder()
                .excludedFields(URL.class, MetaUtils.listOf("protocol"))
                .build();

        assertThat(options.getExcludedAccessors(URL.class)).hasSize(1);
    }

    @Test
    void withFieldNameBlackListMap() {
        Map<Class<?>, Collection<String>> map = new HashMap<>();
        map.put(URL.class, MetaUtils.listOf("protocol"));
        map.put(LocalDate.class, MetaUtils.listOf("month"));

        WriteOptions options = new WriteOptionsBuilder()
                .excludedFields(map)
                .build();

        assertThat(options.getExcludedAccessors(URL.class)).hasSize(1);
    }

    @Test
    void withFieldNameBlackListMap_accumulates_andKeepsLastUnique() {

        Map<Class<?>, Collection<String>> map = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("host", "port"),
                LocalDate.class, MetaUtils.listOf("month")
        );

        WriteOptions options = new WriteOptionsBuilder()
                .excludedFields(URL.class, MetaUtils.listOf("protocol"))
                .excludedFields(map)
                .build();

        assertThat(options.getExcludedAccessors(URL.class)).hasSize(3);
     }

    @Test
    void withFieldNameSpecifiers() {
        WriteOptions options = new WriteOptionsBuilder()
                .includedFields(URL.class, MetaUtils.listOf("protocol"))
                .includedFields(LocalDate.class, MetaUtils.listOf("month"))
                .includedFields(URL.class, MetaUtils.listOf("host", "port"))
                .build();

        assertThat(options.getIncludedAccessors(URL.class)).hasSize(3);
        assertThat(options.getIncludedAccessors(LocalDate.class)).hasSize(1);
    }

    @Test
    void withFieldNameSpecifierMap() {
        Map<Class<?>, Collection<String>> map = MetaUtils.mapOf(URL.class, MetaUtils.listOf("protocol"));

        WriteOptions options = new WriteOptionsBuilder()
                .includedFields(map)
                .build();

        assertThat(options.getIncludedAccessors(URL.class)).hasSize(1);
    }
    @Test
    void withFieldNameSpecifierMap_accumulates_andKeepsUnique() {
        Map<Class<?>, Collection<String>> map = MetaUtils.mapOf(
                URL.class, MetaUtils.listOf("protocol"),
                LocalDate.class, MetaUtils.listOf("month"));

        WriteOptions options = new WriteOptionsBuilder()
                .includedFields(map)
                .includedFields(URL.class, MetaUtils.listOf("host", "ref"))
                .includedFields(LocalDate.class, MetaUtils.listOf("year"))
                .build();

        assertThat(options.getIncludedAccessors(URL.class)).hasSize(3);
    }

    @Test
    void withPrettyPrint() {
        WriteOptions options = new WriteOptionsBuilder()
                .withPrettyPrint()
                .build();

        assertThat(options.isPrettyPrint()).isTrue();
    }

    @Test
    void withDefaultOptimizations() {
        WriteOptions options = new WriteOptionsBuilder()
                .withDefaultOptimizations()
                .build();

        assertThat(options.isSkippingNullFields()).isTrue();
        assertThat(options.isUsingShortMetaKeys()).isTrue();
    }

    @Test
    void withCustomWriter() {
        WriteOptions options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .build();

        assertThat(options.getCustomWriter(Date.class)).isNotNull();
    }

    @Test
    void withCustomWriterMap() {
        Map<Class<?>, JsonWriter.JsonClassWriter> map = new HashMap<>();

        WriteOptions options = new WriteOptionsBuilder()
                .withCustomWriters(map)
                .build();

        // needs a real test.
        assertThat(options.getCustomWriter(Calendar.class)).isNotNull();
        assertThat(options.getCustomWriter(Timestamp.class)).isNotNull();
        assertThat(options.getCustomWriter(BigInteger.class)).isNotNull();
        assertThat(options.getCustomWriter(BigDecimal.class)).isNotNull();
    }

    @Test
    void withCustomWriterMap_whenCustomWriterMapAlreadyExists_throwsIllegalStateException() {
        WriteOptions options = new WriteOptionsBuilder()
                .withCustomWriter(Date.class, new Writers.DateWriter())
                .withCustomWriter(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonWriter())
                .build();

        assertThat(options.getCustomWriter(Date.class)).isNotNull();
        assertThat(options.getCustomWriter(CustomWriterTest.Person.class)).isNotNull();

    }

    @Test
    void withCustomTypeName_usingClassAsKey() {
        String value = "foobar";

        WriteOptions options = new WriteOptionsBuilder()
                .withCustomTypeName(Date.class, value)
                .build();

        assertThat(options.getCustomWriter(Calendar.class)).isNotNull();
        assertThat(options.getCustomWriter(Timestamp.class)).isNotNull();
        assertThat(options.getCustomWriter(BigInteger.class)).isNotNull();
        assertThat(options.getCustomWriter(BigDecimal.class)).isNotNull();
    }

    @Test
    void withCustomTypeName_usingStringAsKey() {
        String key = "javax.sql.Date";
        String value = "foobar";

        WriteOptions options = new WriteOptionsBuilder()
                .withCustomTypeName(key, value)
                .build();

        assertThat(options.getCustomNameOrDefault(key, null)).isEqualTo(value);
    }

    @Test
    void withCustomTypeName_whenAddingNames_addsUniqueNames() {
        WriteOptions options = new WriteOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(Date.class.getName(), "foo1")
                .withCustomTypeName(String.class, "bar2")
                .withCustomTypeName(Date.class.getName(), "foo2")
                .build();

        assertThat(options.getCustomNameOrDefault(String.class.getName(), null)).isEqualTo("bar2");
        assertThat(options.getCustomNameOrDefault(Date.class.getName(), null)).isEqualTo("foo2");
    }

    @Test
    void withCustomTypeName_withMixedCustomTypeNameInitialization_accumulates() {
        Map<String, String> map = MetaUtils.mapOf(
                String.class.getName(), "char1",
                "foo", "bar");

        WriteOptions options = new WriteOptionsBuilder()
                .withCustomTypeNames(map)
                .withCustomTypeName(String.class, "char2")
                .withCustomTypeName(Date.class, "dt")
                .withCustomTypeName(TimeZone.class.getName(), "tz")
                .build();

        assertThat(options.getCustomNameOrDefault("foo", null)).isEqualTo("bar");
        assertThat(options.getCustomNameOrDefault(String.class.getName(), null)).isEqualTo("char2");
    }



    @Test
    void withCustomTypeName_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        String value = "foobar";

        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .neverShowTypeInfo()
                        .withCustomTypeName(Date.class, value)
                        .build())
                .withMessage("There is no need to set the type name map when types are never being written");
    }

    @Test
    void withCustomTypeNameMap_whenNoTypeInformationIsBeingOutput_throwsIllegalStateException() {
        assertThatIllegalStateException().isThrownBy(() ->new WriteOptionsBuilder()
                        .neverShowTypeInfo()
                        .withCustomTypeNames(new HashMap<>())
                        .build())
                .withMessage("There is no need to set the type name map when types are never being written");
    }

    @Test
    void withCustomTypeNameMap_whenAddingNames_addsUniqueNames() {
        WriteOptions options = new WriteOptionsBuilder()
                .withCustomTypeName(String.class, "bar1")
                .withCustomTypeName(String.class, "bar2")
                .build();

        assertThat(options.getCustomNameOrDefault(String.class.getName(), null)).isEqualTo("bar2");
    }

    private Map<String, String> expectedTypeNameMap() {
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(Date.class.getName(), "foo2");
        expectedMap.put(String.class.getName(), "bar2");
        return expectedMap;
    }

    @Test
    void writeLongsAsStrigs() {
        WriteOptions options = new WriteOptionsBuilder()
                .writeLongsAsStrings()
                .build();

        assertThat(options.isWritingLongsAsStrings()).isTrue();
    }

    @Test
    void doNotWritePrivateEnumFields() {
        WriteOptions options = new WriteOptionsBuilder()
                .doNotWritePrivateEnumFields()
                .build();

        assertThat(options.isEnumPublicOnly()).isTrue();
    }

    @Test
    void writeEnumsAsObjects() {
        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumsAsObject()
                .build();

        assertThat(options.isEnumPublicOnly()).isFalse();
    }

    @Test
    void forceMapOutputAsKeysAndItems() {
        WriteOptions options = new WriteOptionsBuilder()
                .forceMapOutputAsKeysAndValues()
                .build();

        assertThat(options.isForcingMapFormatWithKeyArrays()).isTrue();
    }

    @Test
    void withClassLoader() {
        ClassLoader classLoader = this.getClass().getClassLoader();

        WriteOptions options = new WriteOptionsBuilder()
                .withClassLoader(classLoader)
                .build();

        assertThat(options.getClassLoader()).isEqualTo(this.getClass().getClassLoader());
    }

    @Test
    void doNotCustomizeClass() {
        WriteOptions options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .build();

        assertThat(options.isNonCustomClass(String.class)).isTrue();
    }

    @Test
    void doNotCustomizeClass_withTwoOfSameClass_isUsingSetUnderneath() {
        WriteOptions options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(String.class)
                .build();

        assertThat(options.isNonCustomClass(String.class)).isTrue();
    }
    @Test
    void doNotCustomizeClass_addsAdditionalUniqueClasses() {
        Collection<Class<?>> list = MetaUtils.listOf(HashMap.class, String.class);

        WriteOptions options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(Map.class)
                .withNoCustomizationsFor(list)
                .build();

        assertThat(options.isNonCustomClass(String.class)).isTrue();
        assertThat(options.isNonCustomClass(Map.class)).isTrue();
        assertThat(options.isNonCustomClass(HashMap.class)).isTrue();

    }

    @Test
    void withNonCustomizableClasses_whenNonCustomizableClassesExist_addsUniqueItemsToTheCollection() {
        WriteOptions options = new WriteOptionsBuilder()
                .withNoCustomizationFor(String.class)
                .withNoCustomizationFor(Date.class)
                .withNoCustomizationsFor(MetaUtils.listOf(String.class, List.class))
                .build();

        assertThat(options.isNonCustomClass(String.class)).isTrue();
        assertThat(options.isNonCustomClass(Date.class)).isTrue();
        assertThat(options.isNonCustomClass(List.class)).isTrue();
    }
}
