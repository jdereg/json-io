package com.cedarsoftware.util.reflect.factories;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanObjects;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanValues;
import com.cedarsoftware.util.reflect.filters.models.PrivateFinalObject;

class BooleanAccessorFactoryTests extends AbstractAccessFactoryTest {

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(ObjectWithBooleanValues.class, "test5"),
                Arguments.of(ObjectWithBooleanObjects.class, "test5")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenBooleanIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildDeepAccessorMethods(cls);

        Accessor accessor = factory.createAccessor(cls.getDeclaredField(fieldName), methodMap);

        assertThat(accessor)
                .describedAs("field was not static and public")
                .isNotNull();
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
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildDeepAccessorMethods(cls);
        Accessor accessor = this.factory.createAccessor(cls.getDeclaredField(fieldName), methodMap);
        assertThat(accessor).isNull();
    }

    @Test
    void create_whenMethodDoesNotExist_cannotCreateAccessor() throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildDeepAccessorMethods(ObjectWithBooleanObjects.class);
        Accessor accessor = this.factory.createAccessor(PrivateFinalObject.class.getDeclaredField("x"), methodMap);
        assertThat(accessor).isNull();
    }

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new BooleanAccessorFactory();
    }
}
