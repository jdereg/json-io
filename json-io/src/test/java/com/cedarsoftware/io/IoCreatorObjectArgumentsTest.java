package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An {@code @IoCreator}'s arguments are read as the types its parameters declare: an object, a list or map of objects,
 * an array, an {@code Object} holding a typed value, another class with a creator. Up to now only values the
 * Converter makes (strings, numbers, dates, ...) reached a creator; an object argument arrived as the parsed map, so
 * reading back json-io's own output of such a class threw "@IoCreator invocation failed".
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
class IoCreatorObjectArgumentsTest {
    public static class Address {
        public String street;
        public int zip;

        public Address() {
        }

        public Address(String street, int zip) {
            this.street = street;
            this.zip = zip;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Address && Objects.equals(((Address) o).street, street) && ((Address) o).zip == zip;
        }

        @Override
        public int hashCode() {
            return Objects.hash(street, zip);
        }

        @Override
        public String toString() {
            return "Address(" + street + ", " + zip + ")";
        }
    }

    public static class Person {
        private final String name;
        private final Address address;
        private final List<String> tags;
        private final Map<String, Address> homes;
        private final int[] scores;
        private final Address[] past;

        @IoCreator
        public Person(@IoProperty("name") String name, @IoProperty("address") Address address,
                      @IoProperty("tags") List<String> tags, @IoProperty("homes") Map<String, Address> homes,
                      @IoProperty("scores") int[] scores, @IoProperty("past") Address[] past) {
            this.name = name;
            this.address = address;
            this.tags = tags;
            this.homes = homes;
            this.scores = scores;
            this.past = past;
        }
    }

    /** A creator taking another class with a creator, and a list of them. */
    public static class Team {
        private final Person lead;
        private final List<Person> members;

        @IoCreator
        public Team(@IoProperty("lead") Person lead, @IoProperty("members") List<Person> members) {
            this.lead = lead;
            this.members = members;
        }
    }

    /** A creator taking the same object twice, and one taking whatever is written. */
    public static class Pair {
        private final Address left;
        private final Address right;

        @IoCreator
        public Pair(@IoProperty("left") Address left, @IoProperty("right") Address right) {
            this.left = left;
            this.right = right;
        }
    }

    public static class Box {
        private final Object content;

        @IoCreator
        public Box(@IoProperty("content") Object content) {
            this.content = content;
        }
    }

    /** A creator given one object, itself built by a creator, twice. */
    public static class Duo {
        private final Person first;
        private final Person second;

        @IoCreator
        public Duo(@IoProperty("first") Person first, @IoProperty("second") Person second) {
            this.first = first;
            this.second = second;
        }
    }

    /** A creator whose parameter is a type variable. */
    public static class Generic<T> {
        private final T content;

        @IoCreator
        public Generic(@IoProperty("content") T content) {
            this.content = content;
        }
    }

    /** One object held by an ordinary field before and after the object a creator builds from it. */
    public static class Shared {
        public Address first;
        public Pair pair;
        public Address last;
    }

    /** A creator given the object it is creating. */
    public static class Loop {
        private final Object self;

        @IoCreator
        public Loop(@IoProperty("self") Object self) {
            this.self = self;
        }
    }

    private static Person person(String name) {
        Map<String, Address> homes = new LinkedHashMap<>();
        homes.put("summer", new Address("1 Lake", 11));
        return new Person(name, new Address("2 Main", 22), new ArrayList<>(Arrays.asList("a", "b")), homes,
                new int[] {7, 8}, new Address[] {new Address("3 Old", 33)});
    }

    private static void assertSamePerson(Person expected, Person actual, String text) {
        assertEquals(expected.name, actual.name, text);
        assertEquals(expected.address, actual.address, text);
        assertEquals(expected.tags, actual.tags, text);
        assertEquals(expected.homes, actual.homes, text);
        for (Object home : actual.homes.values()) {
            assertInstanceOf(Address.class, home, text);
        }
        assertArrayEquals(expected.scores, actual.scores, text);
        assertArrayEquals(expected.past, actual.past, text);
    }

    private static final WriteOptions[] JSON_MODES = {null, new WriteOptionsBuilder().showTypeInfoNever().build(),
            new WriteOptionsBuilder().showTypeInfoAlways().build()};
    private static final WriteOptions[] TOON_MODES = {null, new WriteOptionsBuilder().showTypeInfoMinimal().build(),
            new WriteOptionsBuilder().showTypeInfoAlways().build()};

    @Test
    void objectListMapAndArrayArgumentsReadBack() {
        Person p = person("p");
        for (WriteOptions options : JSON_MODES) {
            String json = JsonIo.toJson(p, options);
            assertSamePerson(p, JsonIo.toJava(json, null).asClass(Person.class), json);
        }
        for (WriteOptions options : TOON_MODES) {
            String toon = JsonIo.toToon(p, options);
            assertSamePerson(p, JsonIo.fromToon(toon, null).asClass(Person.class), toon);
        }
        String handWritten = "{\"name\":\"h\",\"address\":{\"street\":\"9 Elm\",\"zip\":9},\"tags\":[\"x\"],"
                + "\"homes\":{\"winter\":{\"street\":\"8 Ski\",\"zip\":8}},\"scores\":[1],\"past\":[]}";
        Person back = JsonIo.toJava(handWritten, null).asClass(Person.class);
        assertEquals(new Address("9 Elm", 9), back.address);
        assertEquals(new Address("8 Ski", 8), back.homes.get("winter"));
        assertEquals(Arrays.asList("x"), back.tags);
    }

    @Test
    void anArgumentBuiltByItsOwnCreatorReadsBack() {
        Team team = new Team(person("lead"), new ArrayList<>(Arrays.asList(person("m1"), person("m2"))));
        for (WriteOptions options : JSON_MODES) {
            String json = JsonIo.toJson(team, options);
            Team back = JsonIo.toJava(json, null).asClass(Team.class);
            assertSamePerson(team.lead, back.lead, json);
            assertEquals(2, back.members.size(), json);
            assertSamePerson(team.members.get(1), back.members.get(1), json);
        }
        for (WriteOptions options : TOON_MODES) {
            String toon = JsonIo.toToon(team, options);
            Team back = JsonIo.fromToon(toon, null).asClass(Team.class);
            assertSamePerson(team.lead, back.lead, toon);
            assertSamePerson(team.members.get(0), back.members.get(0), toon);
        }
    }

    @Test
    void anObjectArgumentReadsAsTheTypeItWasWrittenWith() {
        Box box = new Box(new Address("4 Box", 4));
        Box back = JsonIo.toJava(JsonIo.toJson(box, null), null).asClass(Box.class);
        assertEquals(new Address("4 Box", 4), back.content);
    }

    @Test
    void aTypeVariableArgumentReadsAsTheTypeItWasWrittenWith() {
        Generic<Address> generic = new Generic<>(new Address("8 Gen", 8));
        Generic<?> back = JsonIo.toJava(JsonIo.toJson(generic, null), null).asClass(Generic.class);
        assertEquals(new Address("8 Gen", 8), back.content);
    }

    /** One object given to a creator twice, and held by fields before and after it, stays one object. */
    @Test
    void aSharedArgumentStaysOneObject() {
        Address shared = new Address("5 Same", 5);
        Pair pair = new Pair(shared, shared);
        Pair back = JsonIo.toJava(JsonIo.toJson(pair, null), null).asClass(Pair.class);
        assertEquals(shared, back.left);
        assertSame(back.left, back.right);

        Shared before = new Shared();
        before.first = shared;
        before.pair = new Pair(shared, new Address("6 Other", 6));
        String json = JsonIo.toJson(before, null);
        Shared readBefore = JsonIo.toJava(json, null).asClass(Shared.class);
        assertSame(readBefore.first, readBefore.pair.left, json);

        Shared after = new Shared();
        after.pair = new Pair(shared, new Address("6 Other", 6));
        after.last = shared;
        json = JsonIo.toJson(after, null);
        Shared readAfter = JsonIo.toJava(json, null).asClass(Shared.class);
        assertSame(readAfter.pair.left, readAfter.last, json);

        Person person = person("twice");
        Duo duo = JsonIo.toJava(JsonIo.toJson(new Duo(person, person), null), null).asClass(Duo.class);
        assertSamePerson(person, duo.first, "duo");
        assertSame(duo.first, duo.second);

        String forward = "{\"pair\":{\"left\":{\"@ref\":1},\"right\":{\"@ref\":1}},\"last\":{\"@id\":1,\"street\":\"7 Later\",\"zip\":7}}";
        Shared readForward = JsonIo.toJava(forward, null).asClass(Shared.class);
        assertEquals(new Address("7 Later", 7), readForward.pair.left);
        assertSame(readForward.pair.left, readForward.pair.right);
        assertSame(readForward.pair.left, readForward.last);
    }

    @Test
    void anArgumentThatIsTheObjectBeingCreatedSaysWhy() {
        String json = "{\"@id\":1,\"@type\":\"" + Loop.class.getName() + "\",\"self\":{\"@ref\":1}}";
        JsonIoException e = assertThrows(JsonIoException.class, () -> JsonIo.toJava(json, null).asClass(Loop.class));
        assertTrue(e.getMessage().contains("self"), e.getMessage());
        assertTrue(e.getMessage().contains("before it exists"), e.getMessage());
    }
}
