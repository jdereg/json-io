package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three minimal type info modes. MINIMAL, the default, leaves {@code @type} off a List, Set or Map whose class
 * is the one the reader builds for its declared type -- the natural default -- and everything reads back exactly.
 * MINIMAL_LEGACY writes what MINIMAL wrote before 4.113.0, byte for byte: for json-io readers older than 4.88.0 and
 * for text that is hashed or compared. MINIMAL_PLUS is MINIMAL and also lossy where values sit in Object slots.
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
class MinimalLegacyTest {

    public static class Address {
        public String street;
        public int zip;
    }

    public static class Person {
        public String name;
        public List<String> tags = new ArrayList<>();
        public List<Address> addresses = new ArrayList<>();
        public Map<String, Object> attrs = new LinkedHashMap<>();
        public Set<String> roles = new LinkedHashSet<>();
        public SortedSet<String> sorted = new TreeSet<>();
    }

    private static Person person() {
        Person p = new Person();
        p.name = "ann";
        p.tags.add("t1");
        Address a = new Address();
        a.street = "main";
        a.zip = 45202;
        p.addresses.add(a);
        p.attrs.put("count", 3);
        p.attrs.put("label", "x");
        p.roles.add("admin");
        p.sorted.add("b");
        p.sorted.add("a");
        return p;
    }

    private static List<Object> list() {
        List<Object> list = new ArrayList<>();
        list.add(person());
        list.add(7);
        return list;
    }

    private static final String PERSON = "{\"@type\":\"com.cedarsoftware.io.MinimalLegacyTest$Person\",\"name\":\"ann\",";

    /** person() and list() written by json-io 4.112.0 with its default options (MINIMAL), run on that release. */
    private static final String PERSON_4_112 = PERSON
            + "\"tags\":{\"@type\":\"ArrayList\",\"@items\":[\"t1\"]},"
            + "\"addresses\":{\"@type\":\"ArrayList\",\"@items\":[{\"street\":\"main\",\"zip\":45202}]},"
            + "\"attrs\":{\"@type\":\"LinkedHashMap\",\"count\":{\"@type\":\"Integer\",\"value\":3},\"label\":\"x\"},"
            + "\"roles\":{\"@type\":\"java.util.LinkedHashSet\",\"@items\":[\"admin\"]},"
            + "\"sorted\":{\"@type\":\"TreeSet\",\"@items\":[\"a\",\"b\"]}}";
    private static final String LIST_4_112 = "{\"@type\":\"ArrayList\",\"@items\":["
            + PERSON_4_112 + ",{\"@type\":\"Integer\",\"value\":7}]}";

    @Test
    void legacyWritesWhatMinimalWroteBefore() {
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build();
        assertEquals(PERSON_4_112, JsonIo.toJson(person(), legacy));
        assertEquals(LIST_4_112, JsonIo.toJson(list(), legacy));
        assertTrue(DeepEquals.deepEquals(person(), JsonIo.toJava(PERSON_4_112, null).asClass(Person.class)));
    }

    @Test
    void theDefaultLeavesTypeOffNaturalDefaultsAndReadsBackExactly() {
        String json = JsonIo.toJson(person(), null);
        assertEquals(PERSON + "\"tags\":[\"t1\"],\"addresses\":[{\"street\":\"main\",\"zip\":45202}],"
                + "\"attrs\":{\"count\":{\"@type\":\"Integer\",\"value\":3},\"label\":\"x\"},"
                + "\"roles\":[\"admin\"],\"sorted\":[\"a\",\"b\"]}", json);
        assertTrue(json.length() < PERSON_4_112.length() * 3 / 4, json);

        Person back = JsonIo.toJava(json, null).asClass(Person.class);
        assertTrue(DeepEquals.deepEquals(person(), back));
        assertEquals(Integer.class, back.attrs.get("count").getClass());     // an Object slot keeps its type
        assertEquals(TreeSet.class, back.sorted.getClass());
        assertEquals(LinkedHashSet.class, back.roles.getClass());
        assertEquals(Address.class, back.addresses.get(0).getClass());
    }

    @Test
    void minimalPlusIsSmallerAndLossy() {
        String json = JsonIo.toJson(person(), new WriteOptionsBuilder().showTypeInfoMinimalPlus().build());
        assertTrue(json.contains("\"count\":3"), json);
        Person back = JsonIo.toJava(json, null).asClass(Person.class);
        assertEquals(3L, back.attrs.get("count"));                           // read back a Long
    }

    @Test
    void legacyIsAMinimalMode() {
        WriteOptions legacy = new WriteOptionsBuilder().showTypeInfoMinimalLegacy().omitRootTypeInfo().build();
        assertTrue(legacy.isMinimalShowingType());
        assertTrue(legacy.isMinimalLegacyShowingType());
        assertTrue(JsonIo.toJson(person(), legacy).startsWith("{\"name\""));

        WriteOptions minimal = new WriteOptionsBuilder().showTypeInfoMinimal().build();
        assertEquals(JsonIo.toToon(person(), minimal),
                JsonIo.toToon(person(), new WriteOptionsBuilder().showTypeInfoMinimalLegacy().build()));

        try {
            WriteOptionsBuilder.addPermanentShowTypeInfoMinimalLegacy();
            assertEquals(PERSON_4_112, JsonIo.toJson(person(), new WriteOptionsBuilder().build()));
        } finally {
            WriteOptionsBuilder.addPermanentShowTypeInfoMinimal();
        }
        assertTrue(new WriteOptionsBuilder().build().isMinimalShowingType());
        assertTrue(!new WriteOptionsBuilder().build().isMinimalLegacyShowingType());
    }
}
