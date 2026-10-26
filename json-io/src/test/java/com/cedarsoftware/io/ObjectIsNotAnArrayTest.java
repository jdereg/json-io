package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JSON object holding fields, read as a list, a set or an array, is an error saying so, and where. It was an empty
 * one -- an array of numbers or text a null -- its fields lost without a word, at the root and in a field alike. An
 * empty object, one holding only its {@code @id}, or one whose {@code @type} names a collection, is read as before.
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
class ObjectIsNotAnArrayTest {
    public static class Line {
        public int qty;
    }

    public static class Holder {
        public List<Line> lines;
        public Set<String> tags;
        public Collection<Integer> nums;
        public int[] sizes;
        public String[] names;
        public Object[] objects;
        public Line[] arr;
    }

    private static void assertRefused(Class<?> asked, String typeName) {
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"a\":1}", null).asClass(asked));
        assertTrue(e.getMessage().startsWith("Expecting a JSON array for " + typeName + ", found a JSON object"),
                e.getMessage());
    }

    @Test
    void atTheRoot() {
        assertRefused(List.class, ArrayList.class.getName());
        assertRefused(Set.class, LinkedHashSet.class.getName());
        assertRefused(Collection.class, ArrayList.class.getName());
        assertRefused(Object[].class, "java.lang.Object[]");
        assertRefused(int[].class, "int[]");
        assertRefused(String[].class, "java.lang.String[]");
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"qty\":1}", null).asType(new TypeHolder<List<Line>>() {}));
    }

    @Test
    void inAFieldWhereItStands() {
        String[][] cases = {{"lines", "{\"qty\":1}"}, {"tags", "{\"x\":1}"}, {"nums", "{\"x\":1}"}, {"sizes", "{\"x\":1}"},
                {"names", "{\"x\":\"y\"}"}, {"objects", "{\"x\":1}"}, {"arr", "{\"qty\":1}"}};
        for (String[] c : cases) {
            JsonIoException e = assertThrows(JsonIoException.class,
                    () -> JsonIo.toJava("{\"" + c[0] + "\":" + c[1] + "}", null).asClass(Holder.class));
            String firstLine = e.getMessage().split("\n")[0];
            assertTrue(firstLine.contains("Expecting a JSON array for "), e.getMessage());
            assertTrue(firstLine.endsWith("(at $." + c[0] + ")"), e.getMessage());
        }
    }

    /** Maps mode refuses it at the root too; an empty object there is the empty list. */
    @Test
    void mapsModeAtTheRoot() {
        assertThrows(JsonIoException.class, () -> JsonIo.toMaps("{\"a\":1}", null).asClass(List.class));
        assertEquals(new ArrayList<>(), JsonIo.toMaps("{}", null).asClass(List.class));
    }

    /** An empty object, one holding only its @id, one whose @type names a collection, and json-io's own text read. */
    @Test
    void whatIsReadAsBefore() {
        assertEquals(new ArrayList<>(), JsonIo.toJava("{}", null).asClass(List.class));
        assertEquals(new ArrayList<>(), JsonIo.toJava("{\"@type\":\"ArrayList\"}", null).asClass(List.class));
        assertNull(JsonIo.toJava("{}", null).asClass(int[].class));
        assertThrows(JsonIoException.class, () -> JsonIo.toJava("{\"@type\":\"ArrayList\",\"x\":1}", null).asClass(List.class));
        Holder empty = JsonIo.toJava("{\"lines\":{},\"tags\":{\"@type\":\"java.util.LinkedHashSet\"},\"nums\":{\"@id\":3}}", null)
                .asClass(Holder.class);
        assertEquals(0, empty.lines.size());
        assertEquals(0, empty.tags.size());
        assertEquals(0, empty.nums.size());
        Holder written = new Holder();
        written.lines = new ArrayList<>();
        written.tags = new LinkedHashSet<>();
        written.sizes = new int[0];
        written.names = new String[0];
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().showTypeInfoNever().build(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build()}) {
            Holder back = JsonIo.toJava(JsonIo.toJson(written, options), null).asClass(Holder.class);
            assertEquals(0, back.lines.size());
            assertArrayEquals(new int[0], back.sizes);
        }
    }
}
