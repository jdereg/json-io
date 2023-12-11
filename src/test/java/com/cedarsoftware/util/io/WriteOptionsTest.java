package com.cedarsoftware.util.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class WriteOptionsTest {

    @Test
    void tesSkipNullFields_default() {
        assertFalse(new WriteOptionsBuilder().build().isSkipNullFields());
    }

    // Example test for a setter method (skipNullFields)
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSetSkipNullFields(boolean value) {
        WriteOptions options = new WriteOptionsBuilder().skipNullFields(value).build();
        assertThat(options.isSkipNullFields()).isEqualTo(value);
    }

    private static Stream<Arguments> classLoadersTest() {
        return Stream.of(
                Arguments.of(ClassLoader.getSystemClassLoader()),
                Arguments.of(WriteOptionsTest.class.getClassLoader())
        );
    }

    @ParameterizedTest
    @MethodSource("classLoadersTest")
    void testClassLoader(ClassLoader classLoader) {
        WriteOptions options = new WriteOptionsBuilder().classLoader(classLoader).build();
        assertThat(options.getClassLoader()).isSameAs(classLoader);
    }

    @Test
    void testClassLoader_default() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.getClassLoader()).isSameAs(WriteOptionsBuilder.class.getClassLoader());
    }

    // Test for forceMapOutputAsTwoArrays method
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testForceMapOutputAsTwoArrays(boolean value) {
        WriteOptions options = new WriteOptionsBuilder().forceMapOutputAsTwoArrays(value).build();
        assertTrue(options.isForceMapOutputAsTwoArrays());
    }

    // Test for isNonReferenceableClass method
    private static Stream<Arguments> nonReferenceableClasses() {
        return Stream.of(
                Arguments.of(byte.class),
                Arguments.of(short.class),
                Arguments.of(int.class),
                Arguments.of(long.class),
                Arguments.of(float.class),
                Arguments.of(double.class),
                Arguments.of(char.class),
                Arguments.of(boolean.class),
                Arguments.of(Byte.class),
                Arguments.of(Short.class),
                Arguments.of(Integer.class),
                Arguments.of(Long.class),
                Arguments.of(Float.class),
                Arguments.of(Double.class),
                Arguments.of(Character.class),
                Arguments.of(Boolean.class),
                Arguments.of(Class.class),
                Arguments.of(String.class),
                Arguments.of(Date.class),
                Arguments.of(BigInteger.class),
                Arguments.of(BigDecimal.class),
                Arguments.of(AtomicBoolean.class),
                Arguments.of(AtomicInteger.class),
                Arguments.of(AtomicLong.class),
                Arguments.of(Duration.class),
                Arguments.of(Instant.class),
                Arguments.of(LocalDate.class),
                Arguments.of(LocalDateTime.class),
                Arguments.of(LocalTime.class),
                Arguments.of(MonthDay.class),
                Arguments.of(OffsetDateTime.class),
                Arguments.of(OffsetTime.class),
                Arguments.of(Period.class),
                Arguments.of(Year.class),
                Arguments.of(YearMonth.class),
                Arguments.of(ZonedDateTime.class),
                Arguments.of(ZoneId.class),
                Arguments.of(ZoneOffset.class)
        );
    }

    @ParameterizedTest
    @MethodSource("nonReferenceableClasses")
    void testIsNonReferenceable_defaults(Class<?> c) {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertTrue(options.isNonReferenceableClass(c)); // Assuming Date is a logical primitive
    }

    @Test
    void testIsNonReferenceable_notInDefaults() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertFalse(options.isNonReferenceableClass(NoTypeTest.Person.class)); // Assuming Date is a logical primitive
    }

    // Test for getNonReferenceable method
    @Test
    void testGetNotReferenceableTypes() {
        Collection<Class<?>> nonReferenceableClasses = new WriteOptionsBuilder().build().getNonReferenceableClasses();
        assertNotNull(nonReferenceableClasses);
        assertTrue(nonReferenceableClasses instanceof LinkedHashSet);
    }

    // Test for addNonReferenceable method
    @Test
    void testAddNonReferenceableClass_isFound() {
        WriteOptions options = new WriteOptionsBuilder().addNonReferenceableClass(MapOfMapsTest.Person.class).build();
        assertTrue(options.isNonReferenceableClass(MapOfMapsTest.Person.class));
    }

    private static Stream<Arguments> aliasDefaults() {
        return Stream.of(
                Arguments.of("java.lang.Class", "class"),
                Arguments.of("java.lang.String", "string"),
                Arguments.of("java.lang.Date", "date"),
                Arguments.of("java.lang.Byte", "byte"),
                Arguments.of("java.lang.Short", "short"),
                Arguments.of("java.lang.Integer", "int"),
                Arguments.of("java.lang.Long", "long"),
                Arguments.of("java.lang.Float", "float"),
                Arguments.of("java.lang.Double", "double"),
                Arguments.of("java.lang.Character", "char"),
                Arguments.of("java.lang.Boolean", "boolean")
        );
    }

    @ParameterizedTest
    @MethodSource("aliasDefaults")
    void testAliasTypeNames_defaults(String fqName, String shortName) {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.getTypeNameAlias(fqName)).isEqualTo(shortName);
    }

    private static Stream<Arguments> aliasWithExtended() {
        return Stream.of(
                Arguments.of("java.util.ArrayList", "ArrayList"),
                Arguments.of("java.util.LinkedHashMap$LinkedValues", "LinkedValues"),
                Arguments.of("java.util.HashMap$Values", "HashMapValues"),
                Arguments.of("java.util.HashMap", "HashMap"),
                Arguments.of("java.util.TreeMap", "TreeMap"),
                Arguments.of("java.util.LinkedHashMap", "LinkedHashMap"),
                Arguments.of("java.util.Collections$SingletonMap", "SingletonMap"),
                Arguments.of("java.util.Collections$UnmodifiableMap", "UnmodifiableMap"),
                Arguments.of("java.util.HashMap$KeySet ", "HashMapKeySet"),
                Arguments.of("java.util.concurrent.ConcurrentHashMap$KeySetView", "ConcurrentHashMapKeySetView"),
                Arguments.of("java.util.concurrent.ConcurrentSkipListMap$KeySet", "ConcurrentSkipListMapKeySet")
        );
    }

    @ParameterizedTest
    @MethodSource("aliasWithExtended")
    void testAliasTypeNames_includingExtendedDefaults(String fqName, String shortName) {
        WriteOptions options = new WriteOptionsBuilder().withExtendedAliases().build();
        assertThat(options.getTypeNameAlias(fqName)).isEqualTo(shortName);
    }

    private static Stream<Arguments> aliasExceptions() {
        return Stream.of(
                Arguments.of(null, "ArrayList"),
                Arguments.of("foo", "bar")
        );
    }

    @ParameterizedTest
    @MethodSource("aliasExceptions")
    void testAliasTypeNames_exceptionCases(String fqName, String shortName) {
        WriteOptions options = new WriteOptionsBuilder().withExtendedAliases().build();
        assertThatExceptionOfType(JsonIoException.class).isThrownBy(() ->
                new WriteOptionsBuilder().aliasTypeName(fqName, shortName));
    }

    @Test
    void testAliasTypeNames_addedByMap() {
        Map<String, String> map = MetaUtils.mapOf("int", "properInt", "java.lang.Integer", "Int");

        WriteOptions options = new WriteOptionsBuilder()
                .aliasTypeNames(map)
                .build();

        assertEquals("properInt", options.getTypeNameAlias("int"));
        assertEquals("Int", options.getTypeNameAlias("java.lang.Integer"));
    }

    @Test
    void testAliasTypeNames_addedByNameValue() {
        Map<String, String> map = MetaUtils.mapOf("int", "properInt", "java.lang.Integer", "Int");

        WriteOptions options = new WriteOptionsBuilder()
                .aliasTypeName("int", "properInt")
                .aliasTypeName("java.lang.Integer", "Int")
                .build();

        assertEquals("properInt", options.getTypeNameAlias("int"));
        assertEquals("Int", options.getTypeNameAlias("java.lang.Integer"));
    }

    @Test
    void testShowTypeInfoMinimal_defaultValue() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertTrue(options.isMinimalShowingType());
        assertFalse(options.isNeverShowingType());
        assertFalse(options.isAlwaysShowingType());
    }


    @Test
    void testShowTypeInfoMinimal() {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoMinimal().build();
        assertTrue(options.isMinimalShowingType());
        assertFalse(options.isNeverShowingType());
        assertFalse(options.isAlwaysShowingType());
    }

    @Test
    void testShowTypeInfoAlways() {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoAlways().build();
        assertFalse(options.isMinimalShowingType());
        assertFalse(options.isNeverShowingType());
        assertTrue(options.isAlwaysShowingType());
    }

    @Test
    void testShowTypeInfoNever() {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
        assertFalse(options.isMinimalShowingType());
        assertTrue(options.isNeverShowingType());
        assertFalse(options.isAlwaysShowingType());
    }

    private static Stream<Arguments> customWrittenClasses_true() {
        return Stream.of(
                Arguments.of(Throwable.class),
                Arguments.of(Class.class),
                Arguments.of(String.class),
                Arguments.of(Date.class),
                Arguments.of(BigInteger.class),
                Arguments.of(BigDecimal.class),
                Arguments.of(Duration.class),
                Arguments.of(Instant.class),
                Arguments.of(LocalDate.class),
                Arguments.of(LocalDateTime.class),
                Arguments.of(LocalTime.class),
                Arguments.of(MonthDay.class),
                Arguments.of(OffsetDateTime.class),
                Arguments.of(OffsetTime.class),
                Arguments.of(Period.class),
                Arguments.of(Year.class),
                Arguments.of(YearMonth.class),
                Arguments.of(ZonedDateTime.class),
                Arguments.of(ZoneId.class),
                Arguments.of(ZoneOffset.class)
        );
    }

    @ParameterizedTest
    @MethodSource("customWrittenClasses_true")
    void testCustomWrittenClasses_defaults(Class<?> c) {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.getCustomWriter(c)).isNotNull();
    }

    private static Stream<Arguments> customWrittenClasses_exceptions() {
        return Stream.of(
                Arguments.of(JsonIoException.class),
                Arguments.of(IllegalAccessException.class),
                Arguments.of(IllegalArgumentException.class),
                Arguments.of(Throwable.class),
                Arguments.of(Exception.class)
        );
    }

    @ParameterizedTest
    @MethodSource("customWrittenClasses_exceptions")
    void testCustomWrittenClasses_exceptionAlwaysHasCustomWriter(Class<?> c) {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.getCustomWriter(c)).isNotNull();
    }

    @Test
    void testCustomWrittenClasses_asAsMap() {
        Map<Class<?>, JsonWriter.JsonClassWriter> map = MetaUtils.mapOf(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonWriter());

        WriteOptions options = new WriteOptionsBuilder()
                .addCustomWrittenClasses(map)
                .build();

        assertThat(options.getCustomWriter(CustomWriterTest.Person.class)).isNotNull();
    }

    @Test
    void testCustomWrittenClasses_addedIndividually() {
        WriteOptions options = new WriteOptionsBuilder()
                .addCustomWrittenClass(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonWriter())
                .build();

        assertThat(options.getCustomWriter(CustomWriterTest.Person.class)).isNotNull();
    }

    @Test
    void testNotCustomWrittenClass_default() {
        WriteOptions options = new WriteOptionsBuilder()
                .build();

        assertThat(options.getNotCustomWrittenClasses()).isEmpty();
    }

    @Test
    void testIsNotCustomWrittenClass_default_returnsFalseWhenNotContained() {
        WriteOptions options = new WriteOptionsBuilder()
                .build();

        assertThat(options.isNotCustomWrittenClass(String.class)).isFalse();
    }

    @Test
    void testAddNotCustomWrittenClass() {
        WriteOptions options = new WriteOptionsBuilder()
                .addNotCustomWrittenClass(String.class)
                .build();

        assertThat(options.isNotCustomWrittenClass(String.class)).isTrue();
    }

    @Test
    void testSetNotCustomWrittenClasses() {
        WriteOptions options = new WriteOptionsBuilder()
                .setNotCustomWrittenClasses(MetaUtils.setOf(String.class, Long.class, Integer.class))
                .build();

        assertThat(options.isCustomWrittenClass(String.class)).isTrue();
        assertThat(options.isCustomWrittenClass(Long.class)).isTrue();
        assertThat(options.isCustomWrittenClass(Integer.class)).isTrue();
    }

    @Test
    void testIncludedFields()
    {
        WriteOptions options = new WriteOptionsBuilder()
                .addIncludedField(String.class, "dog")
                .addIncludedFields(String.class, MetaUtils.setOf("cat", "bird"))
                .build();

        assertThat(options.getIncludedFields(String.class)).hasSize(3);
        assertThat(options.getDeepAccessors(String.class)).isEmpty();
        assertThat(options.getIncludedFields(String.class)).containsAll(MetaUtils.setOf("dog", "cat", "bird"));
    }

    @Test
    void testExcludedFields_containsBaseExclusions() {
        Collection<String> fieldNames = new WriteOptionsBuilder().build().getExcludedFieldsPerClass(Throwable.class);

        assertThat(fieldNames)
                .hasSize(4);
    }

    @Test
    void testExcludedFields_subclassPicksUpSuperclassExclusions() {
        Collection<String> fieldNames = new WriteOptionsBuilder().build().getExcludedFieldsPerClass(Exception.class);

        assertThat(fieldNames)
                .hasSize(4);
    }

    @Test
    void testExcludeFields_containsUniqueFieldNames()
    {
        WriteOptions options = new WriteOptionsBuilder()
                .addExcludedField(String.class, "dog")
                .addExcludedFields(String.class, MetaUtils.setOf("dog", "cat"))
                .build();

        assertThat(options.getExcludedFieldsPerClass(String.class))
                .hasSize(2);
    }

    @Test
    void testExcludeFields_addsNewExclusionsToBase() {
        WriteOptions options = new WriteOptionsBuilder()
                .addExcludedField(String.class, "dog")
                .addExcludedFields(String.class, MetaUtils.setOf("dog", "cat"))
                .build();

        Collection<String> set = options.getExcludedFieldsPerClass(String.class);
        assertThat(set)
                .hasSize(2)
                .contains("dog", "cat");

    }

    @Test
    void testIsLongFormat_defaultIsTrue()
    {
        WriteOptions options = new WriteOptionsBuilder()
                .build();

        assertThat(options.isLongDateFormat()).isTrue();
    }

    @Test
    void testIsLongFormat_whenIsoDateFormat_returnsFalse() {
        WriteOptions options = new WriteOptionsBuilder()
                .isoDateFormat()
                .build();

        assertThat(options.isLongDateFormat()).isFalse();
    }

    @Test
    void testIsLongFormat_whenIsoDateTimeFormat_returnsFalse() {
        WriteOptions options = new WriteOptionsBuilder()
                .isoDateTimeFormat()
                .build();

        assertThat(options.isLongDateFormat()).isFalse();
    }

    @Test
    void testWritingLongFormat() {
        WriteOptions options = new WriteOptionsBuilder()
                .longDateFormat()
                .build();

        Date now = new Date();
        Date[] dates = new Date[]{now};
        String json = TestUtil.toJson(dates, options);
        Date[] dates2 = TestUtil.toObjects(json, Date[].class);
        assertThat(dates2).isEqualTo(dates);
        assertThat(dates2[0]).isEqualTo(now);
    }

    @Test
    void testWritingIsoDateTimeFormat() {
        WriteOptions options = new WriteOptionsBuilder()
                .isoDateTimeFormat()
                .build();

        Date now = new Date();
        Date[] dates = new Date[]{now};
        String json = TestUtil.toJson(dates, options);
        Date[] dates2 = TestUtil.toObjects(json, Date[].class);
        assertThat(dates2).isEqualTo(dates);
        assertThat(dates2[0]).isEqualTo(now);
    }

//    @Test
//    void testGetDeepAccessorMap_whereSomeFieldsHaveAccessorsAndOthersDoNot() {
//
//        Collection<Accessor> accessors = new WriteOptionsBuilder().build().getDeepAccessors(PrivateFinalObject.class);
//
//        assertThat(accessors).hasSize(4);
//        assertThat(accessors.get("x").isPublic()).isFalse();
//        assertThat(accessors.get("x").getDisplayName()).isEqualTo("x");
//        assertThat(accessors.get("y").isPublic()).isFalse();
//        assertThat(accessors.get("y").getDisplayName()).isEqualTo("y");
//        assertThat(accessors.get("key").isPublic()).isTrue();
//        assertThat(accessors.get("key").getDisplayName()).isEqualTo("getKey");
//        assertThat(accessors.get("flatuated").isPublic()).isTrue();
//        assertThat(accessors.get("flatuated").getDisplayName()).isEqualTo("isFlatuated");
//    }
//
//    @Test
//    void testGetDeepAccessorMap_onEnumDefinitionClass_findsName() {
//        Map<String, Accessor> accessors = new WriteOptionsBuilder().getDeepAccessorMap(ColorEnum.class);
//        assertThat(accessors).hasSize(1);
//        assertThat(accessors.get("name").isPublic()).isTrue();
//        assertThat(accessors.get("name").getDisplayName()).isEqualTo("name");
//    }
//
//    @Test
//    void testGetDeepAccessorMap_onEnumClass() {
//        Map<String, Accessor> accessors = new WriteOptionsBuilder().getDeepAccessorMap(Enum.class);
//
//        assertThat(accessors).hasSize(1);
//        assertThat(accessors.get("name").isPublic()).isTrue();
//        assertThat(accessors.get("name").getDisplayName()).isEqualTo("name");
//    }


}
