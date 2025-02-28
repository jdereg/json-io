package com.cedarsoftware.io;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import com.cedarsoftware.io.util.SealableList;
import com.cedarsoftware.util.DeepEquals;
import com.cedarsoftware.util.FastByteArrayOutputStream;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static com.cedarsoftware.util.CollectionUtilities.listOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
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
class CollectionTests {
    @Test
    void testCollection() {
        TestUtil.printLine("\nTestJsonReaderWriter.testCollection()");
        ManyCollections obj = new ManyCollections();
        obj.init();
        String jsonOut = TestUtil.toJson(obj);
        TestUtil.printLine(jsonOut);

        ManyCollections root = TestUtil.toObjects(jsonOut, null);

        assertCollection(root);

        JsonWriter writer = new JsonWriter(new FastByteArrayOutputStream());
        writer.write(obj);
        writer.flush();
        // TODO: Uncomment to test identity counter strategies (currently incremental + only referenced)
//        System.out.TestUtil.printLine("writer._identity = " + writer._identity)
    }

    @Test
    void testEmptyList()
    {
        List<?> list = Collections.emptyList();
        String json = TestUtil.toJson(list, null);
        List<?> list2 = TestUtil.toObjects(json, null);
        assert list2.getClass().equals(Collections.emptyList().getClass());
    }

    @Test
    void testEmptySet()
    {
        Set<String> set = Collections.emptySet();
        String json = TestUtil.toJson(set, null);
        Set<String> set2 = TestUtil.toObjects(json, null);
        assert set2.getClass().equals(Collections.emptySet().getClass());
        assert set2.isEmpty();
        assertThrows(UnsupportedOperationException.class, () -> set2.add("foo"));
    }

    @Test
    void testEmptySortedSet()
    {
        Set<String> set = Collections.emptySortedSet();
        String json = TestUtil.toJson(set, null);
        Set<String> set2 = TestUtil.toObjects(json, null);
        assert set2.isEmpty();
        assert set2.getClass().equals(Collections.emptySortedSet().getClass());
        assertThrows(UnsupportedOperationException.class, () -> set2.add("foo"));
    }

    @Test
    void testEmptyNavigableSet()
    {
        Set<String> set = Collections.emptyNavigableSet();
        String json = TestUtil.toJson(set, null);
        Set<String> set2 = TestUtil.toObjects(json, null);
        assert set2.isEmpty();
        assert set2.getClass().equals(Collections.emptyNavigableSet().getClass());
        assertThrows(UnsupportedOperationException.class, () -> set2.add("foo"));
    }
    
    private void assertCollection(ManyCollections root) {
        assertEquals(3, root._cols.length);
        assertEquals(root._cols[0].getClass(), ArrayList.class);
        assertEquals(root._cols[1].getClass(), HashSet.class);
        assertEquals(root._cols[2].getClass(), TreeSet.class);

        Collection array = root._cols[0];
        assertEquals(4, array.size());
        assertEquals(array.getClass(), ArrayList.class);
        List alist = (List) array;
        assertEquals(alist.get(0), _testDate);
        assertEquals("Hello", alist.get(1));
        assertEquals(alist.get(2), new TestObject("fudge"));
        assertEquals(alist.get(3), _CONST_INT);

        Collection set = root._cols[1];
        assertEquals(4, set.size());
        assertEquals(set.getClass(), HashSet.class);
        assertTrue(set.contains(Map.class));
        assertTrue(set.contains(Boolean.TRUE));
        assertTrue(set.contains(null));
        assertTrue(set.contains(_CONST_INT));

        set = root._cols[2];
        assertEquals(4, set.size());
        assertEquals(set.getClass(), TreeSet.class);
        assertTrue(set.contains(Integer.MIN_VALUE));
        assertTrue(set.contains(1));
        assertTrue(set.contains(Integer.MAX_VALUE));
        assertTrue(set.contains(_CONST_INT));

        assertEquals(4, root._strings_a.size());
        assertEquals("Alpha", root._strings_a.get(0));
        assertEquals("Bravo", root._strings_a.get(1));
        assertEquals("Charlie", root._strings_a.get(2));
        assertEquals("Delta", root._strings_a.get(3));
        assertTrue(root._strings_b.isEmpty());
        assertNull(root._strings_c);

        assertEquals(4, root._dates_a.size());
        assertEquals(root._dates_a.get(0), new Date(0));
        assertEquals(root._dates_a.get(1), _testDate);
        assertEquals(root._dates_a.get(2), new Date(Long.MAX_VALUE));
        assertNull(root._dates_a.get(3));
        assertTrue(root._dates_b.isEmpty());
        assertNull(root._dates_c);

        assertEquals(12, root._classes_a.size());
        assertEquals(root._classes_a.get(0), Boolean.class);
        assertEquals(root._classes_a.get(1), Character.class);
        assertEquals(root._classes_a.get(2), Byte.class);
        assertEquals(root._classes_a.get(3), Short.class);
        assertEquals(root._classes_a.get(4), Integer.class);
        assertEquals(root._classes_a.get(5), Long.class);
        assertEquals(root._classes_a.get(6), Float.class);
        assertEquals(root._classes_a.get(7), Double.class);
        assertEquals(root._classes_a.get(8), String.class);
        assertEquals(root._classes_a.get(9), Date.class);
        assertNull(root._classes_a.get(10));
        assertEquals(root._classes_a.get(11), Class.class);
        assertTrue(root._classes_b.isEmpty());
        assertNull(root._classes_c);

        assertEquals(2, root._sb_a.size());
        assertEquals("one", root._sb_a.get(0).toString());
        assertEquals("two", root._sb_a.get(1).toString());
        assertTrue(root._sb_b.isEmpty());
        assertNull(root._sb_c);

        assertEquals(17, root._poly_a.size());
        assertEquals(root._poly_a.get(0), Boolean.TRUE);
        assertEquals(root._poly_a.get(1), 'a');
        assertEquals(root._poly_a.get(2), (byte) 16);
        assertEquals(root._poly_a.get(3), (short) (byte) 69);
        assertEquals(root._poly_a.get(4), 714);
        assertEquals(root._poly_a.get(5), 420L);
        assertEquals(root._poly_a.get(6), 0.4f);
        assertEquals(root._poly_a.get(7), 3.14);
        assertEquals("Jones'in\tfor\u0019a\ncoke", root._poly_a.get(8));
        assertNull(root._poly_a.get(9));
        assertEquals("eddie", root._poly_a.get(10).toString());
        assertEquals(root._poly_a.get(11), _testDate);
        assertEquals(root._poly_a.get(12), Long.class);

        String[] sa = (String[]) root._poly_a.get(13);
        assertEquals("beatles", sa[0]);
        assertEquals("stones", sa[1]);
        TestObject[] to = (TestObject[]) root._poly_a.get(14);
        assertEquals("flint", to[0].getName());
        assertEquals("stone", to[1].getName());
        Object[] arrayInCol = (Object[]) root._poly_a.get(15);
        assertEquals("fox", arrayInCol[0]);
        assertEquals("wolf", arrayInCol[1]);
        assertEquals("dog", arrayInCol[2]);
        assertEquals("hound", arrayInCol[3]);

        Set colors = (Set) root._poly_a.get(16);
        assertEquals(3, colors.size());
        assertTrue(colors.contains(new TestObject("red")));
        assertTrue(colors.contains(new TestObject("green")));
        assertTrue(colors.contains(new TestObject("blue")));

        assertEquals(7, root._strs_a.size());
        assertTrue(root._strs_a.contains("Dog"));
        assertTrue(root._strs_a.contains("Cat"));
        assertTrue(root._strs_a.contains("Cow"));
        assertTrue(root._strs_a.contains("Horse"));
        assertTrue(root._strs_a.contains("Duck"));
        assertTrue(root._strs_a.contains("Bird"));
        assertTrue(root._strs_a.contains("Goose"));
        assertTrue(root._strs_b.isEmpty());
        assertNull(root._strs_c);
        assertEquals(7, root._strs_d.size());
        assertTrue(root._strs_d instanceof TreeSet);

        assertNotNull(root._typedCol);
        assertEquals(6, root._typedCol.size());
        assertEquals("string", root._typedCol.get(0));
        assertNull(root._typedCol.get(1));
        assertEquals((new Date(19)), root._typedCol.get(2));
        assertTrue((Boolean) root._typedCol.get(3));
        assertEquals(17.76, (Double) root._typedCol.get(4));
        assertEquals(TimeZone.getTimeZone("PST"), root._typedCol.get(5));

        assertNotNull(root._typedSet);
        assertEquals(6, root._typedSet.size());
        assertTrue(root._typedSet.contains("string"));
        assertTrue(root._typedCol.contains(null));
        assertTrue(root._typedCol.contains(new Date(19)));
        assertTrue(root._typedCol.contains(true));
        assertTrue(root._typedCol.contains(17.76));
        assertTrue(root._typedCol.contains(TimeZone.getTimeZone("PST")));
    }

    @Test
    void testReconstituteCollection2() {
        ManyCollections testCol = new ManyCollections();
        testCol.init();
        String json0 = TestUtil.toJson(testCol);
        TestUtil.printLine("json0=" + json0);
        ManyCollections testCol2 = TestUtil.toObjects(json0, null);

        String json1 = TestUtil.toJson(testCol2);
        TestUtil.printLine("json1=" + json1);

        ManyCollections testCol3 = TestUtil.toObjects(json1, null);
        assertCollection(testCol3);// Re-written from Map of Maps and re-loaded correctly
        Map options = new HashMap();
        boolean equals = DeepEquals.deepEquals(testCol2, testCol3, options);
        if (!equals) {
            System.out.println(options.get("diff"));
        }
        assert equals;
    }

    @Test
    void testAlwaysShowType() {
        ManyCollections tc = new ManyCollections();
        tc.init();
        WriteOptions writeOptions = new WriteOptionsBuilder().showTypeInfoAlways().build();
        String json0 = TestUtil.toJson(tc, writeOptions);
        String json1 = TestUtil.toJson(tc);
        TestUtil.printLine("json0 = " + json0);
        TestUtil.printLine("json1 = " + json1);
        assertTrue(json0.length() > json1.length());
    }

    @Test
    void testCollectionWithEmptyElement() {
        List list = new ArrayList<>();
        list.add("a");
        list.add(null);
        list.add("b");
        String json = TestUtil.toJson(list);
        List list2 = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(list, list2));

        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[\"a\",{},\"b\"]}";
        list2 = TestUtil.toObjects(json, null);
        assertEquals(3, list2.size());
        assertEquals("a", list2.get(0));
        assertEquals(list2.get(1).getClass(), JsonObject.class);
        assertEquals("b", list2.get(2));
    }

    @Test
    void testCollectionWithReferences() {
        TestObject o = new TestObject("JSON");
        List list = new ArrayList<>();
        list.add(o);
        list.add(o);

        // Backward reference
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        List list2 = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(list, list2));

        // Forward reference
        String pkg = TestObject.class.getName();
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@ref\":3},{\"@id\":3,\"@type\":\"" + pkg + "\",\"_name\":\"JSON\",\"_other\":null}]}";
        list2 = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(list, list2));
    }

    @Test
    void testCollectionWithNonJsonPrimitives() {
        Collection col = new ArrayList<>();
        col.add(Integer.valueOf(7));
        col.add(Short.valueOf((short) 9));
        col.add(Float.valueOf(3.14f));
        String json = TestUtil.toJson(col);
        Collection col1 = TestUtil.toObjects(json, null);
        assertEquals(col, col1);
    }

    @Test
    void testCollectionWithParameterizedTypes() {
        String json = "{\"@type\":\"" + ParameterizedCollection.class.getName() + "\", \"content\":{\"foo\":[{\"x\":1,\"y\":2},{\"x\":10,\"y\":20}],\"bar\":[{\"x\":3,\"y\":4}, {\"x\":30,\"y\":40}]}}";
        ParameterizedCollection pCol = TestUtil.toObjects(json, null);
        Set<Point> points = pCol.getContent().get("foo");
        assertNotNull(points);
        assertEquals(2, points.size());
        assert points.contains(new Point(1, 2));
        assert points.contains(new Point(10, 20));

        points = pCol.getContent().get("bar");
        assertNotNull(points);
        assertEquals(2, points.size());
        assert points.contains(new Point(3, 4));
        assert points.contains(new Point(30, 40));

        json = "{\"@type\":\"" + ParameterizedCollection.class.getName() + "\", \"content\":{\"foo\":[],\"bar\":null}}";
        pCol = TestUtil.toObjects(json, null);
        points = pCol.getContent().get("foo");
        assertNotNull(points);
        assertEquals(0, points.size());

        points = pCol.getContent().get("bar");
        assertNull(points);

        json = "{\"@type\":\"" + ParameterizedCollection.class.getName() + "\", \"content\":{}}";
        pCol = TestUtil.toObjects(json, null);
        assertNotNull(pCol.getContent());
        assertEquals(0, pCol.getContent().size());

        json = "{\"@type\":\"" + ParameterizedCollection.class.getName() + "\", \"content\":null}";
        pCol = TestUtil.toObjects(json, null);
        assertNull(pCol.getContent());
    }

    @Test
    void testEmptyCollections() {
        EmptyCols emptyCols;
        String className = CollectionTests.class.getName();
        String json = "{\"@type\":\"" + className + "$EmptyCols\",\"col\":{},\"list\":{},\"map\":{},\"set\":{},\"sortedSet\":{},\"sortedMap\":{}}";
        TestUtil.printLine("json = " + json);
        emptyCols = TestUtil.toObjects(json, null);

        assertEquals(0, emptyCols.getCol().size());
        assert ArrayList.class.isAssignableFrom(emptyCols.getCol().getClass());
        assertEquals(0, emptyCols.getList().size());
        assert ArrayList.class.isAssignableFrom(emptyCols.getList().getClass());
        assertEquals(0, emptyCols.getMap().size());
        assert LinkedHashMap.class.isAssignableFrom(emptyCols.getMap().getClass());
        assertEquals(0, emptyCols.getSet().size());
        assert LinkedHashSet.class.isAssignableFrom(emptyCols.getSet().getClass());
        assertEquals(0, emptyCols.getSortedSet().size());
        assert TreeSet.class.isAssignableFrom(emptyCols.getSortedSet().getClass());
        assertEquals(0, emptyCols.getSortedMap().size());
        assert TreeMap.class.isAssignableFrom(emptyCols.getSortedMap().getClass());
    }

    @Test
    void testEnumsInsideOfACollection_whenWritingAsObject_withPrivateMembersIncluded() {

        WriteOptions writeOptions = new WriteOptionsBuilder().writeEnumAsJsonObject(false).build();

        List arrayList = new ArrayList<>();
        arrayList.add(TestEnum4.B);
        String json = TestUtil.toJson(arrayList, writeOptions);
        TestUtil.printLine(json);
        String className = CollectionTests.class.getName();

        String expectedJson = "{\"@type\":\"ArrayList\",\"@items\":[{\"@type\":\"" + className + "$TestEnum4\",\"age\":21,\"foo\":\"bar\",\"name\":\"B\"}]}";

        assert JsonParser.parseString(json).equals(JsonParser.parseString(expectedJson));
    }

    @Test
    void testEnumsInsideOfACollection_whenWritingAsObject_withPublicFieldsOnly() {

        WriteOptions writeOptions = new WriteOptionsBuilder().writeEnumAsJsonObject(true).build();

        List list = listOf(TestEnum4.B);
        String json = TestUtil.toJson(list, writeOptions);
        Object o = TestUtil.toObjects(json, null);

        assertThat(DeepEquals.deepEquals(list, o)).isTrue();
    }

    @Test
    void testGenericInfoCollection() {
        String className = PointList.class.getName();
        String json = "{\"@type\":\"" + className + "\",\"points\":{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"x\":1,\"y\":2}]}}";
        PointList list = TestUtil.toObjects(json, null);
        assertEquals(1, list.getPoints().size());
        Point p1 = list.getPoints().get(0);
        assertTrue(p1.getX() == 1 && p1.getY() == 2);
    }

    @Test
    void testLocaleInCollection() {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        List list = new ArrayList<>();
        list.add(locale);
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.toObjects(json, null);
        assertEquals(1, list.size());
        assertEquals(list.get(0), locale);
    }

    @Test
    void testMapOfMapsCollection() {
        List stuff = new ArrayList<>();
        stuff.add("Hello");
        Object testObj = new TestObject("test object");
        stuff.add(testObj);
        stuff.add(testObj);
        stuff.add(new Date());
        String json = TestUtil.toJson(stuff);
        TestUtil.printLine("json=" + json);

        List map = TestUtil.toObjects(json, null);
        Object[] items = map.toArray();
        assertEquals(4, items.length);
        assertEquals("Hello", items[0]);
        assertEquals(items[1], items[2]);

        List list = new ArrayList<>();
        list.add(new Object[]{123L, null, true, "Hello"});
        json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        map = TestUtil.toObjects(json, null);
        items = map.toArray();
        assertEquals(1, items.length);
        Object[] oa = (Object[]) items[0];
        assertEquals(4, oa.length);
        assertEquals(123L, oa[0]);
        assertNull(oa[1]);
        assertEquals(oa[2], Boolean.TRUE);
        assertEquals("Hello", oa[3]);
    }

    @Test
    void testReconstituteCollection() {
        TestObject to = new TestObject("football");
        Collection objs = new ArrayList<>();
        Date now = new Date();
        objs.add(now);
        objs.add(123.45);
        objs.add("This is a string");
        objs.add(null);
        objs.add(to);
        objs.add(new Object[]{"dog", new String[]{"a", "b", "c"}});
        Collection two = new ArrayList<>();
        two.add(objs);
        two.add("bella");
        two.add(objs);

        String json0 = TestUtil.toJson(two);
        TestUtil.printLine("json0=" + json0);
        List map = TestUtil.toObjects(json0, null);
        map.hashCode();
        String json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);

        List colOuter = TestUtil.toObjects(json1, null);
        assertEquals(colOuter.get(0), colOuter.get(2));
        assertEquals("bella", colOuter.get(1));
        List col1 = (List) colOuter.get(0);
        assertEquals(col1.get(0), now);
        assertEquals(123.45, col1.get(1));
        assertEquals("This is a string", col1.get(2));
        assertNull(col1.get(3));
        assertEquals(col1.get(4), to);
        assert Object[].class.isAssignableFrom(col1.get(5).getClass());
        Object[] oa = (Object[]) col1.get(5);
        assertEquals("dog", oa[0]);
        assert String[].class.isAssignableFrom(oa[1].getClass());
        String[] sa = (String[]) oa[1];
        assertEquals("a", sa[0]);
        assertEquals("b", sa[1]);
        assertEquals("c", sa[2]);

        assertEquals(json0, json1);
    }

    @Test
    void testReconstituteEmptyCollection() {
        Collection empty = new ArrayList<>();
        String json0 = TestUtil.toJson(empty);
        TestUtil.printLine("json0=" + json0);

        List map = TestUtil.toObjects(json0, null);
        assertNotNull(map);
        assertTrue(map.isEmpty());
        String json1 = TestUtil.toJson(map);
        TestUtil.printLine("json1=" + json1);

        assertEquals(json0, json1);

        Object[] list = new Object[]{empty, empty};
        json0 = TestUtil.toJson(list);
        TestUtil.printLine("json0=" + json0);

        Object[] array = TestUtil.toObjects(json0, null);
        assertNotNull(array);
        list = array;
        assertEquals(2, list.length);
        List e1 = (List) list[0];
        List e2 = (List) list[1];
        assertTrue(e1.isEmpty());
        assertTrue(e2.isEmpty());
    }

    @Test
    void testUntypedCollections() {
        Object[] poly = new Object[]{"Road Runner", 16L, 3.1415d, true, false, null, 7, "Coyote", "Coyote"};
        String json = TestUtil.toJson(poly);
        TestUtil.printLine("json=" + json);
        assertEquals("[\"Road Runner\",16,3.1415,true,false,null,{\"@type\":\"Integer\",\"value\":7},\"Coyote\",\"Coyote\"]", json);
        Collection col = new ArrayList<>();
        col.add("string");
        col.add(Long.valueOf(16));
        col.add(Double.valueOf(3.14159));
        col.add(Boolean.TRUE);
        col.add(Boolean.FALSE);
        col.add(null);
        col.add(Integer.valueOf(7));
        json = TestUtil.toJson(col);
        TestUtil.printLine("json=" + json);
        assertEquals("{\"@type\":\"ArrayList\",\"@items\":[\"string\",16,3.14159,true,false,null,{\"@type\":\"Integer\",\"value\":7}]}", json);
    }

    @Test
    void testEmptyArrayList() {
        EmptyArrayList x = new EmptyArrayList();
        String json = TestUtil.toJson(x);
        TestUtil.printLine(json);
        assertTrue(json.contains("list\":[]"));

        EmptyArrayList obj = TestUtil.toObjects(json, null);
        json = TestUtil.toJson(obj);
        TestUtil.printLine(json);
        assertTrue(json.contains("list\":[]"));
    }

    @Test
    void testCollectionOfPrimitiveArrays()
    {
        List<Object> list = new ArrayList<>();
        list.add(new byte[]{1, 3, 5});
        list.add(new double[]{2.0, 4.0, 6.1});

        String json = TestUtil.toJson(list, new WriteOptionsBuilder().shortMetaKeys(true).build());
        TestUtil.printLine(json);
        List<Object> dupe = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert DeepEquals.deepEquals(list, dupe);
    }

    @Test
    void testCollectionOfLogicalPrimitiveArrays()
    {
        Date now = new Date();
        List<Object> list = new ArrayList<>();
        list.add(new String[]{"1", "3", "5"});
        list.add(new Date[]{now, now, now});

        String json = TestUtil.toJson(list, new WriteOptionsBuilder().shortMetaKeys(true).build());
        TestUtil.printLine(json);
        List<Object> dupe = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert DeepEquals.deepEquals(list, dupe);
    }

    @Test
    void testCollectionArrayOfObjects()
    {
        Date now = new Date();
        List<Object> list = new ArrayList<>();
        list.add(new TestObject[]{new TestObject("1", new TestObject("3")), new TestObject("5")});
        list.add(new Date[]{now, now, now});

        String json = TestUtil.toJson(list, new WriteOptionsBuilder().shortMetaKeys(true).build());
        TestUtil.printLine(json);
        List<Object> dupe = TestUtil.toObjects(json, new ReadOptionsBuilder().build(), null);
        assert DeepEquals.deepEquals(list, dupe);
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_9)
    void testReadListOfInJDK1_8()
    {
        // java.util.ImmutableCollections$ListN - created by using JDK9+ and then storing the String so that we are
        // still JDK 1.8 but can test how it properly handles a List.of() generated JSON.
        //
        // First, note that the ImmutableCollection is aliased to ListN.  This will then be unaliased when read in.
        // Second, the java.util.ImmutableCollections$ListN = com.cedarsoftware.io.factory.ImmutableListFactory entry
        // in the classFactory.txt file will cause the ImmutableCollection$ListN to be created by the
        // ImmutableListFactory.
        // Third, the ImmutableListFactory will create the List as a SealableList, which allows it to be added to
        // during construction and then sealed (become immutable) before passing back to caller.
        String json = "{\"@type\":\"ListN\",\"@items\":[\"alpha\",\"bravo\",\"charlie\",\"delta\"]}";
        List<String> x = JsonIo.toObjects(json, null, null);
        assert x instanceof SealableList;
    }

    private static Date _testDate = new Date();
    private static Integer _CONST_INT = Integer.valueOf(36);

    private static enum TestEnum4 {
        A, B, C;

        public String getFoo() {
            return foo;
        }

        void setFoo(String foo) {
            this.foo = foo;
        }

        private int internal = 6;
        protected long age = 21;
        private String foo = "bar";
    }

    public static class EmptyCols {
        public Collection getCol() {
            return col;
        }

        void setCol(Collection col) {
            this.col = col;
        }

        public List getList() {
            return list;
        }

        void setList(List list) {
            this.list = list;
        }

        public Map getMap() {
            return map;
        }

        void setMap(Map map) {
            this.map = map;
        }

        public Set getSet() {
            return set;
        }

        void setSet(Set set) {
            this.set = set;
        }

        public SortedSet getSortedSet() {
            return sortedSet;
        }

        void setSortedSet(SortedSet sortedSet) {
            this.sortedSet = sortedSet;
        }

        public SortedMap getSortedMap() {
            return sortedMap;
        }

        void setSortedMap(SortedMap sortedMap) {
            this.sortedMap = sortedMap;
        }

        private Collection col = new LinkedList<>();
        private List list = new ArrayList<>();
        private Map map = new HashMap<>();
        private Set set = new HashSet();
        private SortedSet sortedSet = new TreeSet();
        private SortedMap sortedMap = new TreeMap();
    }

    public static class ParameterizedCollection {
        public Map<String, Set<Point>> getContent() {
            return content;
        }

        void setContent(Map<String, Set<Point>> content) {
            this.content = content;
        }

        private Map<String, Set<Point>> content = new LinkedHashMap<>();
    }

    public static class PointList {
        public List<Point> getPoints() {
            return points;
        }

        void setPoints(List<Point> points) {
            this.points = points;
        }

        private List<Point> points;
    }

    public static class EmptyArrayList {
        public ArrayList<String> getList() {
            return list;
        }

        void setList(ArrayList<String> list) {
            this.list = list;
        }

        private ArrayList<String> list = new ArrayList<>();
    }

    private static class ManyCollections implements Serializable {
        private void init() {
            Collection array = new ArrayList<>();
            array.add(_testDate);
            array.add("Hello");
            array.add(new TestObject("fudge"));
            array.add(_CONST_INT);

            Collection set = new HashSet();
            set.add(Map.class);
            set.add(Boolean.TRUE);
            set.add(null);
            set.add(_CONST_INT);

            Collection tree = new TreeSet();
            tree.add(Integer.valueOf(Integer.MIN_VALUE));
            tree.add(Integer.valueOf(1));
            tree.add(Integer.valueOf(Integer.MAX_VALUE));
            tree.add(_CONST_INT);

            _cols = new Collection[]{array, set, tree};

            _strings_a = new LinkedList<>();
            _strings_a.add("Alpha");
            _strings_a.add("Bravo");
            _strings_a.add("Charlie");
            _strings_a.add("Delta");
            _strings_b = new LinkedList<>();
            _strings_c = null;

            _dates_a = new ArrayList<>();
            _dates_a.add(new Date(0));
            _dates_a.add(_testDate);
            _dates_a.add(new Date(Long.MAX_VALUE));
            _dates_a.add(null);
            _dates_b = new ArrayList<>();
            _dates_c = null;

            _classes_a = new ArrayList<>();
            _classes_a.add(Boolean.class);
            _classes_a.add(Character.class);
            _classes_a.add(Byte.class);
            _classes_a.add(Short.class);
            _classes_a.add(Integer.class);
            _classes_a.add(Long.class);
            _classes_a.add(Float.class);
            _classes_a.add(Double.class);
            _classes_a.add(String.class);
            _classes_a.add(Date.class);
            _classes_a.add(null);
            _classes_a.add(Class.class);
            _classes_b = new ArrayList<>();
            _classes_c = null;

            _sb_a = new LinkedList<>();
            _sb_a.add(new StringBuffer("one"));
            _sb_a.add(new StringBuffer("two"));
            _sb_b = new LinkedList<>();
            _sb_c = null;

            _poly_a = new ArrayList<>();
            _poly_a.add(Boolean.TRUE);
            _poly_a.add(Character.valueOf('a'));
            _poly_a.add(Byte.valueOf((byte) 16));
            _poly_a.add(Short.valueOf((short) 69));
            _poly_a.add(Integer.valueOf(714));
            _poly_a.add(Long.valueOf(420));
            _poly_a.add(Float.valueOf(0.4f));
            _poly_a.add(Double.valueOf(3.14));
            _poly_a.add("Jones'in\tfor\u0019a\ncoke");
            _poly_a.add(null);
            _poly_a.add(new StringBuffer("eddie"));
            _poly_a.add(_testDate);
            _poly_a.add(Long.class);
            _poly_a.add(new String[]{"beatles", "stones"});
            _poly_a.add(new TestObject[]{new TestObject("flint"), new TestObject("stone")});
            _poly_a.add(new Object[]{"fox", "wolf", "dog", "hound"});

            Set colors = new TreeSet();
            colors.add(new TestObject("red"));
            colors.add(new TestObject("green"));
            colors.add(new TestObject("blue"));
            _poly_a.add(colors);

            _strs_a = new HashSet();
            _strs_a.add("Dog");
            _strs_a.add("Cat");
            _strs_a.add("Cow");
            _strs_a.add("Horse");
            _strs_a.add("Duck");
            _strs_a.add("Bird");
            _strs_a.add("Goose");
            _strs_b = new HashSet();
            _strs_c = null;
            _strs_d = new TreeSet();
            _strs_d.addAll(_strs_a);

            _typedCol = new ArrayList<>();
            _typedCol.add("string");
            _typedCol.add(null);
            _typedCol.add(new Date(19));
            _typedCol.add(true);
            _typedCol.add(17.76);
            _typedCol.add(TimeZone.getTimeZone("PST"));

            _typedSet = new HashSet();
            _typedSet.add("string");
            _typedSet.add(null);
            _typedSet.add(new Date(19));
            _typedSet.add(true);
            _typedSet.add(17.76);
            _typedSet.add(TimeZone.getTimeZone("PST"));

            _imm_lst_0 = listOf();
            _imm_lst_1 = listOf("One");
        }

        private Collection[] _cols;
        private List _strings_a;
        private List _strings_b;
        private List _strings_c;
        private List _dates_a;
        private List _dates_b;
        private List _dates_c;
        private List _classes_a;
        private List _classes_b;
        private List _classes_c;
        private List _sb_a;
        private List _sb_b;
        private List _sb_c;
        private List _poly_a;
        private ArrayList _typedCol;
        private Set _strs_a;
        private Set _strs_b;
        private Set _strs_c;
        private Set _strs_d;
        private HashSet _typedSet;
        private List<String> _imm_lst_0;
        private List<String> _imm_lst_1;
    }

    @Test
    public void testCheckedList() {
        // Create a checked List of Strings
        List<String> originalList = Collections.checkedList(
                new ArrayList<>(),
                String.class
        );
        originalList.add("one");
        originalList.add("two");
        originalList.add("three");

        // Serialize to JSON
        String json = JsonIo.toJson(originalList, WriteOptionsBuilder.getDefaultWriteOptions());

        // Deserialize back
        List<?> deserializedList = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), null);

        // Verify
        assertEquals(originalList.size(), deserializedList.size());
        assertEquals(originalList, deserializedList);

        // Verify type checking still works
        // If we ever decide to write checked types to JSON, and then honor them, uncomment below.
//        try {
//            ((List)deserializedList).add(123); // Should throw ClassCastException
//            fail("Should have thrown ClassCastException");
//        } catch (ClassCastException e) {
//            // Expected
//        }
    }

    @Test
    public void testCheckedSet() {
        // Create a checked Set of Integers
        Set<Integer> originalSet = Collections.checkedSet(
                new HashSet<>(),
                Integer.class
        );
        originalSet.add(1);
        originalSet.add(2);
        originalSet.add(3);

        // Serialize to JSON
        String json = JsonIo.toJson(originalSet, WriteOptionsBuilder.getDefaultWriteOptions());

        // Deserialize back
        Set<?> deserializedSet = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), null);

        // Verify
        assertEquals(originalSet.size(), deserializedSet.size());
        assertEquals(originalSet, deserializedSet);

        // Verify type checking still works
        // If we ever decide to write checked types to JSON, and then honor them, uncomment below.
//        try {
//            ((Set)deserializedSet).add("string"); // Should throw ClassCastException
//            fail("Should have thrown ClassCastException");
//        } catch (ClassCastException e) {
//            // Expected
//        }
    }

    @Test
    public void testCheckedMap() {
        // Create a checked Map with String keys and Integer values
        Map<String, Integer> originalMap = Collections.checkedMap(
                new HashMap<>(),
                String.class,
                Integer.class
        );
        originalMap.put("one", 1);
        originalMap.put("two", 2);
        originalMap.put("three", 3);

        // Serialize to JSON
        String json = JsonIo.toJson(originalMap, WriteOptionsBuilder.getDefaultWriteOptions());

        // Deserialize back
        Map<?, ?> deserializedMap = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), null);

        // Verify
        assertEquals(originalMap.size(), deserializedMap.size());
        assertEquals(originalMap, deserializedMap);

        // Verify type checking still works
        // If we ever decide to write checked types to JSON, and then honor them, uncomment below.
//        try {
//            ((Map)deserializedMap).put(123, "string"); // Should throw ClassCastException
//            fail("Should have thrown ClassCastException");
//        } catch (ClassCastException e) {
//            // Expected
//        }
    }

    @Test
    public void testCheckedSortedSet() {
        // Create a checked SortedSet of Strings
        SortedSet<String> originalSet = Collections.checkedSortedSet(
                new TreeSet<>(),
                String.class
        );
        originalSet.add("apple");
        originalSet.add("banana");
        originalSet.add("cherry");

        // Serialize to JSON
        String json = JsonIo.toJson(originalSet, WriteOptionsBuilder.getDefaultWriteOptions());

        // Deserialize back
        SortedSet<?> deserializedSet = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), null);

        // Verify
        assertEquals(originalSet.size(), deserializedSet.size());
        assertEquals(originalSet, deserializedSet);
        assertEquals(originalSet.first(), deserializedSet.first());
        assertEquals(originalSet.last(), deserializedSet.last());

        // Verify type checking still works
        // If we ever decide to write checked types to JSON, and then honor them, uncomment below.
//        try {
//            ((SortedSet)deserializedSet).add(123); // Should throw ClassCastException
//            fail("Should have thrown ClassCastException");
//        } catch (ClassCastException e) {
//            // Expected
//        }
    }

    @Test
    public void testCheckedSortedMap() {
        // Create a checked SortedMap with String keys and Integer values
        SortedMap<String, Integer> originalMap = Collections.checkedSortedMap(
                new TreeMap<>(),
                String.class,
                Integer.class
        );
        originalMap.put("a", 1);
        originalMap.put("b", 2);
        originalMap.put("c", 3);

        // Serialize to JSON
        String json = JsonIo.toJson(originalMap, WriteOptionsBuilder.getDefaultWriteOptions());

        // Deserialize back
        SortedMap<?, ?> deserializedMap = JsonIo.toObjects(json, ReadOptionsBuilder.getDefaultReadOptions(), null);

        // Verify
        assertEquals(originalMap.size(), deserializedMap.size());
        assertEquals(originalMap, deserializedMap);
        assertEquals(originalMap.firstKey(), deserializedMap.firstKey());
        assertEquals(originalMap.lastKey(), deserializedMap.lastKey());

        // Verify type checking still works
        // If we ever decide to write checked types to JSON, and then honor them, uncomment below.
//        try {
//            ((SortedMap)deserializedMap).put(123, "string"); // Should throw ClassCastException
//            fail("Should have thrown ClassCastException");
//        } catch (ClassCastException e) {
//            // Expected
//        }
    }
}
