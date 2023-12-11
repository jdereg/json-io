package com.cedarsoftware.util.reflect.factories;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.io.WriteOptionsBuilder;
import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.GetMethodTestObject;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanObjects;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanValues;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;

class MappedMethodAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new MethodAccessorFactory();
    }

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(GetMethodTestObject.class, "getTest5"),
                Arguments.of(ObjectWithBooleanValues.class, "isTest5"),
                Arguments.of(ObjectWithBooleanObjects.class, "isTest5"),
                Arguments.of(ColorEnum.class, "name"),
                Arguments.of(CarEnumWithCustomFields.class, "name")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenMethodIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(cls);
        assertThat(methodMap).containsKey(fieldName);
    }

    private static Stream<Arguments> checkFailedToCreateSituations() {
        return Stream.of(
                Arguments.of(GetMethodTestObject.class, "test1"),
                Arguments.of(GetMethodTestObject.class, "test2"),
                Arguments.of(GetMethodTestObject.class, "test3"),
                Arguments.of(GetMethodTestObject.class, "test4"),
                Arguments.of(GetMethodTestObject.class, "test6"),
                Arguments.of(ObjectWithBooleanValues.class, "test1"),
                Arguments.of(ObjectWithBooleanValues.class, "test2"),
                Arguments.of(ObjectWithBooleanValues.class, "test3"),
                Arguments.of(ObjectWithBooleanValues.class, "test4"),
                Arguments.of(ObjectWithBooleanValues.class, "test6"),
                Arguments.of(ObjectWithBooleanObjects.class, "test1"),
                Arguments.of(ObjectWithBooleanObjects.class, "test2"),
                Arguments.of(ObjectWithBooleanObjects.class, "test3"),
                Arguments.of(ObjectWithBooleanObjects.class, "test4"),
                Arguments.of(ObjectWithBooleanObjects.class, "test6")
        );
    }

    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(cls);
        assertThat(methodMap).doesNotContainKey(fieldName);
    }


    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenBooleanIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(cls);

        assertThat(methodMap).containsKey(fieldName);
    }


    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(cls);
        Accessor accessor = this.factory.createAccessor(cls.getDeclaredField(fieldName), nonStandardMethodNames, methodMap, fieldName);
        assertThat(accessor).isNull();
    }

    @Test
    void create_whenMethodDoesNotExist_cannotCreateAccessor() throws Throwable {
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(ObjectWithBooleanObjects.class);
        Accessor accessor = this.factory.createAccessor(PrivateFinalObject.class.getDeclaredField("x"), nonStandardMethodNames, methodMap, "x");
        assertThat(accessor).isNull();
    }

    @Test
    void create_whenIsEnumClass_canCreateAccessor_ifNameMappingIsPresent() throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(Enum.class);
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        Accessor accessor = factory.createAccessor(Enum.class.getDeclaredField("name"), nonStandardMethodNames, methodMap, "name");
        assertThat(accessor).isNull();
    }

    @Test
    void create_whenIsEnumClass_andHasNameMapping_canCreateAccessor() throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(Enum.class);
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        nonStandardMethodNames.computeIfAbsent(Enum.class, k -> new LinkedHashMap<>()).put("name", "name");
        Accessor accessor = factory.createAccessor(Enum.class.getDeclaredField("name"), nonStandardMethodNames, methodMap, "name");
        assertThat(accessor).isNotNull();
    }

    @Test
    void buildDeepAccessorMethods_findsPublicGetMethod() throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(GetMethodTestObject.class);

        assertThat(methodMap)
                .hasSize(1)
                .containsKey("getTest5");
    }

    @Test
    void buildDeepAccessorMethods_findsPublicIsMethod() throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(ObjectWithBooleanValues.class);

        assertThat(methodMap)
                .hasSize(1)
                .containsKey("isTest5");
    }

    @Test
    void buildDeepAccessorMethods_findEnumWithCustomFields() throws Throwable {
        Map<String, Method> methodMap = new WriteOptionsBuilder().build().buildDeepMethods(CarEnumWithCustomFields.class);

        assertThat(methodMap)
                .hasSize(2)
                .containsKey("name")
                .containsKey("ordinal");
    }
}
