package com.cedarsoftware.io.reflect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.cedarsoftware.io.JsonIoException;

import org.junit.jupiter.api.Test;

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

    @Test
    void getJavaVersion_legacyVersion_returns8() {
        assertThat(Injector.getJavaVersion("1.8.0_321")).isEqualTo(8);
    }

    @Test
    void getJavaVersion_modernVersion_returns17() {
        assertThat(Injector.getJavaVersion("17.0.2")).isEqualTo(17);
    }

    @Test
    void getJavaVersion_singleDigitVersion_returns11() {
        assertThat(Injector.getJavaVersion("11")).isEqualTo(11);
    }

    @Test
    void getJavaVersion_malformedVersion_throwsException() {
        assertThrows(NumberFormatException.class, () -> Injector.getJavaVersion("foo.bar"));
    }

    @Test
    void getJavaVersion_shortLegacyVersion_returns8() {
        assertThat(Injector.getJavaVersion("1.8")).isEqualTo(8);
    }

    @Test
    void getJavaVersion_preReleaseVersion_returns17() {
        assertThat(Injector.getJavaVersion("17-ea")).isEqualTo(17);
    }
}
