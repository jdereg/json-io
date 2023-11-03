package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.reflect.filters.models.CarEnumWithCustomFields;
import com.cedarsoftware.util.reflect.filters.models.ColorEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class StaticFilterTests {

    @Test
    void enumFilter_withEnumThatHasAdditionalFields() {
        StaticFilter staticFilter = new StaticFilter();
        Field[] fields = CarEnumWithCustomFields.class.getDeclaredFields();

        for (Field field : fields) {
            if ((field.getModifiers() & Modifier.STATIC) == Modifier.STATIC) {
                assertThat(staticFilter.filter(field))
                        .isTrue()
                        .withFailMessage("static items should be filtered.");
            } else {
                assertThat(staticFilter.filter(field))
                        .isFalse()
                        .withFailMessage("non-static items should not be filtered.");
            }
        }
    }

    @Test
    void staticFilter_filtersAll_onEnumWithAllStatics() {
        StaticFilter staticFilter = new StaticFilter();
        Field[] fields = ColorEnum.BLUE.getClass().getDeclaredFields();

        for (Field field : fields) {
            assertThat(staticFilter.filter(field))
                    .isTrue()
                    .withFailMessage("non-static items should not be filtered.");
        }
    }
}
