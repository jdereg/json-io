package com.cedarsoftware.io;

import com.cedarsoftware.io.Resolver.DefaultReferenceTracker;
import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
     * !converter.isSimpleTypeConversionSupported(javaType) is true.
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
