package com.cedarsoftware.util.io;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.GetMethodTestObject;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanValues;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

//    private static Stream<Arguments> classLoadersTest() {
//        return Stream.of(
//                Arguments.of(ClassLoader.getSystemClassLoader()),
//                Arguments.of(WriteOptionsTest.class.getClassLoader())
//        );
//    }
//
//    @ParameterizedTest
//    @MethodSource("classLoadersTest")
//    void testClassLoader(ClassLoader classLoader) {
//        WriteOptions options = new WriteOptionsBuilder().classLoader(classLoader).build();
//        assertThat(options.getClassLoader()).isSameAs(classLoader);
//    }

    @Test
    void testClassLoader_default() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.getClassLoader()).isSameAs(WriteOptionsBuilder.class.getClassLoader());
    }

    @Test
    void testForceMapOutputAsTwoArrays_defaultValue() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.isForceMapOutputAsTwoArrays()).isFalse();
    }


    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testForceMapOutputAsTwoArrays(boolean value) {
        WriteOptions options = new WriteOptionsBuilder().forceMapOutputAsTwoArrays(value).build();
        assertThat(options.isForceMapOutputAsTwoArrays()).isEqualTo(value);
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


    @Test
    void testAddNonReferenceableClass_isFound() {
        WriteOptions options = new WriteOptionsBuilder().addNonReferenceableClass(MapOfMapsTest.Person.class).build();
        assertTrue(options.isNonReferenceableClass(MapOfMapsTest.Person.class));
    }

    private static Stream<Arguments> aliasDefaults() {
        return Stream.of(
                Arguments.of("java.lang.Class", "class"),
                Arguments.of("java.lang.String", "string"),
                Arguments.of("java.util.Date", "date"),
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
                Arguments.of("java.lang.Class", "class"),
                Arguments.of("java.lang.String", "string"),
                Arguments.of("java.util.Date", "date"),
                Arguments.of("java.lang.Byte", "byte"),
                Arguments.of("java.lang.Short", "short"),
                Arguments.of("java.lang.Integer", "int"),
                Arguments.of("java.lang.Long", "long"),
                Arguments.of("java.lang.Float", "float"),
                Arguments.of("java.lang.Double", "double"),
                Arguments.of("java.lang.Character", "char"),
                Arguments.of("java.lang.Boolean", "boolean"),
                Arguments.of("java.util.ArrayList", "ArrayList"),
                Arguments.of("java.util.LinkedHashMap$LinkedValues", "LinkedValues"),
                Arguments.of("java.util.HashMap$Values", "HashMapValues"),
                Arguments.of("java.util.HashMap", "HashMap"),
                Arguments.of("java.util.TreeMap", "TreeMap"),
                Arguments.of("java.util.LinkedHashMap", "LinkedHashMap"),
                Arguments.of("java.util.Collections$SingletonMap", "SingletonMap"),
                Arguments.of("java.util.Collections$UnmodifiableMap", "UnmodifiableMap"),
                Arguments.of("java.util.HashMap$KeySet", "HashMapKeySet"),
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
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                new WriteOptionsBuilder().aliasTypeName(fqName, shortName));
    }

    @Test
    void testAliasTypeNames_addedByMap() {
        Map<String, String> map = MetaUtils.mapOf("int", "properInt", "long", "properLong");

        WriteOptions options = new WriteOptionsBuilder()
                .aliasTypeNames(map)
                .build();

        assertEquals("properInt", options.getTypeNameAlias("int"));
        assertEquals("properLong", options.getTypeNameAlias("long"));
    }

    // TODO: not sure we want to throw an exception on this?
    @Test
    void teatAliasTypeNames_whenAliasAlreadyExists_throwsException() {
        Map<String, String> map = MetaUtils.mapOf("java.lang.Integer", "properInt");

        WriteOptionsBuilder builder = new WriteOptionsBuilder();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> builder.aliasTypeNames(map));
    }

    @Test
    void testAliasTypeNames_addedByNameValue() {
        WriteOptions options = new WriteOptionsBuilder()
                .aliasTypeName("int", "properInt")
                .aliasTypeName("long", "properLong")
                .build();

        assertEquals("properInt", options.getTypeNameAlias("int"));
        assertEquals("properLong", options.getTypeNameAlias("long"));
    }

    @Test
    void testAliasTypeNames_addedByNameValue_whenAlreadyExists_throwsException() {
        WriteOptionsBuilder builder = new WriteOptionsBuilder();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> builder.aliasTypeName("java.lang.Integer", "properInt"));
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
        assertThat(dates).isEqualTo(dates2);
        assertThat(dates[0]).isEqualTo(now);
        assertEquals(dates2[0].toString(), dates[0].toString());    // differ in millis
        assertEquals(dates2[0].toString(), now.toString());         // differ in millis
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
        // ISO DateTime format does not include milliseconds.....whhhhyyyyyy?
        assertEquals(dates2[0].toString(), dates[0].toString());    // differ in millis
        assertEquals(dates2[0].toString(), now.toString());         // differ in millis
        //this date time format does not support ms, so don't use the more accurate isEqualTo
//        assertThat(dates2).isEqualTo(dates);
//        assertThat(dates2[0]).isEqualTo(now);
    }

    @Test
    void testCustomFormat_thatIncludesMilliseconds() {
        WriteOptions options = new WriteOptionsBuilder()
                .addCustomWrittenClass(Date.class, new Writers.DateWriter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
                .build();

        Date now = new Date();
        String json = TestUtil.toJson(now, options);
        Date date2 = TestUtil.toObjects(json, Date.class);
        assertThat(date2).isEqualTo(now);
    }

    @Test
    void getAccessorsForClass_withMixedAccessors() {

        List<Accessor> accessors = new WriteOptionsBuilder().build().getAccessorsForClass(PrivateFinalObject.class);

        assertThat(accessors).hasSize(4);

        assertThat(accessors.get(0).isPublic()).isFalse();
        assertThat(accessors.get(0).getDisplayName()).isEqualTo("x");
        assertThat(accessors.get(0).getActualFieldName()).isEqualTo("x");
        assertThat(accessors.get(0).getUniqueFieldName()).isEqualTo("x");
        assertThat(accessors.get(0).getFieldType()).isEqualTo(int.class);
        assertThat(accessors.get(0).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(1).isPublic()).isFalse();
        assertThat(accessors.get(1).getDisplayName()).isEqualTo("y");
        assertThat(accessors.get(1).getActualFieldName()).isEqualTo("y");
        assertThat(accessors.get(1).getUniqueFieldName()).isEqualTo("y");
        assertThat(accessors.get(1).getFieldType()).isEqualTo(int.class);
        assertThat(accessors.get(1).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(2).isPublic()).isTrue();
        assertThat(accessors.get(2).getDisplayName()).isEqualTo("getKey");
        assertThat(accessors.get(2).getActualFieldName()).isEqualTo("key");
        assertThat(accessors.get(2).getUniqueFieldName()).isEqualTo("key");
        assertThat(accessors.get(2).getFieldType()).isEqualTo(String.class);
        assertThat(accessors.get(2).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(3).isPublic()).isTrue();
        assertThat(accessors.get(3).getDisplayName()).isEqualTo("isFlatuated");
        assertThat(accessors.get(3).getActualFieldName()).isEqualTo("flatuated");
        assertThat(accessors.get(3).getUniqueFieldName()).isEqualTo("flatuated");
        assertThat(accessors.get(3).getFieldType()).isEqualTo(boolean.class);
        assertThat(accessors.get(3).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);
    }

    @Test
    void getAccessorsForClass_onEnumDefinition_fillsOutAccessorCorrectly() {
        List<Accessor> accessors = new WriteOptionsBuilder().build().getAccessorsForClass(ColorEnum.class);

        assertThat(accessors).hasSize(1);
        assertThat(accessors.get(0).isPublic()).isTrue();
        assertThat(accessors.get(0).getDisplayName()).isEqualTo("name");
        assertThat(accessors.get(0).getActualFieldName()).isEqualTo("name");
        assertThat(accessors.get(0).getUniqueFieldName()).isEqualTo("name");
        assertThat(accessors.get(0).getFieldType()).isEqualTo(String.class);
        assertThat(accessors.get(0).getDeclaringClass()).isEqualTo(Enum.class);
    }


    @Test
    void testGetDeepAccessorMap_onEnumDefinitionClass_findsName() {
        List<Accessor> accessors = new WriteOptionsBuilder().build().getAccessorsForClass(ColorEnum.class);
        assertThat(accessors).hasSize(1);
//        assertThat(accessors.get("name").isPublic()).isTrue();
//        assertThat(accessors.get("name").getDisplayName()).isEqualTo("name");
    }

    @Test
    void getAccessorsForClass_onEnum_fillsOutAccessorCorrectly() {
        List<Accessor> accessors = new WriteOptionsBuilder().build().getAccessorsForClass(Enum.class);

        assertThat(accessors).hasSize(1);
        assertThat(accessors.get(0).isPublic()).isTrue();
        assertThat(accessors.get(0).getDisplayName()).isEqualTo("name");
        assertThat(accessors.get(0).getActualFieldName()).isEqualTo("name");
        assertThat(accessors.get(0).getUniqueFieldName()).isEqualTo("name");
        assertThat(accessors.get(0).getFieldType()).isEqualTo(String.class);
        assertThat(accessors.get(0).getDeclaringClass()).isEqualTo(Enum.class);
    }

    @Test
    void getAccessorsForClass_whenMethodDoesNotExists_doesNotThrowException() throws Throwable {
        List<Accessor> list = new WriteOptionsBuilder()
                .addNonStandardMapping(PrivateFinalObject.class, "x", "getTotal")
                .build()
                .getAccessorsForClass(PrivateFinalObject.class);

        assertThat(list)
                .hasSize(4);

        assertThat(list.get(0).getDisplayName()).isEqualTo("getTotal");
        assertThat(list.get(1).getDisplayName()).isEqualTo("y");
        assertThat(list.get(2).getDisplayName()).isEqualTo("getKey");
        assertThat(list.get(3).getDisplayName()).isEqualTo("isFlatuated");

    }

    @Test
    void getAccessorsForClass_findsName_whenIncludedInNonStandardMappings() {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(WriteOptions.ShowType.class);

        assertThat(list)
                .hasSize(1);

        //  non-standard mapping for Enum.name()
        assertThat(list.get(0).getDisplayName()).isEqualTo("name");
    }

    @Test
    void getAccessorsForClass_findsAllNonStaticFieldAccessors() {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(GetMethodTestObject.class);

        //  will not include
        assertThat(list)
                .hasSize(5);

        assertThat(list.get(0).getDisplayName()).isEqualTo("test1");
        assertThat(list.get(1).getDisplayName()).isEqualTo("test2");
        assertThat(list.get(2).getDisplayName()).isEqualTo("test3");
        assertThat(list.get(3).getDisplayName()).isEqualTo("test4");

        //  public method for accessing test5 so we use that.
        assertThat(list.get(4).getDisplayName()).isEqualTo("getTest5");
    }

    @Test
    void getAccessorsForClass_bindsBooleanNonStaticFieldAccessors() throws Throwable {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(ObjectWithBooleanValues.class);

        assertThat(list)
                .hasSize(5);

        assertThat(list.get(0).getDisplayName()).isEqualTo("test1");
        assertThat(list.get(1).getDisplayName()).isEqualTo("test2");
        assertThat(list.get(2).getDisplayName()).isEqualTo("test3");
        assertThat(list.get(3).getDisplayName()).isEqualTo("test4");

        //  public method for accessing test5 so we use that.
        assertThat(list.get(4).getDisplayName()).isEqualTo("isTest5");
    }

    @Test
    void getAccessorsForClass_findEnumWithCustomFields_whenNotPublicFieldsOnly() {
        List<Accessor> list = new WriteOptionsBuilder().writeEnumAsJsonObject(false).build().getAccessorsForClass(CarEnumWithCustomFields.class);

        assertThat(list)
                .hasSize(5);


        assertThat(list.get(0).getDisplayName()).isEqualTo("speed");
        assertThat(list.get(1).getDisplayName()).isEqualTo("getRating");
        assertThat(list.get(2).getDisplayName()).isEqualTo("tire");
        assertThat(list.get(3).getDisplayName()).isEqualTo("isStick");

        //  public method for accessing test5 so we use that.
        assertThat(list.get(4).getDisplayName()).isEqualTo("name");
    }
}
