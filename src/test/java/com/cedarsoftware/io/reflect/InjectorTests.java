package com.cedarsoftware.io.reflect;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.util.SystemUtilities;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InjectorTests {

    private static class Sample {
        private int value;
    }

    @Test
    void injectWithVarHandle_setsFieldValue() throws Exception {
        Field field = Sample.class.getDeclaredField("value");
        Method create = Injector.class.getDeclaredMethod("createWithVarHandle", Field.class, String.class);
        create.setAccessible(true);
        Injector injector = (Injector) create.invoke(null, field, "num");
        assertThat(injector).isNotNull();

        Method injectMethod = Injector.class.getDeclaredMethod("injectWithVarHandle", Object.class, Object.class);
        injectMethod.setAccessible(true);

        Sample sample = new Sample();
        injectMethod.invoke(injector, sample, 42);
        assertThat(sample.value).isEqualTo(42);
        assertThat(injector.getDisplayName()).isEqualTo("value");
        assertThat(injector.getUniqueFieldName()).isEqualTo("num");
    }

    @Test
    void injectWithVarHandle_whenVarHandleMissing_throwsException() throws Exception {
        Field field = Sample.class.getDeclaredField("value");
        Method create = Injector.class.getDeclaredMethod("createWithVarHandle", Field.class, String.class);
        create.setAccessible(true);
        Injector injector = (Injector) create.invoke(null, field, "num");
        assertThat(injector).isNotNull();

        Field vhField = Injector.class.getDeclaredField("varHandle");
        vhField.setAccessible(true);
        vhField.set(injector, null);

        Method injectMethod = Injector.class.getDeclaredMethod("injectWithVarHandle", Object.class, Object.class);
        injectMethod.setAccessible(true);

        Sample sample = new Sample();
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, () ->
                injectMethod.invoke(injector, sample, 1));
        Throwable cause = ex.getCause();
        assertThat(cause).isInstanceOf(JsonIoException.class);
        assertThat(cause.getMessage()).contains("VarHandle not available");
    }
}
