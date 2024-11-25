package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify mixing old and new formats in the same object graph,
 * backward compatibility, and round-trip conversions
 */
class EnumSetCrossFormatTest {
    private enum FirstEnum {
        A1, B1, C1
    }

    private enum SecondEnum {
        X2, Y2, Z2
    }

    // Container class with multiple EnumSet fields
    private static class MultiFormatContainer {
        private EnumSet<FirstEnum> newFormat;      // Will be written with new format
        private EnumSet<SecondEnum> legacyFormat;  // Will be written with @enum
        private Map<String, EnumSet<?>> enumSets;  // Mixed formats in map
        private List<EnumSet<?>> enumSetList;      // Mixed formats in list
    }

    @Test
    void testEnumSet_mixedFormatsInContainer() {
        MultiFormatContainer source = new MultiFormatContainer();
        source.newFormat = EnumSet.of(FirstEnum.A1, FirstEnum.B1);
        source.legacyFormat = EnumSet.of(SecondEnum.X2, SecondEnum.Y2);

        // Create container with both formats
        String json = TestUtil.toJson(source);

        MultiFormatContainer target = TestUtil.toObjects(json, null);

        assertThat(target.newFormat).containsExactly(FirstEnum.A1, FirstEnum.B1);
        assertThat(target.legacyFormat).containsExactly(SecondEnum.X2, SecondEnum.Y2);
    }

    @Test
    void testEnumSet_mixedFormatsInCollection() {
        List<Object> source = new ArrayList<>();

        // Add EnumSets and regular enums
        source.add(FirstEnum.A1);                                  // Single enum
        source.add(EnumSet.of(FirstEnum.B1, FirstEnum.C1));       // EnumSet new format
        source.add(EnumSet.of(SecondEnum.X2));                    // Another EnumSet

        String json = TestUtil.toJson(source);

        List<Object> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(3)
                .satisfies(list -> {
                    assertThat(list.get(0)).isEqualTo(FirstEnum.A1);
                    assertThat(list.get(1)).isInstanceOf(EnumSet.class);
                    assertThat(list.get(2)).isInstanceOf(EnumSet.class);

                    @SuppressWarnings("unchecked")
                    EnumSet<FirstEnum> set1 = (EnumSet<FirstEnum>)list.get(1);
                    assertThat(set1).containsExactly(FirstEnum.B1, FirstEnum.C1);

                    @SuppressWarnings("unchecked")
                    EnumSet<SecondEnum> set2 = (EnumSet<SecondEnum>)list.get(2);
                    assertThat(set2).containsExactly(SecondEnum.X2);
                });
    }

    @Test
    void testEnumSet_roundTripFormatConversion() {
        // Start with new format
        EnumSet<FirstEnum> source = EnumSet.of(FirstEnum.A1, FirstEnum.B1);

        // First round - write and read
        String json1 = TestUtil.toJson(source);

        @SuppressWarnings("unchecked")
        EnumSet<FirstEnum> intermediate = TestUtil.toObjects(json1, null);

        // Second round - write and read again
        String json2 = TestUtil.toJson(intermediate);

        @SuppressWarnings("unchecked")
        EnumSet<FirstEnum> target = TestUtil.toObjects(json2, null);

        // All three objects should be equal
        assertThat(target)
                .isEqualTo(intermediate)
                .isEqualTo(source);
    }

    @Test
    void testEnumSet_complexObjectGraph() {
        // Create complex object with mixed formats
        Map<EnumSet<FirstEnum>, List<EnumSet<SecondEnum>>> source = new LinkedHashMap<>();

        EnumSet<FirstEnum> key1 = EnumSet.of(FirstEnum.A1);
        EnumSet<FirstEnum> key2 = EnumSet.of(FirstEnum.B1, FirstEnum.C1);

        List<EnumSet<SecondEnum>> value1 = Arrays.asList(
                EnumSet.of(SecondEnum.X2),
                EnumSet.of(SecondEnum.Y2, SecondEnum.Z2)
        );

        List<EnumSet<SecondEnum>> value2 = Arrays.asList(
                EnumSet.of(SecondEnum.X2, SecondEnum.Y2),
                EnumSet.noneOf(SecondEnum.class)
        );

        source.put(key1, value1);
        source.put(key2, value2);

        String json = TestUtil.toJson(source);

        Map<EnumSet<FirstEnum>, List<EnumSet<SecondEnum>>> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(2)
                .containsKey(key1)
                .containsKey(key2);

        List<EnumSet<SecondEnum>> targetValue1 = target.get(key1);
        assertThat(targetValue1).hasSize(2);
        assertThat(targetValue1.get(0)).containsExactly(SecondEnum.X2);
        assertThat(targetValue1.get(1)).containsExactly(SecondEnum.Y2, SecondEnum.Z2);

        List<EnumSet<SecondEnum>> targetValue2 = target.get(key2);
        assertThat(targetValue2).hasSize(2);
        assertThat(targetValue2.get(0)).containsExactly(SecondEnum.X2, SecondEnum.Y2);
        assertThat(targetValue2.get(1)).isEmpty();
    }

    @Test
    void testEnumSet_oldFormatCompatibility() {
        // Test reading old format written as string
        String oldJson = "{\n" +
                "  \"@enum\": \"" + FirstEnum.class.getName() + "\",\n" +
                "  \"@items\": [\"A1\", \"B1\"]\n" +
                "}";

        EnumSet<?> result = TestUtil.toObjects(oldJson, null);

        @SuppressWarnings("unchecked")
        EnumSet<FirstEnum> typed = (EnumSet<FirstEnum>) result;

        assertThat(typed).containsExactly(FirstEnum.A1, FirstEnum.B1);

        // Write it back out and verify new format
        String newJson = TestUtil.toJson(result);

        // Should be able to read it back in new format
        @SuppressWarnings("unchecked")
        EnumSet<FirstEnum> reconverted = TestUtil.toObjects(newJson, null);
        assertThat(reconverted).containsExactly(FirstEnum.A1, FirstEnum.B1);
    }
}