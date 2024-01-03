package com.cedarsoftware.util.reflect.filters;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class EnumFieldFilterTests {

    // TODO: needs to be rewritten for ReflectionUtils.getDeepDeclaredFields
    @Test
    void enumFilter_withABasicEnum_willNotFilterAnyFields() {
//        EnumFieldFilter enumFilter = new EnumFieldFilter();
//        Field[] fields = ColorEnum.class.getDeclaredFields();
//
//        for (Field field : fields) {
//            assertThat(enumFilter.filter(field)).isFalse();
//        }
    }

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
//        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
//        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();
//
//        for (Field field : fields) {
//            assertThat(enumFieldFilter.filter(field)).isFalse();
//        }
    }

    @Test
    void enumFilter_enumValue() {
//        EnumFieldFilter enumFieldFilter = new EnumFieldFilter();
//        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();
//
//        for (Field field : fields) {
//            assertThat(enumFieldFilter.filter(field)).isFalse();
//        }
    }
}
