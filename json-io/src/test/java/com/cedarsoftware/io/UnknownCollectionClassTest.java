package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * In Maps mode an object holding {@code @items} whose class is not on the classpath ({"@type":"com.nowhere.Bag",
 * "@items":[..]}) is a collection still: written back it holds its items -- it was written as a map's fields, it had
 * none, and its items were lost, in every release -- and asked for as a List or a Set it is a list or a set of them
 * (it was "Return type mismatch .. found LinkedHashMap"; as an array it gave them already). An EnumSet of an enum not
 * on the classpath keeps its names too.
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
class UnknownCollectionClassTest {
    private static final String BAG = "{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,{\"x\":2},3]}";

    @Test
    void writtenBackWithItsItems() {
        for (String json : new String[] {BAG, "{\"b\":" + BAG + "}", "[" + BAG + "]"}) {
            assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null));
        }
        String noTypes = JsonIo.toJson(JsonIo.toMaps("{\"b\":" + BAG + "}", null).asClass(null),
                new WriteOptionsBuilder().showTypeInfoNever().build());
        assertEquals("{\"b\":[1,{\"x\":2},3]}", noTypes);
    }

    @Test
    void askedForAsAListOrASet() {
        List<?> list = JsonIo.toMaps(BAG, null).asClass(List.class);
        assertEquals(3, list.size());
        assertEquals(1L, list.get(0));
        assertEquals(2L, ((Map<?, ?>) list.get(1)).get("x"));
        Set<?> set = JsonIo.toMaps("{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,1,2]}", null).asClass(Set.class);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L, 2L)), set);
        List<Integer> ints = JsonIo.toMaps("{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,2]}", null)
                .asType(new TypeHolder<List<Integer>>() {});
        assertEquals(Arrays.asList(1, 2), ints);
        assertEquals(new ArrayList<>(Arrays.asList(1L, 3L)),
                new ArrayList<>(JsonIo.toMaps("{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,3]}", null).asClass(ArrayList.class)));
        assertArrayEquals(new Object[] {1L, 3L},
                (Object[]) JsonIo.toMaps("{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,3]}", null).asClass(Object[].class));
        // a collection class on the classpath is read as itself, as before
        assertEquals(LinkedList.class, JsonIo.toMaps("{\"@type\":\"java.util.LinkedList\",\"@items\":[1,2]}", null)
                .asClass(List.class).getClass());
    }

    /** An EnumSet of an enum not on the classpath keeps its names, written in the EnumSet form json-io reads. */
    @Test
    void anEnumSetOfAnEnumNotOnTheClasspath() {
        String json = "{\"s\":{\"@type\":\"java.util.EnumSet\",\"@enum\":\"com.nowhere.Color\",\"@items\":[\"RED\",\"BLUE\"]}}";
        assertEquals("{\"s\":{\"@type\":\"com.nowhere.Color\",\"@items\":[\"RED\",\"BLUE\"]}}",
                JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null));
    }
}
