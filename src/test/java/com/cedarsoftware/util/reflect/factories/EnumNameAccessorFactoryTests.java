package com.cedarsoftware.util.reflect.factories;

import com.cedarsoftware.util.reflect.Accessor;
import com.cedarsoftware.util.reflect.AccessorFactory;
import com.cedarsoftware.util.reflect.ReflectionUtils;
import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import com.cedarsoftware.util.reflect.filters.models.ObjectWithBooleanValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EnumNameAccessorFactoryTests extends AbstractAccessFactoryTest {

    @Test
    void create_whenIsEnumClass_canCreateAccessor() throws Exception {
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
    void create_whenBooleanIsStaticOrNotPublic_cannotCreateAccessor(Class<?> cls) throws Exception {
        final Map<String, Method> methodMap = ReflectionUtils.buildAccessorMap(cls);

        Arrays.stream(cls.getDeclaredFields()).forEach(field -> {
            Accessor accessor = factory.createAccessor(field, methodMap);
            assertThat(accessor).isNull();
        });
    }

    @Override
    protected AccessorFactory provideAccessorFactory() {
        return new EnumNameAccessorFactory();
    }
}
