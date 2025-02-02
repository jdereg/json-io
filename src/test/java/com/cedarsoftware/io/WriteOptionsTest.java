package com.cedarsoftware.io;

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

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.io.reflect.filters.models.ColorEnum;
import com.cedarsoftware.io.reflect.filters.models.GetMethodTestObject;
import com.cedarsoftware.io.reflect.filters.models.ObjectWithBooleanValues;
import com.cedarsoftware.io.reflect.filters.models.PrivateFinalObject;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.cedarsoftware.util.CollectionUtilities.setOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
                Arguments.of(ClassUtilities.getClassLoader(WriteOptionsTest.class))
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

    @Test
    void testForceMapOutputAsTwoArrays_defaultValue() {
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThat(options.isForceMapOutputAsTwoArrays()).isFalse();
    }


    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testForceMapOutputAsTwoArrays(boolean value) {
        WriteOptions options = new WriteOptionsBuilder().forceMapOutputAsTwoArrays(value).lruSize(10).build();
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
                Arguments.of("java.lang.Class", "Class"),
                Arguments.of("java.lang.String", "String"),
                Arguments.of("java.util.Date", "Date"),
                Arguments.of("java.lang.Byte", "Byte"),
                Arguments.of("java.lang.Short", "Short"),
                Arguments.of("java.lang.Integer", "Integer"),
                Arguments.of("java.lang.Long", "Long"),
                Arguments.of("java.lang.Float", "Float"),
                Arguments.of("java.lang.Double", "Double"),
                Arguments.of("java.lang.Character", "Character"),
                Arguments.of("java.lang.Boolean", "Boolean")
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
                Arguments.of("java.lang.Class", "Class"),
                Arguments.of("java.lang.String", "String"),
                Arguments.of("java.util.Date", "Date"),
                Arguments.of("java.lang.Byte", "Byte"),
                Arguments.of("java.lang.Short", "Short"),
                Arguments.of("java.lang.Integer", "Integer"),
                Arguments.of("java.lang.Long", "Long"),
                Arguments.of("java.lang.Float", "Float"),
                Arguments.of("java.lang.Double", "Double"),
                Arguments.of("java.lang.Character", "Character"),
                Arguments.of("java.lang.Boolean", "Boolean"),
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
        WriteOptions options = new WriteOptionsBuilder().build();
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
        WriteOptions options = new WriteOptionsBuilder().build();
        assertThrows(IllegalArgumentException.class, () ->
        { new WriteOptionsBuilder().aliasTypeName(fqName, shortName); });
    }

    @Test
    void testAliasTypeNames_addedByMap() {
        Map<String, String> map = mapOf("int", "properInt", "long", "properLong");

        assertThrows(IllegalArgumentException.class, () -> new WriteOptionsBuilder()
                .aliasTypeNames(map)
                .build());
    }

    // TODO: not sure we want to throw an exception on this?
    @Test
    void teatAliasTypeNames_whenAliasAlreadyExists_throwsException() {
        Map<String, String> map = mapOf("java.lang.Integer", "properInt");

        WriteOptionsBuilder builder = new WriteOptionsBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.aliasTypeNames(map));
    }

    @Test
    void testAliasTypeNames_addedByNameValue() {
        assertThrows(IllegalArgumentException.class, ()-> new WriteOptionsBuilder()
                .aliasTypeName("int", "properInt")
                .aliasTypeName("long", "properLong")
                .removeAliasTypeNamesMatching("int")
                .build());
    }

    @Test
    void testAliasTypeNames_addedByNameValue_whenAlreadyExists_throwsException() {
        WriteOptionsBuilder builder = new WriteOptionsBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.aliasTypeName("java.lang.Integer", "properInt"));
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
        Map<Class<?>, JsonWriter.JsonClassWriter> map = mapOf(CustomWriterTest.Person.class, new CustomWriterTest.CustomPersonWriter());

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
                .setNotCustomWrittenClasses(setOf(String.class, Long.class, Integer.class))
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
        WriteOptions options = new WriteOptionsBuilder().isoDateFormat().build();
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
        assert DeepEquals.deepEquals(dates, dates2);
        assertEquals(dates[0], now);
        assertEquals(dates2[0].toString(), dates[0].toString());    // differ in millis
        assertEquals(dates2[0].toString(), now.toString());         // differ in millis
    }

    @Test
    void testWritingIsoDateTimeFormat() {
        WriteOptions options = new WriteOptionsBuilder().isoDateFormat().build();

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
    void getAccessorsForClass_withMixedAccessors() {

        List<Accessor> accessors = new WriteOptionsBuilder().build().getAccessorsForClass(PrivateFinalObject.class);

        assertThat(accessors).hasSize(4);

        assertThat(accessors.get(0).isPublic()).isFalse();
        assertThat(accessors.get(0).getFieldOrMethodName()).isEqualTo("x");
        assertThat(accessors.get(0).getActualFieldName()).isEqualTo("x");
        assertThat(accessors.get(0).getUniqueFieldName()).isEqualTo("x");
        assertThat(accessors.get(0).getFieldType()).isEqualTo(int.class);
        assertThat(accessors.get(0).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(1).isPublic()).isFalse();
        assertThat(accessors.get(1).getFieldOrMethodName()).isEqualTo("y");
        assertThat(accessors.get(1).getActualFieldName()).isEqualTo("y");
        assertThat(accessors.get(1).getUniqueFieldName()).isEqualTo("y");
        assertThat(accessors.get(1).getFieldType()).isEqualTo(int.class);
        assertThat(accessors.get(1).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(2).isPublic()).isTrue();
        assertThat(accessors.get(2).getFieldOrMethodName()).isEqualTo("getKey");
        assertThat(accessors.get(2).getActualFieldName()).isEqualTo("key");
        assertThat(accessors.get(2).getUniqueFieldName()).isEqualTo("key");
        assertThat(accessors.get(2).getFieldType()).isEqualTo(String.class);
        assertThat(accessors.get(2).getDeclaringClass()).isEqualTo(PrivateFinalObject.class);

        assertThat(accessors.get(3).isPublic()).isTrue();
        assertThat(accessors.get(3).getFieldOrMethodName()).isEqualTo("isFlatuated");
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
        assertThat(accessors.get(0).getFieldOrMethodName()).isEqualTo("name");
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

        assert accessors.size() == 1;
        assert accessors.get(0).isPublic();
        assert accessors.get(0).getFieldOrMethodName().equals("name");
        assert accessors.get(0).getActualFieldName().equals("name");
        assert accessors.get(0).getUniqueFieldName().equals("name");
        assert accessors.get(0).getFieldType().equals(String.class);
        assert accessors.get(0).getDeclaringClass().equals(Enum.class);
    }

    @Test
    void getAccessorsForClass_whenMethodDoesNotExists_doesNotThrowException() throws Throwable {
        List<Accessor> list = new WriteOptionsBuilder()
                .addNonStandardGetter(PrivateFinalObject.class, "x", "getTotal")
                .build()
                .getAccessorsForClass(PrivateFinalObject.class);

        assertThat(list)
                .hasSize(4);

        assertThat(list.get(0).getFieldOrMethodName()).isEqualTo("getTotal");
        assertThat(list.get(1).getFieldOrMethodName()).isEqualTo("y");
        assertThat(list.get(2).getFieldOrMethodName()).isEqualTo("getKey");
        assertThat(list.get(3).getFieldOrMethodName()).isEqualTo("isFlatuated");
    }

    @Test
    void getAccessorsForClass_findsName_whenIncludedInNonStandardMappings() {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(WriteOptions.ShowType.class);

        assertEquals(list.size(), 1);

        //  non-standard mapping for Enum.name()
        assertThat(list.get(0).getFieldOrMethodName()).isEqualTo("name");
    }

    @Test
    void getAccessorsForClass_findsAllNonStaticFieldAccessors() {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(GetMethodTestObject.class);

        //  will not include
        assertEquals(list.size(), 5);

        assertEquals(list.get(0).getFieldOrMethodName(), "test1");
        assertEquals(list.get(1).getFieldOrMethodName(), "test2");
        assertEquals(list.get(2).getFieldOrMethodName(), "test3");
        assertEquals(list.get(3).getFieldOrMethodName(), "test4");

        //  public method for accessing test5 so we use that.
        assertEquals(list.get(4).getFieldOrMethodName(), "getTest5");
    }

    @Test
    void getAccessorsForClass_bindsBooleanNonStaticFieldAccessors() throws Throwable {
        List<Accessor> list = new WriteOptionsBuilder().build().getAccessorsForClass(ObjectWithBooleanValues.class);

        assertEquals(list.size(), 5);

        assertEquals(list.get(0).getFieldOrMethodName(), "test1");
        assertEquals(list.get(1).getFieldOrMethodName(), "test2");
        assertEquals(list.get(2).getFieldOrMethodName(), "test3");
        assertEquals(list.get(3).getFieldOrMethodName(), "test4");

        //  public method for accessing test5 so we use that.
        assertEquals(list.get(4).getFieldOrMethodName(), "isTest5");
    }

    @Test
    void getAccessorsForClass_findEnumWithCustomFields_whenNotPublicFieldsOnly() {
        List<Accessor> list = new WriteOptionsBuilder().writeEnumAsJsonObject(false).build().getAccessorsForClass(CarEnumWithCustomFields.class);

        assertEquals(list.size(), 5);
        
        assertEquals(list.get(0).getFieldOrMethodName(), "speed");
        assertEquals(list.get(1).getFieldOrMethodName(), "getRating");
        assertEquals(list.get(2).getFieldOrMethodName(), "tire");
        assertEquals(list.get(3).getFieldOrMethodName(), "isStick");

        //  public method for accessing test5 so we use that.
        assertEquals(list.get(4).getFieldOrMethodName(), "name");
    }
}
