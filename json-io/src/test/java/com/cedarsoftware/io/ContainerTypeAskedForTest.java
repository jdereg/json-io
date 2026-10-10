package com.cedarsoftware.io;

import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractQueue;
import java.util.AbstractSequentialList;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

import com.cedarsoftware.util.ConcurrentHashMapNullSafe;
import com.cedarsoftware.util.ConcurrentNavigableMapNullSafe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A map or collection type json-io cannot instantiate (an interface, an abstract class) is built as the Converter
 * builds it -- a {@code ConcurrentMap} is a {@code ConcurrentHashMapNullSafe} (JSON carries null values), a
 * {@code BlockingQueue} a {@code LinkedBlockingQueue} -- wherever it is asked for: at the root, in a field, in JSON and
 * in TOON. They failed ("Cannot instantiate interface"). And the class asked for is the one built where TOON's reader
 * builds a container itself (a complex-key map, a root list read as a {@code LinkedList}): it was a LinkedHashMap or
 * an ArrayList in its place.
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
class ContainerTypeAskedForTest {

    public static class Maps {
        public ConcurrentMap<String, Integer> concurrent;
        public ConcurrentNavigableMap<String, Integer> concurrentNavigable;
        public AbstractMap<String, Integer> abstractMap;
    }

    @Test
    void mapInterfacesAreBuiltEverywhere() {
        ConcurrentMap<?, ?> root = JsonIo.toJava("{\"a\":1,\"n\":null}", null).asClass(ConcurrentMap.class);
        assertInstanceOf(ConcurrentHashMapNullSafe.class, root);
        assertTrue(root.containsKey("n"));     // a null value: why the null-safe one
        ConcurrentNavigableMap<?, ?> navigable = JsonIo.toJava("{\"b\":2,\"a\":1}", null)
                .asClass(ConcurrentNavigableMap.class);
        assertInstanceOf(ConcurrentNavigableMapNullSafe.class, navigable);
        assertEquals("a", navigable.firstKey());
        assertInstanceOf(LinkedHashMap.class, JsonIo.toJava("{\"a\":1}", null).asClass(AbstractMap.class));

        String json = "{\"concurrent\":{\"a\":1},\"concurrentNavigable\":{\"a\":1},\"abstractMap\":{\"a\":1}}";
        String toon = "concurrent:\n  a: 1\nconcurrentNavigable:\n  a: 1\nabstractMap:\n  a: 1";
        for (Maps maps : new Maps[] {JsonIo.toJava(json, null).asClass(Maps.class),
                JsonIo.fromToon(toon, null).asClass(Maps.class)}) {
            assertSame(Integer.class, maps.concurrent.get("a").getClass());
            assertInstanceOf(ConcurrentNavigableMap.class, maps.concurrentNavigable);
            assertSame(Integer.class, maps.abstractMap.get("a").getClass());
        }
        assertInstanceOf(ConcurrentHashMapNullSafe.class, JsonIo.fromToon("a: 1", null).asClass(ConcurrentMap.class));
    }

    /**
     * The class built for each interface or abstract container type, at the root and in a field, JSON and TOON --
     * the table in the user guide ("Which class you get for an interface").
     */
    private static final Object[][] BUILT_FOR = {
            {Map.class, LinkedHashMap.class}, {SortedMap.class, TreeMap.class}, {NavigableMap.class, TreeMap.class},
            {ConcurrentMap.class, ConcurrentHashMapNullSafe.class},
            {ConcurrentNavigableMap.class, ConcurrentNavigableMapNullSafe.class}, {AbstractMap.class, LinkedHashMap.class},
            {Collection.class, ArrayList.class}, {List.class, ArrayList.class}, {Set.class, LinkedHashSet.class},
            {SortedSet.class, TreeSet.class}, {NavigableSet.class, TreeSet.class}, {Queue.class, LinkedList.class},
            {Deque.class, ArrayDeque.class}, {BlockingQueue.class, LinkedBlockingQueue.class},
            {BlockingDeque.class, LinkedBlockingDeque.class}, {TransferQueue.class, LinkedTransferQueue.class},
            {AbstractCollection.class, ArrayList.class}, {AbstractList.class, ArrayList.class},
            {AbstractSequentialList.class, LinkedList.class}, {AbstractSet.class, LinkedHashSet.class},
            {AbstractQueue.class, ConcurrentLinkedQueue.class}};

    public static class AllKinds {
        public Map<String, Integer> map;
        public Set<String> set;
        public SortedSet<String> sortedSet;
        public Queue<String> queue;
        public Deque<String> deque;
        public ConcurrentMap<String, Integer> concurrentMap;
        public AbstractQueue<String> abstractQueue;
    }

    @Test
    void theClassBuiltForEachInterfaceIsTheDocumentedOne() {
        for (Object[] row : BUILT_FOR) {
            Class<?> asked = (Class<?>) row[0];
            boolean isMap = Map.class.isAssignableFrom(asked);
            String json = isMap ? "{\"b\":2,\"a\":1}" : "[\"b\",\"a\"]";
            String toon = isMap ? "b: 2\na: 1" : "[2]: b,a";
            assertSame(row[1], JsonIo.toJava(json, null).asClass(asked).getClass(), asked.getName());
            assertSame(row[1], JsonIo.fromToon(toon, null).asClass(asked).getClass(), asked.getName() + " (TOON)");
        }
        String json = "{\"map\":{\"a\":1},\"set\":[\"a\"],\"sortedSet\":[\"a\"],\"queue\":[\"a\"],"
                + "\"deque\":[\"a\"],\"concurrentMap\":{\"a\":1},\"abstractQueue\":[\"a\"]}";
        AllKinds k = JsonIo.toJava(json, null).asClass(AllKinds.class);
        assertSame(LinkedHashMap.class, k.map.getClass());
        assertSame(LinkedHashSet.class, k.set.getClass());
        assertSame(TreeSet.class, k.sortedSet.getClass());
        assertSame(LinkedList.class, k.queue.getClass());
        assertSame(ArrayDeque.class, k.deque.getClass());
        assertSame(ConcurrentHashMapNullSafe.class, k.concurrentMap.getClass());
        assertSame(ConcurrentLinkedQueue.class, k.abstractQueue.getClass());
    }

    public static class Containers {
        public BlockingQueue<String> blockingQueue;
        public BlockingDeque<String> blockingDeque;
        public TransferQueue<String> transferQueue;
        public AbstractCollection<String> abstractCollection;
        public AbstractList<String> abstractList;
        public AbstractSet<String> abstractSet;
        public AbstractQueue<String> abstractQueue;
        public AbstractSequentialList<String> abstractSequentialList;
    }

    @Test
    void collectionInterfacesAreBuiltEverywhere() {
        StringBuilder json = new StringBuilder("{");
        StringBuilder toon = new StringBuilder();
        for (String field : new String[] {"blockingQueue", "blockingDeque", "transferQueue", "abstractCollection",
                "abstractList", "abstractSet", "abstractQueue", "abstractSequentialList"}) {
            json.append(json.length() > 1 ? "," : "").append('"').append(field).append("\":[\"a\",\"b\"]");
            toon.append(field).append("[2]: a,b\n");
        }
        json.append('}');
        for (Containers c : new Containers[] {JsonIo.toJava(json.toString(), null).asClass(Containers.class),
                JsonIo.fromToon(toon.toString(), null).asClass(Containers.class)}) {
            assertTrue(c.blockingQueue.offer("c"));      // an unbounded LinkedBlockingQueue
            assertEquals("b", c.blockingDeque.getLast());
            assertEquals(2, c.transferQueue.size());
            assertEquals(2, c.abstractCollection.size());
            assertEquals("b", c.abstractList.get(1));
            assertTrue(c.abstractSet.contains("a"));
            assertEquals("a", c.abstractQueue.peek());
            assertInstanceOf(LinkedList.class, c.abstractSequentialList);
        }
        BlockingQueue<String> root = JsonIo.toJava("[\"a\",\"b\"]", null)
                .asType(new TypeHolder<BlockingQueue<String>>() { });
        assertTrue(root.offer("c"));
        assertInstanceOf(LinkedList.class, JsonIo.toJava("[\"a\"]", null).asClass(AbstractSequentialList.class));
    }

    /** A TOON list at the root, read as a list class: that class, not an ArrayList. */
    @Test
    void toonRootListIsTheListClassAskedFor() {
        for (Class<?> listClass : new Class<?>[] {LinkedList.class, Vector.class, Stack.class,
                CopyOnWriteArrayList.class}) {
            Collection<?> back = (Collection<?>) JsonIo.fromToon("[2]: a,b", null).asClass(listClass);
            assertSame(listClass, back.getClass());
            assertEquals(2, back.size());
        }
    }

    public static class Key {
        public String name;
    }

    /** A map whose keys are objects, written by TOON as key/value entries, read as an interface: built as above. */
    @Test
    void toonComplexKeyMapIsTheMapTypeAskedFor() {
        String toon = "[1]:\n  - $key:\n      name: k\n    $value: v";
        Map<?, ?> back = (Map<?, ?>) JsonIo.fromToon(toon, null).asClass(ConcurrentMap.class);
        assertInstanceOf(ConcurrentHashMapNullSafe.class, back);
        assertEquals("v", back.values().iterator().next());
    }

    public interface Registry extends Map<String, Object> {
    }

    /** A map type no class json-io knows can be: an error that says so, and what to do. */
    @Test
    void anInterfaceNoKnownClassIsSaysSo() {
        JsonIoException e = assertThrows(JsonIoException.class,
                () -> JsonIo.toJava("{\"a\":1}", null).asClass(Registry.class));
        assertTrue(e.getMessage().contains(Registry.class.getName()), e.getMessage());
    }
}
