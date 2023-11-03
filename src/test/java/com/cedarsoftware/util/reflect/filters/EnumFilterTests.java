package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class EnumFilterTests {

    // not sure how to get the ordinal field or hash that we're actually filtering here?
    @Test
    void enumFilter_withABasicEnum_willNotFilterAnyFields() {
        EnumFilter enumFilter = new EnumFilter();
        Field[] fields = ColorEnum.class.getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFilter.filter(field)).isFalse();
        }
    }

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
        EnumFilter enumFilter = new EnumFilter();
        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFilter.filter(field)).isFalse();
        }
    }

    @Test
    void enumFilter_enumValue() {
        EnumFilter enumFilter = new EnumFilter();
        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();

        for (Field field : fields) {
            assertThat(enumFilter.filter(field)).isFalse();
        }
    }
}
