package com.cedarsoftware.io;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectResolverMethodsTest {

    @Test
    void safeToString_null_returnsNull() throws Exception {
        Method m = ObjectResolver.class.getDeclaredMethod("safeToString", Object.class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, (Object) null);
        assertEquals("null", result);
    }

    @Test
    void safeToString_validObject_returnsToString() throws Exception {
        Method m = ObjectResolver.class.getDeclaredMethod("safeToString", Object.class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, "foo");
        assertEquals("foo", result);
    }

    private static class BadToString {
        @Override
        public String toString() {
            throw new RuntimeException("bad");
        }
    }

    @Test
    void safeToString_whenToStringThrows_returnsClassName() throws Exception {
        Method m = ObjectResolver.class.getDeclaredMethod("safeToString", Object.class);
        m.setAccessible(true);
        BadToString obj = new BadToString();
        String result = (String) m.invoke(null, obj);
        assertEquals(obj.getClass().toString(), result);
    }
}
