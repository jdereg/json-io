package com.cedarsoftware.io.reflect.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.io.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.io.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.io.reflect.filters.models.ColorEnum;


class EnumFieldFilterTests {

    @Test
    void enumFilter_withABasicEnum_willNotFilterAnyFields() {
        EnumFieldFilter enumFilter = new EnumFieldFilter();
        Field[] fields = ColorEnum.class.getDeclaredFields();

        for (Field field : fields) {
            // The filter should return false for regular enum constants
            // and true for internal/synthetic fields like $VALUES
            String fieldName = field.getName();
            if (fieldName.equals("$VALUES") || fieldName.equals("ENUM$VALUES")) {
                assertThat(enumFilter.filter(field)).isTrue();
            } else {
                assertThat(enumFilter.filter(field)).isFalse();
            }
        }
    }

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();

        for (Field field : fields) {
            // The filter should return true (filter out) for synthetic fields like $VALUES
            // and specific enum-internal fields
            String fieldName = field.getName();
            if (fieldName.equals("$VALUES") || fieldName.equalsIgnoreCase("ENUM$VALUES") || 
                fieldName.equals("internal") || fieldName.equals("hash") || fieldName.equals("ordinal")) {
                assertThat(enumFieldFilter.filter(field)).isTrue();
            } else {
                assertThat(enumFieldFilter.filter(field)).isFalse();
            }
        }
    }

    @Test
    void enumFilter_enumValue() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();

        for (Field field : fields) {
            // The filter should return true (filter out) for synthetic fields like $VALUES
            // and specific enum-internal fields
            String fieldName = field.getName();
            if (fieldName.equals("$VALUES") || fieldName.equalsIgnoreCase("ENUM$VALUES") || 
                fieldName.equals("internal") || fieldName.equals("hash") || fieldName.equals("ordinal")) {
                assertThat(enumFieldFilter.filter(field)).isTrue();
            } else {
                assertThat(enumFieldFilter.filter(field)).isFalse();
            }
        }
    }
}
