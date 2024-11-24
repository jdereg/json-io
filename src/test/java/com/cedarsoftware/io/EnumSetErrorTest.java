package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests error cases and invalid inputs for EnumSet serialization/deserialization
 */
class EnumSetErrorTest {
    private enum TestEnum {
        A, B, C
    }

    @Test
    void testEnumSet_invalidEnumName() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", \"INVALID_NAME\", \"C\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("No enum constant")
                .hasMessageContaining("INVALID_NAME");
    }

    @Test
    void testEnumSet_invalidEnumType() {
        String json = "{\n" +
                "  \"@type\": \"com.invalid.NonExistentEnum\",\n" +
                "  \"@items\": [\"A\", \"B\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unknown type (class)")
                .hasMessageContaining("com.invalid.NonExistentEnum")
                .hasMessageContaining("not defined");
    }
    
    @Test
    void testEnumSet_nullItemInArray() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", null, \"C\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("null");
    }

    @Test
    void testEnumSet_missingItems() {
        // Test that attempting to use @type without @items fails
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\"\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("Unable to instantiate enum");

        // Test that proper format with empty @items works
        String correctJson = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": []\n" +
                "}";

        EnumSet<?> result = TestUtil.toObjects(correctJson, null);
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }
    
    @Test
    void testEnumSet_invalidItemType() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [42, true, \"C\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("type");
    }

    @Test
    void testEnumSet_mixedEnumTypes() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", \"X\", \"C\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void testEnumSet_malformedJson() {
        // Missing closing brace
        String malformed1 = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", \"B\"]\n";

        assertThatThrownBy(() -> TestUtil.toObjects(malformed1, null))
                .isInstanceOf(JsonIoException.class);

        // Invalid array syntax
        String malformed2 = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\" \"B\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(malformed2, null))
                .isInstanceOf(JsonIoException.class);

        // Invalid quotes
        String malformed3 = "{\n" +
                "  \"@type\": '" + TestEnum.class.getName() + "',\n" +
                "  \"@items\": [\"A\", \"B\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(malformed3, null))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void testEnumSet_duplicateEnumValues() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", \"B\", \"A\"]\n" +
                "}";

        // Should not throw exception - EnumSet handles duplicates by ignoring them
        EnumSet<?> result = TestUtil.toObjects(json, null);

        @SuppressWarnings("unchecked")
        EnumSet<TestEnum> typed = (EnumSet<TestEnum>) result;

        assertThat(typed)
                .hasSize(2)
                .containsExactlyInAnyOrder(TestEnum.A, TestEnum.B);
    }

    @Test
    void testEnumSet_caseSensitivity() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"a\", \"B\", \"c\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class)
                .hasMessageContaining("No enum constant");
    }

    @Test
    void testEnumSet_emptyString() {
        String json = "{\n" +
                "  \"@type\": \"" + TestEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A\", \"\", \"C\"]\n" +
                "}";

        assertThatThrownBy(() -> TestUtil.toObjects(json, null))
                .isInstanceOf(JsonIoException.class);
    }
}