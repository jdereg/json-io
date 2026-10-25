package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Maps mode types every object as Java mode does -- by its {@code @type}, else by the type declared for it: a field's
 * object, the elements of a list or an array, a map's values and keys, a container in a container, a generic class's
 * fields by its type arguments -- so their fields read as Java mode reads them (an int an Integer, a Date a Date).
 * Only the root object's fields were typed: below it, an object's fields kept the JSON's values (a Long, a Long),
 * even one with its own {@code @type}. A typed tree written back is the text it was read from, as before.
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
class MapsModeNestedTypesTest {
    public static class P {
        public int x = 1;
        public Date d = new Date(1700000000000L);
        public P inner;
    }

    public static class Box<T> {
        public T item;
        public List<T> items = new ArrayList<>();
    }

    public static class Holder {
        public P field = new P();
        public Object any = new P();
        public List<P> list = new ArrayList<>(Arrays.asList(new P()));
        public P[] array = {new P()};
        public Map<String, P> values = new LinkedHashMap<>();
        public Map<P, String> keys = new LinkedHashMap<>();
        public List<List<P>> nested = new ArrayList<>();
        public Map<String, List<P>> listsInMap = new LinkedHashMap<>();
        public Box<P> box = new Box<>();

        {
            field.inner = new P();
            values.put("v", new P());
            keys.put(new P(), "k");
            nested.add(new ArrayList<>(Arrays.asList(new P())));
            listsInMap.put("l", new ArrayList<>(Arrays.asList(new P())));
            box.item = new P();
            box.items.add(new P());
        }
    }

    private static void assertTyped(Object o, String where) {
        Map<?, ?> p = (Map<?, ?>) o;
        assertEquals(P.class, ((JsonObject) p).getRawType(), where);
        assertEquals(1, p.get("x"), where);
        assertEquals(new Date(1700000000000L), p.get("d"), where);
    }

    private static Object first(Object container) {
        if (container instanceof Object[]) {
            return ((Object[]) container)[0];
        }
        if (container instanceof List) {
            return ((List<?>) container).get(0);
        }
        return ((JsonObject) container).getItems()[0];
    }

    private static void assertHolder(Map<?, ?> h, String text) {
        assertTyped(h.get("field"), "field " + text);
        assertTyped(((Map<?, ?>) h.get("field")).get("inner"), "field.inner " + text);
        assertTyped(h.get("any"), "any " + text);
        assertTyped(first(h.get("list")), "list " + text);
        assertTyped(first(h.get("array")), "array " + text);
        assertTyped(((Map<?, ?>) h.get("values")).get("v"), "values " + text);
        assertTyped(((Map<?, ?>) h.get("keys")).keySet().iterator().next(), "keys " + text);
        assertTyped(first(first(h.get("nested"))), "nested " + text);
        assertTyped(first(((Map<?, ?>) h.get("listsInMap")).get("l")), "listsInMap " + text);
        Map<?, ?> box = (Map<?, ?>) h.get("box");
        assertTyped(box.get("item"), "box.item " + text);
        assertTyped(first(box.get("items")), "box.items " + text);
    }

    private static final WriteOptions MINIMAL = new WriteOptionsBuilder().showTypeInfoMinimal().build();

    @Test
    void everyObjectIsTyped() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoAlways().build(),
                new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(new Holder(), options);
            assertHolder((Map<?, ?>) JsonIo.toMaps(json, null).asClass(null), json);
            // (TOON's default names no type, its root's neither: Maps mode has no class to go by, as for any text
            // without types)
            String toon = JsonIo.toToon(new Holder(), options == null ? MINIMAL : options);
            assertHolder((Map<?, ?>) JsonIo.fromToonToMaps(toon, null).asClass(null), toon);
        }
    }

    /** A typed tree written back is the text it was read from: its classes declare their values' types. */
    @Test
    void writtenBackAsItWasRead() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build(),
                new WriteOptionsBuilder().writeLongsAsStrings(true).build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(new Holder(), options);
            Object maps = JsonIo.toMaps(json, null).asClass(null);
            assertEquals(json, JsonIo.toJson(maps, options));
        }
    }

    public static class Longs {
        public long big = 5;
        public Object number = 6L;
        public Inner inner = new Inner();
        public Object extra;
    }

    public static class Inner {
        public long more = 7;
    }

    /** writeLongsAsStrings writes back a long field's value as text, and only a long field's. */
    @Test
    void longsWrittenBackAsText() {
        WriteOptions asText = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        String json = JsonIo.toJson(new Longs(), asText);
        assertEquals(json, JsonIo.toJson(JsonIo.toMaps(json, null).asClass(null), asText));
        // an object no class declares: its number (a Long, as the JSON gives it) written as the number it was
        String untyped = json.replace("\"extra\":null", "\"extra\":{\"count\":3}");
        assertEquals(untyped, JsonIo.toJson(JsonIo.toMaps(untyped, null).asClass(null), asText));
    }

    public static class Dated {
        public List<Date> dates = new ArrayList<>(Arrays.asList(new Date(1700000000000L)));
        public java.util.Deque<String> deque = new java.util.LinkedList<>(Arrays.asList("a", null));
    }

    public static class DatedHolder {
        public Dated dated = new Dated();
    }

    /**
     * A typed value in a list ({"@type":"Date","date":..} in a {@code List<Date>}) is its value, as a field's is; a
     * TOON list in a Deque field, a null among its items, stays a list (the Converter's ArrayDeque took no null).
     */
    @Test
    void typedValuesInListsAndListsInDequeFields() {
        WriteOptions always = new WriteOptionsBuilder().showTypeInfoAlways().build();
        Map<?, ?> dated = (Map<?, ?>) ((Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(new DatedHolder(), always), null)
                .asClass(null)).get("dated");
        assertEquals(new Date(1700000000000L), first(dated.get("dates")));
        String toon = "$type: " + DatedHolder.class.getName() + "\ndated:\n  deque[2]: a,null\n";
        dated = (Map<?, ?>) ((Map<?, ?>) JsonIo.fromToonToMaps(toon, null).asClass(null)).get("dated");
        assertEquals(Arrays.asList("a", null), dated.get("deque"));
    }

    public static class Untyped {
        public Box<P> box;
        public Object anyArray;
    }

    /**
     * Text that names few types, as another writer's or one written by hand: a generic class's fields typed by its
     * declared type arguments ({@code Box<P>}'s {@code T item}), an array's elements by its own {@code @type}.
     */
    @Test
    void typeArgumentsAndAnArraysOwnType() {
        String p = "{\"x\":1,\"d\":1700000000000}";
        String json = "{\"@type\":\"" + Untyped.class.getName() + "\",\"box\":{\"item\":" + p + ",\"items\":[" + p
                + "]},\"anyArray\":{\"@type\":\"[L" + P.class.getName() + ";\",\"@items\":[" + p + "]}}";
        Map<?, ?> read = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
        Map<?, ?> box = (Map<?, ?>) read.get("box");
        assertTyped(box.get("item"), "box.item");
        assertTyped(first(box.get("items")), "box.items");
        assertTyped(first(read.get("anyArray")), "anyArray");
    }

    /** Blank text where a class is declared is null below the root too, as Java mode reads it. */
    @Test
    void blankTextIsNullBelowTheRoot() {
        String json = "{\"@type\":\"" + Holder.class.getName() + "\",\"field\":{\"x\":1,\"d\":\"\",\"inner\":\"\"}}";
        Map<?, ?> h = (Map<?, ?>) JsonIo.toMaps(json, null).asClass(null);
        Map<?, ?> field = (Map<?, ?>) h.get("field");
        assertNull(field.get("d"));
        assertNull(field.get("inner"));
        assertSame(Integer.class, field.get("x").getClass());
    }
}
