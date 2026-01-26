package com.cedarsoftware.io;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.cedarsoftware.util.TypeUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonReaderHandleObjectRootTest {

    @Test
    void treeSetSubstitutionWhenJsonMode() {
        String json = "{\"@type\":\"java.util.TreeSet\",\"@items\":[1,2,3]}";
        ReadOptions read = new ReadOptionsBuilder().returnAsJsonObjects().build();
        Object result = TestUtil.toJava(json, read).asClass(null);
        assertTrue(result instanceof JsonObject);
        JsonObject jo = (JsonObject) result;
        assertTrue(Set.class.isAssignableFrom(TypeUtilities.getRawClass(jo.getType())));
        Set<?> actual = JsonIo.toJava(jo, read).asClass(Set.class);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L, 3L)), actual);
    }

    @Test
    void assignableRootTypeReturnsGraph() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":7}";
        Number number = TestUtil.toJava(json, null).asClass(Number.class);
        assertTrue(number instanceof AtomicInteger);
        assertEquals(7, ((AtomicInteger) number).get());
    }

    @Test
    void convertibleRootTypeReturnsConverted() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":5}";
        Integer value = TestUtil.toJava(json, null).asClass(Integer.class);
        assertEquals(Integer.valueOf(5), value);
    }

    @Test
    void incompatibleRootTypeThrows() {
        String json = "{\"@type\":\"java.util.concurrent.atomic.AtomicInteger\",\"value\":5}";
        assertThrows(JsonIoException.class, () -> TestUtil.toJava(json, null).asClass(List.class));
    }

    /**
     * Test that when resolution of an empty JsonObject in Maps mode returns the JsonObject
     * (equivalent behavior to old nullGraphReturnsOriginalJsonObject test).
     * In Maps mode, untyped objects should return as JsonObject.
     */
    @Test
    void emptyObjectInMapsModeReturnsJsonObject() {
        String json = "{}";
        ReadOptions opts = new ReadOptionsBuilder().returnAsJsonObjects().build();
        Object result = TestUtil.toMaps(json, opts).asClass(null);
        assertTrue(result instanceof JsonObject, "Empty object in Maps mode should return JsonObject");
    }

    /**
     * Test that complex types with @type are properly resolved (equivalent behavior to old
     * simpleGraphReturnedWhenTypeNotSimple test).
     */
    @Test
    void complexTypeWithTargetResolvesCorrectly() {
        // StringBuilder is a complex type that json-io knows how to handle
        String json = "{\"@type\":\"java.lang.StringBuilder\",\"value\":\"hello\"}";
        Object result = TestUtil.toJava(json, null).asClass(null);
        assertTrue(result instanceof StringBuilder);
        assertEquals("hello", result.toString());
    }

    @Test
    void untypedNonPrimitiveReturnsJsonObject() {
        // In Maps mode, when there's no @type and the content is a complex object (not a simple type),
        // the result should be a JsonObject, not a fully resolved Java object.
        // This tests the behavior through the normal flow where MapResolver.reconcileResult handles this.
        String json = "{\"name\":\"test\",\"value\":42}";
        ReadOptions opts = new ReadOptionsBuilder().returnAsJsonObjects().build();
        Object result = TestUtil.toMaps(json, opts).asClass(null);
        assertTrue(result instanceof JsonObject, "Complex objects without @type should be returned as JsonObject in Maps mode, but got: " + result.getClass().getName());
        JsonObject jo = (JsonObject) result;
        assertEquals("test", jo.get("name"));
        assertEquals(42L, jo.get("value"));
    }
}

