package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enum usage within various container types (arrays, collections, maps)
 * and as fields within objects.
 */
class EnumContainerTest {
    private enum TestEnum {
        ALPHA, BETA, GAMMA
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
    void testEnum_inArray() {
        TestEnum[] source = new TestEnum[] { TestEnum.ALPHA, TestEnum.GAMMA, TestEnum.BETA };
        String json = TestUtil.toJson(source);
        TestEnum[] target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(3)
                .containsExactly(TestEnum.ALPHA, TestEnum.GAMMA, TestEnum.BETA);
    }

    @Test
    void testEnum_inList() {
        List<TestEnum> source = Arrays.asList(TestEnum.ALPHA, TestEnum.BETA);
        String json = TestUtil.toJson(source);
        List<TestEnum> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2)
                .containsExactly(TestEnum.ALPHA, TestEnum.BETA);
    }

    @Test
    void testEnum_inSet() {
        Set<TestEnum> source = new LinkedHashSet<>(Arrays.asList(TestEnum.GAMMA, TestEnum.ALPHA));
        String json = TestUtil.toJson(source);
        Set<TestEnum> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2)
                .containsExactly(TestEnum.GAMMA, TestEnum.ALPHA);
    }

    @Test
    void testEnum_asMapKey() {
        Map<TestEnum, String> source = new LinkedHashMap<>();
        source.put(TestEnum.ALPHA, "first");
        source.put(TestEnum.BETA, "second");

        String json = TestUtil.toJson(source);
        Map<TestEnum, String> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2)
                .containsEntry(TestEnum.ALPHA, "first")
                .containsEntry(TestEnum.BETA, "second");
    }

    @Test
    void testEnum_asMapValue() {
        Map<String, TestEnum> source = new LinkedHashMap<>();
        source.put("first", TestEnum.GAMMA);
        source.put("second", TestEnum.ALPHA);

        String json = TestUtil.toJson(source);
        Map<String, TestEnum> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2)
                .containsEntry("first", TestEnum.GAMMA)
                .containsEntry("second", TestEnum.ALPHA);
    }

    @Test
    void testEnum_asClassField() {
        EnumContainer source = new EnumContainer();
        source.directEnum = TestEnum.BETA;
        source.complexEnum = ComplexEnum.TWO;

        String json = TestUtil.toJson(source);
        EnumContainer target = TestUtil.toObjects(json, null);

        assertThat(target.directEnum).isEqualTo(TestEnum.BETA);
        assertThat(target.complexEnum)
                .isEqualTo(ComplexEnum.TWO)
                .matches(e -> e.getText().equals("second"))
                .matches(e -> e.getValue() == 2);
    }

    @Test
    void testEnum_inNestedCollections() {
        // Map<Enum, List<Enum>>
        Map<TestEnum, List<ComplexEnum>> source = new LinkedHashMap<>();
        source.put(TestEnum.ALPHA, Arrays.asList(ComplexEnum.ONE, ComplexEnum.TWO));
        source.put(TestEnum.BETA, Arrays.asList(ComplexEnum.TWO, ComplexEnum.THREE));

        String json = TestUtil.toJson(source);
        Map<TestEnum, List<ComplexEnum>> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .isNotNull()
                .hasSize(2);

        assertThat(target.get(TestEnum.ALPHA))
                .containsExactly(ComplexEnum.ONE, ComplexEnum.TWO);

        assertThat(target.get(TestEnum.BETA))
                .containsExactly(ComplexEnum.TWO, ComplexEnum.THREE);

        // Verify complex enum properties are maintained
        ComplexEnum complexEnum = target.get(TestEnum.ALPHA).get(0);
        assertThat(complexEnum.getText()).isEqualTo("first");
        assertThat(complexEnum.getValue()).isEqualTo(1);
    }

    @Test
    void testEnum_inComplexStructure() {
        ComplexContainer source = new ComplexContainer();
        source.enumList = new ArrayList<>(Arrays.asList(TestEnum.ALPHA, TestEnum.BETA));
        source.enumsByString = new HashMap<>();
        source.enumsByString.put("test", Arrays.asList(ComplexEnum.ONE, ComplexEnum.TWO));
        source.stringsByEnum = new HashMap<>();
        source.stringsByEnum.put(TestEnum.GAMMA, Arrays.asList("a", "b"));

        String json = TestUtil.toJson(source);
        ComplexContainer target = TestUtil.toObjects(json, null);

        assertThat(target.enumList)
                .containsExactly(TestEnum.ALPHA, TestEnum.BETA);

        assertThat(target.enumsByString.get("test"))
                .containsExactly(ComplexEnum.ONE, ComplexEnum.TWO);

        assertThat(target.stringsByEnum.get(TestEnum.GAMMA))
                .containsExactly("a", "b");
    }

    // Support classes
    private static class EnumContainer {
        private TestEnum directEnum;
        private ComplexEnum complexEnum;
    }

    private static class ComplexContainer {
        private List<TestEnum> enumList;
        private Map<String, List<ComplexEnum>> enumsByString;
        private Map<TestEnum, List<String>> stringsByEnum;
    }
}