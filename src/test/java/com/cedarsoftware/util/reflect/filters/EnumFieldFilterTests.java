package com.cedarsoftware.util.reflect.filters;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.cedarsoftware.util.reflect.filters.field.EnumFieldFilter;
import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;

import static org.assertj.core.api.Assertions.assertThat;


class EnumFieldFilterTests {

    @Test
    void enumFilter_withABasicEnum_willNotFilterAnyFields() {
        EnumFieldFilter enumFilter = new EnumFieldFilter();
        Field[] fields = ColorEnum.class.getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFilter.filter(field)).isFalse();
        }
    }

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFieldFilter.filter(field)).isFalse();
        }
    }

    @Test
    void enumFilter_enumValue() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFieldFilter.filter(field)).isFalse();
        }
    }
}
