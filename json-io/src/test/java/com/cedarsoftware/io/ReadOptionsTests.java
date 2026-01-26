package com.cedarsoftware.io;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.CollectionUtilities.setOf;

public class ReadOptionsTests {

    private static Stream<Arguments> filteredFields() {
        return Stream.of(
                Arguments.of(Throwable.class, new String[]{"backtrace", "depth", "suppressedExceptions", "stackTrace"}),
                Arguments.of(StackTraceElement.class, new String[]{"declaringClassObject", "format"})
        );
    }

    @ParameterizedTest
    @MethodSource("filteredFields")
    void testContains_fieldsThatShouldBeFiltered(Class c, String... fieldNames) {

        ReadOptions options = new ReadOptionsBuilder().build();
        Set set = setOf(fieldNames);
//        List<Field> fields = getFields(c, set);
//
//        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
//
//        for (Field f : fields) {
//            assertThat(knownFilteredFields.isFieldFiltered(f)).isTrue();
//        }
    }

    @Test
    void testAddMapping() {
//        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
//        List<Field> fields = getFields(Throwable.class, CollectionUtilities.setOf("detailMessage"));
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isFalse();
//
//        knownFilteredFields.addFieldFilter(Throwable.class, "detailMessage");
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isTrue();
    }

    @Test
    void testAddMappings() {
//        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
//        List<Field> fields = getFields(Throwable.class, CollectionUtilities.setOf("detailMessage", "cause"));
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isFalse();
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(1))).isFalse();
//
//        knownFilteredFields.addFieldFilters(Throwable.class, CollectionUtilities.listOf("detailMessage", "cause"));
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isTrue();
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(1))).isTrue();
    }

    @Test
    void testRemoveMapping() {
//        KnownFilteredFields knownFilteredFields = KnownFilteredFields.instance();
//        List<Field> fields = getFields(Throwable.class, CollectionUtilities.setOf("stackTrace"));
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isTrue();
//
//        knownFilteredFields.removeFieldFilters(Throwable.class, "stackTrace");
//        assertThat(knownFilteredFields.isFieldFiltered(fields.get(0))).isFalse();
    }
}
