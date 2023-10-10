package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import java.awt.*
import java.util.List

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class TestCollection {
    public static Date _testDate = new Date()
    public static Integer _CONST_INT = new Integer(36)

    private static enum TestEnum4
    {
        A, B, C;

        private int internal = 6;
        protected long age = 21;
        String foo = "bar"
    }

    static class EmptyCols {
        Collection col = new LinkedList()
        List list = new ArrayList()
        Map map = new HashMap()
        Set set = new HashSet()
        SortedSet sortedSet = new TreeSet()
        SortedMap sortedMap = new TreeMap()
    }

    static class ParameterizedCollection {
        Map<String, Set<Point>> content = new LinkedHashMap<String, Set<Point>>()
    }

    static class PointList {
        List<Point> points;
    }

    static class EmptyArrayList {
        ArrayList<String> list = []
    }

    private static class ManyCollections implements Serializable {
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

        private void init() {
            Collection array = new ArrayList()
            array.add(_testDate)
            array.add("Hello")
            array.add(new TestObject("fudge"))
            array.add(_CONST_INT)

            Collection set = new HashSet()
            set.add(Map.class)
            set.add(Boolean.TRUE)
            set.add(null)
            set.add(_CONST_INT)

            Collection tree = new TreeSet()
            tree.add(Integer.valueOf(Integer.MIN_VALUE))
            tree.add(Integer.valueOf(1))
            tree.add(Integer.valueOf(Integer.MAX_VALUE))
            tree.add(_CONST_INT)

            _cols = [array, set, tree] as Collection[];

            _strings_a = new LinkedList()
            _strings_a.add("Alpha")
            _strings_a.add("Bravo")
            _strings_a.add("Charlie")
            _strings_a.add("Delta")
            _strings_b = new LinkedList()
            _strings_c = null;

            _dates_a = new ArrayList()
            _dates_a.add(new Date(0))
            _dates_a.add(_testDate)
            _dates_a.add(new Date(Long.MAX_VALUE))
            _dates_a.add(null)
            _dates_b = new ArrayList()
            _dates_c = null;

            _classes_a = new ArrayList()
            _classes_a.add(boolean.class)
            _classes_a.add(char.class)
            _classes_a.add(byte.class)
            _classes_a.add(short.class)
            _classes_a.add(int.class)
            _classes_a.add(long.class)
            _classes_a.add(float.class)
            _classes_a.add(double.class)
            _classes_a.add(String.class)
            _classes_a.add(Date.class)
            _classes_a.add(null)
            _classes_a.add(Class.class)
            _classes_b = new ArrayList()
            _classes_c = null;

            _sb_a = new LinkedList()
            _sb_a.add(new StringBuffer("one"))
            _sb_a.add(new StringBuffer("two"))
            _sb_b = new LinkedList()
            _sb_c = null;

            _poly_a = new ArrayList()
            _poly_a.add(Boolean.TRUE)
            _poly_a.add(Character.valueOf('a' as char))
            _poly_a.add(Byte.valueOf((byte) 16))
            _poly_a.add(Short.valueOf((short) 69))
            _poly_a.add(Integer.valueOf(714))
            _poly_a.add(Long.valueOf(420))
            _poly_a.add(Float.valueOf(0.4))
            _poly_a.add(Double.valueOf(3.14))
            _poly_a.add("Jones'in\tfor\u0019a\ncoke")
            _poly_a.add(null)
            _poly_a.add(new StringBuffer("eddie"))
            _poly_a.add(_testDate)
            _poly_a.add(Long.class)
            _poly_a.add(['beatles', 'stones'] as String[])
            _poly_a.add([new TestObject("flint"), new TestObject("stone")] as TestObject[])
            _poly_a.add(['fox', 'wolf', 'dog', 'hound'] as Object[])

            Set colors = new TreeSet()
            colors.add(new TestObject("red"))
            colors.add(new TestObject("green"))
            colors.add(new TestObject("blue"))
            _poly_a.add(colors)

            _strs_a = new HashSet()
            _strs_a.add("Dog")
            _strs_a.add("Cat")
            _strs_a.add("Cow")
            _strs_a.add("Horse")
            _strs_a.add("Duck")
            _strs_a.add("Bird")
            _strs_a.add("Goose")
            _strs_b = new HashSet()
            _strs_c = null;
            _strs_d = new TreeSet()
            _strs_d.addAll(_strs_a)

            _typedCol = new ArrayList()
            _typedCol.add("string")
            _typedCol.add(null)
            _typedCol.add(new Date(19))
            _typedCol.add(true)
            _typedCol.add(17.76)
            _typedCol.add(TimeZone.getTimeZone("PST"))

            _typedSet = new HashSet()
            _typedSet.add("string")
            _typedSet.add(null)
            _typedSet.add(new Date(19))
            _typedSet.add(true)
            _typedSet.add(17.76)
            _typedSet.add(TimeZone.getTimeZone("PST"))

            _imm_lst_0 = List.of()
            _imm_lst_1 = List.of("One")

        }
    }

    @Test
    void testCollection() {
        TestUtil.printLine("\nTestJsonReaderWriter.testCollection()")
        ManyCollections obj = new ManyCollections()
        obj.init()
        String jsonOut = TestUtil.getJsonString(obj)
        TestUtil.printLine(jsonOut)

        ManyCollections root = (ManyCollections) TestUtil.readJsonObject(jsonOut)

        assertCollection(root)

        JsonWriter writer = new JsonWriter(new ByteArrayOutputStream())
        writer.write(obj)
        // TODO: Uncomment to test identity counter strategies (currently incremental + only referenced)
//        System.out.TestUtil.printLine("writer._identity = " + writer._identity)
    }

    private void assertCollection(ManyCollections root) {
        assertTrue(root._cols.length == 3)
        assertTrue(root._cols[0].getClass().equals(ArrayList.class))
        assertTrue(root._cols[1].getClass().equals(HashSet.class))
        assertTrue(root._cols[2].getClass().equals(TreeSet.class))

        Collection array = root._cols[0];
        assertTrue(array.size() == 4)
        assertTrue(array.getClass().equals(ArrayList.class))
        List alist = (List) array;
        assertTrue(alist.get(0).equals(_testDate))
        assertTrue(alist.get(1).equals("Hello"))
        assertTrue(alist.get(2).equals(new TestObject("fudge")))
        assertTrue(alist.get(3).equals(_CONST_INT))

        Collection set = root._cols[1];
        assertTrue(set.size() == 4)
        assertTrue(set.getClass().equals(HashSet.class))
        assertTrue(set.contains(Map.class))
        assertTrue(set.contains(Boolean.TRUE))
        assertTrue(set.contains(null))
        assertTrue(set.contains(_CONST_INT))

        set = root._cols[2];
        assertTrue(set.size() == 4)
        assertTrue(set.getClass().equals(TreeSet.class))
        assertTrue(set.contains(Integer.valueOf(Integer.MIN_VALUE)))
        assertTrue(set.contains(Integer.valueOf(1)))
        assertTrue(set.contains(Integer.valueOf(Integer.MAX_VALUE)))
        assertTrue(set.contains(_CONST_INT))

        assertTrue(root._strings_a.size() == 4)
        assertTrue(root._strings_a.get(0).equals("Alpha"))
        assertTrue(root._strings_a.get(1).equals("Bravo"))
        assertTrue(root._strings_a.get(2).equals("Charlie"))
        assertTrue(root._strings_a.get(3).equals("Delta"))
        assertTrue(root._strings_b.isEmpty())
        assertNull(root._strings_c)

        assertTrue(root._dates_a.size() == 4)
        assertTrue(root._dates_a.get(0).equals(new Date(0)))
        assertTrue(root._dates_a.get(1).equals(_testDate))
        assertTrue(root._dates_a.get(2).equals(new Date(Long.MAX_VALUE)))
        assertNull(root._dates_a.get(3))
        assertTrue(root._dates_b.isEmpty())
        assertNull(root._dates_c)

        assertTrue(root._classes_a.size() == 12)
        assertTrue(root._classes_a.get(0) == boolean.class)
        assertTrue(root._classes_a.get(1).equals(char.class))
        assertTrue(root._classes_a.get(2).equals(byte.class))
        assertTrue(root._classes_a.get(3).equals(short.class))
        assertTrue(root._classes_a.get(4).equals(int.class))
        assertTrue(root._classes_a.get(5).equals(long.class))
        assertTrue(root._classes_a.get(6).equals(float.class))
        assertTrue(root._classes_a.get(7).equals(double.class))
        assertTrue(root._classes_a.get(8).equals(String.class))
        assertTrue(root._classes_a.get(9).equals(Date.class))
        assertNull(root._classes_a.get(10))
        assertTrue(root._classes_a.get(11).equals(Class.class))
        assertTrue(root._classes_b.isEmpty())
        assertNull(root._classes_c)

        assertTrue(root._sb_a.size() == 2)
        assertTrue(root._sb_a.get(0).toString().equals("one"))
        assertTrue(root._sb_a.get(1).toString().equals("two"))
        assertTrue(root._sb_b.isEmpty())
        assertNull(root._sb_c)

        assertTrue(root._poly_a.size() == 17)
        assertTrue(root._poly_a.get(0).equals(Boolean.TRUE))
        assertTrue(root._poly_a.get(1).equals(Character.valueOf('a' as char)))
        assertTrue(root._poly_a.get(2).equals(Byte.valueOf((byte) 16)))
        assertTrue(root._poly_a.get(3).equals(Short.valueOf((byte) 69)))
        assertTrue(root._poly_a.get(4).equals(Integer.valueOf(714)))
        assertTrue(root._poly_a.get(5).equals(Long.valueOf(420)))
        assertTrue(root._poly_a.get(6).equals(Float.valueOf(0.4)))
        assertTrue(root._poly_a.get(7).equals(Double.valueOf(3.14)))
        assertTrue(root._poly_a.get(8).equals("Jones'in\tfor\u0019a\ncoke"))
        assertNull(root._poly_a.get(9))
        assertTrue(root._poly_a.get(10).toString().equals("eddie"))
        assertTrue(root._poly_a.get(11).equals(_testDate))
        assertTrue(root._poly_a.get(12).equals(Long.class))

        String[] sa = (String[]) root._poly_a.get(13)
        assertTrue(sa[0].equals("beatles"))
        assertTrue(sa[1].equals("stones"))
        TestObject[] to = (TestObject[]) root._poly_a.get(14)
        assertTrue(to[0].name.equals("flint"))
        assertTrue(to[1].name.equals("stone"))
        Object[] arrayInCol = (Object[]) root._poly_a.get(15)
        assertTrue(arrayInCol[0].equals("fox"))
        assertTrue(arrayInCol[1].equals("wolf"))
        assertTrue(arrayInCol[2].equals("dog"))
        assertTrue(arrayInCol[3].equals("hound"))

        Set colors = (Set) root._poly_a.get(16)
        assertTrue(colors.size() == 3)
        assertTrue(colors.contains(new TestObject("red")))
        assertTrue(colors.contains(new TestObject("green")))
        assertTrue(colors.contains(new TestObject("blue")))

        assertTrue(root._strs_a.size() == 7)
        assertTrue(root._strs_a.contains("Dog"))
        assertTrue(root._strs_a.contains("Cat"))
        assertTrue(root._strs_a.contains("Cow"))
        assertTrue(root._strs_a.contains("Horse"))
        assertTrue(root._strs_a.contains("Duck"))
        assertTrue(root._strs_a.contains("Bird"))
        assertTrue(root._strs_a.contains("Goose"))
        assertTrue(root._strs_b.empty)
        assertNull(root._strs_c)
        assertTrue(root._strs_d.size() == 7)
        assertTrue(root._strs_d instanceof TreeSet)

        assertTrue(root._typedCol != null)
        assertTrue(root._typedCol.size() == 6)
        assertTrue("string".equals(root._typedCol.get(0)))
        assertTrue(null == root._typedCol.get(1))
        assertTrue((new Date(19)).equals(root._typedCol.get(2)))
        assertTrue((Boolean) root._typedCol.get(3))
        assertTrue(17.76 == (Double) root._typedCol.get(4))
        assertTrue(TimeZone.getTimeZone("PST").equals(root._typedCol.get(5)))

        assertTrue(root._typedSet != null)
        assertTrue(root._typedSet.size() == 6)
        assertTrue(root._typedSet.contains("string"))
        assertTrue(root._typedCol.contains(null))
        assertTrue(root._typedCol.contains(new Date(19)))
        assertTrue(root._typedCol.contains(true))
        assertTrue(root._typedCol.contains(17.76))
        assertTrue(root._typedCol.contains(TimeZone.getTimeZone("PST")))
    }

    @Test
    void testReconstituteCollection2() {
        ManyCollections testCol = new ManyCollections()
        testCol.init()
        String json0 = TestUtil.getJsonString(testCol)
        TestUtil.printLine("json0=" + json0)
        Map testCol2 = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS): true] as Map)

        String json1 = TestUtil.getJsonString(testCol2)
        TestUtil.printLine("json1=" + json1)

        ManyCollections testCol3 = (ManyCollections) TestUtil.readJsonObject(json1)
        assertCollection(testCol3)   // Re-written from Map of Maps and re-loaded correctly
        assertTrue(json0.equals(json1))
    }

    @Test
    void testAlwaysShowType() {
        ManyCollections tc = new ManyCollections()
        tc.init()
        def args = [(JsonWriter.TYPE): true]
        def json0 = JsonWriter.objectToJson(tc, args)
        def json1 = JsonWriter.objectToJson(tc)
        TestUtil.printLine("json0 = " + json0)
        TestUtil.printLine("json1 = " + json1)
        assertTrue(json0.length() > json1.length())
    }

    @Test
    void testCollectionWithEmptyElement() {
        List list = new ArrayList()
        list.add("a")
        list.add(null)
        list.add("b")
        String json = TestUtil.getJsonString(list)
        List list2 = (List) TestUtil.readJsonObject(json)
        assertTrue(list.equals(list2))

        json = '{"@type":"java.util.ArrayList","@items":["a",{},"b"]}'
        list2 = (List) TestUtil.readJsonObject(json)
        assertTrue(list2.size() == 3)
        assertTrue(list2.get(0).equals("a"))
        assertEquals(list2.get(1).getClass(), JsonObject.class)
        assertTrue(list2.get(2).equals("b"))
    }

    @Test
    void testCollectionWithReferences() {
        TestObject o = new TestObject("JSON")
        List list = new ArrayList()
        list.add(o)
        list.add(o)

        // Backward reference
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        List list2 = (List) TestUtil.readJsonObject(json)
        assertTrue(list.equals(list2))

        // Forward reference
        String pkg = TestObject.class.name;
        json = '{"@type":"java.util.ArrayList","@items":[{"@ref":3},{"@id":3,"@type":"' + pkg + '","_name":"JSON","_other":null}]}'
        list2 = (List) JsonReader.jsonToJava(json)
        assertTrue(list.equals(list2))
    }

    @Test
    void testCollectionWithNonJsonPrimitives() {
        Collection col = new ArrayList()
        col.add(Integer.valueOf(7))
        col.add(Short.valueOf((short) 9))
        col.add(Float.valueOf(3.14))
        String json = TestUtil.getJsonString(col)
        Collection col1 = (Collection) TestUtil.readJsonObject(json)
        assertTrue(col.equals(col1))
    }

    @Test
    void testCollectionWithParameterizedTypes() {
        String json = '{"@type":"' + ParameterizedCollection.class.getName() + '", "content":{"foo":[{"x":1,"y":2},{"x":10,"y":20}],"bar":[{"x":3,"y":4}, {"x":30,"y":40}]}}'
        ParameterizedCollection pCol = (ParameterizedCollection) JsonReader.jsonToJava(json)
        Set<Point> points = pCol.content.get("foo")
        assertNotNull(points)
        assertEquals(2, points.size())
        points.contains(new Point(1, 2))
        points.contains(new Point(10, 20))

        points = pCol.content.get("bar")
        assertNotNull(points)
        assertEquals(2, points.size())
        points.contains(new Point(3, 4))
        points.contains(new Point(30, 40))

        json = '{"@type":"' + ParameterizedCollection.class.getName() + '", "content":{"foo":[],"bar":null}}'
        pCol = (ParameterizedCollection) JsonReader.jsonToJava(json)
        points = pCol.content.get("foo")
        assertNotNull(points)
        assertEquals(0, points.size())

        points = pCol.content.get("bar")
        assertNull(points)

        json = '{"@type":"' + ParameterizedCollection.class.getName() + '", "content":{}}'
        pCol = (ParameterizedCollection) JsonReader.jsonToJava(json)
        assertNotNull(pCol.content)
        assertEquals(0, pCol.content.size())

        json = '{"@type":"' + ParameterizedCollection.class.getName() + '", "content":null}'
        pCol = (ParameterizedCollection) JsonReader.jsonToJava(json)
        assertNull(pCol.content)
    }

    @Test
    void testEmptyCollections() {
        EmptyCols emptyCols;
        String className = TestCollection.class.getName()
        String json = '{"@type":"' + className + '$EmptyCols","col":{},"list":{},"map":{},"set":{},"sortedSet":{},"sortedMap":{}}'
        TestUtil.printLine("json = " + json)
        emptyCols = (EmptyCols) TestUtil.readJsonObject(json)

        assertTrue(emptyCols.col.size() == 0)
        assertTrue(emptyCols.col instanceof ArrayList)
        assertTrue(emptyCols.list.size() == 0)
        assertTrue(emptyCols.list instanceof ArrayList)
        assertTrue(emptyCols.map.size() == 0)
        assertTrue(emptyCols.map instanceof LinkedHashMap)
        assertTrue(emptyCols.set.size() == 0)
        assertTrue(emptyCols.set instanceof LinkedHashSet)
        assertTrue(emptyCols.sortedSet.size() == 0)
        assertTrue(emptyCols.sortedSet instanceof TreeSet)
        assertTrue(emptyCols.sortedMap.size() == 0)
        assertTrue(emptyCols.sortedMap instanceof TreeMap)
    }

    @Test
    void testEnumWithPrivateMembersInCollection() {
        TestEnum4 x = TestEnum4.B;
        List list = new ArrayList()
        list.add(x)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine(json)
        String className = TestCollection.class.getName()
        assertEquals('{"@type":"java.util.ArrayList","@items":[{"@type":"' + className + '$TestEnum4","name":"B","age":21,"foo":"bar"}]}', json)

        ByteArrayOutputStream ba = new ByteArrayOutputStream()
        JsonWriter writer = new JsonWriter(ba, [(JsonWriter.ENUM_PUBLIC_ONLY): true])
        writer.write(list)
        json = new String(ba.toByteArray())
        TestUtil.printLine(json)
        assertEquals('{"@type":"java.util.ArrayList","@items":[{"@type":"' + className + '$TestEnum4","name":"B"}]}', json)
    }

    @Test
    void testGenericInfoCollection() {
        String className = PointList.class.name
        String json = '{"@type":"' + className + '","points":{"@type":"java.util.ArrayList","@items":[{"x":1,"y":2}]}}'
        PointList list = (PointList) TestUtil.readJsonObject(json)
        assertTrue(list.points.size() == 1)
        Point p1 = list.points.get(0)
        assertTrue(p1.x == 1 && p1.y == 2)
    }

    @Test
    void testLocaleInCollection() {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry())
        List list = new ArrayList()
        list.add(locale)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        list = (List) TestUtil.readJsonObject(json)
        assertTrue(list.size() == 1)
        assertTrue(list.get(0).equals(locale))
    }

    @Test
    void testMapOfMapsCollection() {
        List stuff = new ArrayList()
        stuff.add("Hello")
        Object testObj = new TestObject("test object")
        stuff.add(testObj)
        stuff.add(testObj)
        stuff.add(new Date())
        String json = TestUtil.getJsonString(stuff)
        TestUtil.printLine("json=" + json)

        JsonObject map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        Object[] items = map.getArray()
        assertTrue(items.length == 4)
        assertTrue("Hello".equals(items[0]))
        assertTrue(items[1] == items[2])

        List list = new ArrayList()
        list.add([123L, null, true, "Hello"] as Object[])
        json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        items = (Object[]) map.getArray()
        assertTrue(items.length == 1)
        Object[] oa = (Object[]) items[0];
        assertTrue(oa.length == 4)
        assertTrue(oa[0].equals(123L))
        assertTrue(oa[1] == null)
        assertTrue(oa[2] == Boolean.TRUE)
        assertTrue("Hello".equals(oa[3]))
    }

    @Test
    void testReconstituteCollection() {
        TestObject to = new TestObject("football")
        Collection objs = new ArrayList()
        Date now = new Date()
        objs.add(now)
        objs.add(123.45)
        objs.add("This is a string")
        objs.add(null)
        objs.add(to)
        objs.add(["dog", ["a", "b", "c"] as String[]] as Object[])
        Collection two = new ArrayList()
        two.add(objs)
        two.add("bella")
        two.add(objs)

        String json0 = TestUtil.getJsonString(two)
        TestUtil.printLine("json0=" + json0)
        Map map = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS): true] as Map)
        map.hashCode()
        String json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)

        List colOuter = (List) TestUtil.readJsonObject(json1)
        assertTrue(colOuter.get(0) == colOuter.get(2))
        assertTrue("bella".equals(colOuter.get(1)))
        List col1 = (List) colOuter.get(0)
        assertTrue(col1.get(0).equals(now))
        assertTrue(col1.get(1).equals(123.45))
        assertTrue("This is a string".equals(col1.get(2)))
        assertTrue(col1.get(3) == null)
        assertTrue(col1.get(4).equals(to))
        assertTrue(col1.get(5) instanceof Object[])
        Object[] oa = (Object[]) col1.get(5)
        assertTrue("dog".equals(oa[0]))
        assertTrue(oa[1] instanceof String[])
        String[] sa = (String[]) oa[1];
        assertTrue("a".equals(sa[0]))
        assertTrue("b".equals(sa[1]))
        assertTrue("c".equals(sa[2]))

        assertTrue(json0.equals(json1))
    }

    @Test
    void testReconstituteEmptyCollection() {
        Collection empty = new ArrayList()
        String json0 = TestUtil.getJsonString(empty)
        TestUtil.printLine("json0=" + json0)

        Map map = (Map) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS): true] as Map)
        assertTrue(map != null)
        assertTrue(map.isEmpty())
        String json1 = TestUtil.getJsonString(map)
        TestUtil.printLine("json1=" + json1)

        assertTrue(json0.equals(json1))

        Object[] list = [empty, empty];
        json0 = TestUtil.getJsonString(list)
        TestUtil.printLine("json0=" + json0)

        Object[] array = (Object[]) JsonReader.jsonToJava(json0, [(JsonReader.USE_MAPS): true] as Map)
        assertTrue(array != null)
        list = array
        assertTrue(list.length == 2)
        Map e1 = (Map) list[0];
        Map e2 = (Map) list[1];
        assertTrue(e1.isEmpty())
        assertTrue(e2.isEmpty())
    }

    @Test
    void testUntypedCollections() {
        Object[] poly = ["Road Runner", 16L, 3.1415d, true, false, null, 7, "Coyote", "Coyote"] as Object[];
        String json = TestUtil.getJsonString(poly)
        TestUtil.printLine("json=" + json)
        assertTrue('["Road Runner",16,3.1415,true,false,null,{"@type":"int","value":7},"Coyote","Coyote"]'.equals(json))
        Collection col = new ArrayList()
        col.add("string")
        col.add(Long.valueOf(16))
        col.add(Double.valueOf(3.14159))
        col.add(Boolean.TRUE)
        col.add(Boolean.FALSE)
        col.add(null)
        col.add(Integer.valueOf(7))
        json = TestUtil.getJsonString(col)
        TestUtil.printLine("json=" + json)
        assertEquals('{"@type":"java.util.ArrayList","@items":["string",16,3.14159,true,false,null,{"@type":"int","value":7}]}', json)
    }

    @Test
    void testEmptyArrayList() {
        EmptyArrayList x = new EmptyArrayList()
        String json = TestUtil.getJsonString(x)
        TestUtil.printLine(json)
        assertTrue(json.contains('list":[]'))

        Map obj = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        json = TestUtil.getJsonString(obj)
        TestUtil.printLine(json)
        assertTrue(json.contains('list":[]'))
    }
}
