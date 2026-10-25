package com.cedarsoftware.io;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * In Maps mode, {@code asClass(Map.class)} -- and {@code asType} of a {@code Map<String, Object>} or a
 * {@code Map<?, ?>} -- is the tree's own root object, not a copy of it. The copy was a second map to keep in step, and
 * a {@code @ref} to the root inside the tree named the original, so the copy written back held the root twice. A
 * concrete Map class is still a copy into it.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class MapsModeMapRootTest {
    private static final String JSON = "{\"a\":1,\"n\":{\"b\":2}}";

    @Test
    void mapIsTheTreesOwnRoot() {
        Map<String, Object> map = JsonIo.toMaps(JSON, null).asClass(Map.class);
        assertInstanceOf(JsonObject.class, map);
        Map<String, Object> typed = JsonIo.toMaps(JSON, null).asType(new TypeHolder<Map<String, Object>>() {});
        assertInstanceOf(JsonObject.class, typed);
        Map<?, ?> wild = JsonIo.toMaps(JSON, null).asType(new TypeHolder<Map<?, ?>>() {});
        assertInstanceOf(JsonObject.class, wild);
        map.put("a", 5L);
        assertEquals("{\"a\":5,\"n\":{\"b\":2}}", JsonIo.toJson(map, null));
    }

    /** A @ref back to the root names the map returned: written back, the root once (it was written twice). */
    @Test
    void aRefToTheRootIsTheMapReturned() {
        String json = "{\"@id\":1,\"x\":1,\"a\":{\"back\":{\"@ref\":1}}}";
        Map<?, ?> map = JsonIo.toMaps(json, null).asClass(Map.class);
        assertSame(map, ((Map<?, ?>) map.get("a")).get("back"));
        assertEquals(json, JsonIo.toJson(map, null));
    }

    @Test
    void aConcreteMapClassIsACopy() {
        Map<?, ?> copy = JsonIo.toMaps(JSON, null).asClass(LinkedHashMap.class);
        assertSame(LinkedHashMap.class, copy.getClass());
        Map<?, ?> sorted = JsonIo.toMaps("{\"b\":1,\"a\":2}", null).asClass(TreeMap.class);
        assertSame(TreeMap.class, sorted.getClass());
        assertEquals("a", sorted.keySet().iterator().next());
        // a Map whose type arguments ask for more than JSON's values is not the tree's object either
        Object dates = JsonIo.toMaps("{\"d\":\"2024-01-15\"}", null)
                .asType(new TypeHolder<Map<String, java.time.LocalDate>>() {});
        assertFalse(dates instanceof JsonObject);
    }

    /** A root map whose keys are not text is itself too, and can be changed. */
    @Test
    void aMapWithKeysThatAreNotText() {
        String json = "{\"@type\":\"LinkedHashMap\",\"@keys\":[1],\"@items\":[\"a\"]}";
        Map<Object, Object> map = JsonIo.toMaps(json, null).asClass(Map.class);
        assertInstanceOf(JsonObject.class, map);
        map.put(2L, "b");
        Map<?, ?> back = JsonIo.toJava(JsonIo.toJson(map, null), null).asClass(Map.class);
        assertEquals("b", back.get(2L));
        assertFalse(back instanceof JsonObject);
    }
}
