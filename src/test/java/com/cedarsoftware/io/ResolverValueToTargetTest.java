package com.cedarsoftware.io;

import com.cedarsoftware.util.convert.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResolverValueToTargetTest {

    private static class StubResolver extends Resolver {
        StubResolver(ReadOptions opts, ReferenceTracker refs, Converter conv) {
            super(opts, refs, conv);
        }

        public void traverseFields(JsonObject jsonObj) { }
        protected Object readWithFactoryIfExists(Object o, Type t) { return null; }
        protected void traverseCollection(JsonObject jsonObj) { }
        protected void traverseArray(JsonObject jsonObj) { }
        protected Object resolveArray(Type t, List<Object> list) { return null; }
    }

    private StubResolver createResolver(Converter conv) {
        ReadOptions options = new ReadOptionsBuilder().build();
        ReferenceTracker refs = new JsonReader.DefaultReferenceTracker();
        return new StubResolver(options, refs, conv);
    }

    @Test
    void valueToTarget_noType_returnsFalse() {
        Converter conv = mock(Converter.class);
        StubResolver resolver = createResolver(conv);
        JsonObject obj = new JsonObject();

        assertFalse(resolver.valueToTarget(obj));
    }

    @Test
    void valueToTarget_arrayWithNoItems_setsNullTarget() {
        Converter conv = mock(Converter.class);
        when(conv.isSimpleTypeConversionSupported(String.class, String[].class)).thenReturn(true);
        StubResolver resolver = createResolver(conv);

        JsonObject obj = new JsonObject();
        obj.setType(String[].class);

        assertTrue(resolver.valueToTarget(obj));
        assertTrue(obj.isFinished());
        assertNull(obj.getTarget());
    }

    @Test
    void valueToTarget_arrayConversionException_propagates() {
        Converter conv = mock(Converter.class);
        when(conv.isSimpleTypeConversionSupported(Integer.class, Integer[].class)).thenReturn(true);
        Exception e = new Exception("boom");
        when(conv.convert(any(), any())).thenThrow(e);
        StubResolver resolver = createResolver(conv);

        JsonObject obj = new JsonObject();
        obj.setType(Integer[].class);
        obj.setItems(new Object[]{1});

        JsonIoException ex = assertThrows(JsonIoException.class, () -> resolver.valueToTarget(obj));
        assertEquals("boom", ex.getMessage());
        assertArrayEquals(e.getStackTrace(), ex.getStackTrace());
    }

    @Test
    void valueToTarget_unsupportedConversion_returnsFalse() {
        Converter conv = mock(Converter.class);
        when(conv.isSimpleTypeConversionSupported(Integer.class, Integer.class)).thenReturn(false);
        StubResolver resolver = createResolver(conv);

        JsonObject obj = new JsonObject();
        obj.setType(Integer.class);

        assertFalse(resolver.valueToTarget(obj));
    }

    @Test
    void valueToTarget_nonArrayConversionException_propagates() {
        Converter conv = mock(Converter.class);
        when(conv.isSimpleTypeConversionSupported(Long.class, Long.class)).thenReturn(true);
        Exception e = new Exception("err");
        when(conv.convert(any(), any())).thenThrow(e);
        StubResolver resolver = createResolver(conv);

        JsonObject obj = new JsonObject();
        obj.setType(Long.class);

        JsonIoException ex = assertThrows(JsonIoException.class, () -> resolver.valueToTarget(obj));
        assertEquals("err", ex.getMessage());
        assertArrayEquals(e.getStackTrace(), ex.getStackTrace());
    }
}

