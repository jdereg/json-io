package com.cedarsoftware.io.reflect.filters;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.cedarsoftware.io.reflect.filters.field.ModifierMaskFilter;
import com.cedarsoftware.io.reflect.filters.models.ObjectWithBooleanValues;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModifierMaskFilterTests {

    @Test
    void modifierMaskFilter_filtersFinalFields() {
        ModifierMaskFilter filter = new ModifierMaskFilter(Modifier.FINAL);
        Field[] fields = ObjectWithBooleanValues.class.getDeclaredFields();

        for (Field field : fields) {
            if ((field.getModifiers() & Modifier.FINAL) != 0) {
                assertThat(filter.filter(field))
                        .isTrue()
                        .withFailMessage("final fields should be filtered");
            } else {
                assertThat(filter.filter(field))
                        .isFalse()
                        .withFailMessage("non-final fields should not be filtered");
            }
        }
    }

    @Test
    void modifierMaskFilter_filtersPublicOrStaticFields() {
        int mask = Modifier.PUBLIC | Modifier.STATIC;
        ModifierMaskFilter filter = new ModifierMaskFilter(mask);
        Field[] fields = ObjectWithBooleanValues.class.getDeclaredFields();

        for (Field field : fields) {
            if ((field.getModifiers() & mask) != 0) {
                assertThat(filter.filter(field))
                        .isTrue()
                        .withFailMessage("public or static fields should be filtered");
            } else {
                assertThat(filter.filter(field))
                        .isFalse()
                        .withFailMessage("fields without mask should not be filtered");
            }
        }
    }

    @Test
    void modifierMaskFilter_noMatch_returnsFalse() throws Exception {
        ModifierMaskFilter filter = new ModifierMaskFilter(Modifier.TRANSIENT);
        Field field = ObjectWithBooleanValues.class.getDeclaredField("test1");

        assertThat(filter.filter(field))
                .isFalse()
                .withFailMessage("field without matching modifiers should not be filtered");
    }

    @Test
    void modifierMaskFilter_nullField_throwsNPE() {
        ModifierMaskFilter filter = new ModifierMaskFilter(Modifier.FINAL);
        assertThatThrownBy(() -> filter.filter(null))
                .isInstanceOf(NullPointerException.class);
    }
}
