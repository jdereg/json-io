package com.cedarsoftware.io;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.cedarsoftware.io.models.UniversityFixture;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-product read-path matrix focused on resolver paths:
 * toJava(), toMaps(), and toMaps()->mutate->toJava(JsonObject,...).
 */
class JsonIoReadPathMatrixTest {
    private static final ReadOptions READ_OPTIONS = new ReadOptionsBuilder().build();
    private static final WriteOptions WRITE_NO_TYPES = new WriteOptionsBuilder().showTypeInfoNever().build();

    @Test
    void scalarSentinels_viaMapWrapper_acrossDirectAndMapRoundTrip() {
        assertScalarWrapperRoundTrip("{\"_v\":123}", "_v", Integer.class, 123);
        assertScalarWrapperRoundTrip("{\"_v\":123}", "_v", Long.class, 123L);
        assertScalarWrapperRoundTrip("{\"_v\":3.5}", "_v", Double.class, 3.5d);
        assertScalarWrapperRoundTrip("{\"_v\":\"5f85c2d6-f41d-45f5-adc2-98f005fca865\"}", "_v", UUID.class,
                UUID.fromString("5f85c2d6-f41d-45f5-adc2-98f005fca865"));
        assertScalarWrapperRoundTrip("{\"_v\":\"2025-09-01T09:00:00Z\"}", "_v", ZonedDateTime.class,
                ZonedDateTime.parse("2025-09-01T09:00:00Z"));
        assertScalarWrapperRoundTrip("{\"_v\":\"101001\"}", "_v", BitSet.class, BitSet.valueOf(new long[]{0b101001L}));
    }

    @Test
    void rootRetargeting_arrayCollectionAndMapVariants() {
        Integer[] asIntegerArray = JsonIo.toJava("[1,2,3]", READ_OPTIONS).asClass(Integer[].class);
        assertArrayEquals(new Integer[]{1, 2, 3}, asIntegerArray);

        List<?> asList = JsonIo.toJava("[1,2,3]", READ_OPTIONS).asClass(List.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), asList);

        Collection<?> asCollection = JsonIo.toJava("[1,2,3]", READ_OPTIONS).asClass(Collection.class);
        assertEquals(Arrays.asList(1L, 2L, 3L), new ArrayList<>(asCollection));

        Map<?, ?> concreteMap = JsonIo.toJava("{\"a\":1,\"b\":2}", READ_OPTIONS).asClass(LinkedHashMap.class);
        assertEquals(2, concreteMap.size());
        assertEquals(1L, concreteMap.get("a"));
        assertEquals(2L, concreteMap.get("b"));
    }

    @Test
    void nestedArrayCollectionNuances_includePrimitiveArrays() {
        String json = "{\"mixed\":[[1,2,3],[\"a\",\"b\"],{\"k\":9}],\"primitive\":[1,2,3],\"deep\":[[1,null],[2,3]]}";

        JsonObject mapGraph = toJsonObjectGraph(json);
        assertNotNull(mapGraph);

        @SuppressWarnings("unchecked")
        Map<Object, Object> mixedMap = (Map<Object, Object>) mapGraph;
        Object[] mixed = (Object[]) mixedMap.get("mixed");
        assertEquals(3, mixed.length);
        assertInstanceOf(Object[].class, mixed[0]);
        assertInstanceOf(Object[].class, mixed[1]);
        assertInstanceOf(Map.class, mixed[2]);

        Object[] primitive = (Object[]) mixedMap.get("primitive");
        assertArrayEquals(new Object[]{1L, 2L, 3L}, primitive);

        Object[] deep = (Object[]) mixedMap.get("deep");
        assertEquals(2, deep.length);
        assertInstanceOf(Object[].class, deep[0]);
        assertInstanceOf(Object[].class, deep[1]);
    }

    @Test
    void genericMapKeyAndValueMaterialization_acrossPaths() {
        UniversityFixture.University university = UniversityFixture.createSampleUniversity();
        university.advisorAssignments = null;
        university.oddMapKeys = null;
        String json = JsonIo.toJson(university, WRITE_NO_TYPES);

        UniversityFixture.University direct = JsonIo.toJava(json, READ_OPTIONS).asClass(UniversityFixture.University.class);
        assertNotNull(direct.departmentsByCode);
        assertEquals(2, direct.honorsStudents.size());

        JsonObject mapGraph = toJsonObjectGraph(json);

        UniversityFixture.University fromMapGraph = JsonIo.toJava(mapGraph, READ_OPTIONS).asClass(UniversityFixture.University.class);
        assertNotNull(fromMapGraph);
        assertEquals("North Ridge University", fromMapGraph.name);
        assertEquals(2, fromMapGraph.honorsStudents.size());

        UniversityFixture.University universityWithKeys = UniversityFixture.createSampleUniversity();
        Type targetType = new TypeHolder<Map<UniversityFixture.PersonKey, UniversityFixture.Student>>() {}.getType();
        String keyMapJson = JsonIo.toJson(universityWithKeys.advisorAssignments, null);
        Map<UniversityFixture.PersonKey, UniversityFixture.Student> typedMap = JsonIo.toJava(keyMapJson, READ_OPTIONS)
                .asType(new TypeHolder<Map<UniversityFixture.PersonKey, UniversityFixture.Student>>() {});
        assertEquals(2, typedMap.size());
        typedMap.forEach((k, v) -> {
            assertInstanceOf(UniversityFixture.PersonKey.class, k);
            assertInstanceOf(UniversityFixture.Student.class, v);
        });
        assertNotNull(targetType);
    }

    @Test
    void university_toMaps_mutate_then_toJavaJsonObject_materializesFinalType() {
        UniversityFixture.University original = UniversityFixture.createSampleUniversity();
        original.advisorAssignments = null;
        original.oddMapKeys = null;
        String json = JsonIo.toJson(original, WRITE_NO_TYPES);

        UniversityFixture.University baseline = JsonIo.toJava(json, READ_OPTIONS).asClass(UniversityFixture.University.class);
        assertInstanceOf(UniversityFixture.University.class, baseline);

        JsonObject graph = toJsonObjectGraph(json);
        mutateUniversityGraph(graph);

        UniversityFixture.University mutated = JsonIo.toJava(graph, READ_OPTIONS).asClass(UniversityFixture.University.class);
        assertEquals("North Ridge University - Updated", mutated.name);
        assertEquals(99, mutated.honorsStudents.get(0).age);

        // Ensure we changed what we intended and kept the rest coherent.
        assertFalse(DeepEquals.deepEquals(original, mutated));
    }

    @Test
    void mapAsMapKey_singleOddCase_isStable() {
        Map<Object, String> m = new LinkedHashMap<>();
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("bucket", "x");
        key.put("rank", 1);
        m.put(key, "present");

        String json = JsonIo.toJson(m, null);
        Map<?, ?> restored = JsonIo.toJava(json, READ_OPTIONS).asClass(Map.class);
        assertEquals(1, restored.size());
        Object restoredKey = restored.keySet().iterator().next();
        assertInstanceOf(Map.class, restoredKey);
        assertEquals("present", restored.values().iterator().next());

        Object mapsMode = JsonIo.toMaps(json, READ_OPTIONS).asClass(Map.class);
        assertNotNull(mapsMode);
    }

    @Test
    void toMapsOutput_canBePassedToToJavaJsonObject_forSimpleCase() {
        String json = "{\"name\":\"mini\",\"score\":5}";
        JsonObject graph = JsonIo.toMaps(json, READ_OPTIONS).asClass(JsonObject.class);
        assertNotNull(graph);
        graph.put("score", 8L);

        MiniResult result = JsonIo.toJava(graph, READ_OPTIONS).asClass(MiniResult.class);
        assertEquals("mini", result.name);
        assertEquals(8, result.score);
    }

    @Test
    void scalarWrapper_valueKey_alsoRoundTrips() {
        assertScalarWrapperRoundTrip("{\"value\":77}", "value", Integer.class, 77);
        assertScalarWrapperRoundTrip("{\"value\":\"f5c68f10-2f38-4294-9af5-4a40cd0b5e74\"}", "value", UUID.class,
                UUID.fromString("f5c68f10-2f38-4294-9af5-4a40cd0b5e74"));
    }

    @Test
    void typedMapValueInference_directAndMapGraph() {
        String json = "{\"u1\":{\"id\":\"s1\",\"name\":\"Ann\",\"age\":20,\"gpa\":3.2,\"scores\":[90,91]},\"u2\":{\"id\":\"s2\",\"name\":\"Ben\",\"age\":21,\"gpa\":3.8,\"scores\":[92,93]}}";
        TypeHolder<Map<String, UniversityFixture.Student>> holder = new TypeHolder<Map<String, UniversityFixture.Student>>() {};

        Map<String, UniversityFixture.Student> direct = JsonIo.toJava(json, READ_OPTIONS).asType(holder);
        assertEquals(2, direct.size());
        assertInstanceOf(UniversityFixture.Student.class, direct.get("u1"));
        assertEquals("Ann", direct.get("u1").name);
        assertEquals(20, direct.get("u1").age);

        JsonObject graph = toJsonObjectGraph(json);
        Map<String, UniversityFixture.Student> fromMapGraph = JsonIo.toJava(graph, READ_OPTIONS).asType(holder);
        assertEquals(2, fromMapGraph.size());
        assertInstanceOf(UniversityFixture.Student.class, fromMapGraph.get("u2"));
        assertEquals(21, fromMapGraph.get("u2").age);
    }

    @Test
    void typedListInference_directAndMapGraph() {
        String json = "{\"students\":[{\"id\":\"s1\",\"name\":\"Ann\",\"age\":20,\"gpa\":3.2,\"scores\":[90,91]},{\"id\":\"s2\",\"name\":\"Ben\",\"age\":21,\"gpa\":3.8,\"scores\":[92,93]}]}";

        StudentListHolder direct = JsonIo.toJava(json, READ_OPTIONS).asClass(StudentListHolder.class);
        assertEquals(2, direct.students.size());
        assertInstanceOf(UniversityFixture.Student.class, direct.students.get(0));
        assertEquals("Ben", direct.students.get(1).name);

        JsonObject graph = JsonIo.toMaps(json, READ_OPTIONS).asClass(JsonObject.class);
        StudentListHolder fromMapGraph = JsonIo.toJava(graph, READ_OPTIONS).asClass(StudentListHolder.class);
        assertEquals(2, fromMapGraph.students.size());
        assertEquals(20, fromMapGraph.students.get(0).age);
    }

    @Test
    void rootRetargeting_treeMap_and_primitiveArrays() {
        Map<?, ?> treeMap = JsonIo.toJava("{\"b\":2,\"a\":1}", READ_OPTIONS).asClass(TreeMap.class);
        assertEquals(1L, treeMap.get("a"));
        assertEquals(2L, treeMap.get("b"));

        int[] ints = JsonIo.toJava("[1,2,3]", READ_OPTIONS).asClass(int[].class);
        assertArrayEquals(new int[]{1, 2, 3}, ints);

        long[] longs = JsonIo.toJava("[1,2,3]", READ_OPTIONS).asClass(long[].class);
        assertArrayEquals(new long[]{1L, 2L, 3L}, longs);

        double[] doubles = JsonIo.toJava("[1.5,2.5]", READ_OPTIONS).asClass(double[].class);
        assertArrayEquals(new double[]{1.5d, 2.5d}, doubles);
    }

    @Test
    void arrayRoot_toMaps_mutate_then_toJavaTypedArray() {
        String json = "[{\"name\":\"mini\",\"score\":5},{\"name\":\"two\",\"score\":6}]";
        Object[] maps = JsonIo.toMaps(json, READ_OPTIONS).asClass(Object[].class);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) maps[0];
        first.put("score", 11L);

        String mutatedJson = JsonIo.toJson(maps, WRITE_NO_TYPES);
        MiniResult[] results = JsonIo.toJava(mutatedJson, READ_OPTIONS).asClass(MiniResult[].class);
        assertEquals(2, results.length);
        assertEquals(11, results[0].score);
        assertEquals("two", results[1].name);
    }

    @Test
    void mixedContainment_objectArrayAndCollectionVariants() {
        String json = "{\"arr\":[[1,2],{\"k\":3},[\"x\",\"y\"]],\"list\":[[4,5],{\"m\":6},[\"z\"]]}";
        MixedContainer mixed = JsonIo.toJava(json, READ_OPTIONS).asClass(MixedContainer.class);
        assertNotNull(mixed.arr);
        assertNotNull(mixed.list);
        assertEquals(3, mixed.arr.length);
        assertEquals(3, mixed.list.size());
        assertInstanceOf(Object[].class, mixed.arr[0]);
        assertInstanceOf(Map.class, mixed.arr[1]);
        assertInstanceOf(Object[].class, mixed.arr[2]);
        assertInstanceOf(Object[].class, mixed.list.get(0));
        assertInstanceOf(Map.class, mixed.list.get(1));
    }

    @Test
    void toMaps_unknownType_tolerant_and_reusableAsMap() {
        String json = "{\"@type\":\"com.example.DoesNotExist\",\"name\":\"x\",\"n\":1}";
        Map<?, ?> maps = JsonIo.toMaps(json, READ_OPTIONS).asClass(Map.class);
        assertEquals("x", maps.get("name"));
        assertEquals(1L, maps.get("n"));
    }

    @Test
    void university_baseline_roundTrip_withoutTypeMetadata() {
        UniversityFixture.University u = UniversityFixture.createSampleUniversity();
        u.advisorAssignments = null;
        u.oddMapKeys = null;
        String json = JsonIo.toJson(u, WRITE_NO_TYPES);
        UniversityFixture.University read = JsonIo.toJava(json, READ_OPTIONS).asClass(UniversityFixture.University.class);
        assertEquals(u.name, read.name);
        assertEquals(u.honorsStudents.size(), read.honorsStudents.size());
        assertEquals(u.departmentsByCode.size(), read.departmentsByCode.size());
    }

    @Test
    void scalarBooleanAndString_roundTripViaMaps() {
        assertScalarWrapperRoundTrip("{\"_v\":true}", "_v", Boolean.class, true);
        assertScalarWrapperRoundTrip("{\"_v\":\"hello\"}", "_v", String.class, "hello");
    }

    @Test
    void scalarBigDecimal_roundTripViaMaps() {
        assertScalarWrapperRoundTrip("{\"_v\":\"123.45\"}", "_v", java.math.BigDecimal.class, new java.math.BigDecimal("123.45"));
    }

    @Test
    void rootRetargeting_objectArray() {
        Object[] values = JsonIo.toJava("[1,\"x\",true,null]", READ_OPTIONS).asClass(Object[].class);
        assertEquals(4, values.length);
        assertEquals(1L, values[0]);
        assertEquals("x", values[1]);
        assertEquals(true, values[2]);
        assertEquals(null, values[3]);
    }

    @Test
    void toMaps_arrayRoot_asObjectArray() {
        Object[] values = JsonIo.toMaps("[1,2,3]", READ_OPTIONS).asClass(Object[].class);
        assertArrayEquals(new Object[]{1L, 2L, 3L}, values);
    }

    @Test
    void toMaps_objectRoot_asMap() {
        Map<?, ?> map = JsonIo.toMaps("{\"a\":1,\"b\":{\"c\":2}}", READ_OPTIONS).asClass(Map.class);
        assertEquals(2, map.size());
        assertEquals(1L, map.get("a"));
        assertInstanceOf(Map.class, map.get("b"));
    }

    @Test
    void toMaps_then_toJavaJsonObject_forTypedMapTarget() {
        JsonObject graph = JsonIo.toMaps("{\"a\":1,\"b\":2}", READ_OPTIONS).asClass(JsonObject.class);
        Map<String, Long> typed = JsonIo.toJava(graph, READ_OPTIONS).asType(new TypeHolder<Map<String, Long>>() {});
        assertEquals(2, typed.size());
        assertEquals(1L, typed.get("a"));
        assertEquals(2L, typed.get("b"));
    }

    @Test
    void nestedPrimitiveArrays_directTypedRead() {
        PrimitiveArrayHolder holder = JsonIo.toJava("{\"ints\":[1,2],\"longs\":[3,4],\"doubles\":[1.5,2.5]}", READ_OPTIONS)
                .asClass(PrimitiveArrayHolder.class);
        assertArrayEquals(new int[]{1, 2}, holder.ints);
        assertArrayEquals(new long[]{3L, 4L}, holder.longs);
        assertArrayEquals(new double[]{1.5d, 2.5d}, holder.doubles);
    }

    @Test
    void mapValuePojoInference_withTypeHolder() {
        String json = "{\"k1\":{\"name\":\"mini\",\"score\":1},\"k2\":{\"name\":\"max\",\"score\":2}}";
        Map<String, MiniResult> typed = JsonIo.toJava(json, READ_OPTIONS).asType(new TypeHolder<Map<String, MiniResult>>() {});
        assertEquals(2, typed.size());
        assertEquals(1, typed.get("k1").score);
        assertEquals("max", typed.get("k2").name);
    }

    @Test
    void listOfPojoInference_withTypeHolder() {
        String json = "{\"items\":[{\"name\":\"mini\",\"score\":1},{\"name\":\"max\",\"score\":2}]}";
        MiniListHolder typed = JsonIo.toJava(json, READ_OPTIONS).asClass(MiniListHolder.class);
        assertEquals(2, typed.items.size());
        assertEquals("mini", typed.items.get(0).name);
        assertEquals(2, typed.items.get(1).score);
    }

    @Test
    void twoPhase_editAndConvert_forTypedMapOfPojo() {
        String json = "{\"k1\":{\"name\":\"mini\",\"score\":1}}";
        JsonObject graph = JsonIo.toMaps(json, READ_OPTIONS).asClass(JsonObject.class);
        @SuppressWarnings("unchecked")
        Map<Object, Object> root = (Map<Object, Object>) graph;
        @SuppressWarnings("unchecked")
        Map<String, Object> k1 = (Map<String, Object>) root.get("k1");
        k1.put("score", 7L);

        Map<String, MiniResult> typed = JsonIo.toJava(graph, READ_OPTIONS).asType(new TypeHolder<Map<String, MiniResult>>() {});
        assertEquals(7, typed.get("k1").score);
    }

    private static <T> void assertScalarWrapperRoundTrip(String json, String key, Class<T> targetType, T expected) {
        T direct = JsonIo.toJava(json, READ_OPTIONS).asClass(targetType);
        assertEquals(expected, direct);

        @SuppressWarnings("unchecked")
        Map<Object, Object> mapGraph = (Map<Object, Object>) JsonIo.toMaps(json, READ_OPTIONS).asClass(Map.class);
        assertTrue(mapGraph.containsKey(key));
        assertEquals(expected, JsonIo.toJava(JsonIo.toJson(mapGraph, WRITE_NO_TYPES), READ_OPTIONS).asClass(targetType));
    }

    private static JsonObject toJsonObjectGraph(String json) {
        return JsonIo.toMaps(json, READ_OPTIONS).asClass(JsonObject.class);
    }

    @SuppressWarnings("unchecked")
    private static void mutateUniversityGraph(JsonObject graph) {
        Map<Object, Object> root = (Map<Object, Object>) graph;
        root.put("name", "North Ridge University - Updated");

        Object honorsObject = root.get("honorsStudents");
        Object[] honors;
        if (honorsObject instanceof JsonObject) {
            honors = ((JsonObject) honorsObject).getItems();
        } else {
            honors = (Object[]) honorsObject;
        }
        Map<String, Object> firstStudent = (Map<String, Object>) honors[0];
        firstStudent.put("age", 99L);

        Object[] mixed = (Object[]) root.get("mixedArtifacts");
        Map<String, Object> mapInsideMixed = (Map<String, Object>) mixed[4];
        mapInsideMixed.put("inner", "patched");
    }

    public static class MiniResult {
        public String name;
        public int score;
    }

    public static class MixedContainer {
        public Object[] arr;
        public List<Object> list;
    }

    public static class PrimitiveArrayHolder {
        public int[] ints;
        public long[] longs;
        public double[] doubles;
    }

    public static class MiniListHolder {
        public List<MiniResult> items;
    }

    public static class StudentListHolder {
        public List<UniversityFixture.Student> students;
    }
}
