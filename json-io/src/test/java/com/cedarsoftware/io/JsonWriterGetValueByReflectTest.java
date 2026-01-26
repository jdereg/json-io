package com.cedarsoftware.io;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.cedarsoftware.util.FastByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonWriterGetValueByReflectTest {

    static class PublicHolder {
        public String text = "hello";
    }

    static class PrivateHolder {
        private int number = 42;
    }

    static class StaticHolder {
        public static final String CONST = "static";
    }

    @Test
    void testReadPublicField() throws Exception {
        JsonWriter writer = new JsonWriter(new FastByteArrayOutputStream());
        Method m = JsonWriter.class.getDeclaredMethod("getValueByReflect", Object.class, Field.class);
        m.setAccessible(true);

        PublicHolder holder = new PublicHolder();
        Field f = PublicHolder.class.getField("text");
        Object result = m.invoke(writer, holder, f);
        assertEquals("hello", result);
    }

    @Test
    void testReadPrivateField_returnsNull() throws Exception {
        JsonWriter writer = new JsonWriter(new FastByteArrayOutputStream());
        Method m = JsonWriter.class.getDeclaredMethod("getValueByReflect", Object.class, Field.class);
        m.setAccessible(true);

        PrivateHolder holder = new PrivateHolder();
        Field f = PrivateHolder.class.getDeclaredField("number");
        Object result = m.invoke(writer, holder, f);
        assertNull(result);
    }

    @Test
    void testReadStaticFieldWithNullObject() throws Exception {
        JsonWriter writer = new JsonWriter(new FastByteArrayOutputStream());
        Method m = JsonWriter.class.getDeclaredMethod("getValueByReflect", Object.class, Field.class);
        m.setAccessible(true);

        Field f = StaticHolder.class.getDeclaredField("CONST");
        Object result = m.invoke(writer, null, f);
        assertEquals("static", result);
    }
}
