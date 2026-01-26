package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import com.cedarsoftware.io.reflect.filters.models.ObjectWithBooleanObjects;
import com.cedarsoftware.io.reflect.filters.models.ObjectWithBooleanValues;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class IsMethodAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new IsMethodAccessorFactory();
    }

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(ObjectWithBooleanValues.class, "test5"),
                Arguments.of(ObjectWithBooleanObjects.class, "test5")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenMethodIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Field field = cls.getDeclaredField(fieldName);
        Map<Class<?>, Map<String, String>> mapping = new HashMap();

        Accessor accessor = this.factory.buildAccessor(field, mapping, fieldName);
        assertThat(accessor).isNotNull();
    }

    private static Stream<Arguments> checkFailedToCreateSituations() {
        return Stream.of(
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
    void createAccessor_whenMethodDoesNotExist_throwsException(Class<?> cls, String fieldName) throws Exception {
        Field field = cls.getDeclaredField(fieldName);
        Map<Class<?>, Map<String, String>> mapping = new HashMap();

        Accessor accessor = this.factory.buildAccessor(field, mapping, fieldName);
        assertThat(accessor).isNull();
    }


    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenBooleanIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Exception {
        Field field = cls.getDeclaredField(fieldName);
        Map<Class<?>, Map<String, String>> mapping = new HashMap();

        Accessor accessor = this.factory.buildAccessor(field, mapping, fieldName);
        assertThat(accessor).isNotNull();
    }


    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Exception {
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        Accessor accessor = this.factory.buildAccessor(cls.getDeclaredField(fieldName), nonStandardMethodNames, fieldName);
        assertThat(accessor).isNull();
    }

}
