package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In Java mode with {@code failOnUnknownType(false)}, an object holding {@code @items} whose class is not on the
 * classpath ({"@type":"com.nowhere.Bag","@items":[1,2,3]}) is read as one with no {@code @type} is: by the type its
 * place declares, an Object[] where none is. A map stood in for its class and held none of its items: an empty map at
 * the root and in an Object field, and an error in a List, Set or array field ("Unsupported conversion, source type
 * [LinkedHashMap ({})]"), in every release.
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
class UnknownClassItemsJavaModeTest {
    public static class Holder {
        public Object any;
        public List<Integer> ints;
        public int[] array;
        public Set<String> set;
        public List<Object> list;
        public Collection<Long> collection;
    }

    private static final ReadOptions LENIENT = new ReadOptionsBuilder().failOnUnknownType(false).build();
    private static final String BAG = "{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,2,3]}";
    private static final String UNTYPED = "{\"@items\":[1,2,3]}";
    private static final String HOLDER = "{\"@type\":\"" + Holder.class.getName() + "\",";

    private static Object read(String json, Class<?> asked) {
        return JsonIo.toJava(json, LENIENT).asClass(asked);
    }

    /** Wherever it stands, it is what the same object with no @type is. */
    @Test
    void readAsAnObjectWithNoType() {
        for (Class<?> asked : new Class<?>[] {null, List.class, Object[].class}) {
            Object unknown = read(BAG, asked);
            assertTrue(DeepEquals.deepEquals(read(UNTYPED, asked), unknown), String.valueOf(asked));
        }
        for (String field : new String[] {"any", "ints", "array", "set", "list", "collection"}) {
            Holder unknown = (Holder) read(HOLDER + "\"" + field + "\":" + BAG + "}", Holder.class);
            Holder untyped = (Holder) read(HOLDER + "\"" + field + "\":" + UNTYPED + "}", Holder.class);
            assertTrue(DeepEquals.deepEquals(untyped, unknown), field);
        }
        Holder inAList = (Holder) read(HOLDER + "\"list\":[" + BAG + "]}", Holder.class);
        assertArrayEquals(new Object[] {1L, 2L, 3L}, (Object[]) inAList.list.get(0));
    }

    @Test
    void itsItemsAreThere() {
        assertArrayEquals(new Object[] {1L, 2L, 3L}, (Object[]) read(BAG, null));
        Holder holder = (Holder) read(HOLDER + "\"ints\":" + BAG + ",\"array\":" + BAG + "}", Holder.class);
        assertEquals(3, holder.ints.size());
        assertInstanceOf(Integer.class, holder.ints.get(0));
        assertArrayEquals(new int[] {1, 2, 3}, holder.array);
    }

    /** Strict about unknown types, it is the error; a known map class, or an unknown class with fields, as before. */
    @Test
    void whatIsReadAsBefore() {
        assertThrows(JsonIoException.class, () -> JsonIo.toJava(BAG, null).asClass(null));
        Map<?, ?> fields = (Map<?, ?>) read("{\"@type\":\"com.nowhere.Bag\",\"size\":3}", null);
        assertEquals(3L, fields.get("size"));
        assertInstanceOf(LinkedHashMap.class, read("{\"@type\":\"java.util.LinkedHashMap\",\"@items\":[1]}", null));
        ReadOptions toMap = new ReadOptionsBuilder().failOnUnknownType(false).unknownTypeClass(LinkedHashMap.class).build();
        assertArrayEquals(new Object[] {1L, 2L, 3L}, (Object[]) JsonIo.toJava(BAG, toMap).asClass(null));
        // a stand-in class of the caller's that holds items is what it is read as
        ReadOptions toList = new ReadOptionsBuilder().failOnUnknownType(false).unknownTypeClass(ArrayList.class).build();
        assertEquals(new ArrayList<>(Arrays.asList(1L, 2L, 3L)), JsonIo.toJava(BAG, toList).asClass(null));
        // Maps mode keeps it the JSON it is
        assertEquals(BAG, JsonIo.toJson(JsonIo.toMaps(BAG, null).asClass(null), null));
    }
}
