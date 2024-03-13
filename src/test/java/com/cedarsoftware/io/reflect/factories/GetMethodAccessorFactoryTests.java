package com.cedarsoftware.io.reflect.factories;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.cedarsoftware.io.reflect.Accessor;
import com.cedarsoftware.io.reflect.AccessorFactory;
import com.cedarsoftware.io.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.io.reflect.filters.models.ColorEnum;
import com.cedarsoftware.io.reflect.filters.models.GetMethodTestObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class GetMethodAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new GetMethodAccessorFactory();
    }


    @Test
    void create_whenMethodIsPublicAndNotStatic_canCreateAccessor() throws Throwable {
        Field field = GetMethodTestObject.class.getDeclaredField("test5");

        Accessor accessor = this.factory.buildAccessor(field, new HashMap<>(), "test5");
        assertThat(accessor).isNotNull();
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
    void createAccessor_whenMethodDoesNotExist_throwsException(Class<?> cls, String fieldName) throws Exception {
        Field field = cls.getDeclaredField(fieldName);
        Map<Class<?>, Map<String, String>> mapping = new HashMap();

        Accessor accessor = this.factory.buildAccessor(field, mapping, fieldName);
        assertThat(accessor).isNull();
    }

    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Map<Class<?>, Map<String, String>> nonStandardMethodNames = Collections.emptyMap();
        Accessor accessor = this.factory.buildAccessor(cls.getDeclaredField(fieldName), nonStandardMethodNames, fieldName);
        assertThat(accessor).isNull();
    }

    private static Stream<Arguments> checkSuccessfulCreationSituations() {
        return Stream.of(
                Arguments.of(ColorEnum.class, "name"),
                Arguments.of(CarEnumWithCustomFields.class, "name")
        );
    }

    @ParameterizedTest
    @MethodSource("checkSuccessfulCreationSituations")
    void create_whenEnumTypeWithMapping_canCreateAccessor(Class<?> cls, String fieldName) throws Throwable {
        Field field = Enum.class.getDeclaredField(fieldName);
        Map<Class<?>, Map<String, String>> mapping = new HashMap();
        Map<String, String> mappings = new HashMap();
        mappings.put("name", "name");
        mapping.put(Enum.class, mappings);

        Accessor accessor = this.factory.buildAccessor(field, mapping, fieldName);
        assertThat(accessor).isNotNull();
    }
}
