package com.cedarsoftware.io.reflect;

import com.cedarsoftware.io.JsonIoException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InjectorTests {

    private static class Sample {
        private int value;
    }

    @Test
    void create_setsFieldValue() throws Exception {
        Field field = Sample.class.getDeclaredField("value");
        Injector injector = Injector.create(field, "num");
        assertThat(injector).isNotNull();

        Sample sample = new Sample();
        injector.inject(sample, 42);
        assertThat(sample.value).isEqualTo(42);
        assertThat(injector.getDisplayName()).isEqualTo("value");
        assertThat(injector.getUniqueFieldName()).isEqualTo("num");
    }

    @Test
    void inject_nullObject_throwsException() throws Exception {
        Field field = Sample.class.getDeclaredField("value");
        Injector injector = Injector.create(field, "value");
        assertThat(injector).isNotNull();

        assertThrows(JsonIoException.class, () -> injector.inject(null, 42));
    }
}
