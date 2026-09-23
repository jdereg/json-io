package com.cedarsoftware.io.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.ConcurrentHashMapNullSafe;
import com.cedarsoftware.util.ConcurrentList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every json-io container's {@code toString()} must terminate on ANY cycle, not only on direct self-containment.
 * <p>
 * These are the containers json-io hands back when a read target is an unmodifiable or singleton JDK type
 * ({@code Collections.unmodifiableMap}, {@code List.of}, {@code Collections.singletonList} ...), so a cyclic
 * graph read from JSON puts them straight into user code. Before this fix each one died with
 * {@link StackOverflowError} on a cycle, for one of two reasons:
 * <ul>
 *   <li>The {@code Sealable*} wrappers delegated {@code toString()} to the container they wrap. The wrapped
 *       container's guard compares against itself, never the wrapper, so even a wrapper holding ITSELF
 *       overflowed.</li>
 *   <li>{@code SingletonMap} and {@code SingletonList} had no {@code toString()} at all. {@code Object.toString()}
 *       prints the hash code, and their {@code hashCode()} hashes the element -- which, in a cycle, hashes the
 *       singleton again.</li>
 * </ul>
 * The entries the maps hand out had the same gap: {@code SealableSet.SealAwareEntry} had no {@code toString()}, so it
 * printed {@code SealableSet$SealAwareEntry@1d} and overflowed on a cyclic value, and {@code SingletonMap}'s entry
 * printed {@code SingletonMap$1@2810f49c}.
 * <p>
 * The rule these tests pin is java-util's: a container already being rendered further up the current path
 * renders as {@code (cycle)}; direct self-containment keeps the JDK's {@code (this Map)} /
 * {@code (this Collection)}; and anything that is not a cycle renders exactly as the JDK renders it.
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
class ContainerToStringCycleTest {

    private static final Supplier<Boolean> OPEN = () -> false;

    // Orders by identity, so a navigable set can hold maps and lists (which are not Comparable)
    private static final Comparator<Object> BY_IDENTITY =
            (a, b) -> Integer.compare(System.identityHashCode(a), System.identityHashCode(b));

    // ---------------------------------------------------------------------------- the containers

    static Stream<Arguments> maps() {
        return Stream.of(
                Arguments.of("SealableMap (as the reader builds it)", (Supplier<Map<Object, Object>>) () -> new SealableMap<>(OPEN)),
                Arguments.of("SealableMap over a LinkedHashMap",
                        (Supplier<Map<Object, Object>>) () -> new SealableMap<>(new LinkedHashMap<>(), OPEN)),
                Arguments.of("SealableNavigableMap (as the reader builds it)",
                        (Supplier<Map<Object, Object>>) () -> new SealableNavigableMap<>(OPEN)),
                Arguments.of("SealableNavigableMap over a TreeMap",
                        (Supplier<Map<Object, Object>>) () -> new SealableNavigableMap<>(new TreeMap<>(), OPEN)),
                Arguments.of("SingletonMap", (Supplier<Map<Object, Object>>) SingletonMap::new));
    }

    static Stream<Arguments> collections() {
        return Stream.of(
                Arguments.of("SealableList (as the reader builds it)",
                        (Supplier<Collection<Object>>) () -> new SealableList<>(OPEN)),
                Arguments.of("SealableList over an ArrayList",
                        (Supplier<Collection<Object>>) () -> new SealableList<>(new ArrayList<>(), OPEN)),
                Arguments.of("SealableSet (as the reader builds it)",
                        (Supplier<Collection<Object>>) () -> new SealableSet<>(OPEN)),
                Arguments.of("SealableSet over a LinkedHashSet",
                        (Supplier<Collection<Object>>) () -> new SealableSet<>(new LinkedHashSet<>(), OPEN)),
                Arguments.of("SealableNavigableSet",
                        (Supplier<Collection<Object>>) () -> new SealableNavigableSet<>(BY_IDENTITY, OPEN)),
                Arguments.of("SingletonList", (Supplier<Collection<Object>>) SingletonList::new),
                Arguments.of("SingletonSet", (Supplier<Collection<Object>>) SingletonSet::new));
    }

    // ------------------------------------------------------------------- maps: every cycle shape

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void mapHoldingItselfKeepsTheJdkMarker(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> map = factory.get();
        map.put("k", map);
        assertEquals("{k=(this Map)}", map.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void mapCycleThroughAJdkMapTerminates(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> map = factory.get();
        Map<Object, Object> hop = new LinkedHashMap<>();
        map.put("k", hop);
        hop.put("back", map);
        assertEquals("{k={back=(cycle)}}", map.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void mapCycleThroughAJdkListTerminates(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> map = factory.get();
        List<Object> hop = new ArrayList<>();
        map.put("k", hop);
        hop.add(map);
        assertEquals("{k=[(cycle)]}", map.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void mapCycleThroughAnotherOfTheSameTypeTerminates(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> a = factory.get();
        Map<Object, Object> b = factory.get();
        a.put("k", b);
        b.put("k", a);
        assertEquals("{k={k=(cycle)}}", a.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void mapCycleThroughAJavaUtilMapTerminates(String name, Supplier<Map<Object, Object>> factory) {
        // The loop leaves json-io and comes back: a java-util map renders by its own toString(), which must
        // still bring the walk back to the json-io container it started from.
        Map<Object, Object> map = factory.get();
        Map<Object, Object> compact = new CompactMap<>();
        map.put("k", compact);
        compact.put("back", map);
        assertEquals("{k={back=(cycle)}}", map.toString());
    }

    // ------------------------------------------------------------ collections: every cycle shape

    @ParameterizedTest(name = "{0}")
    @MethodSource("collections")
    void collectionHoldingItselfKeepsTheJdkMarker(String name, Supplier<Collection<Object>> factory) {
        Collection<Object> col = factory.get();
        col.add(col);
        assertEquals("[(this Collection)]", col.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("collections")
    void collectionCycleThroughAJdkMapTerminates(String name, Supplier<Collection<Object>> factory) {
        Collection<Object> col = factory.get();
        Map<Object, Object> hop = new LinkedHashMap<>();
        col.add(hop);           // added while empty, so a hashing container can hash it
        hop.put("back", col);
        assertEquals("[{back=(cycle)}]", col.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("collections")
    void collectionCycleThroughAJdkListTerminates(String name, Supplier<Collection<Object>> factory) {
        Collection<Object> col = factory.get();
        List<Object> hop = new ArrayList<>();
        col.add(hop);
        hop.add(col);
        assertEquals("[[(cycle)]]", col.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("collections")
    void collectionCycleThroughAnotherOfTheSameTypeTerminates(String name, Supplier<Collection<Object>> factory) {
        Collection<Object> a = factory.get();
        Collection<Object> b = factory.get();
        a.add(b);
        b.add(a);
        assertEquals("[[(cycle)]]", a.toString());
    }

    // --------------------------------------------------------------------- entries from the views

    @Test
    void aMapEntryPrintsAsTheEntryItWraps() {
        Map<Object, Object> backing = new LinkedHashMap<>();
        backing.put("k", "v");
        backing.put(null, null);
        SealableMap<Object, Object> map = new SealableMap<>(backing, OPEN);
        List<String> printed = new ArrayList<>();
        for (Map.Entry<Object, Object> e : map.entrySet()) {
            printed.add(e.toString());
        }
        assertEquals(Arrays.asList("k=v", "null=null"), printed);

        SealableNavigableMap<String, Object> sorted = new SealableNavigableMap<>(new TreeMap<>(), OPEN);
        sorted.put("a", 1);
        assertEquals("a=1", sorted.firstEntry().toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void aMapEntryWhoseValueHoldsTheMapTerminates(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> map = factory.get();
        map.put("k", map);
        assertEquals("k={k=(this Map)}", map.entrySet().iterator().next().toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maps")
    void aMapEntryWhoseValueLoopsBackTerminates(String name, Supplier<Map<Object, Object>> factory) {
        Map<Object, Object> map = factory.get();
        Map<Object, Object> hop = new LinkedHashMap<>();
        map.put("k", hop);
        hop.put("back", map);
        assertEquals("k={back={k=(cycle)}}", map.entrySet().iterator().next().toString());
    }

    @Test
    void aNavigableMapsFirstEntryTerminates() {
        SealableNavigableMap<String, Object> map = new SealableNavigableMap<>(new TreeMap<>(), OPEN);
        map.put("k", map);
        assertEquals("k={k=(this Map)}", map.firstEntry().toString());
    }

    // ------------------------------------------------------------------------- the loop budget

    @Test
    void aDenselyCrossLinkedGraphIsBounded() {
        // A clique: twelve containers, each holding all the others. Every route that does not repeat one is a
        // distinct path -- about 11! of them, which exhausts the heap. Once the render has closed a loop and spent its
        // budget, it stops expanding.
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            List<Map<Object, Object>> maps = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                maps.add(new SealableMap<>(new LinkedHashMap<>(), OPEN));
            }
            for (int i = 0; i < 12; i++) {
                for (int j = 0; j < 12; j++) {
                    if (i != j) {
                        maps.get(i).put("m" + j, maps.get(j));
                    }
                }
            }
            String s = maps.get(0).toString();
            assertTrue(s.startsWith("{") && s.endsWith("}"), "still a well-formed render");
            assertTrue(s.contains("(cycle)"));
            assertTrue(s.contains("..."), "the budget was reached");
            assertTrue(s.length() < 5_000_000, "bounded: " + s.length() + " chars");
        });
    }

    @Test
    void aGraphTheJdkCanPrintIsNeverCut() {
        // Far past the budget, but no loop has closed -- holding itself is not one, the JDK prints that -- so the
        // output is the JDK's, all of it.
        Map<Object, Object> twin = new LinkedHashMap<>();
        SealableMap<Object, Object> map = new SealableMap<>(new LinkedHashMap<>(), OPEN);
        for (int i = 0; i < 150_000; i++) {
            twin.put("k" + i, Arrays.asList(i, "v"));
            map.put("k" + i, Arrays.asList(i, "v"));
        }
        twin.put("self", twin);
        map.put("self", map);
        assertEquals(twin.toString(), map.toString());
    }

    @Test
    void aSparseLoopRendersInFull() {
        // A tree whose nodes point back to their parent is cyclic, but each node is reached by one route only
        SealableList<Object> nodes = new SealableList<>(new ArrayList<>(), OPEN);
        for (int i = 0; i < 3000; i++) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("name", "n" + i);
            node.put("owner", nodes);
            nodes.add(node);
        }
        String s = nodes.toString();
        assertFalse(s.contains("..."));
        assertEquals(3000, s.split("name=n", -1).length - 1);
    }

    // ----------------------------------------------------------------------- what must NOT change

    @Test
    void wrappersRenderExactlyAsTheContainerTheyWrap() {
        Map<Object, Object> nested = new TreeMap<>();
        nested.put("m", 1);
        nested.put("n", null);

        Map<Object, Object> backing = new LinkedHashMap<>();
        backing.put("string", "text");
        backing.put("null", null);
        backing.put(null, "nullKey");
        backing.put("nested", nested);
        backing.put("list", new ArrayList<>(Arrays.asList(1, "two", null, new ArrayList<>())));
        backing.put("empty", new LinkedHashMap<>());
        backing.put("array", new int[] {1, 2, 3});
        assertEquals(backing.toString(), new SealableMap<>(backing, OPEN).toString());

        TreeMap<Object, Object> sorted = new TreeMap<>(nested);
        assertEquals(sorted.toString(), new SealableNavigableMap<>(sorted, OPEN).toString());

        List<Object> list = new ArrayList<>(Arrays.asList("a", null, nested, new int[] {4}));
        assertEquals(list.toString(), new SealableList<>(list, OPEN).toString());

        LinkedHashSet<Object> set = new LinkedHashSet<>(Arrays.asList("x", "y", nested));
        assertEquals(set.toString(), new SealableSet<>(set, OPEN).toString());
    }

    @Test
    void wrappersBuiltByTheReaderRenderAsTheirJavaUtilContainerDoes() {
        // The reader's wrappers sit over java-util's concurrent containers, whose own toString() is the JDK format
        SealableMap<Object, Object> map = new SealableMap<>(OPEN);
        Map<Object, Object> expectedMap = new ConcurrentHashMapNullSafe<>();
        SealableList<Object> list = new SealableList<>(OPEN);
        List<Object> expectedList = new ConcurrentList<>();
        for (int i = 0; i < 40; i++) {
            map.put("key" + i, i);
            expectedMap.put("key" + i, i);
            list.add(i % 3 == 0 ? null : "item" + i);
            expectedList.add(i % 3 == 0 ? null : "item" + i);
        }
        assertEquals(expectedMap.toString(), map.toString());
        assertEquals(expectedList.toString(), list.toString());
    }

    @Test
    void singletonsRenderAsTheJdkSingletonsTheyStandIn() {
        // The reader substitutes these for Collections.singletonMap / singletonList / singleton, so a round-tripped
        // value must print as the original did -- they used to print as SingletonList@1b6d3586.
        assertEquals(Collections.singletonMap("k", "v").toString(), new SingletonMap<>("k", "v").toString());
        assertEquals(Collections.singletonList("x").toString(), new SingletonList<>("x").toString());
        assertEquals(Collections.singleton("x").toString(), new SingletonSet<>("x").toString());
        assertEquals("{}", new SingletonMap<>().toString());
        assertEquals("[]", new SingletonList<>().toString());
        assertEquals("[]", new SingletonSet<>().toString());
    }

    @Test
    void aSharedNodeIsADiamondNotACycle() {
        List<Object> shared = new ArrayList<>(Collections.singletonList(1));
        SealableList<Object> list = new SealableList<>(new ArrayList<>(Arrays.asList(shared, shared)), OPEN);
        assertEquals("[[1], [1]]", list.toString());
    }

    @Test
    void aFailedRenderLeavesNothingBehindOnTheThread() {
        // A leaf that throws aborts the render. The next render on this thread must not inherit the aborted walk's
        // path and misreport an ordinary container as a cycle.
        List<Object> inner = new ArrayList<>();
        inner.add(new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        });
        SealableList<Object> list = new SealableList<>(new ArrayList<>(Collections.singletonList(inner)), OPEN);
        assertThrows(IllegalStateException.class, list::toString);

        inner.clear();
        inner.add("ok");
        assertEquals("[[ok]]", list.toString());
    }
}
