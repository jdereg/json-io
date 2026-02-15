package com.cedarsoftware.io;

import com.cedarsoftware.io.Resolver.DefaultReferenceTracker;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolverValueToTargetTest {

    private static class TestResolver extends Resolver {
        TestResolver(ReadOptions options) {
            super(options, new DefaultReferenceTracker(options), new Converter(options.getConverterOptions()));
        }

        boolean callValueToTarget(JsonObject obj) {
            return valueToTarget(obj);
        }

        Object callToJava(Type type, Object value) {
            return toJava(type, value);
        }

        @Override
        public void traverseFields(JsonObject jsonObj) {
        }

        @Override
        protected Object readWithFactoryIfExists(Object o, Type compType) {
            return null;
        }

        @Override
        protected void traverseCollection(JsonObject jsonObj) {
        }

        @Override
        protected void traverseArray(JsonObject jsonObj) {
        }

        @Override
        protected void traverseMap(JsonObject jsonObj) {
        }

        @Override
        protected Object resolveArray(Type suggestedType, List<Object> list) {
            return null;
        }
    }

    private final TestResolver resolver = new TestResolver(new ReadOptionsBuilder().build());

    @Test
    void nullTypeReturnsFalse() {
        JsonObject obj = new JsonObject();
        assertThat(resolver.callValueToTarget(obj)).isFalse();
    }

    @Test
    void convertsPrimitiveArray() {
        JsonObject nested = new JsonObject();
        nested.setType(int.class);
        nested.setValue("3");

        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        arrayObj.setItems(new Object[]{1, "2", nested});

        assertThat(resolver.callValueToTarget(arrayObj)).isTrue();
        assertThat(arrayObj.getTarget()).isInstanceOf(int[].class);
        assertThat((int[]) arrayObj.getTarget()).containsExactly(1, 2, 3);
    }

    @Test
    void conversionFailureWrapsException() {
        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        arrayObj.setItems(new Object[]{"bad"});

        assertThatThrownBy(() -> resolver.callValueToTarget(arrayObj))
                .isInstanceOf(JsonIoException.class);
    }

    @Test
    void conversionFailurePreservesCause() {
        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        arrayObj.setItems(new Object[]{"bad"});

        assertThatThrownBy(() -> resolver.callValueToTarget(arrayObj))
                .isInstanceOf(JsonIoException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    /**
     * Tests that Resolver.toJava() converts a primitive array (int[]) to a different
     * target primitive array type (long[]) when the Converter supports the conversion.
     * This exercises line 342 in Resolver.java: return converter.convert(value, targetClass);
     */
    @Test
    void toJava_convertsPrimitiveArrayToDifferentPrimitiveArrayType() {
        int[] intArray = new int[]{1, 2, 3};

        // Request conversion to long[] - should use Converter to convert int[] to long[]
        Object result = resolver.callToJava(long[].class, intArray);

        assertThat(result).isInstanceOf(long[].class);
        assertThat((long[]) result).containsExactly(1L, 2L, 3L);
    }

    /**
     * Tests that Resolver.toJava() returns the original primitive array when
     * no type conversion is needed (target type matches source type).
     * This exercises line 345 in Resolver.java: return value;
     */
    @Test
    void toJava_returnsPrimitiveArrayUnchangedWhenTypesMatch() {
        int[] intArray = new int[]{4, 5, 6};

        // Request same type - should return original array unchanged
        Object result = resolver.callToJava(int[].class, intArray);

        assertThat(result).isSameAs(intArray);
        assertThat((int[]) result).containsExactly(4, 5, 6);
    }

    /**
     * Tests that Resolver.toJava() returns the original primitive array when
     * no target type is specified (type is null).
     * This exercises line 345 in Resolver.java: return value;
     */
    @Test
    void toJava_returnsPrimitiveArrayWhenTypeIsNull() {
        int[] intArray = new int[]{7, 8, 9};

        // No type specified - should return original array
        Object result = resolver.callToJava(null, intArray);

        assertThat(result).isSameAs(intArray);
        assertThat((int[]) result).containsExactly(7, 8, 9);
    }

    /**
     * Tests that Resolver.toJava() converts a Collection (List) to an array type.
     * This conversion is handled by convertToType() via Converter.
     */
    @Test
    void toJava_convertsCollectionToArray() {
        List<Integer> list = new ArrayList<>(Arrays.asList(10, 20, 30));

        // Request conversion to int[] - should use Converter
        Object result = resolver.callToJava(int[].class, list);

        assertThat(result).isInstanceOf(int[].class);
        assertThat((int[]) result).containsExactly(10, 20, 30);
    }

    /**
     * Tests that Resolver.toJava() converts an array to a Collection type.
     */
    @Test
    void toJava_convertsArrayToCollection() {
        int[] intArray = new int[]{100, 200, 300};

        // Request conversion to List - should use Converter
        Object result = resolver.callToJava(List.class, intArray);

        assertThat(result).isInstanceOf(List.class);
        List<?> resultList = (List<?>) result;
        assertThat(resultList).hasSize(3);
        assertThat(resultList.get(0)).isEqualTo(100);
        assertThat(resultList.get(1)).isEqualTo(200);
        assertThat(resultList.get(2)).isEqualTo(300);
    }

    /**
     * Tests that valueToTarget handles empty arrays (null items).
     * This exercises lines 1255-1257 in Resolver.java where jsonItems == null.
     */
    @Test
    void valueToTarget_handlesEmptyArray() {
        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(int[].class);
        // Don't set items - they will be null (empty array case)

        assertThat(resolver.callValueToTarget(arrayObj)).isTrue();
        assertThat(arrayObj.getTarget()).isNull();
        assertThat(arrayObj.isFinished()).isTrue();
    }

    /**
     * Tests that valueToTarget returns false for non-simple types.
     * This exercises lines 1282-1283 in Resolver.java where
     * !Resolver.isPseudoPrimitive(javaType) is true.
     */
    @Test
    void valueToTarget_returnsFalseForNonSimpleType() {
        // Use a custom class that Converter doesn't support for simple conversion
        JsonObject obj = new JsonObject();
        obj.setType(Thread.class);  // Thread is not a simple type

        assertThat(resolver.callValueToTarget(obj)).isFalse();
    }

    /**
     * Tests that valueToTarget handles non-primitive arrays (like String[]).
     * This exercises the else branch at line 1272: typedArray[i] = converted
     */
    @Test
    void valueToTarget_convertsNonPrimitiveArray() {
        JsonObject arrayObj = new JsonObject();
        arrayObj.setType(String[].class);
        arrayObj.setItems(new Object[]{"hello", "world", "test"});

        assertThat(resolver.callValueToTarget(arrayObj)).isTrue();
        assertThat(arrayObj.getTarget()).isInstanceOf(String[].class);
        assertThat((String[]) arrayObj.getTarget()).containsExactly("hello", "world", "test");
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_arrayList() {
        ArrayList<Object> source = new ArrayList<>();
        source.add(miniJson("a", 1));
        source.add(miniJson("b", 2));

        Type targetType = new TypeHolder<ArrayList<Mini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        assertThat(result).isInstanceOf(ArrayList.class);
        Collection<?> out = (Collection<?>) result;
        assertThat(out).hasSize(2);
        assertThat(out.iterator().next()).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_linkedList() {
        LinkedList<Object> source = new LinkedList<>();
        source.add(miniJson("a", 1));
        source.add(miniJson("b", 2));

        Type targetType = new TypeHolder<LinkedList<Mini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        assertThat(result).isInstanceOf(LinkedList.class);
        Collection<?> out = (Collection<?>) result;
        assertThat(out).hasSize(2);
        assertThat(out.iterator().next()).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_linkedHashSet() {
        LinkedHashSet<Object> source = new LinkedHashSet<>();
        source.add(miniJson("a", 1));
        source.add(miniJson("b", 2));

        Type targetType = new TypeHolder<LinkedHashSet<Mini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        assertThat(result).isInstanceOf(LinkedHashSet.class);
        Collection<?> out = (Collection<?>) result;
        assertThat(out).hasSize(2);
        assertThat(out.iterator().next()).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_hashSet() {
        HashSet<Object> source = new HashSet<>();
        source.add(miniJson("a", 1));
        source.add(miniJson("b", 2));

        Type targetType = new TypeHolder<HashSet<Mini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        assertThat(result).isInstanceOf(HashSet.class);
        Collection<?> out = (Collection<?>) result;
        assertThat(out).hasSize(2);
        assertThat(out.iterator().next()).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_treeSet() {
        TreeSet<Object> source = new TreeSet<>((a, b) -> {
            JsonObject x = (JsonObject) a;
            JsonObject y = (JsonObject) b;
            Integer rx = (Integer) x.get("rank");
            Integer ry = (Integer) y.get("rank");
            return Integer.compare(rx, ry);
        });
        source.add(orderedMiniJson("x", 2));
        source.add(orderedMiniJson("y", 1));

        Type targetType = new TypeHolder<TreeSet<OrderedMini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        assertThat(result).isInstanceOf(TreeSet.class);
        TreeSet<?> out = (TreeSet<?>) result;
        assertThat(out).isNotEmpty();
        assertThat(out).allMatch(OrderedMini.class::isInstance);
    }

    @Test
    void toJava_collectionBranch_createSameTypeCollection_defaultArrayList() {
        Vector<Object> source = new Vector<>();
        source.add(miniJson("a", 1));
        source.add(miniJson("b", 2));

        Type targetType = new TypeHolder<Vector<Mini>>() {}.getType();
        Object result = resolver.callToJava(targetType, source);

        // Default branch in createSameTypeCollection() is ArrayList for unknown collection types.
        assertThat(result).isInstanceOf(ArrayList.class);
        Collection<?> out = (Collection<?>) result;
        assertThat(out).hasSize(2);
        assertThat(out.iterator().next()).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_mapBranch_createSameTypeMap_linkedHashMapAndMapInterface() {
        JsonObject source = mapWithMiniValues();

        Object asLinked = resolver.callToJava(new TypeHolder<LinkedHashMap<String, Mini>>() {}.getType(), source);
        assertThat(asLinked).isInstanceOf(LinkedHashMap.class);
        assertThat(((Map<?, ?>) asLinked).get("k1")).isInstanceOf(Mini.class);

        Object asMap = resolver.callToJava(new TypeHolder<Map<String, Mini>>() {}.getType(), source);
        assertThat(asMap).isInstanceOf(LinkedHashMap.class);
        assertThat(((Map<?, ?>) asMap).get("k2")).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_mapBranch_createSameTypeMap_hashMap() {
        JsonObject source = mapWithMiniValues();
        Object result = resolver.callToJava(new TypeHolder<HashMap<String, Mini>>() {}.getType(), source);

        assertThat(result).isInstanceOf(HashMap.class);
        assertThat(((Map<?, ?>) result).get("k1")).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_mapBranch_createSameTypeMap_treeMap() {
        JsonObject source = mapWithMiniValues();
        Object result = resolver.callToJava(new TypeHolder<TreeMap<String, Mini>>() {}.getType(), source);

        assertThat(result).isInstanceOf(TreeMap.class);
        assertThat(((Map<?, ?>) result).get("k1")).isInstanceOf(Mini.class);
    }

    @Test
    void toJava_mapBranch_createSameTypeMap_defaultLinkedHashMap() {
        JsonObject source = mapWithMiniValues();
        Object result = resolver.callToJava(new TypeHolder<ConcurrentHashMap<String, Mini>>() {}.getType(), source);

        // Default branch in createSameTypeMap() uses LinkedHashMap for non-explicit target classes.
        assertThat(result).isInstanceOf(LinkedHashMap.class);
        assertThat(((Map<?, ?>) result).get("k2")).isInstanceOf(Mini.class);
    }

    private static JsonObject miniJson(String name, int score) {
        JsonObject j = new JsonObject();
        j.put("name", name);
        j.put("score", score);
        return j;
    }

    private static JsonObject orderedMiniJson(String name, int rank) {
        JsonObject j = new JsonObject();
        j.put("name", name);
        j.put("rank", rank);
        return j;
    }

    private static JsonObject mapWithMiniValues() {
        JsonObject source = new JsonObject();
        source.put("k1", miniJson("a", 1));
        source.put("k2", miniJson("b", 2));
        return source;
    }

    public static class Mini {
        public String name;
        public int score;
    }

    public static class OrderedMini implements Comparable<OrderedMini> {
        public String name;
        public int rank;

        @Override
        public int compareTo(OrderedMini o) {
            String left = this.name == null ? "" : this.name;
            String right = o.name == null ? "" : o.name;
            return left.compareTo(right);
        }
    }

    // Common interface for testing lenient mode
    interface Vehicle {
        String getName();
    }

    // Custom class implementing Vehicle
    static class Car implements Vehicle {
        private final String name;
        Car(String name) { this.name = name; }
        @Override
        public String getName() { return name; }
    }

    // Different class implementing same interface
    static class Motorcycle implements Vehicle {
        private final String name;
        Motorcycle(String name) { this.name = name; }
        @Override
        public String getName() { return name; }
    }

    /**
     * Tests that Resolver.toJava() allows lenient type conversion when
     * complex objects share a meaningful common ancestor (interface).
     * This exercises lines 647-655 in Resolver.convertToType() where
     * findLowestCommonSupertypesExcluding finds a shared interface.
     */
    @Test
    void toJava_lenientModeAcceptsCommonAncestor() {
        // Create a Car instance
        Car car = new Car("Tesla");

        // Request conversion to Motorcycle.class - they share Vehicle interface
        // The converter won't support Car→Motorcycle, but they share Vehicle
        Object result = resolver.callToJava(Motorcycle.class, car);

        // Lenient mode should accept the Car since it shares Vehicle with Motorcycle
        assertThat(result).isSameAs(car);
        assertThat(result).isInstanceOf(Vehicle.class);
    }
}
