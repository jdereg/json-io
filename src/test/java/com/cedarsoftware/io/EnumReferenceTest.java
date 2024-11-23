package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;
import java.util.*;

import static com.cedarsoftware.io.JsonValue.ID;
import static com.cedarsoftware.io.JsonValue.REF;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify enum reference behavior - ensuring enums are not referenced
 * and proper instance behavior is maintained.
 */
class EnumReferenceTest {
    private enum TestEnum {
        FOO, BAR, BAZ
    }

    @Test
    void testEnum_whenSameEnumInArrayTwice_doesNotUseReferences() {
        TestEnum[] source = new TestEnum[] { TestEnum.FOO, TestEnum.FOO };
        String json = TestUtil.toJson(source);

        // Verify no @id/@ref in JSON
        assertThat(json).doesNotContain(ID);
        assertThat(json).doesNotContain(REF);

        TestEnum[] target = TestUtil.toObjects(json, null);
        assertThat(target).hasSize(2);
        assertThat(target[0]).isEqualTo(TestEnum.FOO);
        assertThat(target[1]).isEqualTo(TestEnum.FOO);

        // Verify enum identity is preserved (same instance)
        assertThat(target[0]).isSameAs(target[1]);
    }

    @Test
    void testEnum_whenUsedInMultipleCollections_maintainsIdentity() {
        // Create various collections containing same enum instance
        TestEnum testEnum = TestEnum.BAR;
        List<TestEnum> list = Arrays.asList(testEnum);
        Set<TestEnum> set = new HashSet<>(Collections.singleton(testEnum));
        Map<String, TestEnum> map = new HashMap<>();
        map.put("test", testEnum);

        // Create container class
        EnumContainer source = new EnumContainer(list, set, map);

        String json = TestUtil.toJson(source);
        // Verify no references used
        assertThat(json).doesNotContain(ID);
        assertThat(json).doesNotContain(REF);

        EnumContainer target = TestUtil.toObjects(json, null);

        // Verify contents
        assertThat(target.list.get(0)).isEqualTo(TestEnum.BAR);
        assertThat(target.set.iterator().next()).isEqualTo(TestEnum.BAR);
        assertThat(target.map.get("test")).isEqualTo(TestEnum.BAR);

        // Verify all are same instance
        TestEnum listEnum = target.list.get(0);
        TestEnum setEnum = target.set.iterator().next();
        TestEnum mapEnum = target.map.get("test");

        assertThat(listEnum).isSameAs(setEnum);
        assertThat(setEnum).isSameAs(mapEnum);
    }

    @Test
    void testEnum_inComplexObject_maintainsIdentityWithoutReferences() {
        ComplexObject source = new ComplexObject();
        source.directEnum = TestEnum.BAZ;
        source.enumArray = new TestEnum[] { TestEnum.BAZ };
        source.enumList = Collections.singletonList(TestEnum.BAZ);
        source.enumInMap = Collections.singletonMap("key", TestEnum.BAZ);

        String json = TestUtil.toJson(source);
        assertThat(json).doesNotContain(ID);
        assertThat(json).doesNotContain(REF);

        ComplexObject target = TestUtil.toObjects(json, null);

        // Verify all enum instances are equal
        assertThat(target.directEnum).isEqualTo(TestEnum.BAZ);
        assertThat(target.enumArray[0]).isEqualTo(TestEnum.BAZ);
        assertThat(target.enumList.get(0)).isEqualTo(TestEnum.BAZ);
        assertThat(target.enumInMap.get("key")).isEqualTo(TestEnum.BAZ);

        // Verify all are same instance
        assertThat(target.directEnum).isSameAs(target.enumArray[0]);
        assertThat(target.enumArray[0]).isSameAs(target.enumList.get(0));
        assertThat(target.enumList.get(0)).isSameAs(target.enumInMap.get("key"));
    }

    // Test support classes
    private static class EnumContainer {
        private final List<TestEnum> list;
        private final Set<TestEnum> set;
        private final Map<String, TestEnum> map;

        private EnumContainer(List<TestEnum> list, Set<TestEnum> set, Map<String, TestEnum> map) {
            this.list = list;
            this.set = set;
            this.map = map;
        }
    }

    private static class ComplexObject {
        private TestEnum directEnum;
        private TestEnum[] enumArray;
        private List<TestEnum> enumList;
        private Map<String, TestEnum> enumInMap;
    }
}