package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Character in a Map or a Collection, written with {@code showTypeInfoNever()}, is JSON text: {"v":"x"}. It was
 * written as its toString() -- {"v":x}, [x] -- which is not JSON, and json-io could not read it back ("Unknown JSON
 * value type"), in every release. A quote, a backslash or a control character is escaped, as in any text.
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
class CharacterWithoutTypeInfoTest {
    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();

    @Test
    void inAMapAndInACollection() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("v", 'x');
        assertEquals("{\"v\":\"x\"}", JsonIo.toJson(map, NEVER));
        assertEquals("[\"x\",\"y\"]", JsonIo.toJson(new ArrayList<>(Arrays.asList('x', 'y')), NEVER));
        assertEquals("[\"x\"]", JsonIo.toJson(new LinkedHashSet<>(Collections.singletonList('x')), NEVER));
        JsonObject tree = JsonIo.toMaps("{\"a\":1}", null).asClass(JsonObject.class);
        tree.put("v", 'x');
        assertEquals("{\"a\":1,\"v\":\"x\"}", JsonIo.toJson(tree, NEVER));
        Map<String, Object> back = JsonIo.toMaps(JsonIo.toJson(map, NEVER), null).asClass(Map.class);
        assertEquals("x", back.get("v"));
    }

    @Test
    void escapedAsText() {
        List<Character> special = new ArrayList<>(Arrays.asList('"', '\\', '\n', '\u0001'));
        String json = JsonIo.toJson(special, NEVER);
        assertEquals("[\"\\\"\",\"\\\\\",\"\\n\",\"\\u0001\"]", json);
        Object[] back = JsonIo.toMaps(json, null).asClass(Object[].class);
        assertEquals(Arrays.asList("\"", "\\", "\n", "\u0001"), Arrays.asList(back));
    }

    /** With type info, as before: the Character's own type. */
    @Test
    void withTypeInfoAsBefore() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("v", 'x');
        String json = JsonIo.toJson(map, null);
        assertEquals("{\"@type\":\"LinkedHashMap\",\"v\":{\"@type\":\"Character\",\"value\":\"x\"}}", json);
        assertEquals('x', ((Map<?, ?>) JsonIo.toJava(json, null).asClass(Map.class)).get("v"));
    }
}
