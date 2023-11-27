package com.cedarsoftware.util.reflect.factories;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.models.GetMethodTestObject;

public class MappedMethodAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new MappedMethodAccessorFactory();
    }

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(GetMethodTestObject.class, "test5")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenMethodIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildDeepAccessorMethods(cls);

        Accessor accessor = factory.createAccessor(cls.getDeclaredField(fieldName), methodMap);

        assertThat(accessor).isNotNull().describedAs("field was not static and public");
    }

    private static Stream<Arguments> checkFailedToCreateSituations() {
        return Stream.of(
                Arguments.of(GetMethodTestObject.class, "test1"),
                Arguments.of(GetMethodTestObject.class, "test2"),
                Arguments.of(GetMethodTestObject.class, "test3"),
                Arguments.of(GetMethodTestObject.class, "test4"),
                Arguments.of(GetMethodTestObject.class, "test6")
        );
    }

    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildDeepAccessorMethods(cls);
        Accessor accessor = this.factory.createAccessor(cls.getDeclaredField(fieldName), methodMap);
        assertThat(accessor).isNull();
    }
}
