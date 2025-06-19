package com.cedarsoftware.io;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonReaderHandleObjectRootTest {

    static class NullReader extends JsonReader {
        NullReader(ReadOptions opts) {
            super(opts);
        }
        @Override
        protected <T> T resolveObjects(JsonObject rootObj, Type rootType) {
            return null;
        }
        Object invokeHandle(Type type, JsonObject obj) throws Exception {
            Method m = JsonReader.class.getDeclaredMethod("handleObjectRoot", Type.class, JsonObject.class);
            m.setAccessible(true);
            return m.invoke(this, type, obj);
        }
    }

    static class IdentityReader extends JsonReader {
        IdentityReader(ReadOptions opts) { super(opts); }
        @SuppressWarnings("unchecked")
        @Override
        protected <T> T resolveObjects(JsonObject rootObj, Type rootType) {
            return (T) rootObj.getTarget();
        }
        Object invokeHandle(Type type, JsonObject obj) throws Exception {
            Method m = JsonReader.class.getDeclaredMethod("handleObjectRoot", Type.class, JsonObject.class);
            m.setAccessible(true);
            return m.invoke(this, type, obj);
        }
    }

    @Test
    void treeSetSubstitutionWhenJsonMode() {
        String json = "{\"@type\":\"java.util.TreeSet\",\"@items\":[1,2,3]}";
        ReadOptions read = new ReadOptionsBuilder().returnAsJsonObjects().build();
        Object result = TestUtil.toObjects(json, read, null);
        assertTrue(result instanceof JsonObject);
        JsonObject jo = (JsonObject) result;
        assertEquals(Set.class, jo.getType());
        Set<?> actual = JsonIo.toJava(jo, read).asClass(Set.class);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L, 3L)), actual);
    }

    @Test
    void assignableRootTypeReturnsGraph() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":7}";
        Number number = TestUtil.toObjects(json, Number.class);
        assertTrue(number instanceof AtomicInteger);
        assertEquals(7, ((AtomicInteger) number).get());
    }

    @Test
    void convertibleRootTypeReturnsConverted() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":5}";
        Integer value = TestUtil.toObjects(json, Integer.class);
        assertEquals(Integer.valueOf(5), value);
    }

    @Test
    void incompatibleRootTypeThrows() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":5}";
        assertThrows(ClassCastException.class, () -> TestUtil.toObjects(json, List.class));
    }

    @Test
    void nullGraphReturnsOriginalJsonObject() throws Exception {
        ReadOptions opts = new ReadOptionsBuilder().returnAsJsonObjects().build();
        NullReader reader = new NullReader(opts);
        JsonObject obj = new JsonObject();
        obj.setType(String.class);
        Object result = reader.invokeHandle(null, obj);
        assertSame(obj, result);
    }

    @Test
    void simpleGraphReturnedWhenTypeNotSimple() throws Exception {
        ReadOptions opts = new ReadOptionsBuilder().returnAsJsonObjects().build();
        IdentityReader reader = new IdentityReader(opts);
        JsonObject obj = new JsonObject();
        obj.setTarget("hello");
        obj.setType(Object.class);
        Object result = reader.invokeHandle(null, obj);
        assertEquals("hello", result);
    }

    @Test
    void untypedNonPrimitiveReturnsJsonObject() throws Exception {
        ReadOptions opts = new ReadOptionsBuilder().returnAsJsonObjects().build();
        IdentityReader reader = new IdentityReader(opts);
        JsonObject obj = new JsonObject();
        obj.setTarget(new Object());
        obj.clear();
        Object result = reader.invokeHandle(null, obj);
        assertSame(obj, result);
    }
}

