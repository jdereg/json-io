package com.cedarsoftware.io;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Maps mode a map whose keys are not text -- json-io writes it as {"@keys":[..],"@items":[..]} -- can be changed:
 * a value replaced, an entry added or removed, and written back it holds the change. Every release from 4.102.0
 * refused put and remove (a JsonIoException); before that put lost the value or scrambled the keys.
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
class MapsModeKeyedMapEditTest {
    private static final String JSON = "{\"m\":{\"@type\":\"LinkedHashMap\",\"@keys\":[1,2],\"@items\":[\"a\",\"b\"]}}";

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> keyed(Map<?, ?> root) {
        return (Map<Object, Object>) root.get("m");
    }

    private static Map<?, ?> read(String json) {
        return (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
    }

    /** What Java mode reads from the text the edited tree is written as. */
    private static Map<?, ?> writtenBack(Map<?, ?> root) {
        return (Map<?, ?>) JsonIo.toJava(JsonIo.toJson(root, null), null).asClass(Map.class).get("m");
    }

    private static Map<Object, Object> expected(Object... keysAndValues) {
        Map<Object, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    void aValueReplaced() {
        Map<?, ?> root = read(JSON);
        assertEquals("a", keyed(root).put(1L, "A"));
        assertEquals("A", keyed(root).get(1L));
        assertEquals(expected(1L, "A", 2L, "b"), writtenBack(root));
    }

    @Test
    void anEntryAddedAndOneRemoved() {
        Map<?, ?> root = read(JSON);
        assertNull(keyed(root).put(3L, "c"));
        assertEquals("b", keyed(root).remove(2L));
        assertNull(keyed(root).remove(9L));
        assertEquals(2, keyed(root).size());
        assertEquals(expected(1L, "a", 3L, "c"), writtenBack(root));
    }

    /** A key that is an object: replaced through an equal map, as any map finds it. */
    @Test
    void aKeyThatIsAnObject() {
        Map<?, ?> root = read("{\"m\":{\"@type\":\"LinkedHashMap\",\"@keys\":[{\"x\":1}],\"@items\":[\"a\"]}}");
        keyed(root).put(Collections.singletonMap("x", 1L), "z");
        assertEquals(1, keyed(root).size());
        assertEquals("z", keyed(root).values().iterator().next());
    }

    @Test
    void clearedThenFilled() {
        Map<?, ?> root = read(JSON);
        keyed(root).clear();
        assertTrue(keyed(root).isEmpty());
        keyed(root).putAll(expected(5L, "e"));
        assertEquals(expected(5L, "e"), writtenBack(root));
    }
}
