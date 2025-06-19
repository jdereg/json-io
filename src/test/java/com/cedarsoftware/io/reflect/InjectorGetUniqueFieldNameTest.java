package com.cedarsoftware.io.reflect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class InjectorGetUniqueFieldNameTest {

    private static class Sample {
        private int number;
        private int value;

        void setNumber(int number) {
            this.number = number;
        }
    }

    @Test
    void create_withField_preservesUniqueName() throws Exception {
        Field field = Sample.class.getDeclaredField("value");
        Injector injector = Injector.create(field, "val");

        assertThat(injector).isNotNull();
        assertThat(injector.getUniqueFieldName()).isEqualTo("val");
    }

    @Test
    void create_withMethod_preservesUniqueName() throws Exception {
        Field field = Sample.class.getDeclaredField("number");
        Injector injector = Injector.create(field, "setNumber", "num");

        assertThat(injector).isNotNull();
        assertThat(injector.getUniqueFieldName()).isEqualTo("num");
    }
}
