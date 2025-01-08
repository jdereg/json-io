package com.cedarsoftware.io;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonValue.ENUM;
import static com.cedarsoftware.io.JsonValue.ITEMS;
import static com.cedarsoftware.io.JsonValue.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for empty EnumSet serialization/deserialization in various formats
 * and verifying proper type preservation and mutability.
 */
class EmptyEnumSetTest {
    private enum TestEnum {
        A, B, C
    }

    @Test
    void testEmptyEnumSet_newFormat() {
        EnumSet<TestEnum> source = EnumSet.noneOf(TestEnum.class);
        String json = TestUtil.toJson(source, new WriteOptionsBuilder().writeEnumSetOldWay(false).build());

        // Verify format
        assertThat(json).contains(TYPE);
        assertThat(json).contains("\"" + ITEMS + "\":[]");

        EnumSet<?> target = TestUtil.toObjects(json, null);
        assertThat(target)
                .isNotNull()
                .isEmpty();

        // Verify type preserved by successfully adding element
        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> typed = (EnumSet<TestEnum>) target;
        typed.add(TestEnum.A);
        assertThat(typed).containsExactly(TestEnum.A);
    }

    @Test
    void testEmptyEnumSet_legacyFormat() {
        // Test reading legacy format
        String json = "{\"" + ENUM + "\":\"" + TestEnum.class.getName() + "\"}";
        EnumSet<?> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .isEmpty();

        // Verify type preserved by successfully adding element
        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> typed = (EnumSet<TestEnum>) target;
        typed.add(TestEnum.B);
        assertThat(typed).containsExactly(TestEnum.B);
    }

    @Test
    void testEmptyEnumSet_withSpecifiedTypeAtRoot() {
        // Create with explicit type
        EnumSet<TestEnum> source = EnumSet.noneOf(TestEnum.class);
        String json = TestUtil.toJson(source);
        EnumSet<TestEnum> target = (EnumSet<TestEnum>)(Object)TestUtil.toObjects(json, TestEnum.class);

        assertThat(target)
                .isNotNull()
                .isEmpty();

        // Verify correct type by adding all enum values
        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> typed = target;
        typed.addAll(EnumSet.allOf(TestEnum.class));
        assertThat(typed).containsExactly(TestEnum.A, TestEnum.B, TestEnum.C);
    }

    @Test
    void testEmptyEnumSet_elementTypeVerification() {
        EnumSet<TestEnum> source = EnumSet.noneOf(TestEnum.class);
        String json = TestUtil.toJson(source);
        EnumSet<?> target = TestUtil.toObjects(json, null);

        // Verify we can complement the set (only works if element type is correct)
        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> typed = (EnumSet<TestEnum>) target;
        EnumSet<TestEnum> complement = EnumSet.complementOf(typed);

        assertThat(complement)
                .isNotEmpty()
                .containsExactly(TestEnum.A, TestEnum.B, TestEnum.C);
    }

    @Test
    void testEmptyEnumSet_mutabilityAfterDeserialization() {
        EnumSet<TestEnum> source = EnumSet.noneOf(TestEnum.class);
        String json = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> target = TestUtil.toObjects(json, null);

        // Verify all mutation operations work
        assertThat(target.add(TestEnum.A)).isTrue();
        assertThat(target).containsExactly(TestEnum.A);

        assertThat(target.remove(TestEnum.A)).isTrue();
        assertThat(target).isEmpty();

        target.addAll(EnumSet.of(TestEnum.B, TestEnum.C));
        assertThat(target).containsExactly(TestEnum.B, TestEnum.C);

        target.removeAll(EnumSet.of(TestEnum.B));
        assertThat(target).containsExactly(TestEnum.C);

        target.clear();
        assertThat(target).isEmpty();
    }

    @Test
    void testEmptyEnumSet_roundTripWithModification() {
        // Create empty set
        EnumSet<TestEnum> source = EnumSet.noneOf(TestEnum.class);
        String json1 = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> intermediate = TestUtil.toObjects(json1, null);

        // Modify and serialize again
        intermediate.add(TestEnum.A);
        String json2 = TestUtil.toJson(intermediate);

        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> target = TestUtil.toObjects(json2, null);

        assertThat(target)
                .isNotNull()
                .containsExactly(TestEnum.A);
    }
}