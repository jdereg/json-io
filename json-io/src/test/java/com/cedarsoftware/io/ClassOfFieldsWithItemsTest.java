package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An object whose {@code @type} is a class of fields (no collection, array or map) but which holds {@code @items} --
 * JSON json-io writes for no such class -- failed with a ClassCastException in both modes ("Line cannot be cast to
 * class java.util.Collection"), in every release. Java mode now says what is wrong, and where; Maps mode leaves it the
 * JSON it is, as it leaves such an object of a class not on the classpath.
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
class ClassOfFieldsWithItemsTest {
    public static class Line {
        public int qty;
    }

    public static class Holder {
        public Object any;
        public List<Object> list;
        public Object[] array;
    }

    private static final String LINE = "{\"@type\":\"" + Line.class.getName()
            + "\",\"@items\":[1,{\"x\":{\"@type\":\"Integer\",\"value\":5}},3]}";

    @Test
    void javaModeSaysWhatIsWrongAndWhere() {
        String expected = "Expecting a JSON object for " + Line.class.getName() + ", found a JSON array (@items)";
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(LINE, null).asClass(null));
        assertEquals(expected, e.getMessage().split("\n")[0]);
        String holder = "{\"@type\":\"" + Holder.class.getName() + "\",";
        String[][] places = {{"any", LINE}, {"list", "[" + LINE + "]"}, {"array", "[" + LINE + "]"}};
        String[] where = {"$.any", "$.list[0]", "$.array[0]"};
        for (int i = 0; i < places.length; i++) {
            String json = holder + "\"" + places[i][0] + "\":" + places[i][1] + "}";
            e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(Holder.class));
            assertEquals(expected + " (at " + where[i] + ")", e.getMessage().split("\n")[0]);
        }
    }

    @Test
    void mapsModeLeavesItTheJson() {
        for (String json : new String[] {LINE, "{\"x\":" + LINE + "}", "[" + LINE + "]", "{\"x\":[" + LINE + "]}"}) {
            assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null));
        }
        JsonObject root = JsonIo.toMaps(LINE, null).asClass(JsonObject.class);
        assertEquals(Line.class.getName(), root.getTypeString());
        assertEquals(3, root.getItems().length);
        List<?> items = JsonIo.toMaps(LINE, null).asClass(List.class);
        assertEquals(3, items.size());
        assertEquals(1L, items.get(0));
        // asked for as its class, it is built as Java mode builds it: the error; and so read from the tree
        assertThrows(JsonIoException.class, () -> JsonIo.toMaps(LINE, null).asClass(Line.class));
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(root, null).asClass(null));
        assertTrue(e.getMessage().startsWith("Expecting a JSON object for " + Line.class.getName()), e.getMessage());
        // a value class holding @items, as malformed, is the JSON too
        for (String value : new String[] {"{\"x\":{\"@type\":\"Integer\",\"@items\":[1]}}",
                "[{\"@type\":\"Integer\",\"@items\":[1]}]", "{\"x\":{\"@type\":\"LocalDate\",\"@items\":[1]}}"}) {
            assertEquals(value, JsonIo.toJson(JsonIo.toMaps(value, null).asClass(null), null));
        }
    }

    /** An object holding @items with no @type is the array it holds, as before. */
    @Test
    void untypedItemsAsBefore() {
        for (String json : new String[] {"{\"@items\":[1,2]}", "{\"x\":{\"@items\":[1,2]}}", "[{\"@items\":[1,2]}]"}) {
            Object tree = JsonIo.toMaps(json, null).asClass(null);
            assertEquals(JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), null), JsonIo.toJson(tree, null));
        }
        Object[] root = (Object[]) JsonIo.toMaps("{\"@items\":[1,2]}", null).asClass(null);
        assertEquals(2, root.length);
    }

    /** A collection class holding @items, and an object of the class holding fields, are read as before. */
    @Test
    void whatIsReadAsBefore() {
        String list = "{\"@type\":\"java.util.LinkedList\",\"@items\":[1,2]}";
        assertInstanceOf(LinkedList.class, JsonIo.toJava(list, null).asClass(null));
        assertEquals(new ArrayList<>(Arrays.asList(1L, 2L)), new ArrayList<>(JsonIo.toMaps(list, null).asClass(List.class)));
        Line line = JsonIo.toJava("{\"@type\":\"" + Line.class.getName() + "\",\"qty\":4}", null).asClass(Line.class);
        assertEquals(4, line.qty);
        String fields = "{\"@type\":\"" + Line.class.getName() + "\",\"qty\":4}";
        assertEquals(fields, JsonIo.toJson(JsonIo.toMaps(fields, null).asClass(null), null));
        assertTrue(JsonIo.toJson(JsonIo.toMaps(list, null).asClass(null), null).contains("[1,2]"));
    }
}
