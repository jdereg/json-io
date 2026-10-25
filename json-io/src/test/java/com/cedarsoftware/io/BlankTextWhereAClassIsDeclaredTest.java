package com.cedarsoftware.io;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.cedarsoftware.io.annotation.IoCreator;
import com.cedarsoftware.io.annotation.IoValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Blank text (empty, or white space alone) where a class is declared that nothing builds from it is null, in a list,
 * a set and an {@code Optional} as in a field, an array and a map. In a list or set it stayed a String (a
 * {@code List<Address>} holding {@code ""}, a ClassCastException on use), and an {@code Optional<Address>} held it
 * (4.117.0 too). Where text is what is declared, or something builds the class from it, it is read as before.
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
class BlankTextWhereAClassIsDeclaredTest {
    public static class Address {
        public String street;
    }

    /** Built from a number: blank text builds nothing. */
    public static class Num {
        private final int n;

        @IoCreator
        public Num(int n) {
            this.n = n;
        }

        @IoValue
        public int n() {
            return n;
        }
    }

    /** Built from text, blank text too. */
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
        public List<Address> addresses;
        public Set<Address> addressSet;
        public List<Num> nums;
        public List<LocalDate> dates;
        public Optional<Address> opt;
        public List<Optional<Address>> opts;
        public Address field;
        public Address[] arr;
        public Map<String, Address> map;
        public List<String> strings;
        public List<Object> objects;
        public List<CharSequence> chars;
        public List<Integer> ints;
        public List<Email> emails;
        public Optional<String> optString;
    }

    private static final String TYPE = Bag.class.getName();

    private static Bag read(String fields) {
        return JsonIo.toJava("{\"@type\":\"" + TYPE + "\"," + fields + "}", null).asClass(Bag.class);
    }

    @Test
    void nullInAListASetAndAnOptional() {
        Bag bag = read("\"addresses\":[\"\",\" \"],\"addressSet\":[\"\"],\"nums\":[\"\"],\"dates\":[\"\"],"
                + "\"opt\":\"\",\"opts\":[\"\",\" \"]");
        assertEquals(Arrays.asList(null, null), bag.addresses);
        assertEquals(Collections.singleton(null), bag.addressSet);
        assertEquals(Collections.singletonList(null), bag.nums);
        assertEquals(Collections.singletonList(null), bag.dates);
        assertEquals(Optional.empty(), bag.opt);
        assertEquals(Arrays.asList(Optional.empty(), Optional.empty()), bag.opts);

        Bag toon = JsonIo.fromToon("addresses[1]: \"\"\nopt: \"\"\n", null).asClass(Bag.class);
        assertEquals(Collections.singletonList(null), toon.addresses);
        assertEquals(Optional.empty(), toon.opt);

        Optional<Address> root = JsonIo.toJava("\"\"", null).asType(new TypeHolder<Optional<Address>>() { });
        assertEquals(Optional.empty(), root);
    }

    /** As in a field, an array and a map, as before. */
    @Test
    void asInAFieldAnArrayAndAMap() {
        Bag bag = read("\"field\":\"\",\"arr\":[\"\"],\"map\":{\"k\":\"\"}");
        assertNull(bag.field);
        assertArrayEquals(new Address[] {null}, bag.arr);
        assertNull(bag.map.get("k"));
        assertEquals(1, bag.map.size());
    }

    /** Text declared, or a class built from it: read as before. */
    @Test
    void readAsBefore() {
        Bag bag = read("\"strings\":[\"\"],\"objects\":[\"\"],\"chars\":[\" \"],\"ints\":[\"\"],\"emails\":[\"\"],"
                + "\"optString\":\"\"");
        assertEquals(Collections.singletonList(""), bag.strings);
        assertEquals(Collections.singletonList(""), bag.objects);
        assertEquals(Collections.singletonList(" "), bag.chars);
        assertEquals(Collections.singletonList(0), bag.ints);
        assertEquals("", bag.emails.get(0).v);
        assertEquals(Optional.of(""), bag.optString);
    }
}
