package com.cedarsoftware.io;

import java.awt.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.cedarsoftware.io.models.ModelHoldingSingleHashMap;
import com.cedarsoftware.util.CompactMap;
import com.cedarsoftware.util.CompactSet;
import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static com.cedarsoftware.util.CollectionUtilities.setOf;
import static com.cedarsoftware.util.MapUtilities.mapOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * @author Kenny Partlow (kpartlow@gmail.com)
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
class MapsTest
{
    public static WriteOptions showTypeNever = new WriteOptionsBuilder().showTypeInfoNever().build();
    public static WriteOptions showTypeAlways = new WriteOptionsBuilder().showTypeInfoNever().build();
    public static WriteOptions showTypeMinimal = new WriteOptionsBuilder().showTypeInfoNever().build();

    private static Stream<Arguments> showTypeInfoVariants() {
        return Stream.of(
                Arguments.of(showTypeNever),
                Arguments.of(showTypeMinimal),
                Arguments.of(showTypeAlways)
        );
    }

    @ParameterizedTest
    @MethodSource("showTypeInfoVariants")
    void testEmptyMap_whenMapIsDefaultMapType_doesNotShowType_noMatterTheWriteOptions(WriteOptions options) {
        Map<Object, Object> map = new JsonObject();
        String json = TestUtil.toJson(map, options);
        assertThat(json).isEqualTo("{}");
    }

    private static Stream<Arguments> nonDefaultMapTypes() {
        return Stream.of(
                Arguments.of(LinkedHashMap.class),
                Arguments.of(HashMap.class),
                Arguments.of(TreeMap.class)
        );
    }

    @SuppressWarnings("rawtypes")
    @ParameterizedTest
    @MethodSource("nonDefaultMapTypes")
    void testEmptyMap_whenMapIsNotDefaultMap_includesType(Class<? extends Map> c) throws Exception {
        Map map = c.getConstructor().newInstance();
        String json = TestUtil.toJson(map);
        assertThat(json).isEqualTo("{\"@type\":\"" + c.getSimpleName() + "\"}");
    }

    @SuppressWarnings("rawtypes")
    @ParameterizedTest
    @MethodSource("nonDefaultMapTypes")
    void testEmptyMap_whenMapIsNotDefaultMap_and_neverShowTypes_doesNotShowType(Class<? extends Map> c) throws Exception {
        WriteOptions options = new WriteOptionsBuilder().showTypeInfoNever().build();
        Map map = c.getConstructor().newInstance();
        String json = TestUtil.toJson(map, options);
        assertThat(json).isEqualTo("{}");
    }

    @Test
    void testMap()
    {
        ManyMaps obj = new ManyMaps();
        obj.init();
        String jsonOut = TestUtil.toJson(obj);
        ManyMaps root = TestUtil.toObjects(jsonOut, null);
        assertMap(root);
        assert DeepEquals.deepEquals(obj, root);
    }

    private static class EmptyStuff {
        public Enumeration emptyEnumeration;
        public Iterator emptyIterator;
        public ListIterator emptyListIterator;
        public List<String> emptyList;
        public Set<String> emptySet;
        public SortedSet<String> emptySortedSet;
        public NavigableSet<String> emptyNavigableSet;
        public Map<String, Object> emptyMap;
        public SortedMap<String, Object> emptySortedMap;
        public NavigableMap<String, Object> emptyNavigableMap;
    }

    @Test
    void testAllEmptyCollectionTypes()
    {
        EmptyStuff stuff = new EmptyStuff();
        stuff.emptyEnumeration = Collections.emptyEnumeration();
        stuff.emptyIterator = Collections.emptyIterator();
        stuff.emptyListIterator = Collections.emptyListIterator();
        stuff.emptyList = Collections.emptyList();
        stuff.emptySet = Collections.emptySet();
        stuff.emptyNavigableSet = Collections.emptyNavigableSet();
        stuff.emptySortedSet = Collections.emptySortedSet();
        stuff.emptyMap = Collections.emptyMap();
        stuff.emptySortedMap = Collections.emptySortedMap();
        stuff.emptyNavigableMap = Collections.emptyNavigableMap();

        String json = TestUtil.toJson(stuff, new WriteOptionsBuilder().build());
        EmptyStuff stuff2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), EmptyStuff.class);

        try {
            stuff2.emptyList.add("a");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptySet.add("a");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptySortedSet.add("a");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptyNavigableSet.add("a");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptyMap.put("a", "b");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptySortedMap.put("a", "b");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        try {
            stuff2.emptyNavigableMap.put("a", "b");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }

        assert DeepEquals.deepEquals(stuff, stuff2);
    }
    
    private static class SingletonStuff {
        public List<String> singletonList;
        public Set<String> singletonSet;
        public Map<String, Object> singletonMap;
    }

    @Test
    void testAllSingletonCollectionTypes()
    {
        SingletonStuff stuff = new SingletonStuff();
        stuff.singletonList = Collections.singletonList("Ethereum");
        stuff.singletonSet = Collections.singleton("Bitcoin");
        stuff.singletonMap = Collections.singletonMap("Solana", "Bitcoin");

        String json = TestUtil.toJson(stuff, new WriteOptionsBuilder().build());
        SingletonStuff stuff2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), SingletonStuff.class);

        assert stuff2.singletonList.size() == 1;
        assert stuff2.singletonSet.size() == 1;
        assert stuff2.singletonMap.size() == 1;

        assert stuff2.singletonList.contains("Ethereum");
        assert stuff2.singletonSet.contains("Bitcoin");
        assert stuff2.singletonMap.containsKey("Solana");

        try {
            stuff2.singletonList.add("t-rex");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }
        try {
            stuff2.singletonSet.add("triceratops");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }
        try {
            stuff2.singletonMap.put("dino", "dna");
            fail();
        } catch(UnsupportedOperationException ignored) {
        }
        
        assert DeepEquals.deepEquals(stuff, stuff2);
    }

    private static class UnmodifiableStuff {
        public Collection<String> unmodifiableCollection;
        public List<String> unmodifiableList;
        public Set<String> unmodifiableSet;
        public SortedSet<String> unmodifiableSortedSet;
        public NavigableSet<String> unmodifiableNavigableSet;
        public Map<String, Object> unmodifiableMap;
        public SortedMap<String, Object> unmodifiableSortedMap;
        public NavigableMap<String, Object> unmodifiableNavigableMap;
    }

    @Test
    void testAllUnmodifiableCollectionTypes()
    {
        UnmodifiableStuff stuff = new UnmodifiableStuff();
        stuff.unmodifiableCollection = Collections.unmodifiableCollection(Arrays.asList("foo", "bar"));
        stuff.unmodifiableList = Collections.unmodifiableList(listOf("foo", "bar", "baz", "qux"));
        stuff.unmodifiableSet = Collections.unmodifiableSet(setOf("foo", "bar", "baz", "qux"));
        stuff.unmodifiableMap = Collections.unmodifiableMap(mapOf("foo", "bar", "baz", "qux"));
        SortedSet<String> sortedSet = new TreeSet<>();
        sortedSet.add("foo");
        sortedSet.add("bar");
        sortedSet.add("baz");
        sortedSet.add("qux");
        stuff.unmodifiableSortedSet = Collections.unmodifiableSortedSet(sortedSet);
        NavigableSet<String> navSet = new TreeSet<>();
        navSet.add("foo");
        navSet.add("bar");
        navSet.add("baz");
        navSet.add("qux");
        stuff.unmodifiableNavigableSet = Collections.unmodifiableNavigableSet(navSet);
        SortedMap<String, Object> sortedMap = new TreeMap<>();
        sortedMap.put("foo", "bar");
        sortedMap.put("baz", "qux");
        stuff.unmodifiableSortedMap = Collections.unmodifiableSortedMap(sortedMap);
        NavigableMap<String, Object> navMap = new TreeMap<>();
        navMap.put("foo", "bar");
        navMap.put("baz", "qux");
        stuff.unmodifiableNavigableMap = Collections.unmodifiableNavigableMap(navMap);

        String json = TestUtil.toJson(stuff, new WriteOptionsBuilder().build());
        UnmodifiableStuff stuff2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), UnmodifiableStuff.class);
        Map<String, Object> options = new HashMap<>();
        assert DeepEquals.deepEquals(stuff, stuff2, options);
        assertThrows(UnsupportedOperationException.class, () -> stuff2.unmodifiableCollection.add("a"));
    }

    private static class SynchronizedStuff {
        public Collection<String> synchronizedCollection;
        public List<String> synchronizedList;
        public Set<String> synchronizedSet;
        public SortedSet<String> synchronizedSortedSet;
        public NavigableSet<String> synchronizedNavigableSet;
        public Map<String, Object> synchronizedMap;
        public SortedMap<String, Object> synchronizedSortedMap;
        public NavigableMap<String, Object> synchronizedNavigableMap;
    }

    @Test
    void testAllSynchronizedCollectionTypes()
    {
        SynchronizedStuff stuff = new SynchronizedStuff();
        stuff.synchronizedCollection = Collections.synchronizedCollection(Arrays.asList("foo", "bar"));
        stuff.synchronizedList = Collections.synchronizedList(Arrays.asList("foo", "bar", "baz", "qux"));
        stuff.synchronizedSet = Collections.synchronizedSet(setOf("foo", "bar", "baz", "qux"));
        stuff.synchronizedMap = Collections.synchronizedMap(mapOf("foo", "bar", "baz", "qux"));
        SortedSet<String> sortedSet = new TreeSet<>();
        sortedSet.addAll(Arrays.asList("foo", "bar", "baz", "qux"));
        stuff.synchronizedSortedSet = Collections.synchronizedSortedSet(sortedSet);
        NavigableSet<String> navSet = new TreeSet<>();
        navSet.addAll(Arrays.asList("foo", "bar", "baz", "qux"));
        stuff.synchronizedNavigableSet = Collections.synchronizedNavigableSet(navSet);
        SortedMap<String, Object> sortedMap = new TreeMap<>();
        sortedMap.put("foo", "bar");
        sortedMap.put("baz", "qux");
        stuff.synchronizedSortedMap = Collections.synchronizedSortedMap(sortedMap);
        NavigableMap<String, Object> navMap = new TreeMap<>();
        navMap.put("foo", "bar");
        navMap.put("baz", "qux");
        stuff.synchronizedNavigableMap = Collections.synchronizedNavigableMap(navMap);

        String json = TestUtil.toJson(stuff, new WriteOptionsBuilder().build());
        SynchronizedStuff stuff2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), SynchronizedStuff.class);
        Map<String, Object> options = new HashMap<>();
        assert DeepEquals.deepEquals(stuff, stuff2, options);
    }

    private static class ConcurrentStuff {
        public Collection<String> concurrentCollection;
        public List<String> concurrentList;
        public Set<String> concurrentSet;
        public SortedSet<String> concurrentSortedSet;
        public NavigableSet<String> concurrentNavigableSet;
        public Map<String, Object> concurrentMap;
        public SortedMap<String, Object> concurrentSortedMap;
        public NavigableMap<String, Object> concurrentNavigableMap;
    }

    @Test
    void testAllConcurrentCollectionTypes()
    {
        ConcurrentStuff stuff = new ConcurrentStuff();
        stuff.concurrentCollection = new Vector<>(Arrays.asList("foo", "bar"));
        stuff.concurrentList = new CopyOnWriteArrayList<>(Arrays.asList("foo", "bar", "baz", "qux")); // Using CopyOnWriteArrayList
        stuff.concurrentMap = new ConcurrentHashMap<>();
        stuff.concurrentMap.put("foo", "bar");
        stuff.concurrentMap.put("baz", "qux");
        stuff.concurrentSet = ConcurrentHashMap.newKeySet();
        stuff.concurrentSet.addAll(Arrays.asList("foo", "bar", "baz", "qux"));

        NavigableSet<String> navSet = new TreeSet<>();
        navSet.addAll(Arrays.asList("foo", "bar", "baz", "qux"));
        stuff.concurrentNavigableSet = new ConcurrentSkipListSet<>(navSet);
        
        NavigableMap<String, Object> navMap = new TreeMap<>();
        navMap.put("foo", "bar");
        navMap.put("baz", "qux");
        stuff.concurrentNavigableMap = new ConcurrentSkipListMap<>();
        stuff.concurrentNavigableMap.put("foo", "bar");
        stuff.concurrentNavigableMap.put("baz", "qux");

        String json = TestUtil.toJson(stuff, new WriteOptionsBuilder().build());
        ConcurrentStuff stuff2 = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), ConcurrentStuff.class);

        assert DeepEquals.deepEquals(stuff, stuff2);
    }

    @Test
    void testObject_holdingMap_withStringOfStrings_andAlwaysShowingType() {
        HashMap<String, String> map = new HashMap<>();
        map.put("cn", "name");
        map.put("on", "unger");
        map.put("en", "us");

        ModelHoldingSingleHashMap model = new ModelHoldingSingleHashMap(map);
        String json = TestUtil.toJson(model, new WriteOptionsBuilder().showTypeInfoAlways().build());
        ModelHoldingSingleHashMap actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

        Map<String, String> deserialized = actual.getMap();
        assertThat(deserialized)
                .hasSize(3)
                .containsEntry("cn", "name")
                .containsEntry("on", "unger")
                .containsEntry("en", "us");
    }

    @Test
    void testObject_holdingMap_withStringOfStrings_andMinimalType() {
        HashMap<String, String> map = new HashMap<>();
        map.put("cn", "name");
        map.put("on", "unger");
        map.put("en", "us");

        ModelHoldingSingleHashMap model = new ModelHoldingSingleHashMap(map);
        String json = TestUtil.toJson(model, new WriteOptionsBuilder().build());
        ModelHoldingSingleHashMap actual = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);

        Map<String, String> deserialized = actual.getMap();
        assertThat(deserialized)
                .hasSize(3)
                .containsEntry("cn", "name")
                .containsEntry("on", "unger")
                .containsEntry("en", "us");
    }

    private static void assertMap(ManyMaps root)
    {
        assertEquals(3, root._strings_a.size());
        assertEquals("tiger", root._strings_a.get("woods"));
        assertEquals("phil", root._strings_a.get("mickleson"));
        assertEquals("sergio", root._strings_a.get("garcia"));
        assertTrue(root._strings_b.isEmpty());
        assertNull(root._strings_c);

        assertEquals(2, root._testobjs_a.size());
        assertEquals(root._testobjs_a.get(new TestObject("one")), new TestObject("alpha"));
        assertEquals(root._testobjs_a.get(new TestObject("two")), new TestObject("bravo"));

        assertEquals(1, root._map_col.size());
        Iterator i = root._map_col.keySet().iterator();
        TestObject[] key = (TestObject[]) i.next();
        key[0]._name.equals("earth");
        key[1]._name.equals("jupiter");
        i = root._map_col.values().iterator();
        Collection list = (Collection) i.next();
        list.contains("andromeda");

        // Check value-side of Map with Object[] (special case because Object[]'s @type is never written)
        Object[] catTypes = (Object[]) root._map_col_2.get("cat");
        assertEquals("tiger", catTypes[0]);
        assertEquals("lion", catTypes[1]);
        assertEquals("cheetah", catTypes[2]);
        assertEquals("jaguar", catTypes[3]);

        assertEquals(1, root._map_col_3.size());
        i = root._map_col_3.keySet().iterator();
        Object[] key_a = (Object[]) i.next();
        key_a[0].equals("composite");
        key_a[1].equals("key");
        String value = (String) root._map_col_3.get(key_a);
        assertEquals("value", value);

        assertEquals(2, root._map_obj.size());
        assertEquals(root._map_obj.get(99), 0.123d);
        assertNull(root._map_obj.get(null));

        assertEquals(2, root._map_con.size());
        Map syncMap = Collections.synchronizedMap(new LinkedHashMap<>());
        assertTrue(syncMap.getClass().isAssignableFrom(root._map_con.getClass()));
        i = root._map_con.entrySet().iterator();
        while (i.hasNext())
        {
            Map.Entry e = (Map.Entry) i.next();
            TestObject key1 = (TestObject) e.getKey();
            TestObject value1 = (TestObject) e.getValue();
            if (key1.equals(new TestObject("alpha")))
            {
                assertEquals("one", value1.getName());
            }
            else if (key1.equals(new TestObject("bravo")))
            {
                assertEquals("two", value1.getName());
            }
            else
            {
                fail("Unknown Key");
            }
        }

        assertNotNull(root._typedMap);
        assertEquals(2, root._typedMap.size());
        assertEquals("alpha", root._typedMap.get("a"));
        assertEquals("bravo", root._typedMap.get("b"));
    }

    @Test
    void testReconstituteMap()
    {
        ManyMaps testMap = new ManyMaps();
        testMap.init();
        String json0 = TestUtil.toJson(testMap);
        TestUtil.printLine("json0=" + json0);
        Map testMap2 = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);

        String json1 = TestUtil.toJson(testMap2);
        TestUtil.printLine("json1=" + json1);

        ManyMaps testMap3 = TestUtil.toObjects(json1, null);
        assertMap(testMap3);// Re-written from Map of Maps and re-loaded correctly
        assertEquals(json0, json1);
    }

    @Test
    void testMap2()
    {
        TestObject a = new TestObject("A");
        TestObject b = new TestObject("B");
        TestObject c = new TestObject("C");
        a._other = b;
        b._other = c;
        c._other = a;

        Map map = new HashMap<>();
        map.put(a, b);
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json = " + json);
        map = TestUtil.toObjects(json, null);
        assertNotNull(map);
        assertEquals(1, map.size());
        TestObject bb = (TestObject) map.get(new TestObject("A"));
        assertEquals(bb._other, new TestObject("C"));
        TestObject aa = (TestObject) map.keySet().toArray()[0];
        assertEquals(aa._other, bb);
    }

    @Test
    void testMap3()
    {
        Map map = new HashMap<>();
        map.put("a", "b");
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json = " + json);
        map = TestUtil.toObjects(json, null);
        assertNotNull(map);
        assertEquals(1, map.size());
    }

    @Test
    void testMapArrayKey()
    {
        MapArrayKey m = new MapArrayKey();
        m.setContent(new LinkedHashMap<>());
        Object[] arrayA = new Object[2];
        arrayA[0] = new Point(1, 2);
        arrayA[1] = new Point(10, 20);
        m.getContent().put(arrayA, "foo");
        Object[] arrayB = new Object[2];
        arrayB[0] = new Point(3, 4);
        arrayB[1] = new Point(30, 40);
        m.getContent().put(arrayB, "bar");
        String json = TestUtil.toJson(m);
        MapArrayKey x = TestUtil.toObjects(json, null);

        Iterator<Map.Entry<Object[], String>> i = x.getContent().entrySet().iterator();
        Map.Entry<Object[], String> entry = i.next();

        assertEquals("foo", entry.getValue());
        arrayA = entry.getKey();
        assertEquals(new Point(1, 2), arrayA[0]);
        assertEquals(new Point(10, 20), arrayA[1]);

        entry = i.next();
        assertEquals("bar", entry.getValue());
        arrayB = entry.getKey();
        assertEquals(new Point(3, 4), arrayB[0]);
        assertEquals(new Point(30, 40), arrayB[1]);
    }

    @Test
    void testMapSetKey()
    {
        MapSetKey m = new MapSetKey();
        m.setContent(new LinkedHashMap<>());
        Set<Point> setA = new LinkedHashSet<>();
        setA.add(new Point(1, 2));
        setA.add(new Point(10, 20));
        m.getContent().put(setA, "foo");
        Set<Point> setB = new LinkedHashSet<>();
        setB.add(new Point(3, 4));
        setB.add(new Point(30, 40));
        m.getContent().put(setB, "bar");
        String json = TestUtil.toJson(m);
        MapSetKey x = TestUtil.toObjects(json, null);

        assertEquals("foo", x.getContent().get(setA));
        assertEquals("bar", x.getContent().get(setB));

        m = new MapSetKey();
        m.setContent(new LinkedHashMap<>());
        m.getContent().put(null, null);
        json = TestUtil.toJson(m);
        x = TestUtil.toObjects(json, null);
        assertNull(x.getContent().get(null));

        m = new MapSetKey();
        m.setContent(new LinkedHashMap<>());
        m.getContent().put(new LinkedHashSet<>(), "Fargo");
        json = TestUtil.toJson(m);
        x = TestUtil.toObjects(json, null);
        assertEquals("Fargo", x.getContent().get(new LinkedHashSet<Point>()));
    }

    @Test
    void testMapToMapCompatibility()
    {
        String json0 = "{\"rows\":[{\"columns\":[{\"name\":\"FOO\",\"value\":\"9000\"},{\"name\":\"VON\",\"value\":\"0001-01-01\"},{\"name\":\"BAR\",\"value\":\"0001-01-01\"}]},{\"columns\":[{\"name\":\"FOO\",\"value\":\"9713\"},{\"name\":\"VON\",\"value\":\"0001-01-01\"},{\"name\":\"BAR\",\"value\":\"0001-01-01\"}]}],\"selectedRows\":\"110\"}";
        Map root = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(root);
        Map root2 = TestUtil.toObjects(json1, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertTrue(DeepEquals.deepEquals(root, root2));

        // Will be different because @keys and @items get inserted during processing
        TestUtil.printLine("json0=" + json0);
        TestUtil.printLine("json1=" + json1);
    }

    @Test
    void testMapWithAtType()
    {
        AssignToList atl = new AssignToList();
        String json = "{\"@id\":1,\"@type\":\"java.util.LinkedHashMap\",\"@keys\":[\"1000004947\",\"0000020985\",\"0000029443\",\"0000020994\"],\"@items\":[\"Me\",\"Fox, James\",\"Renewals, CORE\",\"Gade, Raja\"]}";
        Map assignTo = TestUtil.toObjects(json, null);
        assert assignTo instanceof LinkedHashMap;
        atl.setAssignTo(assignTo);
        json = TestUtil.toJson(atl);
        TestUtil.printLine(json);
    }

    @Test
    void testMapWithParameterizedTypes()
    {
        String json = "{\"@type\":\"" + ParameterizedMap.class.getName() + "\", \"content\":{\"foo\":{\"one\":{\"x\":1,\"y\":2},\"two\":{\"x\":10,\"y\":20}},\"bar\":{\"ten\":{\"x\":3,\"y\":4},\"twenty\":{\"x\":30,\"y\":40}}}}";
        ParameterizedMap pCol = TestUtil.toObjects(json, null);
        Map<String, Point> points = pCol.getContent().get("foo");
        assertNotNull(points);
        assertEquals(2, points.size());
        assertEquals(new Point(1, 2), points.get("one"));
        assertEquals(new Point(10, 20), points.get("two"));

        points = pCol.getContent().get("bar");
        assertNotNull(points);
        assertEquals(2, points.size());
        assertEquals(new Point(3, 4), points.get("ten"));
        assertEquals(new Point(30, 40), points.get("twenty"));
    }

    @Test
    void testOddMaps()
    {
        String json = "{\"@type\":\"HashMap\",\"@keys\":null,\"@items\":null}";
        Map map = TestUtil.toObjects(json, null);
        assertTrue(map instanceof HashMap);
        assertTrue(map.isEmpty());

        json = "{\"@type\":\"java.util.HashMap\"}";
        map = TestUtil.toObjects(json, null);
        assertTrue(map instanceof HashMap);
        assertTrue(map.isEmpty());
        
        final String json1 = "{\"@type\":\"HashMap\",\"@keys\":null,\"@items\":[]}";
        Exception e = assertThrows(Exception.class, () -> { TestUtil.toObjects(json1, null);});
        assert e.getMessage().toLowerCase().contains("@keys or @items");
        assert e.getMessage().toLowerCase().contains("empty");

        final String json2 = "{\"@type\":\"HashMap\",\"@keys\":[1,2],\"@items\":[true]}";
        e = assertThrows(Exception.class, () -> { TestUtil.toObjects(json2, null); });
        assert e.getMessage().toLowerCase().contains("must be same length");
    }

    @Test
    void testReconstituteMapEmpty()
    {
        Map map = new LinkedHashMap<>();
        String json0 = TestUtil.toJson(map);
        TestUtil.printLine("json0=" + json0);

        map = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);

        map = TestUtil.toObjects(json1, null);
        assertTrue(map instanceof LinkedHashMap);
        assertTrue(map.isEmpty());
        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteRefMap()
    {
        Map m1 = new HashMap<>();
        Object[] root = new Object[]{m1, m1};
        String json0 = TestUtil.toJson(root);
        TestUtil.printLine("json0=" + json0);

        Object[] array = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(array);
        TestUtil.printLine("json1=" + json1);

        root = TestUtil.toObjects(json1, null);
        assertEquals(2, root.length);
        assertTrue(root[0] instanceof Map);
        assertTrue(((Map) root[0]).isEmpty());
        assertTrue(root[1] instanceof Map);
        assertTrue(((Map) root[1]).isEmpty());
        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteMapSimple()
    {
        SimpleMapTest smt = new SimpleMapTest();
        smt.getMap().put("a", "alpha");
        String json0 = TestUtil.toJson(smt);
        TestUtil.printLine("json0=" + json0);

        Map result = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(result);
        TestUtil.printLine("json1=" + json1);

        SimpleMapTest mapTest = TestUtil.toObjects(json1, null);
        assertTrue(DeepEquals.deepEquals(mapTest.getMap(), smt.getMap()));
        assertEquals(json0, json1);
    }

    @Test
    void testMapFromUnknown()
    {
        Map map = TestUtil.toObjects("{\"a\":\"alpha\", \"b\":\"beta\"}", new ReadOptionsBuilder().unknownTypeClass(ConcurrentHashMap.class).build(), null);
        assert map instanceof ConcurrentHashMap;
        assert map.size() == 2;
        assert map.get("a").equals("alpha");
        assert map.get("b").equals("beta");

        map = TestUtil.toObjects("{\"a\":\"alpha\", \"b\":\"beta\"}", new ReadOptionsBuilder().unknownTypeClass(ConcurrentSkipListMap.class).build(), null);
        assert map instanceof ConcurrentSkipListMap;
        assert map.size() == 2;
        assert map.get("a").equals("alpha");
        assert map.get("b").equals("beta");

        map = TestUtil.toObjects("{\"a\":\"alpha\", \"b\":\"beta\"}", null);
        assert map instanceof Map;// Default 'Map' type
        assert map.size() == 2;
        assert map.get("a").equals("alpha");
        assert map.get("b").equals("beta");
    }

    @Test
    void testMapWithQuoteInKey()
    {
        Map<Serializable, Long> quoteInKeyMap = new LinkedHashMap<>(3);
        quoteInKeyMap.put(0L, 0L);
        quoteInKeyMap.put("\"one\"", 1L);
        quoteInKeyMap.put("\"two\"", 2L);
        String json = TestUtil.toJson(quoteInKeyMap);
        Map ret = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert ret.size() == 3;

        assert ret.get(0L).equals(0L);
        assert ret.get("\"one\"").equals(1L);
        assert ret.get("\"two\"").equals(2L);

        Map<String, Long> stringKeys = new LinkedHashMap<>(3);
        stringKeys.put("\"zero\"", 0L);
        stringKeys.put("\"one\"", 1L);
        stringKeys.put("\"two\"", 2L);
        json = TestUtil.toJson(stringKeys);
        ret = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert ret.size() == 3;

        assert ret.get("\"zero\"").equals(0L);
        assert ret.get("\"one\"").equals(1L);
        assert ret.get("\"two\"").equals(2L);
    }

    @Test
    void testMapWithPrimitiveValues()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.clear();
        cal.set(2017, 5, 10, 8, 1, 32);
        final Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("Byte", (byte) 79);
        map.put("Integer", 179);
        map.put("Short", (short) 179);
        map.put("Float", 179.0f);
        map.put("date", cal.getTime());
        map.put("Long", 179L);
        map.put("Double", 179.0);
        map.put("Character", 'z');
        map.put("Boolean", Boolean.FALSE);
        map.put("BigInteger", new BigInteger("55"));
        map.put("BigDecimal", new BigDecimal("3.33333"));

        final String str = TestUtil.toJson(map, new WriteOptionsBuilder().isoDateFormat().build());

        TestUtil.printLine(str + "\n");

        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        final Map<String, Object> map2 = TestUtil.toObjects(str, readOptions, null);

        // for debugging
        for (Map.Entry<String, Object> entry : map2.entrySet())
        {
            TestUtil.printLine(entry.getKey() + " : " + entry.getValue() + " {" + entry.getValue().getClass().getSimpleName() + "}");
        }

        assert map2.get("Boolean") instanceof Boolean;
        assert map2.get("Boolean").equals(false);

        assert map2.get("Byte") instanceof Byte;
        assert map2.get("Byte").equals((byte)79);

        assert map2.get("Short") instanceof Short;
        assert map2.get("Short").equals((short)179);

        assert map2.get("Integer") instanceof Integer;
        assert map2.get("Integer").equals(179);

        assert map2.get("Long") instanceof Long;
        assert map2.get("Long").equals(179L);

        assert map2.get("Float") instanceof Float;
        assert map2.get("Float").equals(179f);

        assert map2.get("Double") instanceof Double;
        assert map2.get("Double").equals(179d);

        assert map2.get("Character") instanceof Character;
        assert map2.get("Character").equals('z');

        assert map2.get("date") instanceof Date;
        assert map2.get("date").equals(cal.getTime());

        assert map2.get("BigInteger") instanceof BigInteger;
        assert map2.get("BigInteger").equals(new BigInteger("55"));

        assert map2.get("BigDecimal") instanceof BigDecimal;
        assert map2.get("BigDecimal").equals(new BigDecimal("3.33333"));
    }

    @Test
    void testSingletonMap()
    {
        // SingleTon Maps are simple one key, one value Maps (inner class to Collections) and must be reconstituted
        // in a special way.  Make sure that works.
        Map root1 = Collections.singletonMap("testCfgKey", "testCfgValue");
        String json = TestUtil.toJson(root1);
        Map root2 = TestUtil.toObjects(json, null);
        assert root2.get("testCfgKey").equals("testCfgValue");
        assert root1.get("testCfgKey").equals(root2.get("testCfgKey"));
    }

    @Test
    void testMapWithCircularReferenceInValues() {
        TestMapValues tmv = new TestMapValues();
        String json0 = TestUtil.toJson(tmv, new WriteOptionsBuilder().build());

        // First convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Then convert back to objects
        Object obj = TestUtil.toObjects(json1, new ReadOptionsBuilder().build(), null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Disabled("If we use CompactMap for JsonObject, then we can enable this - it can handle circlular references")
    @Test
    void testMapWithCircularReferenceInKeys() {
        TestMapKeys tmk = new TestMapKeys();
        String json0 = TestUtil.toJson(tmk, new WriteOptionsBuilder().build());

        // First convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Then convert back to objects
        Object obj = TestUtil.toObjects(json1, new ReadOptionsBuilder().build(), null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Disabled("If we use CompactMap for JsonObject, then we can enable this - it can handle circlular references")
    @Test
    void testMapWithCircularReferenceInKeyAndValue() {
        TestMapKeyValueCircular tmkvc = new TestMapKeyValueCircular();
        String json0 = TestUtil.toJson(tmkvc, new WriteOptionsBuilder().build());

        // First convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Then convert back to objects
        Object obj = TestUtil.toObjects(json1, new ReadOptionsBuilder().build(), null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Test
    void testMapWithNestedCircularReferenceInValues() {
        TestMapValuesNested tmvn = new TestMapValuesNested();
        String json0 = TestUtil.toJson(tmvn);

        // Convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Convert back to objects
        Object obj = TestUtil.toObjects(json1, null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Disabled("If we use CompactMap for JsonObject, then we can enable this - it can handle circlular references")
    @Test
    void testMapWithNestedCircularReferenceInKeys() {
        TestMapKeysNested tmkn = new TestMapKeysNested();
        String json0 = TestUtil.toJson(tmkn);

        // Convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Convert back to objects
        Object obj = TestUtil.toObjects(json1, null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Disabled("If we use CompactMap for JsonObject, then we can enable this - it can handle circlular references")
    @Test
    void testMapWithNestedCircularReferenceInKeyAndValue() {
        TestMapKeyValueCircularNested tmkvcn = new TestMapKeyValueCircularNested();
        String json0 = TestUtil.toJson(tmkvcn);

        // Convert to Maps (native JSON objects)
        Object objAsMap = TestUtil.toObjects(json0, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(objAsMap);

        // Convert back to objects
        Object obj = TestUtil.toObjects(json1, null);
        String json2 = TestUtil.toJson(obj);

        assertEquals(json0, json1);
        assertEquals(json1, json2);
    }

    @Test
    void testCompactMap() {
        CompactMap<Object, Object> map = CompactMap.builder().sortedOrder().compactSize(30).build();
        int[] ints = new int[] {1, 2, 3};
        long[] longs = new long[] { 4, 5, 6};
        map.put(ints, "ints");
        map.put("ints", ints);
        map.put(longs, "longs");
        map.put("longs", longs);
        String json = TestUtil.toJson(map);
//        System.out.println(json);
        CompactMap<Object, Object> map2 = JsonIo.toJava(json, ReadOptionsBuilder.getDefaultReadOptions()).asType(new TypeHolder<CompactMap<Object, Object>>() {});
        Map<String, Object> options = new HashMap<>();
        boolean equals = DeepEquals.deepEquals(map, map2, options);
        if (!equals) {
            System.out.println(options.get("diff"));
        }
        assert equals;
    }

    @Test
    void testCompactSetOriginal() {
        CompactSet<Object> set = CompactSet.builder().compactSize(3).build();
        int[] ints = new int[]{1, 2, 3};
        long[] longs = new long[]{4, 5, 6};
        Object[] first = new Object[] {ints, longs};
        set.add(first);
        set.add(ints);
        set.add(longs);
        String json = TestUtil.toJson(set);
        CompactSet<Object> set2 = JsonIo.toJava(json, ReadOptionsBuilder.getDefaultReadOptions()).asType(new TypeHolder<CompactSet<Object>>(){});
        boolean equals = DeepEquals.deepEquals(set, set2);
        if (!equals) {
            System.out.println(json);
        }
        assert equals;
    }
    
    @Test
    void testCompactSet() {
        CompactSet<Object> set = CompactSet.builder().compactSize(3).build();
        int[] ints = new int[]{1, 2, 3};
        long[] longs = new long[]{4, 5, 6};
        Object[] first = new Object[] {ints, longs};
        set.add(first);
        set.add(ints);
        set.add(longs);

        String json = TestUtil.toJson(set);

        CompactSet<Object> set2 = JsonIo.toJava(json, ReadOptionsBuilder.getDefaultReadOptions()).asType(new TypeHolder<CompactSet<Object>>(){});
        boolean equals = DeepEquals.deepEquals(set, set2);
        if (!equals) {
            // Debug the sets
            System.out.println(json);
            System.out.println("Original set: " + debugSet(set));
            System.out.println("Deserialized set: " + debugSet(set2));
        }

        // Check if arrays are equal, not necessarily the same instance
        assert equals;
    }

    private String debugSet(CompactSet<Object> set) {
        StringBuilder sb = new StringBuilder("Set[");
        for (Object obj : set) {
            if (obj == null) {
                sb.append("null");
            } else if (obj.getClass().isArray()) {
                sb.append(debugArray(obj)).append("@").append(System.identityHashCode(obj));
            } else {
                sb.append(obj).append("@").append(System.identityHashCode(obj));
            }
            sb.append(", ");
        }
        if (!set.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("]");
        return sb.toString();
    }

    private String debugArray(Object array) {
        if (array instanceof int[]) {
            return Arrays.toString((int[]) array);
        } else if (array instanceof long[]) {
            return Arrays.toString((long[]) array);
        } else if (array instanceof Object[]) {
            Object[] objs = (Object[]) array;
            StringBuilder sb = new StringBuilder("[");
            for (Object obj : objs) {
                if (obj == null) {
                    sb.append("null");
                } else if (obj.getClass().isArray()) {
                    sb.append(debugArray(obj));
                } else {
                    sb.append(obj);
                }
                sb.append(", ");
            }
            if (objs.length > 0) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
            return sb.toString();
        }
        return array.toString();
    }

    // Helper to check if arrays are equal in content but not necessarily the same instance
    private boolean arraysEqual(CompactSet<Object> set1, CompactSet<Object> set2) {
        if (set1.size() != set2.size()) return false;

        // Convert to lists for easier comparison
        List<Object> list1 = new ArrayList<>(set1);
        List<Object> list2 = new ArrayList<>(set2);

        for (int i = 0; i < list1.size(); i++) {
            Object o1 = list1.get(i);
            Object o2 = list2.get(i);

            if (!objectsEqual(o1, o2)) return false;
        }

        return true;
    }

    private boolean objectsEqual(Object o1, Object o2) {
        if (o1 == o2) return true;
        if (o1 == null || o2 == null) return false;

        if (o1.getClass().isArray() && o2.getClass().isArray()) {
            return arrayContentsEqual(o1, o2);
        }

        return o1.equals(o2);
    }

    private boolean arrayContentsEqual(Object a1, Object a2) {
        if (a1 instanceof int[] && a2 instanceof int[]) {
            return Arrays.equals((int[]) a1, (int[]) a2);
        } else if (a1 instanceof long[] && a2 instanceof long[]) {
            return Arrays.equals((long[]) a1, (long[]) a2);
        } else if (a1 instanceof Object[] && a2 instanceof Object[]) {
            Object[] o1 = (Object[]) a1;
            Object[] o2 = (Object[]) a2;

            if (o1.length != o2.length) return false;

            for (int i = 0; i < o1.length; i++) {
                if (!objectsEqual(o1[i], o2[i])) return false;
            }

            return true;
        }

        return false;
    }

    public static class TestMapKeys {
        private Map<Object, String> _test_a;
        private Map<Object, String> _test_b;

        public TestMapKeys() {
            _test_a = new HashMap<>();
            _test_b = new HashMap<>();
            _test_a.put(_test_b, "value_b");
            _test_b.put(_test_a, "value_a");
        }
    }

    public static class TestMapKeysNested {
        private Map<Object, String>[] _test_a;
        private Map<Object, String>[] _test_b;

        public TestMapKeysNested() {
            _test_a = new Map[1];
            _test_b = new Map[1];
            _test_a[0] = new HashMap<>();
            _test_b[0] = new HashMap<>();
            _test_a[0].put(_test_b, "value_b");
            _test_b[0].put(_test_a, "value_a");
        }
    }

    public static class TestMapValues {
        private Map<String, Object> _test_a;
        private Map<String, Object> _test_b;

        public TestMapValues() {
            _test_a = new HashMap<>();
            _test_b = new HashMap<>();
            _test_a.put("b", _test_b);
            _test_b.put("a", _test_a);
        }
    }

    public static class TestMapValuesNested {
        private Map<String, Object>[] _test_a;
        private Map<String, Object>[] _test_b;

        public TestMapValuesNested() {
            _test_a = new Map[1];
            _test_b = new Map[1];
            _test_a[0] = new HashMap<>();
            _test_b[0] = new HashMap<>();
            _test_a[0].put("b", _test_b);
            _test_b[0].put("a", _test_a);
        }
    }

    public static class TestMapKeyValueCircular {
        private Map<Object, Object> _test_map;

        public TestMapKeyValueCircular() {
            _test_map = new HashMap<>();
            _test_map.put(_test_map, _test_map);
        }
    }

    public static class TestMapKeyValueCircularNested {
        private Map<Object, Object>[] _test_map;

        public TestMapKeyValueCircularNested() {
            _test_map = new Map[1];
            _test_map[0] = new HashMap<>();
            _test_map[0].put(_test_map, _test_map);
        }
    }

    public static class MapArrayKey
    {
        public Map<Object[], String> getContent()
        {
            return content;
        }

        void setContent(Map<Object[], String> content)
        {
            this.content = content;
        }

        private Map<Object[], String> content;
    }

    public static class MapSetKey
    {
        public Map<Set<Point>, String> getContent()
        {
            return content;
        }

        void setContent(Map<Set<Point>, String> content)
        {
            this.content = content;
        }

        private Map<Set<Point>, String> content;
    }

    public static class ParameterizedMap
    {
        public Map<String, Map<String, Point>> getContent()
        {
            return content;
        }

        void setContent(Map<String, Map<String, Point>> content)
        {
            this.content = content;
        }

        private Map<String, Map<String, Point>> content = new LinkedHashMap<>();
    }

    public static class AssignToList
    {
        public Map getAssignTo()
        {
            return assignTo;
        }

        void setAssignTo(Map assignTo)
        {
            this.assignTo = assignTo;
        }

        private Map assignTo;
    }

    public static class SimpleMapTest
    {
        public Map<Object, Object> getMap()
        {
            return map;
        }

        void setMap(Map map)
        {
            this.map = map;
        }

        private Map map = new HashMap<>();
    }

    private static class ManyMaps implements Serializable
    {
        private void init()
        {
            _strings_a = new LinkedHashMap<>();
            _strings_b = new LinkedHashMap<>();
            _strings_c = null;
            _testobjs_a = new TreeMap<>();
            _map_col = new LinkedHashMap<>();
            _map_col_2 = new TreeMap<>();
            _map_col_3 = new LinkedHashMap<>();
            _map_obj = new LinkedHashMap<>();
            _map_con = Collections.synchronizedMap(new LinkedHashMap<>());

            _strings_a.put("woods", "tiger");
            _strings_a.put("mickleson", "phil");
            _strings_a.put("garcia", "sergio");

            _testobjs_a.put(new TestObject("one"), new TestObject("alpha"));
            _testobjs_a.put(new TestObject("two"), new TestObject("bravo"));

            List<String> l = new LinkedList<>();
            l.add("andromeda");
            _map_col.put(new TestObject[]{new TestObject("earth"), new TestObject("jupiter")}, l);
            _map_col_2.put("cat", new Object[]{"tiger", "lion", "cheetah", "jaguar"});
            _map_col_3.put(new Object[]{"composite", "key"}, "value");

            _map_obj.put(99, 0.123d);
            _map_obj.put(null, null);

            _map_con.put(new TestObject("alpha"), new TestObject("one"));
            _map_con.put(new TestObject("bravo"), new TestObject("two"));

            _typedMap = new TreeMap<>();
            _typedMap.put("a", "alpha");
            _typedMap.put("b", "bravo");
        }

        private Map<String, Object> _strings_a;
        private Map<String, Object> _strings_b;
        private Map<String, Object> _strings_c;
        private Map _testobjs_a;
        private Map _map_col;
        private Map _map_col_2;
        private Map _map_col_3;
        private Map _map_obj;
        private Map _map_con;
        private Map<String, Object> _typedMap;
    }
}
