package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EnumSet usage within various container types (arrays, collections, maps)
 * and as fields within objects.
 */
class EnumSetContainerTest {
    private enum TestEnum {
        A, B, C, D
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEnumSet_inArray() {
        // Create array of EnumSets
        EnumSet<TestEnum>[] source = new EnumSet[] {
                EnumSet.of(TestEnum.A, TestEnum.B),
                EnumSet.of(TestEnum.C),
                EnumSet.noneOf(TestEnum.class)
        };

        String json = TestUtil.toJson(source);

        EnumSet<TestEnum>[] target = TestUtil.toObjects(json, null);

        assertThat(target).hasSize(3);
        assertThat(target[0]).containsExactly(TestEnum.A, TestEnum.B);
        assertThat(target[1]).containsExactly(TestEnum.C);
        assertThat(target[2]).isEmpty();
    }

    @Test
    void testEnumSet_inCollection() {
        List<EnumSet<TestEnum>> source = new ArrayList<>();
        source.add(EnumSet.of(TestEnum.A, TestEnum.B));
        source.add(EnumSet.of(TestEnum.C, TestEnum.D));

        String json = TestUtil.toJson(source);

        List<EnumSet<TestEnum>> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(2)
                .satisfies(list -> {
                    assertThat(list.get(0)).containsExactly(TestEnum.A, TestEnum.B);
                    assertThat(list.get(1)).containsExactly(TestEnum.C, TestEnum.D);
                });
    }

    @Test
    void testEnumSet_asMapKey() {
        Map<EnumSet<TestEnum>, String> source = new LinkedHashMap<>();
        source.put(EnumSet.of(TestEnum.A, TestEnum.B), "first");
        source.put(EnumSet.of(TestEnum.C), "second");

        String json = TestUtil.toJson(source);

        Map<EnumSet<TestEnum>, String> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(2)
                .containsEntry(EnumSet.of(TestEnum.A, TestEnum.B), "first")
                .containsEntry(EnumSet.of(TestEnum.C), "second");
    }

    @Test
    void testEnumSet_asMapValue() {
        Map<String, EnumSet<TestEnum>> source = new LinkedHashMap<>();
        source.put("first", EnumSet.of(TestEnum.A, TestEnum.B));
        source.put("second", EnumSet.of(TestEnum.C, TestEnum.D));

        String json = TestUtil.toJson(source);

        Map<String, EnumSet<TestEnum>> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(2)
                .satisfies(map -> {
                    assertThat(map.get("first")).containsExactly(TestEnum.A, TestEnum.B);
                    assertThat(map.get("second")).containsExactly(TestEnum.C, TestEnum.D);
                });
    }

    @Test
    void testEnumSet_asClassField() {
        EnumSetContainer source = new EnumSetContainer();
        source.set1 = EnumSet.of(TestEnum.A, TestEnum.B);
        source.set2 = EnumSet.of(TestEnum.C);
        source.nullSet = null;

        String json = TestUtil.toJson(source);

        EnumSetContainer target = TestUtil.toObjects(json, null);

        assertThat(target.set1).containsExactly(TestEnum.A, TestEnum.B);
        assertThat(target.set2).containsExactly(TestEnum.C);
        assertThat(target.nullSet).isNull();
    }

    @Test
    void testEnumSet_asClassFieldEmptyLegacyWritten() {
        String json = "{" +
                "  \"@type\" : \"com.cedarsoftware.io.EnumSetContainerTest$EnumSetContainer\"," +
                "  \"set1\" : {" +
                "    \"@type\": \"java.util.RegularEnumSet\"" +
                "  }," +
                "  \"set2\" : {" +
                "    \"@type\": \"java.util.RegularEnumSet\"" +
                "  }," +
                "  \"nullSet\" : {" +
                "    \"@type\": \"java.util.RegularEnumSet\"" +
                "  }\n" +
                "}";

        EnumSetContainer target = TestUtil.toObjects(json, null);

        assertThat(target.set1).isNull();
        assertThat(target.set2).isNull();
        assertThat(target.nullSet).isNull();
    }

    @Test
    void testEnumSet_inNestedCollections() {
        // Map<String, List<EnumSet<TestEnum>>>
        Map<String, List<EnumSet<TestEnum>>> source = new LinkedHashMap<>();

        List<EnumSet<TestEnum>> list1 = Arrays.asList(
                EnumSet.of(TestEnum.A),
                EnumSet.of(TestEnum.B, TestEnum.C)
        );

        List<EnumSet<TestEnum>> list2 = Arrays.asList(
                EnumSet.of(TestEnum.D),
                EnumSet.noneOf(TestEnum.class)
        );

        source.put("first", list1);
        source.put("second", list2);

        String json = TestUtil.toJson(source);

        Map<String, List<EnumSet<TestEnum>>> target = TestUtil.toObjects(json, null);

        assertThat(target)
                .hasSize(2)
                .satisfies(map -> {
                    List<EnumSet<TestEnum>> firstList = map.get("first");
                    assertThat(firstList.get(0)).containsExactly(TestEnum.A);
                    assertThat(firstList.get(1)).containsExactly(TestEnum.B, TestEnum.C);

                    List<EnumSet<TestEnum>> secondList = map.get("second");
                    assertThat(secondList.get(0)).containsExactly(TestEnum.D);
                    assertThat(secondList.get(1)).isEmpty();
                });
    }

    private static class EnumSetContainer {
        private EnumSet<TestEnum> set1;
        private EnumSet<TestEnum> set2;
        private EnumSet<TestEnum> nullSet;
    }
}