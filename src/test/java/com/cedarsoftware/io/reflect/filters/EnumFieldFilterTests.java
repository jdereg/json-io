package com.cedarsoftware.io.reflect.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            assertEquals(enumFilter.filter(field), field.isSynthetic(), field.getName());
        }
    }

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();

        for (Field field : fields) {
            assertEquals(enumFieldFilter.filter(field), field.isSynthetic(), field.getName());
        }
    }

    @Test
    void enumFilter_enumValue() {
        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();

        for (Field field : fields) {
            assertEquals(enumFieldFilter.filter(field), field.isSynthetic(), field.getName());
        }
    }
}
