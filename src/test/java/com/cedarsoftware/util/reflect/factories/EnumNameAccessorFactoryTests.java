package com.cedarsoftware.util.reflect.factories;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanValues;

class EnumNameAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Test
    void create_whenIsEnumClass_canCreateAccessor() throws Throwable {
        Map<String, Method> methodMap = ReflectionUtils.buildAccessorMap(Enum.class);
        Accessor accessor = factory.createAccessor(Enum.class.getDeclaredField("name"), methodMap);
        assertThat(accessor).isNotNull().describedAs("not an enum");
    }

    private static Stream<Arguments> checkFailedToCreateSituations() {
        return Stream.of(
                Arguments.of(ColorEnum.class),
                Arguments.of(CarEnumWithCustomFields.class),
                Arguments.of(ObjectWithBooleanValues.class)
        );
    }

    @ParameterizedTest
    @MethodSource("checkFailedToCreateSituations")
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls) throws Throwable {
        final Map<String, Method> methodMap = ReflectionUtils.buildAccessorMap(cls);

        List<Accessor> list = Arrays.stream(cls.getDeclaredFields())
                .map(field -> {
                    try {
                        return factory.createAccessor(field, methodMap);
                    } catch (Throwable t) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        assertThat(list).isEmpty();
    }

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new EnumNameAccessorFactory();
    }
}
