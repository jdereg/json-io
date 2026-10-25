package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * In Maps mode a {@code @ref} inside a typed collection ({"@type":"ArrayList","@items":[{"@ref":2}]}) is the object
 * it names, as it is in a plain array and in a field. The tree kept the object holding the @ref, an empty map, so the
 * tree written back held {} in its place and the object it named lost its @id; a typed collection read at the root as
 * a List held a null.
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
class MapsModeRefsInTypedCollectionsTest {
    private static Map<?, ?> maps(String json) {
        return (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
    }

    private static Object first(Object typedCollection) {
        return ((JsonObject) typedCollection).getItems()[0];
    }

    @Test
    void aRefIsTheObjectItNames() {
        String json = "{\"a\":{\"@id\":2,\"x\":1},\"l\":{\"@type\":\"ArrayList\",\"@items\":[{\"@ref\":2}]}}";
        Map<?, ?> root = maps(json);
        assertSame(root.get("a"), first(root.get("l")));
        assertEquals(json, JsonIo.toJson(root, null));      // the @ref was written {}, the @id dropped
    }

    @Test
    void aForwardRefIsTheObjectItNames() {
        Map<?, ?> root = maps("{\"l\":{\"@type\":\"ArrayList\",\"@items\":[{\"@ref\":2}]},\"a\":{\"@id\":2,\"x\":1}}");
        assertSame(root.get("a"), first(root.get("l")));
        assertEquals(1L, ((Map<?, ?>) first(root.get("l"))).get("x"));
    }

    @Test
    void aRefToASharedArray() {
        String json = "{\"field\":{\"@id\":1,\"@type\":\"int[]\",\"@items\":[1,2]},\"list\":{\"@type\":\"ArrayList\",\"@items\":[{\"@ref\":1}]}}";
        Map<?, ?> root = maps(json);
        assertSame(root.get("field"), first(root.get("list")));
        assertEquals(json, JsonIo.toJson(root, null));
    }

    @Test
    void aTypedCollectionAskedForAsAListAtTheRoot() {
        List<?> read = JsonIo.toMaps("{\"@type\":\"ArrayList\",\"@items\":[{\"@id\":1,\"x\":1},{\"@ref\":1}]}", null)
                .asClass(List.class);
        assertEquals(2, read.size());
        assertSame(read.get(0), read.get(1));   // it was a null
        assertEquals(1L, ((Map<?, ?>) read.get(1)).get("x"));
    }

    /** json-io's own text of a map held in a field and in a list beside it, read as maps and written back. */
    @Test
    void jsonIosOwnTextWrittenBackUnchanged() {
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("x", 1L);
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("a", shared);
        holder.put("l", new ArrayList<>(Arrays.asList(shared, "s")));
        String json = JsonIo.toJson(holder, null);
        assertEquals(json, JsonIo.toJson(maps(json), null));
    }
}
