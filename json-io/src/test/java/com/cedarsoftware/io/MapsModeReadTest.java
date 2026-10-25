package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Maps mode reads what Java mode reads. It threw where the JSON or TOON holds an enum written by its {@code @IoValue}
 * value ("No enum constant Code.g"), an enum written as an object ({"@type":..,"name":..}, or with its value), a TOON
 * list where an array of an {@code @IoValue} class is declared, a NaN in a {@code Number} field (written
 * {"@type":"Float","value":null}), and a class built from its parts -- an {@code @IoCreator} or {@code @IoValue}
 * class, a record -- holding an object: Maps mode keeps that object a map, which the constructor does not take.
 * Such a class is no longer built in Maps mode (the instance only typed its fields; its class does that now).
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
class MapsModeReadTest {
    public enum Code {
        RED("r"), GREEN("g");

        private final String code;

        Code(String code) {
            this.code = code;
        }

        @IoValue
        public String code() {
            return code;
        }
    }

    public enum Plain { A, B }

    public static class Email {
        private final String v;

        @IoCreator
        public Email(String v) {
            this.v = v;
        }

        @IoValue
        public String v() {
            return v;
        }
    }

    public static class Bag {
        public Code code = Code.GREEN;
        public Plain plain = Plain.B;
        public Code[] arr = {Code.GREEN, Code.RED};
        public Object any = Code.RED;
    }

    public static class Sets {
        public EnumSet<Plain> plains = EnumSet.of(Plain.A, Plain.B);
        public EnumSet<Code> codes = EnumSet.of(Code.GREEN);
        public Object any = EnumSet.of(Plain.B);
        public List<Object> list = new ArrayList<>(Collections.singletonList(EnumSet.of(Plain.A)));
        public Map<String, Object> map = new LinkedHashMap<>(Collections.singletonMap("k", EnumSet.of(Plain.B)));
    }

    public static class Mail {
        public Email[] to = {new Email("a@x.com")};
    }

    public static class Num {
        public Number nan = Float.NaN;
    }

    public static class Address {
        public String street;
        public int zip;

        public Address() {
        }

        Address(String street, int zip) {
            this.street = street;
            this.zip = zip;
        }
    }

    /** Built from its value, an object. */
    public static class Site {
        private final Address addr;

        @IoCreator
        public Site(Address addr) {
            this.addr = addr;
        }

        @IoValue
        public Address addr() {
            return addr;
        }
    }

    /** Built from its value, an object, by the constructor taking it (no @IoCreator). */
    public static class Lot {
        private final Address addr;

        public Lot(Address addr) {
            this.addr = addr;
        }

        @IoValue
        public Address addr() {
            return addr;
        }
    }

    /** Built from its fields, one of them an object. */
    public static class Place {
        private final Address addr;
        private final int floor;

        @IoCreator
        public Place(@IoProperty("addr") Address addr, @IoProperty("floor") int floor) {
            this.addr = addr;
            this.floor = floor;
        }
    }

    private static final WriteOptions ALWAYS = new WriteOptionsBuilder().showTypeInfoAlways().build();
    private static final WriteOptions ENUM_OBJECTS = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();
    private static final WriteOptions MINIMAL = new WriteOptionsBuilder().showTypeInfoMinimal().build();

    private static Map<?, ?> json(Object o, WriteOptions options) {
        return (Map<?, ?>) JsonIo.toMaps(JsonIo.toJson(o, options), null).asClass(null);
    }

    private static Map<?, ?> toon(Object o, WriteOptions options) {
        return (Map<?, ?>) JsonIo.fromToonToMaps(JsonIo.toToon(o, options), null).asClass(null);
    }

    @Test
    void enumsWrittenByTheirValueOrAsObjects() {
        List<Map<?, ?>> reads = Arrays.asList(json(new Bag(), null), json(new Bag(), ALWAYS),
                json(new Bag(), ENUM_OBJECTS), toon(new Bag(), MINIMAL), toon(new Bag(), ALWAYS));
        for (Map<?, ?> bag : reads) {
            assertEquals(Code.GREEN, bag.get("code"));     // the value, also where it was written with its type
            assertEquals(Plain.B, bag.get("plain"));
            // Where Object is declared, an enum with a field of its own stays JSON, as an object of a class does: only
            // a simple enum (no fields, no constant with a body) is its constant there
            assertInstanceOf(JsonObject.class, bag.get("any"));
        }
        // An array the JSON types: its elements are read as its component type, also where its type follows them
        assertArrayEquals(new Object[] {Code.GREEN, Code.RED}, ((JsonObject) reads.get(1).get("arr")).getItems());
        String typeLast = "{\"@items\":[\"g\",\"r\"],\"@type\":\"" + Code[].class.getName() + "\"}";
        assertArrayEquals(new Code[] {Code.GREEN, Code.RED}, (Object[]) JsonIo.toMaps(typeLast, null).asClass(null));
    }

    /** An EnumSet holds its items: in a field or a map it read empty, in a list it threw (ClassCastException). */
    @Test
    void enumSetsHoldTheirItems() {
        for (Map<?, ?> sets : Arrays.asList(json(new Sets(), null), json(new Sets(), ALWAYS), toon(new Sets(), MINIMAL),
                toon(new Sets(), ALWAYS))) {
            assertEquals(EnumSet.of(Plain.A, Plain.B), sets.get("plains"));
            assertEquals(EnumSet.of(Code.GREEN), sets.get("codes"));
            assertEquals(EnumSet.of(Plain.B), sets.get("any"));
            assertEquals(EnumSet.of(Plain.B), ((Map<?, ?>) sets.get("map")).get("k"));
        }
        // In a list, as at the root: the object as read, the set its target
        for (Map<?, ?> sets : Arrays.asList(json(new Sets(), null), json(new Sets(), ALWAYS))) {
            Object list = sets.get("list");
            Object element = list instanceof JsonObject ? ((JsonObject) list).getItems()[0] : ((Object[]) list)[0];
            assertEquals(EnumSet.of(Plain.A), ((JsonObject) element).getTarget());
        }
    }

    @Test
    void aToonListOfValueClassesWhereAnArrayIsDeclared() {
        // Left a list, as JSON's array is left an array: the Converter does not build an Email from its value
        assertEquals(new ArrayList<>(Arrays.asList("a@x.com")), toon(new Mail(), MINIMAL).get("to"));
        assertArrayEquals(new Object[] {"a@x.com"}, (Object[]) json(new Mail(), null).get("to"));
    }

    @Test
    void aNanInANumberField() {
        for (Map<?, ?> num : Arrays.asList(json(new Num(), null), json(new Num(), ALWAYS))) {
            assertTrue(num.containsKey("nan"));
            assertNull(num.get("nan"));     // the value, as Java mode reads it (no longer an object holding it)
        }
    }

    @Test
    void classesBuiltFromAnObject() {
        Map<?, ?> site = json(new Site(new Address("1 Main", 45202)), null);
        assertEquals("1 Main", ((Map<?, ?>) site.get("value")).get("street"));
        Map<?, ?> lot = json(new Lot(new Address("3 Oak", 9)), null);
        assertEquals("3 Oak", ((Map<?, ?>) lot.get("value")).get("street"));

        for (Map<?, ?> place : Arrays.asList(json(new Place(new Address("2 Elm", 7), 3), null),
                json(new Place(new Address("2 Elm", 7), 3), ALWAYS))) {
            assertEquals("2 Elm", ((Map<?, ?>) place.get("addr")).get("street"));
            assertEquals(3, place.get("floor"));    // typed by its class, as a built instance's fields were
        }

        List<Object> list = new ArrayList<>(Arrays.asList(new Site(new Address("1 Main", 45202)),
                new Place(new Address("2 Elm", 7), 3)));
        Object[] items = ((JsonObject) json(list, null)).getItems();
        assertEquals("1 Main", ((Map<?, ?>) ((Map<?, ?>) items[0]).get("value")).get("street"));
        assertEquals(3L, ((Map<?, ?>) items[1]).get("floor"));     // in a list it stays JSON, as in a field
    }

    /** A map or list built from its value is still built: it is no object Maps mode keeps a map. */
    @Test
    void mapsAndListsBuiltFromTheirValue() {
        JsonObject tags = (JsonObject) json(IoValueContainerTest.Tags.of("a,b"), null);
        assertEquals(Arrays.asList("a", "b"), tags.getTarget());
        JsonObject dict = (JsonObject) json(IoValueContainerTest.Dict.of("x=1;y=2"), null);
        assertEquals(2, ((Map<?, ?>) dict.getTarget()).get("y"));
    }
}
