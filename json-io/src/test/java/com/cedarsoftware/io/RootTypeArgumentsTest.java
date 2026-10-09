package com.cedarsoftware.io;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The type arguments of the type asked for at the root -- {@code asType(new TypeHolder<List<Employee>>(){})}, the
 * README's example -- are applied to what is read, as a field's are: a list's elements, a map's keys and values,
 * nested. Plain JSON (no {@code @type}: Jackson's, a REST service's) came back with its objects as maps, its numbers
 * as Longs and Doubles, and a map's list values as {@code Object[]}.
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
class RootTypeArgumentsTest {

    public static class Employee {
        public String name;
    }

    public static class Department<E> {
        public String name;
        public List<E> staff;
    }

    private static <T> T json(String json, TypeHolder<T> type) {
        return JsonIo.toJava(json, null).asType(type);
    }

    private static <T> T toon(String toon, TypeHolder<T> type) {
        return JsonIo.fromToon(toon, null).asType(type);
    }

    @Test
    void aListsElementsAreItsTypeArgument() {
        String employees = "[{\"name\":\"ann\"},{\"name\":\"bob\"}]";
        List<Employee> list = json(employees, new TypeHolder<List<Employee>>() { });
        assertEquals("bob", list.get(1).name);
        ArrayList<Employee> arrayList = json(employees, new TypeHolder<ArrayList<Employee>>() { });
        assertEquals("ann", arrayList.get(0).name);
        Set<Employee> set = json(employees, new TypeHolder<Set<Employee>>() { });
        assertInstanceOf(LinkedHashSet.class, set);
        assertEquals("ann", set.iterator().next().name);
        Deque<Employee> deque = json(employees, new TypeHolder<Deque<Employee>>() { });
        assertInstanceOf(ArrayDeque.class, deque);
        assertEquals("bob", deque.getLast().name);
        Collection<Employee> collection = json(employees, new TypeHolder<Collection<Employee>>() { });
        assertEquals("ann", collection.iterator().next().name);

        // The JSON's own type (an ArrayList) with the arguments asked for
        List<Employee> typed = json("{\"@type\":\"ArrayList\",\"@items\":[{\"name\":\"cy\"}]}",
                new TypeHolder<List<Employee>>() { });
        assertEquals("cy", typed.get(0).name);
    }

    @Test
    void scalarElementsAreConvertedToTheTypeArgument() {
        List<Integer> ints = json("[1,2]", new TypeHolder<List<Integer>>() { });
        assertEquals(Arrays.asList(1, 2), ints);
        assertSame(Integer.class, ints.get(0).getClass());
        List<BigDecimal> decimals = json("[1.5]", new TypeHolder<List<BigDecimal>>() { });
        assertEquals(new BigDecimal("1.5"), decimals.get(0));
        List<List<Integer>> nested = json("[[1,2],[3]]", new TypeHolder<List<List<Integer>>>() { });
        assertSame(Integer.class, nested.get(1).get(0).getClass());

        List<Integer> fromToon = toon("[2]: 1,2", new TypeHolder<List<Integer>>() { });
        assertSame(Integer.class, fromToon.get(0).getClass());
    }

    @Test
    void aMapsKeysAndValuesAreItsTypeArguments() {
        Map<String, Integer> map = json("{\"a\":1}", new TypeHolder<Map<String, Integer>>() { });
        assertSame(Integer.class, map.get("a").getClass());
        SortedMap<String, Integer> sorted = json("{\"b\":2,\"a\":1}", new TypeHolder<SortedMap<String, Integer>>() { });
        assertInstanceOf(TreeMap.class, sorted);
        assertSame(Integer.class, sorted.get(sorted.firstKey()).getClass());

        Map<Integer, Employee> keys = json("{\"@keys\":[1],\"@items\":[{\"name\":\"ann\"}]}",
                new TypeHolder<Map<Integer, Employee>>() { });
        assertEquals("ann", keys.get(1).name);

        Map<String, Integer> fromToon = toon("a: 1", new TypeHolder<Map<String, Integer>>() { });
        assertSame(Integer.class, fromToon.get("a").getClass());
    }

    /** An interface or abstract class asked for, with its arguments: built as json-io builds it, its contents typed. */
    @Test
    void abstractContainersAskedForWithArguments() {
        java.util.concurrent.ConcurrentNavigableMap<String, Integer> navigable = json("{\"b\":2,\"a\":1}",
                new TypeHolder<java.util.concurrent.ConcurrentNavigableMap<String, Integer>>() { });
        assertEquals("a", navigable.firstKey());
        assertSame(Integer.class, navigable.get("a").getClass());
        java.util.AbstractList<Integer> numbers = json("[1,2]", new TypeHolder<java.util.AbstractList<Integer>>() { });
        assertSame(Integer.class, numbers.get(0).getClass());
        java.util.LinkedList<Integer> linked = toon("[2]: 1,2", new TypeHolder<java.util.LinkedList<Integer>>() { });
        assertSame(Integer.class, linked.getFirst().getClass());
    }

    /** The user guide's example: a map of lists of a generic class. Its lists came back as Object[]. */
    @Test
    void nestedArgumentsReachTheBottom() {
        Map<String, List<Employee>> byDept = json("{\"k\":[{\"name\":\"ann\"}]}",
                new TypeHolder<Map<String, List<Employee>>>() { });
        assertEquals("ann", byDept.get("k").get(0).name);
        Map<String, List<Integer>> numbers = json("{\"k\":[1]}", new TypeHolder<Map<String, List<Integer>>>() { });
        assertSame(Integer.class, numbers.get("k").get(0).getClass());

        Map<String, List<Department<Employee>>> org = json(
                "{\"k\":[{\"name\":\"d\",\"staff\":[{\"name\":\"ann\"}]}]}",
                new TypeHolder<Map<String, List<Department<Employee>>>>() { });
        assertEquals("ann", org.get("k").get(0).staff.get(0).name);

        Map<String, List<Integer>> fromToon = toon("k[1]: 1", new TypeHolder<Map<String, List<Integer>>>() { });
        assertSame(Integer.class, fromToon.get("k").get(0).getClass());
        // keys that are not text: the $keys / $items form
        Map<Integer, List<Integer>> keyed = toon("$keys[1]: 7\n$items[1]:\n  - [2]: 1,2",
                new TypeHolder<Map<Integer, List<Integer>>>() { });
        assertSame(Integer.class, keyed.get(7).get(1).getClass());
    }
}
