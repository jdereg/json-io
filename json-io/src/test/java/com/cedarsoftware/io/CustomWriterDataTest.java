package com.cedarsoftware.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonClassWriter#toData}: a custom writer gives its object as plain data, and json-io uses that data where it
 * works on contents rather than JSON text. TOON writes it, so a type whose state is out of reflection's reach (the
 * JDK's own on JDK 17+, or computed) round-trips in TOON as it does in JSON; the reference pass sees what the writer
 * writes, so an object the writer writes but the class's fields do not reach is kept shared, and a cycle through it
 * reads back.
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
class CustomWriterDataTest {

    private static <T> T toon(Object value, Class<T> type) {
        return JsonIo.fromToon(JsonIo.toToon(value, null), null).asClass(type);
    }

    // ---------- JDK types with custom writers, through TOON ----------

    public static class Concurrency {
        public AtomicIntegerArray ints;
        public AtomicLongArray longs;
        public Semaphore semaphore;
        public CountDownLatch latch;
        public ReentrantLock lock;
        public ReentrantReadWriteLock readWriteLock;
    }

    @Test
    void jdkTypesWithCustomWritersRoundTripThroughToonAtTheRoot() {
        AtomicIntegerArray ints = toon(new AtomicIntegerArray(new int[] {1, -2, 3}), AtomicIntegerArray.class);
        assertEquals("[1, -2, 3]", ints.toString());
        AtomicLongArray longs = toon(new AtomicLongArray(new long[] {4L, Long.MAX_VALUE}), AtomicLongArray.class);
        assertEquals("[4, " + Long.MAX_VALUE + "]", longs.toString());
        Semaphore semaphore = toon(new Semaphore(3, true), Semaphore.class);
        assertEquals(3, semaphore.availablePermits());
        assertTrue(semaphore.isFair());
        assertEquals(2, toon(new CountDownLatch(2), CountDownLatch.class).getCount());
        assertTrue(toon(new ReentrantLock(true), ReentrantLock.class).isFair());
        assertTrue(toon(new ReentrantReadWriteLock(true), ReentrantReadWriteLock.class).isFair());
    }

    @Test
    void jdkTypesWithCustomWritersRoundTripThroughToonAsFields() {
        Concurrency c = new Concurrency();
        c.ints = new AtomicIntegerArray(new int[] {7, 8});
        c.longs = new AtomicLongArray(new long[] {9L});
        c.semaphore = new Semaphore(5, true);
        c.latch = new CountDownLatch(4);
        c.lock = new ReentrantLock(true);
        c.readWriteLock = new ReentrantReadWriteLock(true);
        Concurrency back = toon(c, Concurrency.class);
        assertEquals("[7, 8]", back.ints.toString());
        assertEquals("[9]", back.longs.toString());
        assertEquals(5, back.semaphore.availablePermits());
        assertTrue(back.semaphore.isFair());
        assertEquals(4, back.latch.getCount());
        assertTrue(back.lock.isFair());
        assertTrue(back.readWriteLock.isFair());
    }

    // ---------- Optional in lists, through TOON ----------

    public static class Item {
        public String name;

        public Item() { }

        Item(String name) {
            this.name = name;
        }
    }

    public static class Optionals {
        public List<Optional<String>> texts;
        public List<Optional<Item>> items;
        public List<OptionalInt> ints;
        public Optional<Item> one;
    }

    /** An Optional is written as its content wherever it sits: in a list as in a field. */
    @Test
    void optionalsInListsRoundTripThroughToon() {
        Optionals o = new Optionals();
        o.texts = new ArrayList<>(Arrays.asList(Optional.of("x"), Optional.empty()));
        o.items = new ArrayList<>(Arrays.asList(Optional.of(new Item("bolt")), Optional.empty()));
        o.ints = new ArrayList<>(Arrays.asList(OptionalInt.of(7), OptionalInt.empty()));
        o.one = Optional.of(new Item("nut"));
        Optionals back = toon(o, Optionals.class);
        assertEquals(Arrays.asList(Optional.of("x"), Optional.empty()), back.texts);
        assertEquals("bolt", back.items.get(0).get().name);
        assertFalse(back.items.get(1).isPresent());
        assertEquals(Arrays.asList(OptionalInt.of(7), OptionalInt.empty()), back.ints);
        assertEquals("nut", back.one.get().name);

        List<Object> plain = new ArrayList<>(Arrays.asList(Optional.of("x"), Optional.empty(), "y"));
        assertEquals("[3]: x,null,y", JsonIo.toToon(plain, null).trim());
    }

    // ---------- A user's writer: what it writes, the fields do not reach ----------

    /** Its writer writes {@code hidden}, which is transient: json-io's own field walk does not reach it. */
    public static class Box {
        transient Item hidden;
        transient Box next;
    }

    public static class Pair {
        public Box box;
        public Item item;
    }

    public static class BoxWriter implements JsonClassWriter<Box> {
        @Override
        public void write(Box box, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("item", box.hidden, Object.class);
            context.writeObjectField("next", box.next, Object.class);
        }

        @Override
        public Object toData(Box box) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("item", box.hidden);
            data.put("next", box.next);
            return data;
        }
    }

    public static class BoxFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            Box box = new Box();
            box.hidden = (Item) resolver.resolveHeldValue(jObj, jObj.get("item"), Item.class,
                    (built, value) -> ((Box) built).hidden = (Item) value);
            box.next = (Box) resolver.resolveHeldValue(jObj, jObj.get("next"), Box.class,
                    (built, value) -> ((Box) built).next = (Box) value);
            return box;
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    private static final WriteOptions BOX_WRITE = new WriteOptionsBuilder()
            .addCustomWrittenClass(Box.class, new BoxWriter()).build();
    private static final ReadOptions BOX_READ = new ReadOptionsBuilder()
            .addClassFactory(Box.class, new BoxFactory()).build();

    /** The Item reached only through the writer and also through a field is written once and read back as one. */
    @Test
    void anObjectAWriterWritesIsKeptShared() {
        Item shared = new Item("bolt");
        Pair pair = new Pair();
        pair.box = new Box();
        pair.box.hidden = shared;
        pair.item = shared;
        String json = JsonIo.toJson(pair, BOX_WRITE);
        Pair back = JsonIo.toJava(json, BOX_READ).asClass(Pair.class);
        assertSame(back.item, back.box.hidden, json);
        assertEquals("bolt", back.item.name);
    }

    /** A cycle through objects the writer writes reads back, instead of being written until the stack overflows. */
    @Test
    void aCycleThroughWhatAWriterWritesReadsBack() {
        Box first = new Box();
        Box second = new Box();
        first.next = second;
        second.next = first;
        first.hidden = new Item("a");
        second.hidden = new Item("b");
        Box back = JsonIo.toJava(JsonIo.toJson(first, BOX_WRITE), BOX_READ).asClass(Box.class);
        assertEquals("b", back.next.hidden.name);
        assertSame(back, back.next.next);
    }

    // ---------- A factory reads TOON as it reads JSON, through the Resolver's read methods ----------

    /** Its writer writes a list and an array; its factory reads them with readList and readArray. */
    public static class Bag {
        final String label;
        final List<Item> items;
        final String[] tags;

        Bag(String label, List<Item> items, String[] tags) {
            this.label = label;
            this.items = items;
            this.tags = tags;
        }
    }

    public static class Satchel {
        public Bag bag;
    }

    public static class BagWriter implements JsonClassWriter<Bag> {
        @Override
        public void write(Bag bag, boolean showType, JsonGenerator gen, WriterContext context) throws IOException {
            context.writeObjectField("label", bag.label, String.class);
            context.writeObjectField("items", bag.items, Object.class);
            context.writeObjectField("tags", bag.tags, String[].class);
        }

        @Override
        public Object toData(Bag bag) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("label", bag.label);
            data.put("items", bag.items);
            data.put("tags", bag.tags);
            return data;
        }
    }

    public static class BagFactory implements ClassFactory {
        @Override
        public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
            List<Item> items = new ArrayList<>();
            List<Object> read = resolver.readList(jObj, "items");
            for (Object item : read) {
                items.add(item instanceof Item ? (Item) item : (Item) resolver.toJava(Item.class, item));
            }
            return new Bag(resolver.readString(jObj, "label"), items, resolver.readArray(jObj, "tags", String[].class));
        }

        @Override
        public boolean isObjectFinal() {
            return true;
        }
    }

    /**
     * A list inside an object is an array as JSON is read and a List as TOON is read (TOON's lists stay lists, as its
     * Maps mode gives them). The Resolver's read methods take either, so a factory that uses them reads both alike.
     */
    @Test
    void aFactoryReadsToonAsItReadsJsonThroughTheResolver() {
        WriteOptions write = new WriteOptionsBuilder().addCustomWrittenClass(Bag.class, new BagWriter()).build();
        ReadOptions read = new ReadOptionsBuilder().addClassFactory(Bag.class, new BagFactory()).build();
        Satchel satchel = new Satchel();
        satchel.bag = new Bag("L", new ArrayList<>(Arrays.asList(new Item("a"), new Item("b"))),
                new String[] {"x", "y"});
        Satchel fromJson = JsonIo.toJava(JsonIo.toJson(satchel, write), read).asClass(Satchel.class);
        Satchel fromToon = JsonIo.fromToon(JsonIo.toToon(satchel, write), read).asClass(Satchel.class);
        for (Satchel back : new Satchel[] {fromJson, fromToon}) {
            assertEquals("L", back.bag.label);
            assertEquals("a", back.bag.items.get(0).name);
            assertEquals("b", back.bag.items.get(1).name);
            assertEquals(Arrays.asList("x", "y"), Arrays.asList(back.bag.tags));
        }
    }

    /** TOON writes the writer's data as the object's fields: the factory reads them back. */
    @Test
    void aWritersDataRoundTripsThroughToon() {
        Pair pair = new Pair();
        pair.box = new Box();
        pair.box.hidden = new Item("bolt");
        pair.item = new Item("nut");
        Pair back = JsonIo.fromToon(JsonIo.toToon(pair, BOX_WRITE), BOX_READ).asClass(Pair.class);
        assertEquals("bolt", back.box.hidden.name);
        assertEquals("nut", back.item.name);
    }
}
