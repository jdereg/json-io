package com.cedarsoftware.io.reflect;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.cedarsoftware.util.ReflectionUtils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InjectorPrivateConstructorsTest {

    static class FieldSetTarget {
        private String value;
    }

    static class VarHandleTarget {
        private int number;
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

    @Test
    void varHandleConstructor_injectsValue() throws Exception {
        Field field = VarHandleTarget.class.getDeclaredField("number");
        field.setAccessible(true);

        Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles");
        Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup");

        Method lookupMethod = ReflectionUtils.getMethod(methodHandlesClass, "lookup");
        Object lookup = lookupMethod.invoke(null);

        Method privateLookupInMethod = ReflectionUtils.getMethod(methodHandlesClass,
                "privateLookupIn", Class.class, lookupClass);
        Object privateLookup = privateLookupInMethod.invoke(null, VarHandleTarget.class, lookup);

        Method findVarHandleMethod = ReflectionUtils.getMethod(lookupClass,
                "findVarHandle", Class.class, String.class, Class.class);
        Object varHandle = findVarHandleMethod.invoke(privateLookup,
                VarHandleTarget.class, "number", int.class);

        Constructor<Injector> ctor = Injector.class.getDeclaredConstructor(Field.class, Object.class, String.class, String.class);
        ctor.setAccessible(true);
        Injector injector = ctor.newInstance(field, varHandle, "number", "number");

        VarHandleTarget target = new VarHandleTarget();
        injector.inject(target, 42);

        assertThat(target.number).isEqualTo(42);
    }
}
