package com.cedarsoftware.io;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An enum in a list, a set, a map's values or a map's {@code @keys}, of the type the field declares for them
 * ({@code List<Plain>}), is written by default as its name (or its {@code @IoValue} value) -- as in a field and an
 * array, as TOON writes it, and as Jackson does -- and reads back by the declared type. JSON wrote each such element
 * as an object with its {@code @type}: {@code [{"@type":"...Plain","name":"A"}]}. An enum of another type than the
 * declared one keeps its {@code @type}, and {@code showTypeInfoMinimalLegacy()} keeps 4.112.0's text.
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
class EnumElementOfDeclaredTypeTest {
    public enum Plain { A, B }

    public enum Code {
        RED("r"), GREEN("g");

        private final String c;

        Code(String c) {
            this.c = c;
        }

        @JsonValue
        public String c() {
            return c;
        }
    }

    /** X has a body: its class is a subclass of the enum. */
    public enum Body {
        X {
            @Override
            int weight() {
                return 1;
            }
        },
        Y;

        int weight() {
            return 0;
        }
    }

    public interface Shape { }

    public enum Circle implements Shape { SMALL, BIG }

    public static class Base {
        public int b = 1;
    }

    /** A subclass of a declared class, as a constant with a body is of its enum: it keeps its @type. */
    public static class Sub extends Base {
        public int s = 2;
    }

    /** Each container holds enums of the type it declares. */
    public static class Declared {
        public List<Plain> list = new ArrayList<>(Arrays.asList(Plain.A, Plain.B));
        public Set<Plain> set = new LinkedHashSet<>(Arrays.asList(Plain.B));
        public Collection<Plain> coll = new ArrayList<>(Arrays.asList(Plain.A));
        public Deque<Plain> deque = new ArrayDeque<>(Arrays.asList(Plain.B));
        public Map<String, Plain> values = new LinkedHashMap<>();
        public List<Code> codes = new ArrayList<>(Arrays.asList(Code.GREEN));
        public List<Body> bodies = new ArrayList<>(Arrays.asList(Body.X, Body.Y));

        {
            values.put("v", Plain.A);
        }
    }

    /** Maps keyed by an enum, written with @keys (stringifyMapKeys off). */
    public static class Keyed {
        public Map<Plain, String> keyed = new LinkedHashMap<>();
        public EnumMap<Plain, String> enumMap = new EnumMap<>(Plain.class);
        public Map<Body, Body> bodies = new LinkedHashMap<>();

        {
            keyed.put(Plain.B, "b");
            enumMap.put(Plain.A, "a");
            bodies.put(Body.X, Body.Y);
        }
    }

    /** Containers whose declared type is not the enum's. */
    public static class Undeclared {
        public List<Object> objects = new ArrayList<>(Arrays.asList(Plain.A));
        public List<Shape> shapes = new ArrayList<>(Arrays.asList(Circle.BIG));
        public List<Enum<?>> enums = new ArrayList<>(Arrays.asList(Plain.B));
        @SuppressWarnings("rawtypes")
        public List raw = new ArrayList<>(Arrays.asList(Plain.A));
        public List<Base> bases = new ArrayList<>(Arrays.asList(new Sub()));
    }

    private static final String DECLARED = "\"list\":[\"A\",\"B\"],\"set\":[\"B\"],\"coll\":[\"A\"],\"deque\":[\"B\"],"
            + "\"values\":{\"v\":\"A\"},\"codes\":[\"g\"],\"bodies\":[\"X\",\"Y\"]";

    private static void assertDeclared(Declared back, String json) {
        assertEquals(Arrays.asList(Plain.A, Plain.B), back.list, json);
        assertSame(Plain.A, back.list.get(0), json);
        assertSame(Plain.B, back.set.iterator().next(), json);
        assertSame(Plain.A, back.coll.iterator().next(), json);
        assertSame(Plain.B, back.deque.getFirst(), json);
        assertSame(Plain.A, back.values.get("v"), json);
        assertSame(Code.GREEN, back.codes.get(0), json);
        assertSame(Body.X, back.bodies.get(0), json);
        assertSame(Body.Y, back.bodies.get(1), json);
    }

    @Test
    void anEnumOfTheDeclaredTypeIsItsName() {
        WriteOptions[] all = {null, new WriteOptionsBuilder().showTypeInfoMinimalPlus().build(),
                new WriteOptionsBuilder().cycleSupport(true).build()};
        for (WriteOptions options : all) {
            String json = JsonIo.toJson(new Declared(), options);
            assertTrue(json.contains(DECLARED), json);
            assertDeclared(JsonIo.toJava(json, null).asClass(Declared.class), json);
        }
    }

    /** Without the root's @type, the text is Jackson's. */
    @Test
    void itIsWhatJacksonWrites() throws Exception {
        String json = JsonIo.toJson(new Declared(), new WriteOptionsBuilder().omitRootTypeInfo().build());
        assertEquals(new ObjectMapper().writeValueAsString(new Declared()), json);
    }

    @Test
    void anEnumKeyIsItsName() {
        String json = JsonIo.toJson(new Keyed(), null);
        assertTrue(json.contains("\"keyed\":{\"@keys\":[\"B\"],\"@items\":[\"b\"]}"), json);
        assertTrue(json.contains("\"enumMap\":{\"@keys\":[\"A\"],\"@items\":[\"a\"]}"), json);
        assertTrue(json.contains("\"bodies\":{\"@keys\":[\"X\"],\"@items\":[\"Y\"]}"), json);
        Keyed back = JsonIo.toJava(json, null).asClass(Keyed.class);
        assertSame(Plain.B, back.keyed.keySet().iterator().next(), json);
        assertSame(Plain.A, back.enumMap.keySet().iterator().next(), json);
        assertSame(Body.X, back.bodies.keySet().iterator().next(), json);
        assertSame(Body.Y, back.bodies.values().iterator().next(), json);
    }

    @Test
    void anEnumOfAnotherTypeKeepsItsType() {
        String json = JsonIo.toJson(new Undeclared(), null);
        String plain = Plain.class.getName();
        assertTrue(json.contains("\"objects\":[{\"@type\":\"" + plain + "\",\"name\":\"A\"}]"), json);
        assertTrue(json.contains("\"shapes\":[{\"@type\":\"" + Circle.class.getName() + "\",\"name\":\"BIG\"}]"), json);
        assertTrue(json.contains("\"enums\":[{\"@type\":\"" + plain + "\",\"name\":\"B\"}]"), json);
        assertTrue(json.contains("\"raw\":[{\"@type\":\"" + plain + "\",\"name\":\"A\"}]"), json);
        assertTrue(json.contains("\"bases\":[{\"@type\":\"" + Sub.class.getName() + "\",\"s\":2,\"b\":1}]"), json);
        Undeclared back = JsonIo.toJava(json, null).asClass(Undeclared.class);
        assertSame(Plain.A, back.objects.get(0), json);
        assertSame(Circle.BIG, back.shapes.get(0), json);
        assertSame(Plain.B, back.enums.get(0), json);
        assertSame(Plain.A, back.raw.get(0), json);
        assertSame(Sub.class, back.bases.get(0).getClass(), json);
    }

    /** 4.112.0's text, byte for byte: each enum element with its @type. */
    @Test
    void theLegacyTextKeepsTheType() {
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build();
        String json = JsonIo.toJson(new Declared(), legacy);
        String plain = Plain.class.getName();
        assertTrue(json.contains("\"list\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"" + plain
                + "\",\"name\":\"A\"},{\"@type\":\"" + plain + "\",\"name\":\"B\"}]}"), json);
        assertTrue(json.contains("\"bodies\":{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"" + Body.class.getName()
                + "$1\",\"name\":\"X\"},{\"@type\":\"" + Body.class.getName() + "\",\"name\":\"Y\"}]}"), json);
        assertDeclared(JsonIo.toJava(json, null).asClass(Declared.class), json);
        json = JsonIo.toJson(new Keyed(), legacy);
        assertTrue(json.contains("\"@keys\":[{\"@type\":\"" + Body.class.getName() + "$1\",\"name\":\"X\"}]"), json);
    }
}
