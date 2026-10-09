package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An {@code Optional} inside a collection or an array, written as its content (or null) -- as Jackson writes it, and
 * as json-io writes it in TOON and in JSON without type info -- reads back as an {@code Optional} of the declared
 * type: the element is wrapped, as a field's is. It was put in the list or array bare, so a
 * {@code List<Optional<String>>} held Strings (a ClassCastException at the first get()) and an
 * {@code Optional<String>[]} could not be filled.
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
class OptionalElementsTest {

    public static class Item {
        public String name;

        public Item() { }

        Item(String name) {
            this.name = name;
        }
    }

    public static class Holder {
        public List<Optional<String>> texts;
        public Set<Optional<String>> textSet;
        public List<OptionalInt> ints;
        public Optional<String>[] textArray;
        public List<Optional<Item>> items;
        public List<Optional<List<String>>> lists;
    }

    @SuppressWarnings("unchecked")
    private static Holder holder() {
        Holder h = new Holder();
        h.texts = new ArrayList<>(Arrays.asList(Optional.of("x"), Optional.empty()));
        h.textSet = new HashSet<>(Arrays.asList(Optional.of("s")));
        h.ints = new ArrayList<>(Arrays.asList(OptionalInt.of(7), OptionalInt.empty()));
        h.textArray = new Optional[] {Optional.of("a"), Optional.empty()};
        h.items = new ArrayList<>(Arrays.asList(Optional.of(new Item("bolt")), Optional.empty()));
        h.lists = new ArrayList<>(Arrays.asList(Optional.of(Arrays.asList("p", "q")), Optional.empty()));
        return h;
    }

    private static void assertHolder(Holder back, String written) {
        assertEquals(Arrays.asList(Optional.of("x"), Optional.empty()), back.texts, written);
        assertEquals(new HashSet<>(Arrays.asList(Optional.of("s"))), back.textSet, written);
        assertEquals(Arrays.asList(OptionalInt.of(7), OptionalInt.empty()), back.ints, written);
        assertArrayEquals(new Object[] {Optional.of("a"), Optional.empty()}, back.textArray, written);
        assertEquals("bolt", back.items.get(0).get().name, written);
        assertEquals(Optional.empty(), back.items.get(1), written);
        assertEquals(Arrays.asList(Optional.of(Arrays.asList("p", "q")), Optional.empty()), back.lists, written);
    }

    @Test
    void optionalsInCollectionsAndArraysReadBackFromJsonWithoutTypes() {
        String json = JsonIo.toJson(holder(), new WriteOptionsBuilder().showTypeInfoNever().build());
        assertHolder(JsonIo.toJava(json, null).asClass(Holder.class), json);
    }

    @Test
    void optionalsInCollectionsAndArraysReadBackFromToon() {
        String toon = JsonIo.toToon(holder(), null);
        assertHolder(JsonIo.fromToon(toon, null).asClass(Holder.class), toon);
    }

    @Test
    void optionalsInCollectionsAndArraysReadBackFromJson() {
        String json = JsonIo.toJson(holder(), null);
        assertHolder(JsonIo.toJava(json, null).asClass(Holder.class), json);
    }

    /** What json-io 4.112.0 wrote in TOON on JDK 8 for a list of Optionals: each as its field, {value: ...}. */
    @Test
    void toonWrittenByReflectionReadsBack() {
        Holder back = JsonIo.fromToon("texts[2]{value}:\n  x\n  null\nints[2]{value}:\n  7\n  null", null)
                .asClass(Holder.class);
        assertEquals(Arrays.asList(Optional.of("x"), Optional.empty()), back.texts);
        assertEquals(Arrays.asList(OptionalInt.of(7), OptionalInt.empty()), back.ints);
    }

    public static class Wrapper {
        public Item item;
    }

    /** The shared Item is defined in {@code first}, which the reader fills in after it has built the list. */
    public static class Shared {
        public Wrapper first;
        public List<Optional<Item>> items;
        public Optional<Item>[] array;
    }

    @Test
    @SuppressWarnings("unchecked")
    void aReferenceToAnObjectNotYetBuiltIsWrappedOnceItIs() {
        Item item = new Item("bolt");
        Shared shared = new Shared();
        shared.first = new Wrapper();
        shared.first.item = item;
        shared.items = new ArrayList<>(Arrays.asList(Optional.of(item), Optional.empty()));
        shared.array = new Optional[] {Optional.of(item)};
        for (WriteOptions options : new WriteOptions[] {null, new WriteOptionsBuilder().showTypeInfoNever().build()}) {
            String json = JsonIo.toJson(shared, options);
            Shared back = JsonIo.toJava(json, null).asClass(Shared.class);
            assertSame(back.first.item, back.items.get(0).get(), json);
            assertEquals(Optional.empty(), back.items.get(1), json);
            assertSame(back.first.item, back.array[0].get(), json);
        }
    }
}
