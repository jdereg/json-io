package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static com.cedarsoftware.io.JsonValue.TYPE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for basic enum serialization/deserialization patterns
 */
class EnumBasicCreationTest {
    private enum SimpleTestEnum {
        ALPHA, BETA, GAMMA
    }

    private enum TestEnumWithField {
        ONE("first"),
        TWO("second"),
        THREE("third");

        private final String description;

        TestEnumWithField(String desc) {
            this.description = desc;
        }

        public String getDescription() {
            return description;
        }
    }

    @Test
    void testEnum_currentFormat() {
        SimpleTestEnum source = SimpleTestEnum.BETA;
        String json = TestUtil.toJson(source);
        SimpleTestEnum target = TestUtil.toObjects(json, null);

        assertThat(target).isEqualTo(source);
        assertThat(json).contains(TYPE);
        assertThat(json).contains("\"name\":\"BETA\"");
    }

    @Test
    void testEnum_legacyEnumNameFormat() {
        TestEnumWithField source = TestEnumWithField.TWO;
        String json = "{\"" + TYPE + "\":\"" + TestEnumWithField.class.getName() + "\",\"Enum.name\":\"TWO\"}";
        TestEnumWithField target = TestUtil.toObjects(json, null);

        assertThat(target).isEqualTo(source);
        assertThat(target.getDescription()).isEqualTo("second");
    }

    @Test
    void testEnum_legacyValueFormat() {
        SimpleTestEnum source = SimpleTestEnum.GAMMA;
        String json = "{\"" + TYPE + "\":\"" + SimpleTestEnum.class.getName() + "\",\"value\":\"GAMMA\"}";
        SimpleTestEnum target = TestUtil.toObjects(json, null);

        assertThat(target).isEqualTo(source);
    }

    @Test
    void testEnum_withFields() {
        TestEnumWithField source = TestEnumWithField.THREE;
        String json = TestUtil.toJson(source);
        TestEnumWithField target = TestUtil.toObjects(json, null);

        assertThat(target).isEqualTo(source);
        assertThat(target.getDescription()).isEqualTo("third");
    }

    @Test
    void testEnum_whenWrittenAsJsonObject() {
        SimpleTestEnum source = SimpleTestEnum.ALPHA;
        WriteOptions options = new WriteOptionsBuilder()
                .writeEnumAsJsonObject(true)
                .build();

        String json = TestUtil.toJson(source, options);
        SimpleTestEnum target = TestUtil.toObjects(json, null);

        assertThat(target).isEqualTo(source);
        assertThat(json).contains(TYPE);
        assertThat(json).contains("\"name\":\"ALPHA\"");
    }

    @Test
    void testEnum_roundTripWithAllFormats() {
        // Test all enum constant variations in one test
        TestEnumWithField[] values = TestEnumWithField.values();
        for (TestEnumWithField value : values) {
            String json = TestUtil.toJson(value);
            TestEnumWithField target = TestUtil.toObjects(json, null);
            assertThat(target).isEqualTo(value);
            assertThat(target.getDescription()).isEqualTo(value.getDescription());
        }
    }
}