package com.cedarsoftware.util.reflect.filters;

import com.cedarsoftware.util.io.MetaUtils;
import com.cedarsoftware.util.reflect.KnownFilteredFields;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KnownFilteredFieldsTest {

    private List<Field> getFields(Class c, Set<String> set) {
        Field[] fields = c.getDeclaredFields();
        return Arrays.stream(fields).filter(f -> set.contains(f.getName())).collect(Collectors.toList());
    }

    private static Stream<Arguments> filteredFields() {
        return Stream.of(
                Arguments.of(Throwable.class, new String[]{"backtrace", "depth", "suppressedExceptions", "stackTrace"}),
                Arguments.of(StackTraceElement.class, new String[]{"declaringClassObject", "format"})
        );
    }

    @BeforeEach
    void beforeEach() {
        KnownFilteredFields.instance().addMapping(Throwable.class, "stackTrace");
        KnownFilteredFields.instance().removeMapping(Throwable.class, "detailMessage");
    }

    @ParameterizedTest
    @MethodSource("filteredFields")
    void testContains_fieldsThatShouldBeFiltered(Class c, String... fieldNames) {
        Set set = MetaUtils.setOf(fieldNames);
        List<Field> fields = getFields(c, set);

        assertThat(fields.size()).isEqualTo(fieldNames.length);

        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();

        for (Field f : fields) {
            assertThat(knownFilteredFields.contains(f)).isTrue();
        }
    }

    @Test
    void testAddMapping() {
        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
        List<Field> fields = getFields(Throwable.class, MetaUtils.setOf("detailMessage"));
        assertThat(knownFilteredFields.contains(fields.get(0))).isFalse();

        knownFilteredFields.addMapping(Throwable.class, "detailMessage");
        assertThat(knownFilteredFields.contains(fields.get(0))).isTrue();
    }

    @Test
    void testAddMappings() {
        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
        List<Field> fields = getFields(Throwable.class, MetaUtils.setOf("detailMessage", "cause"));
        assertThat(knownFilteredFields.contains(fields.get(0))).isFalse();
        assertThat(knownFilteredFields.contains(fields.get(1))).isFalse();

        knownFilteredFields.addMappings(Throwable.class, MetaUtils.listOf("detailMessage", "cause"));
        assertThat(knownFilteredFields.contains(fields.get(0))).isTrue();
        assertThat(knownFilteredFields.contains(fields.get(1))).isTrue();
    }

    @Test
    void testRemoveMapping() {
        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
        List<Field> fields = getFields(Throwable.class, MetaUtils.setOf("stackTrace"));
        assertThat(knownFilteredFields.contains(fields.get(0))).isTrue();

        knownFilteredFields.removeMapping(Throwable.class, "stackTrace");
        assertThat(knownFilteredFields.contains(fields.get(0))).isFalse();
    }

}
