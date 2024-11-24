package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;

import static com.cedarsoftware.io.JsonValue.ENUM;
import static com.cedarsoftware.io.JsonValue.ITEMS;
import static com.cedarsoftware.io.JsonValue.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EnumSets containing enums written in different formats and mixing
 * simple and complex enum types
 */
class MixedFormatEnumSetTest {
    private enum SimpleEnum {
        A, B, C
    }

    private enum ComplexEnum {
        ONE("first", 1),
        TWO("second", 2) {
            @Override
            public String getSpecial() {
                return "override-two";
            }
        },
        THREE("third", 3);

        private final String text;
        private final int value;

        ComplexEnum(String text, int value) {
            this.text = text;
            this.value = value;
        }

        public String getText() { return text; }
        public int getValue() { return value; }
        public String getSpecial() { return "default"; }
    }

    @Test
    void testEnumSet_withMixedFormatEnums() {
        // Create a complex enum set to test both simple and complex formats
        EnumSet<ComplexEnum> source = EnumSet.of(ComplexEnum.ONE, ComplexEnum.TWO, ComplexEnum.THREE);

        // Write it out to see the format
        String json = TestUtil.toJson(source);

        EnumSet<?> target = TestUtil.toObjects(json, null);

        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> typed = (EnumSet<ComplexEnum>) target;

        // Verify content
        assertThat(typed)
                .hasSize(3)
                .containsExactly(ComplexEnum.ONE, ComplexEnum.TWO, ComplexEnum.THREE);

        // Verify properties are maintained
        for (ComplexEnum e : typed) {
            switch (e) {
                case ONE:
                    assertThat(e.getText()).isEqualTo("first");
                    assertThat(e.getValue()).isEqualTo(1);
                    break;
                case TWO:
                    assertThat(e.getText()).isEqualTo("second");
                    assertThat(e.getValue()).isEqualTo(2);
                    assertThat(e.getSpecial()).isEqualTo("override-two");
                    break;
                case THREE:
                    assertThat(e.getText()).isEqualTo("third");
                    assertThat(e.getValue()).isEqualTo(3);
                    break;
            }
        }
    }

    // Separate test to verify different individual enum formats still work
    @Test
    void testEnum_differentFormats() {
        // Test reading individual enums in different formats
        String nameFormat = "{\"" + TYPE + "\":\"" + ComplexEnum.class.getName() + "\", \"name\":\"ONE\"}";
        String enumNameFormat = "{\"" + TYPE + "\":\"" + ComplexEnum.class.getName() + "\", \"Enum.name\":\"TWO\"}";
        String valueFormat = "{\"" + TYPE + "\":\"" + ComplexEnum.class.getName() + "\", \"value\":\"THREE\"}";

        ComplexEnum one = TestUtil.toObjects(nameFormat, null);
        ComplexEnum two = TestUtil.toObjects(enumNameFormat, null);
        ComplexEnum three = TestUtil.toObjects(valueFormat, null);

        assertThat(one).isEqualTo(ComplexEnum.ONE);
        assertThat(two).isEqualTo(ComplexEnum.TWO);
        assertThat(three).isEqualTo(ComplexEnum.THREE);
    }
    
    @Test
    void testEnumSet_mixingSimpleAndComplexEnums() {
        // Create EnumSets of different enum types
        EnumSet<SimpleEnum> simpleSet = EnumSet.of(SimpleEnum.A, SimpleEnum.B);
        EnumSet<ComplexEnum> complexSet = EnumSet.of(ComplexEnum.ONE, ComplexEnum.TWO);

        // Serialize both
        String simpleJson = TestUtil.toJson(simpleSet);
        String complexJson = TestUtil.toJson(complexSet);

        // Deserialize and verify
        @SuppressWarnings("unchecked")
        EnumSet<SimpleEnum> simpleTarget = TestUtil.toObjects(simpleJson, null);
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> complexTarget = TestUtil.toObjects(complexJson, null);

        assertThat(simpleTarget).containsExactly(SimpleEnum.A, SimpleEnum.B);
        assertThat(complexTarget).containsExactly(ComplexEnum.ONE, ComplexEnum.TWO);

        // Verify complex properties maintained
        assertThat(complexTarget.iterator().next().getText()).isEqualTo("first");
    }

    @Test
    void testEnumSet_withConstantSpecificMethods() {
        EnumSet<ComplexEnum> source = EnumSet.of(ComplexEnum.ONE, ComplexEnum.TWO);
        String json = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> target = TestUtil.toObjects(json, null);

        // Verify regular properties
        assertThat(target).containsExactly(ComplexEnum.ONE, ComplexEnum.TWO);

        // Verify constant-specific method override works
        ComplexEnum two = target.stream()
                .filter(e -> e == ComplexEnum.TWO)
                .findFirst()
                .orElse(null);

        assertThat(two).isNotNull();
        assertThat(two.getSpecial()).isEqualTo("override-two");

        ComplexEnum one = target.stream()
                .filter(e -> e == ComplexEnum.ONE)
                .findFirst()
                .orElse(null);

        assertThat(one).isNotNull();
        assertThat(one.getSpecial()).isEqualTo("default");
    }

    @Test
    void testEnumSet_withDifferentWriteOptions() {
        EnumSet<ComplexEnum> source = EnumSet.of(ComplexEnum.TWO, ComplexEnum.THREE);

        // Write with different options
        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(true)
                .writeEnumSetOldWay(false)
                .build();

        String jsonWithOptions = TestUtil.toJson(source, options);
        String jsonDefault = TestUtil.toJson(source, new WriteOptionsBuilder().writeEnumSetOldWay(false).build());
        
        // Both formats should deserialize correctly
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> targetWithOptions = TestUtil.toObjects(jsonWithOptions, null);
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> targetDefault = TestUtil.toObjects(jsonDefault, null);

        assertThat(targetWithOptions)
                .containsExactly(ComplexEnum.TWO, ComplexEnum.THREE)
                .isEqualTo(targetDefault);

        // Verify special property maintained
        ComplexEnum two = targetWithOptions.stream()
                .filter(e -> e == ComplexEnum.TWO)
                .findFirst()
                .orElse(null);
        assertThat(two).isNotNull();
        assertThat(two.getSpecial()).isEqualTo("override-two");
    }

    @Test
    void testEnumSet_withMixedLegacyAndNewFormats() {
        // Create EnumSet using legacy format
        String legacyJson = "{\"" + ENUM + "\":\"" + ComplexEnum.class.getName()
                + "\",\"" + ITEMS + "\":[\"ONE\",\"TWO\"]}";

        // Create same EnumSet using new format
        EnumSet<ComplexEnum> source = EnumSet.of(ComplexEnum.ONE, ComplexEnum.TWO);
        String newJson = TestUtil.toJson(source);

        // Both should deserialize to equivalent EnumSets
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> legacyTarget = TestUtil.toObjects(legacyJson, null);
        @SuppressWarnings("unchecked")
        EnumSet<ComplexEnum> newTarget = TestUtil.toObjects(newJson, null);

        assertThat(legacyTarget)
                .isEqualTo(newTarget)
                .containsExactly(ComplexEnum.ONE, ComplexEnum.TWO);
    }
}