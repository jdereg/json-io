package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.models.GetMethodTestObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class GetMethodAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new GetMethodAccessorFactory();
    }

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(GetMethodTestObject.class, "test5")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenMethodIsPublicAndNotStatic_canCreateAccessor(Class<?> cls, String fieldName) throws Exception {
        Map<String, Method> methodMap = ReflectionUtils.buildAccessorMap(cls);

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
    void create_whenIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Exception {
        Map<String, Method> methodMap = ReflectionUtils.buildAccessorMap(cls);
        Accessor accessor = this.factory.createAccessor(cls.getDeclaredField(fieldName), methodMap);
        assertThat(accessor).isNull();
    }
}
