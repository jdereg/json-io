package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests covering a variety of generic type scenarios
 */
public class GenericTypesTest {

    // A simple custom class for use in parameterized types.
    public static class CustomClass {
        public int id;
        public String name;

        // No-arg constructor is required for deserialization.
        public CustomClass() {}

        public CustomClass(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CustomClass)) return false;
            CustomClass that = (CustomClass) o;
            return id == that.id && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }
    }

    // A generic wrapper that uses a type variable.
    public static class GenericWrapper<T> {
        public T value;

        public GenericWrapper() {}

        public GenericWrapper(T value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GenericWrapper)) return false;
            GenericWrapper<?> that = (GenericWrapper<?>) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    // A container that uses a complex parameterized type.
    public static class ComplexContainer {
        public Map<String, List<CustomClass>> map;

        public ComplexContainer() {}

        public ComplexContainer(Map<String, List<CustomClass>> map) {
            this.map = map;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ComplexContainer)) return false;
            ComplexContainer that = (ComplexContainer) o;
            return Objects.equals(map, that.map);
        }

        @Override
        public int hashCode() {
            return Objects.hash(map);
        }
    }

    // A container that uses a raw type.
    public static class RawContainer {
        public List list;  // raw type usage

        public RawContainer() {}

        public RawContainer(List list) {
            this.list = list;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RawContainer)) return false;
            RawContainer that = (RawContainer) o;
            return Objects.equals(list, that.list);
        }

        @Override
        public int hashCode() {
            return Objects.hash(list);
        }
    }

    // A nested generic type where the type variable is used in more than one place.
    public static class NestedGeneric<T> {
        public T value;
        public GenericWrapper<T> nested;

        public NestedGeneric() {}

        public NestedGeneric(T value, GenericWrapper<T> nested) {
            this.value = value;
            this.nested = nested;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NestedGeneric)) return false;
            NestedGeneric<?> that = (NestedGeneric<?>) o;
            return Objects.equals(value, that.value) && Objects.equals(nested, that.nested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, nested);
        }
    }

    // A container using a wildcard parameterized type.
    public static class WildcardContainer {
        public List<? extends Number> numbers;

        public WildcardContainer() {}

        public WildcardContainer(List<? extends Number> numbers) {
            this.numbers = numbers;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WildcardContainer)) return false;
            WildcardContainer that = (WildcardContainer) o;
            return Objects.equals(numbers, that.numbers);
        }

        @Override
        public int hashCode() {
            return Objects.hash(numbers);
        }
    }

    /**
     * Test serialization and deserialization of a complex parameterized type.
     */
    @Test
    public void testComplexParameterizedType() {
        CustomClass c1 = new CustomClass(1, "One");
        CustomClass c2 = new CustomClass(2, "Two");
        List<CustomClass> customList = Arrays.asList(c1, c2);
        Map<String, List<CustomClass>> complexMap = new HashMap<>();
        complexMap.put("first", customList);
        ComplexContainer original = new ComplexContainer(complexMap);

        String json = JsonIo.toJson(original, null);
        Object readObj = JsonIo.toObjects(json, null, null);

        assertEquals(original, readObj, "Deserialized ComplexContainer should equal the original");
    }

    /**
     * Test generic type resolution with a simple generic wrapper.
     */
    @Test
    public void testGenericType() {
        GenericWrapper<String> original = new GenericWrapper<>("TestValue");

        String json = JsonIo.toJson(original, null);
        Object readObj = JsonIo.toObjects(json, null, null);

        assertEquals(original, readObj, "Deserialized GenericWrapper<String> should equal the original");
    }

    /**
     * Test serialization and deserialization of a container using raw types.
     */
    @Test
    public void testRawType() {
        List<Integer> intList = Arrays.asList(1, 2, 3);
        RawContainer original = new RawContainer(intList);

        String json = JsonIo.toJson(original, null);
        Object readObj = JsonIo.toObjects(json, null, null);

        assertEquals(original, readObj, "Deserialized RawContainer should equal the original");
    }

    /**
     * Test a nested generic type where the type variable appears in multiple places.
     */
    @Test
    public void testNestedGenericType() {
        NestedGeneric<Integer> original = new NestedGeneric<>(42, new GenericWrapper<>(42));

        String json = JsonIo.toJson(original, null);

        Object readObj = JsonIo.toJava(json, null).asType(new TypeHolder<NestedGeneric<Integer>>() {});
        assertEquals(original, readObj, "Deserialized NestedGeneric<Integer> should equal the original");

        readObj = JsonIo.toObjects(json, null, null);
        assertEquals(original, readObj, "Deserialized NestedGeneric<Integer> should equal the original");
    }

    /**
     * Test a parameterized type that uses wildcards.
     */
    @Test
    public void testWildcardParameterizedType() {
        WildcardContainer original = new WildcardContainer(Arrays.asList(1, 2, 3.5));

        String json = JsonIo.toJson(original, null);

        Object readObj = JsonIo.toObjects(json, null, null);
        assertEquals(original, readObj, "Deserialized WildcardContainer should equal the original");

        readObj = JsonIo.toObjects(json, null, WildcardContainer.class);
        assertEquals(original, readObj, "Deserialized WildcardContainer should equal the original");
    }
}
