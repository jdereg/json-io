package com.cedarsoftware.io.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InjectorPrivateConstructorsTest {

    static class FieldSetTarget {
        private String value;
    }

    @Test
    void fieldSetConstructor_injectsValue() throws Exception {
        Field field = FieldSetTarget.class.getDeclaredField("value");
        field.setAccessible(true);
        Constructor<Injector> ctor = Injector.class.getDeclaredConstructor(Field.class, String.class, String.class, boolean.class);
        ctor.setAccessible(true);
        Injector injector = ctor.newInstance(field, "value", "value", true);

        FieldSetTarget target = new FieldSetTarget();
        injector.inject(target, "bar");

        assertThat(target.value).isEqualTo("bar");
    }
}
