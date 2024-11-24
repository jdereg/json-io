package com.cedarsoftware.io;

import java.util.EnumSet;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonValue.ENUM;
import static com.cedarsoftware.io.JsonValue.ITEMS;
import static com.cedarsoftware.io.JsonValue.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for populated EnumSet serialization/deserialization in various formats
 */
class PopulatedEnumSetTest {
    private enum SimpleEnum {
        A, B, C, D
    }

    private enum ComplexEnum {
        ONE("first", 1),
        TWO("second", 2),
        THREE("third", 3);

        private final String text;
        private final int value;

        ComplexEnum(String text, int value) {
            this.text = text;
            this.value = value;
        }

        public String getText() { return text; }
        public int getValue() { return value; }
    }

    @Test
    void testPopulatedEnumSet_newFormat() {
        EnumSet<SimpleEnum> source = EnumSet.of(SimpleEnum.A, SimpleEnum.C);
        String json = TestUtil.toJson(source, new WriteOptionsBuilder().writeEnumSetOldWay(false).build());

        // Verify format
        assertThat(json).contains("\"" + TYPE + "\"");
        assertThat(json).contains("\"" + ITEMS + "\":[");

        EnumSet<?> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2)
                .isInstanceOf(EnumSet.class);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> typed = (EnumSet<SimpleEnum>) target;
        assertThat(typed).containsExactly(SimpleEnum.A, SimpleEnum.C);
    }

    @Test
    void testPopulatedEnumSet_legacyFormat() {
        // Test reading legacy format
        String json = "{\"" + ENUM + "\":\"" + SimpleEnum.class.getName()
                + "\",\"" + ITEMS + "\":[\"A\",\"D\"]}";

        EnumSet<?> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> typed = (EnumSet<SimpleEnum>) target;
        assertThat(typed).containsExactly(SimpleEnum.A, SimpleEnum.D);
    }

    @Test
    void testPopulatedEnumSet_withComplexEnums() {
        EnumSet<ComplexEnum> source = EnumSet.of(ComplexEnum.ONE, ComplexEnum.THREE);
        String json = TestUtil.toJson(source);

        EnumSet<?> target = TestUtil.toObjects(json, null);
        assert DeepEquals.deepEquals(source, target);
        
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> typed = (EnumSet<ComplexEnum>) target;

        assertThat(typed).containsExactly(ComplexEnum.ONE, ComplexEnum.THREE);

        // Verify complex enum properties are maintained
        ComplexEnum first = typed.iterator().next();
        assertThat(first.getText()).isEqualTo("first");
        assertThat(first.getValue()).isEqualTo(1);
    }

    @Test
    void testPopulatedEnumSet_allValues() {
        EnumSet<SimpleEnum> source = EnumSet.allOf(SimpleEnum.class);
        String json = TestUtil.toJson(source);

        EnumSet<?> target = TestUtil.toObjects(json, null);
        assert DeepEquals.deepEquals(source, target);

        assertThat(target)
                .isNotNull()
                .hasSize(SimpleEnum.values().length)
                .isInstanceOf(EnumSet.class);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> typed = (EnumSet<SimpleEnum>) target;
        assertThat(typed).containsExactly(SimpleEnum.values());
    }

    @Test
    void testPopulatedEnumSet_mutabilityOperations() {
        EnumSet<SimpleEnum> source = EnumSet.of(SimpleEnum.A, SimpleEnum.B);
        String json = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> target = TestUtil.toObjects(json, null);
        assert DeepEquals.deepEquals(source, target);

        // Test removing elements
        target.remove(SimpleEnum.A);
        assertThat(target).containsExactly(SimpleEnum.B);

        // Test adding elements
        target.add(SimpleEnum.D);
        assertThat(target).containsExactly(SimpleEnum.B, SimpleEnum.D);

        // Test bulk operations
        target.addAll(EnumSet.of(SimpleEnum.A, SimpleEnum.C));
        assertThat(target).containsExactly(SimpleEnum.A, SimpleEnum.B, SimpleEnum.C, SimpleEnum.D);

        target.removeAll(EnumSet.of(SimpleEnum.B, SimpleEnum.D));
        assertThat(target).containsExactly(SimpleEnum.A, SimpleEnum.C);
    }

    @Test
    void testPopulatedEnumSet_complementOperations() {
        EnumSet<SimpleEnum> source = EnumSet.of(SimpleEnum.A, SimpleEnum.B);
        String json = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> target = TestUtil.toObjects(json, null);
        assert DeepEquals.deepEquals(source, target);

        // Get complement and verify
        EnumSet<SimpleEnum> complement = EnumSet.complementOf(target);
        assertThat(complement).containsExactly(SimpleEnum.C, SimpleEnum.D);

        // Test operations between original and complement
        target.addAll(complement);
        assertThat(target).containsExactly(SimpleEnum.values());
    }

    @Test
    void testPopulatedEnumSet_roundTripModifications() {
        // Create and modify before first serialization
        EnumSet<SimpleEnum> source = EnumSet.of(SimpleEnum.A);
        source.add(SimpleEnum.B);

        String json1 = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> intermediate = TestUtil.toObjects(json1, null);
        assert DeepEquals.deepEquals(source, intermediate);

        intermediate.remove(SimpleEnum.A);
        intermediate.add(SimpleEnum.C);

        String json2 = TestUtil.toJson(intermediate);

        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> target = TestUtil.toObjects(json2, null);

        assertThat(target).containsExactly(SimpleEnum.B, SimpleEnum.C);
    }
}