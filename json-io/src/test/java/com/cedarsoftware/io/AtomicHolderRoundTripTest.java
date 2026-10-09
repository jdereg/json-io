package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicStampedReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AtomicReferenceArray}, {@code AtomicMarkableReference} and {@code AtomicStampedReference} keep their state in
 * fields java.util.concurrent does not open: on JDK 17+ json-io wrote them with that state as null and could not read
 * them back. They are written through their public methods, in the shape json-io wrote by reflection where it could
 * read those fields (JDK 8 and 11), so JSON written before reads back, and they read back on every JDK -- elements and
 * references typed by the field's generics, objects shared with the rest of the graph kept shared, cycles kept.
 * Jackson writes neither {@code AtomicReferenceArray} nor reads the other two.
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
class AtomicHolderRoundTripTest {

    private static final String ARA = "java.util.concurrent.atomic.AtomicReferenceArray";
    private static final String AMR = "java.util.concurrent.atomic.AtomicMarkableReference";
    private static final String ASR = "java.util.concurrent.atomic.AtomicStampedReference";

    public static class Item {
        public String name;
        public int qty;

        public Item() { }

        Item(String name, int qty) {
            this.name = name;
            this.qty = qty;
        }
    }

    public static class Holder {
        public AtomicReferenceArray<Item> items;
        public AtomicMarkableReference<Item> flagged;
        public AtomicStampedReference<String> stamped;
        public AtomicReferenceArray<Integer> numbers;
    }

    public static class Node {
        public String name;
        public AtomicReferenceArray<Node> kids;
        public AtomicStampedReference<Node> parent;
    }

    public static class Wrapper {
        public Item item;
    }

    /**
     * The shared Item is defined inside {@code first}, which the reader fills in only after it has built the atomics
     * that refer to it: each one's first value is the reference.
     */
    public static class ForwardRefs {
        public Wrapper first;
        public AtomicMarkableReference<Item> markable;
        public AtomicStampedReference<Item> stamped;
        public AtomicReferenceArray<Item> array;
    }

    private static final WriteOptions NEVER = new WriteOptionsBuilder().showTypeInfoNever().build();

    private static String item() {
        return AtomicHolderRoundTripTest.Item.class.getName();
    }

    private static Object[] contents(AtomicReferenceArray<?> array) {
        Object[] out = new Object[array.length()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i);
        }
        return out;
    }

    private static Holder holder(Item shared) {
        Holder h = new Holder();
        h.items = new AtomicReferenceArray<>(new Item[] {shared, new Item("nut", 9)});
        h.flagged = new AtomicMarkableReference<>(shared, true);
        h.stamped = new AtomicStampedReference<>("z", 42);
        h.numbers = new AtomicReferenceArray<>(new Integer[] {1, 2});
        return h;
    }

    /** What json-io wrote by reflection on JDK 8 and 11, now written the same on every JDK. */
    @Test
    void writesTheShapeJsonIoWroteWhereItCouldReadTheFields() {
        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[\"a\",\"b\",null]}",
                JsonIo.toJson(new AtomicReferenceArray<>(new Object[] {"a", "b", null})));
        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[{\"@type\":\"Integer\",\"value\":1},2,3.5,true]}",
                JsonIo.toJson(new AtomicReferenceArray<>(new Object[] {1, 2L, 3.5, true})));
        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[]}", JsonIo.toJson(new AtomicReferenceArray<>(0)));
        assertEquals("{\"@type\":\"" + AMR + "\",\"pair\":{\"reference\":\"x\",\"mark\":true}}",
                JsonIo.toJson(new AtomicMarkableReference<>("x", true)));
        assertEquals("{\"@type\":\"" + AMR + "\",\"pair\":{\"reference\":null,\"mark\":false}}",
                JsonIo.toJson(new AtomicMarkableReference<>(null, false)));
        assertEquals("{\"@type\":\"" + ASR + "\",\"pair\":{\"reference\":\"y\",\"stamp\":7}}",
                JsonIo.toJson(new AtomicStampedReference<>("y", 7)));
        assertEquals("{\"array\":[\"a\",\"b\",null]}",
                JsonIo.toJson(new AtomicReferenceArray<>(new Object[] {"a", "b", null}), NEVER));
        assertEquals("{\"pair\":{\"reference\":\"x\",\"mark\":true}}",
                JsonIo.toJson(new AtomicMarkableReference<>("x", true), NEVER));

        Item shared = new Item("bolt", 3);
        assertEquals("{\"@type\":\"" + Holder.class.getName() + "\",\"items\":{\"array\":[{\"@id\":1,\"@type\":\""
                        + item() + "\",\"name\":\"bolt\",\"qty\":3},{\"@type\":\"" + item()
                        + "\",\"name\":\"nut\",\"qty\":9}]},\"flagged\":{\"pair\":{\"reference\":{\"@ref\":1},"
                        + "\"mark\":true}},\"stamped\":{\"pair\":{\"reference\":\"z\",\"stamp\":42}},\"numbers\":"
                        + "{\"array\":[{\"@type\":\"Integer\",\"value\":1},{\"@type\":\"Integer\",\"value\":2}]}}",
                JsonIo.toJson(holder(shared)));
    }

    /** Each type-info setting writes what it wrote by reflection, and reads it back. */
    @Test
    void everyTypeInfoSettingWritesAsBeforeAndReadsBack() {
        WriteOptions plus = new WriteOptionsBuilder().showTypeInfoMinimalPlus().build();
        WriteOptions always = new WriteOptionsBuilder().showTypeInfoAlways().build();
        WriteOptions longText = new WriteOptionsBuilder().writeLongsAsStrings(true).build();
        AtomicReferenceArray<Object> array = new AtomicReferenceArray<>(new Object[] {1, 2L, 3.5, true, "s", null});

        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[1,2,3.5,true,\"s\",null]}", JsonIo.toJson(array, plus));
        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[{\"@type\":\"Integer\",\"value\":1},2,3.5,true,\"s\","
                + "null]}", JsonIo.toJson(array, always));
        assertEquals("{\"array\":[1,2,3.5,true,\"s\",null]}", JsonIo.toJson(array, NEVER));
        assertEquals("{\"@type\":\"" + ARA + "\",\"array\":[{\"@type\":\"Integer\",\"value\":1},"
                + "{\"@type\":\"Long\",\"value\":\"2\"},3.5,true,\"s\",null]}", JsonIo.toJson(array, longText));

        AtomicMarkableReference<Object> markable = new AtomicMarkableReference<>(5, true);
        assertEquals("{\"@type\":\"" + AMR + "\",\"pair\":{\"reference\":5,\"mark\":true}}",
                JsonIo.toJson(markable, plus));
        // The pair's own type, java.util.concurrent's internal Pair class, is not written: no reader needs it
        assertEquals("{\"@type\":\"" + AMR + "\",\"pair\":{\"reference\":{\"@type\":\"Integer\",\"value\":5},"
                + "\"mark\":true}}", JsonIo.toJson(markable, always));
        assertEquals("{\"@type\":\"" + ASR + "\",\"pair\":{\"reference\":{\"@type\":\"Long\",\"value\":\"2\"},"
                + "\"stamp\":3}}", JsonIo.toJson(new AtomicStampedReference<>(2L, 3), longText));

        for (WriteOptions options : new WriteOptions[] {null, plus, always, longText}) {
            AtomicReferenceArray<?> back = JsonIo.toJava(JsonIo.toJson(
                    new AtomicReferenceArray<>(new Object[] {2L, 3.5, true, "s", null}), options), null)
                    .asClass(AtomicReferenceArray.class);
            assertArrayEquals(new Object[] {2L, 3.5, true, "s", null}, contents(back));
            AtomicStampedReference<?> stamped = JsonIo.toJava(JsonIo.toJson(
                    new AtomicStampedReference<>(2L, 3), options), null).asClass(AtomicStampedReference.class);
            assertEquals(2L, stamped.getReference());
            assertEquals(3, stamped.getStamp());
        }
    }

    /**
     * JSON written before reads: json-io's own on JDK 8 with every type shown (which named java.util.concurrent's
     * internal Pair class), and Gson's on JDK 8, which is json-io's shape without types.
     */
    @Test
    void readsWhatWasWrittenBefore() {
        AtomicMarkableReference<?> markable = JsonIo.toJava("{\"@type\":\"" + AMR + "\",\"pair\":{\"@type\":\"" + AMR
                + "$Pair\",\"reference\":{\"@type\":\"Integer\",\"value\":5},\"mark\":true}}", null)
                .asClass(AtomicMarkableReference.class);
        assertEquals(5, markable.getReference());
        assertTrue(markable.isMarked());
        AtomicStampedReference<?> stamped = JsonIo.toJava("{\"@type\":\"" + ASR + "\",\"pair\":{\"@type\":\"" + ASR
                + "$Pair\",\"reference\":2,\"stamp\":3}}", null).asClass(AtomicStampedReference.class);
        assertEquals(2L, stamped.getReference());
        assertEquals(3, stamped.getStamp());

        AtomicReferenceArray<?> gsonArray = JsonIo.toJava("{\"array\":[\"a\",\"b\"]}", null)
                .asClass(AtomicReferenceArray.class);
        assertArrayEquals(new Object[] {"a", "b"}, contents(gsonArray));
        AtomicMarkableReference<?> gsonMarkable = JsonIo.toJava(
                "{\"pair\":{\"reference\":\"x\",\"mark\":true}}", null).asClass(AtomicMarkableReference.class);
        assertEquals("x", gsonMarkable.getReference());
        assertTrue(gsonMarkable.isMarked());
    }

    @Test
    void roundTripAtTheRoot() {
        AtomicReferenceArray<?> array = JsonIo.toJava(JsonIo.toJson(new AtomicReferenceArray<>(
                new Object[] {"a", 1, 2L, 3.5, true, null, Arrays.asList("p", "q")})), null)
                .asClass(AtomicReferenceArray.class);
        assertArrayEquals(new Object[] {"a", 1, 2L, 3.5, true, null, Arrays.asList("p", "q")}, contents(array));
        assertEquals(0, JsonIo.toJava(JsonIo.toJson(new AtomicReferenceArray<>(0)), null)
                .asClass(AtomicReferenceArray.class).length());

        AtomicMarkableReference<?> markable = JsonIo.toJava(JsonIo.toJson(
                new AtomicMarkableReference<>("x", true)), null).asClass(AtomicMarkableReference.class);
        assertEquals("x", markable.getReference());
        assertTrue(markable.isMarked());
        markable = JsonIo.toJava(JsonIo.toJson(new AtomicMarkableReference<>(null, false)), null)
                .asClass(AtomicMarkableReference.class);
        assertNull(markable.getReference());
        assertFalse(markable.isMarked());

        AtomicStampedReference<?> stamped = JsonIo.toJava(JsonIo.toJson(
                new AtomicStampedReference<>(new Item("bolt", 3), -3)), null).asClass(AtomicStampedReference.class);
        assertEquals("bolt", ((Item) stamped.getReference()).name);
        assertEquals(-3, stamped.getStamp());
    }

    /** Typed fields: the field's type argument types elements and references, with and without @type. */
    @Test
    void roundTripAsTypedFields() {
        for (WriteOptions options : new WriteOptions[] {null, NEVER}) {
            Item shared = new Item("bolt", 3);
            Holder back = JsonIo.toJava(JsonIo.toJson(holder(shared), options), null).asClass(Holder.class);
            assertEquals(2, back.items.length());
            assertEquals("bolt", back.items.get(0).name);
            assertEquals(9, back.items.get(1).qty);
            assertSame(back.items.get(0), back.flagged.getReference());
            assertTrue(back.flagged.isMarked());
            assertEquals("z", back.stamped.getReference());
            assertEquals(42, back.stamped.getStamp());
            assertInstanceOf(Integer.class, back.numbers.get(0));
            assertArrayEquals(new Object[] {1, 2}, contents(back.numbers));
        }
    }

    /**
     * The shared Item is defined inside a plain object the reader fills in only after it has built the atomics, so
     * their @ref names an object not yet built: it is set into each atomic once it is.
     */
    @Test
    void aReferenceToAnObjectNotYetBuiltIsSetOnceItIs() {
        Item shared = new Item("bolt", 3);
        ForwardRefs refs = new ForwardRefs();
        refs.first = new Wrapper();
        refs.first.item = shared;
        refs.array = new AtomicReferenceArray<>(new Item[] {shared, new Item("nut", 9)});
        refs.markable = new AtomicMarkableReference<>(shared, true);
        refs.stamped = new AtomicStampedReference<>(shared, 5);

        for (WriteOptions options : new WriteOptions[] {null, NEVER}) {
            String json = JsonIo.toJson(refs, options);
            ForwardRefs back = JsonIo.toJava(json, null).asClass(ForwardRefs.class);
            Item item = back.first.item;
            assertEquals("bolt", item.name);
            assertSame(item, back.array.get(0), json);
            assertEquals("nut", back.array.get(1).name);
            assertSame(item, back.markable.getReference(), json);
            assertTrue(back.markable.isMarked());
            assertSame(item, back.stamped.getReference(), json);
            assertEquals(5, back.stamped.getStamp());
        }
    }

    @Test
    void cyclesRoundTrip() {
        Node root = new Node();
        root.name = "root";
        Node kid = new Node();
        kid.name = "kid";
        root.kids = new AtomicReferenceArray<>(new Node[] {kid});
        root.parent = new AtomicStampedReference<>(null, 0);
        kid.kids = new AtomicReferenceArray<>(0);
        kid.parent = new AtomicStampedReference<>(root, 1);

        Node back = JsonIo.toJava(JsonIo.toJson(root), null).asClass(Node.class);
        Node backKid = back.kids.get(0);
        assertEquals("kid", backKid.name);
        assertSame(back, backKid.parent.getReference());
        assertEquals(1, backKid.parent.getStamp());
        assertNull(back.parent.getReference());

        // An array holding itself, and a reference holding itself
        AtomicReferenceArray<Object> self = new AtomicReferenceArray<>(2);
        self.set(0, self);
        self.set(1, "end");
        AtomicReferenceArray<?> selfBack = JsonIo.toJava(JsonIo.toJson(self), null)
                .asClass(AtomicReferenceArray.class);
        assertSame(selfBack, selfBack.get(0));
        assertEquals("end", selfBack.get(1));

        AtomicMarkableReference<Object> loop = new AtomicMarkableReference<>(null, true);
        loop.set(loop, true);
        AtomicMarkableReference<?> loopBack = JsonIo.toJava(JsonIo.toJson(loop), null)
                .asClass(AtomicMarkableReference.class);
        assertSame(loopBack, loopBack.getReference());
    }

    /** In a list (no declared type), each keeps its own type. */
    @Test
    void roundTripInAList() {
        List<Object> list = new ArrayList<>();
        list.add(new AtomicReferenceArray<>(new Object[] {"p"}));
        list.add(new AtomicMarkableReference<>("q", false));
        list.add(new AtomicStampedReference<>("r", 2));
        List<?> back = JsonIo.toJava(JsonIo.toJson(list), null).asClass(List.class);
        assertEquals("p", ((AtomicReferenceArray<?>) back.get(0)).get(0));
        assertEquals("q", ((AtomicMarkableReference<?>) back.get(1)).getReference());
        assertEquals(2, ((AtomicStampedReference<?>) back.get(2)).getStamp());
    }

    /** TOON writes them as json-io wrote them by reflection, and reads them back. */
    @Test
    void toonRoundTrip() {
        assertEquals("array[3]: a,b,null",
                JsonIo.toToon(new AtomicReferenceArray<>(new Object[] {"a", "b", null}), null).trim());
        assertEquals("pair:\n  reference: x\n  mark: true",
                JsonIo.toToon(new AtomicMarkableReference<>("x", true), null).trim());
        assertEquals("pair:\n  reference: y\n  stamp: 7",
                JsonIo.toToon(new AtomicStampedReference<>("y", 7), null).trim());

        AtomicReferenceArray<?> array = JsonIo.fromToon(JsonIo.toToon(
                new AtomicReferenceArray<>(new Object[] {"a", "b", null}), null)).asClass(AtomicReferenceArray.class);
        assertArrayEquals(new Object[] {"a", "b", null}, contents(array));
        AtomicStampedReference<?> stamped = JsonIo.fromToon(JsonIo.toToon(new AtomicStampedReference<>("y", 7),
                null)).asClass(AtomicStampedReference.class);
        assertEquals("y", stamped.getReference());
        assertEquals(7, stamped.getStamp());

        Holder back = JsonIo.fromToon(JsonIo.toToon(holder(new Item("bolt", 3)), null)).asClass(Holder.class);
        assertEquals("bolt", back.items.get(0).name);
        assertEquals("nut", back.items.get(1).name);
        assertEquals("bolt", back.flagged.getReference().name);
        assertTrue(back.flagged.isMarked());
        assertEquals(42, back.stamped.getStamp());
        assertArrayEquals(new Object[] {1, 2}, contents(back.numbers));
    }

    /** With cycle support, TOON keeps shared objects shared and cycles, through the atomics too. */
    @Test
    void toonKeepsSharedObjectsAndCyclesWithCycleSupport() {
        WriteOptions cycles = new WriteOptionsBuilder().cycleSupport(true).build();
        Item shared = new Item("bolt", 3);
        Holder back = JsonIo.fromToon(JsonIo.toToon(holder(shared), cycles)).asClass(Holder.class);
        assertSame(back.items.get(0), back.flagged.getReference());

        Node root = new Node();
        root.name = "root";
        Node kid = new Node();
        kid.name = "kid";
        root.kids = new AtomicReferenceArray<>(new Node[] {kid});
        kid.parent = new AtomicStampedReference<>(root, 1);
        Node nodeBack = JsonIo.fromToon(JsonIo.toToon(root, cycles)).asClass(Node.class);
        assertSame(nodeBack, nodeBack.kids.get(0).parent.getReference());
    }
}
