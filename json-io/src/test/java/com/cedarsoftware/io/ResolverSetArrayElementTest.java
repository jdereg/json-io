package com.cedarsoftware.io;

import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ResolverSetArrayElementTest {

    private static class TestResolver extends Resolver {
        TestResolver() {
            super(null, null, null);
        }

        public void callSetArrayElement(Object array, int index, Object element) {
            setArrayElement(array, index, element);
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

    private final TestResolver resolver = new TestResolver();

    @Test
    void testObjectArrayValid() {
        Object[] array = new Object[1];
        resolver.callSetArrayElement(array, 0, "foo");
        assertThat(array[0]).isEqualTo("foo");
    }

    @Test
    void testObjectArrayInvalid() {
        String[] array = new String[1];
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolver.callSetArrayElement(array, 0, 5))
                .withMessageContaining("java.lang.Integer")
                .withMessageContaining("java.lang.String[]");
    }

    @Test
    void testPrimitiveAssignments() {
        int[] ints = new int[1];
        resolver.callSetArrayElement(ints, 0, 7);
        assertThat(ints[0]).isEqualTo(7);

        long[] longs = new long[1];
        resolver.callSetArrayElement(longs, 0, 8L);
        assertThat(longs[0]).isEqualTo(8L);

        double[] doubles = new double[1];
        resolver.callSetArrayElement(doubles, 0, 3.5);
        assertThat(doubles[0]).isEqualTo(3.5);

        boolean[] bools = new boolean[1];
        resolver.callSetArrayElement(bools, 0, true);
        assertThat(bools[0]).isTrue();

        byte[] bytes = new byte[1];
        resolver.callSetArrayElement(bytes, 0, (byte) 2);
        assertThat(bytes[0]).isEqualTo((byte) 2);

        short[] shorts = new short[1];
        resolver.callSetArrayElement(shorts, 0, (short) 3);
        assertThat(shorts[0]).isEqualTo((short) 3);

        float[] floats = new float[1];
        resolver.callSetArrayElement(floats, 0, 1.5f);
        assertThat(floats[0]).isEqualTo(1.5f);
    }

    @Test
    void testCharAssignments() {
        char[] chars = new char[4];
        resolver.callSetArrayElement(chars, 0, null);
        resolver.callSetArrayElement(chars, 1, 'a');
        resolver.callSetArrayElement(chars, 2, "b");
        resolver.callSetArrayElement(chars, 3, 5);
        assertThat(chars[0]).isEqualTo('\0');
        assertThat(chars[1]).isEqualTo('a');
        assertThat(chars[2]).isEqualTo('b');
        assertThat(chars[3]).isEqualTo('\0');
    }

    @Test
    void testInvalidPrimitiveElementType() {
        int[] array = new int[1];
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolver.callSetArrayElement(array, 0, "bad"))
                .withMessageContaining("java.lang.String")
                .withMessageContaining("int[]");
    }

    @Test
    void testNonArrayInputUsesReflection() {
        Object notArray = new Object();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolver.callSetArrayElement(notArray, 0, "x"));
    }
}
