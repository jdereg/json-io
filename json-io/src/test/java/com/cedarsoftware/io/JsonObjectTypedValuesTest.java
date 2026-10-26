package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.cedarsoftware.util.convert.Converter;
import com.cedarsoftware.util.convert.DefaultConverterOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JsonObject's typed getters -- {@code get(key, Class)}, {@code get(key, TypeHolder)} and their default-value
 * forms -- give a value as the type the caller asks for, converted as {@code Converter.convert(get(key), type)}
 * converts it; and {@code putAsJson(key, value)}, their mirror, puts a Java value as plain JSON holds it, so the tree
 * writes back as plain JSON. A Maps-mode tree holds JSON's own values (a Long, a Double, text), so a cast
 * ({@code (Integer) m.get("qty")}) failed and a {@code put} of a ZonedDateTime wrote {@code {"@type":..}} where the
 * payload had text.
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
class JsonObjectTypedValuesTest {
    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String ORDER = "{\"qty\":3,\"price\":4.5,\"shipped\":\"2026-01-15\",\"id\":\"" + ID + "\","
            + "\"rush\":\"yes\",\"due\":[\"2026-02-01\",\"2026-03-01\"],\"counts\":{\"a\":\"1\",\"b\":2},\"none\":null,"
            + "\"address\":{\"city\":\"Cincinnati\"}}";

    enum Color { RED, GREEN }

    public static class Person {
        public String name = "Ann";
        public int age = 41;
        public Date born = new Date(0);
    }

    private static JsonObject order() {
        return JsonIo.toMaps(ORDER, null).asClass(JsonObject.class);
    }

    @Test
    void getAsAClass() {
        JsonObject order = order();
        assertEquals(3, order.get("qty", int.class));
        assertEquals(Integer.valueOf(3), order.get("qty", Integer.class));
        assertEquals(4.5f, order.get("price", float.class));
        assertEquals(new BigDecimal("4.5"), order.get("price", BigDecimal.class));
        assertEquals(LocalDate.of(2026, 1, 15), order.get("shipped", LocalDate.class));
        assertEquals(UUID.fromString(ID), order.get("id", UUID.class));
        assertEquals(true, order.get("rush", boolean.class));
        assertEquals("3", order.get("qty", String.class));
        // a value that is already one is the value itself
        JsonObject address = order.get("address", JsonObject.class);
        assertSame(order.get("address"), address);
        assertSame(order.get("shipped"), order.get("shipped", String.class));
        // absent, or null: as null converts
        assertEquals(0, order.get("missing", int.class));
        assertNull(order.get("missing", Integer.class));
        assertNull(order.get("none", LocalDate.class));
        assertThrows(IllegalArgumentException.class, () -> order.get("shipped", int.class));
        assertThrows(IllegalArgumentException.class, () -> order.get("qty", (Class<?>) null));
    }

    /** The same answer as Converter.convert(get(key), type), the call it replaces. */
    @Test
    void asTheConverterConvertsIt() {
        JsonObject order = order();
        Converter converter = new Converter(new DefaultConverterOptions());
        Class<?>[] types = {int.class, long.class, Integer.class, double.class, String.class, BigInteger.class,
                AtomicInteger.class, boolean.class};
        for (String key : new String[] {"qty", "price", "rush", "missing"}) {
            for (Class<?> type : types) {
                Object expected;
                try {
                    expected = converter.convert(order.get(key), type);
                } catch (IllegalArgumentException e) {
                    assertThrows(IllegalArgumentException.class, () -> order.get(key, type), key + " " + type);
                    continue;
                }
                Object actual = order.get(key, type);
                assertEquals(String.valueOf(expected), String.valueOf(actual), key + " as " + type);
                assertEquals(expected == null ? null : expected.getClass(), actual == null ? null : actual.getClass());
            }
        }
    }

    @Test
    void getWithADefault() {
        JsonObject order = order();
        assertEquals(100, order.get("batchSize", int.class, 100));
        assertEquals(100, order.get("none", int.class, 100));
        assertEquals(3, order.get("qty", int.class, 100));
        LocalDate today = LocalDate.of(2026, 10, 25);
        assertEquals(today, order.get("none", LocalDate.class, today));
        List<LocalDate> fallback = Collections.emptyList();
        assertSame(fallback, order.get("missing", new TypeHolder<List<LocalDate>>() {}, fallback));
        assertEquals(2, order.get("due", new TypeHolder<List<LocalDate>>() {}, fallback).size());
    }

    @Test
    void getAsAGenericType() {
        JsonObject order = order();
        List<LocalDate> due = order.get("due", new TypeHolder<List<LocalDate>>() {});
        assertEquals(Arrays.asList(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), due);
        Map<String, Integer> counts = order.get("counts", new TypeHolder<Map<String, Integer>>() {});
        assertEquals(1, counts.get("a"));
        assertEquals(2, counts.get("b"));
        assertEquals(Optional.of(UUID.fromString(ID)), order.get("id", new TypeHolder<Optional<UUID>>() {}));
        assertEquals(Optional.empty(), order.get("none", new TypeHolder<Optional<UUID>>() {}));
        assertEquals(3L, (long) order.get("qty", new TypeHolder<Long>() {}));
        assertThrows(IllegalArgumentException.class, () -> order.get("qty", (TypeHolder<?>) null));
    }

    /** The JSON form of each kind of value, and the text the tree then writes: plain JSON, no @type anywhere. */
    @Test
    void putAsJsonPutsPlainJson() {
        JsonObject tree = JsonIo.toMaps("{\"a\":1}", null).asClass(JsonObject.class);
        Map<Object, Object> codes = new LinkedHashMap<>();
        codes.put(7, Color.RED);
        codes.put("x", 'c');
        Object[][] cases = {
                {5, 5L}, {(short) 5, 5L}, {new AtomicInteger(5), 5L}, {1.1f, 1.1d}, {new BigDecimal("1.10"), 1.1d},
                {new BigInteger("5"), 5L}, {'c', "c"}, {Color.GREEN, "GREEN"}, {new AtomicBoolean(true), true},
                {LocalDate.of(2026, 1, 15), "2026-01-15"}, {UUID.fromString(ID), ID},
                {ZonedDateTime.parse("2026-01-15T10:00:00Z"), "2026-01-15T10:00:00Z"},
                {new Date(0), "1970-01-01T00:00:00.000Z"}, {Optional.of(3), 3L}, {Optional.empty(), null},
                {new StringBuilder("sb"), "sb"}, {"text", "text"}, {null, null}, {true, true}, {7L, 7L}, {2.5d, 2.5d}};
        for (Object[] c : cases) {
            tree.putAsJson("v", c[0]);
            assertEquals(c[1], tree.get("v"), String.valueOf(c[0]));
            assertEquals(c[1] == null ? null : c[1].getClass(), tree.get("v") == null ? null : tree.get("v").getClass());
        }
        tree.putAsJson("list", Arrays.asList(1, "a", LocalDate.of(2026, 1, 15)));
        assertArrayEquals(new Object[] {1L, "a", "2026-01-15"}, (Object[]) tree.get("list"));
        tree.putAsJson("ints", new int[] {1, 2});
        assertArrayEquals(new Object[] {1L, 2L}, (Object[]) tree.get("ints"));
        tree.putAsJson("chars", "abc".toCharArray());
        assertEquals("abc", tree.get("chars"));
        tree.putAsJson("codes", codes);
        tree.putAsJson("person", new Person());
        tree.putAsJson("v", ZonedDateTime.parse("2026-01-15T10:00:00Z"));
        String json = JsonIo.toJson(tree, null);
        assertEquals("{\"a\":1,\"v\":\"2026-01-15T10:00:00Z\",\"list\":[1,\"a\",\"2026-01-15\"],\"ints\":[1,2],"
                + "\"chars\":\"abc\",\"codes\":{\"7\":\"RED\",\"x\":\"c\"},"
                + "\"person\":{\"name\":\"Ann\",\"age\":41,\"born\":\"1970-01-01T00:00:00.000Z\"}}", json);
        assertFalse(json.contains("@type"));
        assertTrue(tree.get("person") instanceof JsonObject);
        // a JsonObject is a JSON node already: put as it is, a node with @items too
        JsonObject address = order().get("address", JsonObject.class);
        tree.putAsJson("address", address);
        assertSame(address, tree.get("address"));
        JsonObject bag = JsonIo.toMaps("{\"@type\":\"com.nowhere.Bag\",\"@items\":[1,2]}", null).asClass(JsonObject.class);
        tree.putAsJson("bag", bag);
        assertSame(bag, tree.get("bag"));
    }

    /** What putAsJson put, the matching getter turns back into the value. */
    @Test
    void theGetterTurnsItBack() {
        JsonObject tree = new JsonObject();
        ZonedDateTime when = ZonedDateTime.parse("2026-01-15T10:00:00Z");
        BigDecimal pi = new BigDecimal("3.14159265358979323846");
        tree.putAsJson("when", when);
        tree.putAsJson("pi", pi);
        tree.putAsJson("color", Color.GREEN);
        tree.putAsJson("big", new BigInteger("123456789012345678901234"));
        tree.putAsJson("dates", Arrays.asList(LocalDate.of(2026, 1, 15)));
        assertEquals(when, tree.get("when", ZonedDateTime.class));
        assertEquals(pi, tree.get("pi", BigDecimal.class));
        assertInstanceOf(BigDecimal.class, tree.get("pi"));          // no double holds it
        assertInstanceOf(BigInteger.class, tree.get("big"));         // no long holds it
        assertEquals(Color.GREEN, tree.get("color", Color.class));
        assertEquals(Collections.singletonList(LocalDate.of(2026, 1, 15)),
                tree.get("dates", new TypeHolder<List<LocalDate>>() {}));
    }

    /** put keeps the Java value, which json-io writes with its type; plain JSON cannot hold a reference to itself. */
    @Test
    void putIsUnchangedAndACycleIsRefused() {
        JsonObject tree = new JsonObject();
        tree.put("when", ZonedDateTime.parse("2026-01-15T10:00:00Z"));
        assertTrue(JsonIo.toJson(tree, null).contains("\"@type\":\"ZonedDateTime\""));
        List<Object> loop = new ArrayList<>();
        loop.add(loop);
        JsonIoException e = assertThrows(JsonIoException.class, () -> tree.putAsJson("loop", loop));
        assertTrue(e.getMessage().contains("holds itself"), e.getMessage());
        Map<Object, Object> keyed = new LinkedHashMap<>();
        keyed.put(Arrays.asList(1, 2), "v");
        assertThrows(JsonIoException.class, () -> tree.putAsJson("keyed", keyed));
        // the same list twice, not inside itself, is put twice
        List<Integer> shared = Arrays.asList(1, 2);
        tree.putAsJson("twice", Arrays.asList(shared, shared));
        assertEquals(2, ((Object[]) tree.get("twice")).length);
    }

    public static class Node {
        public String name;
        public Node parent;
        public List<Node> children = new ArrayList<>();
    }

    /** An object whose fields loop back to it is refused too: its parent was put as null, the link dropped. */
    @Test
    void anObjectThatLoopsIsRefused() {
        Node root = new Node();
        root.name = "root";
        Node kid = new Node();
        kid.name = "kid";
        kid.parent = root;
        root.children.add(kid);
        JsonIoException e = assertThrows(JsonIoException.class, () -> new JsonObject().putAsJson("tree", root));
        assertTrue(e.getMessage().contains("Cannot put a " + Node.class.getName() + " that holds itself"),
                e.getMessage());

        root.children.clear();      // no loop: its fields, nested
        JsonObject tree = new JsonObject();
        tree.putAsJson("tree", kid);
        assertEquals("root", ((JsonObject) ((JsonObject) tree.get("tree")).get("parent")).get("name"));
    }
}
