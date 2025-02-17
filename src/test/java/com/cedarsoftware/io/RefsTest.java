package com.cedarsoftware.io;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
class RefsTest
{
    private static class TestReferences implements Serializable
    {
        // Field ordering below is vital (b, a, c).  We treat JSON keys in alphabetical
        // order, whereas Java Field walking is in declaration order.
        private TestObject _b;
        private TestObject[] _a;
        private TestObject[] _c;

        private TestObject[][] _foo;

        private TestObject _back_a;
        private TestObject _back_b;
        private TestObject _back_c ;

        private TestObject _cycle_a;
        private TestObject _cycle_b;

        private TestObject _polymorphic;
        private TestObject[] _polymorphics;

        private char _big;

        private void init()
        {
            _big = '\ufbfc';
            _b = new TestObject("B");
            _a = new TestObject[] {_b};
            _c = new TestObject[] {_b};
            _foo = new TestObject[][] { null, new TestObject[] {}, new TestObject[] {new TestObject("alpha"), new TestObject("beta")} };
            _back_a = new TestObject("back test");
            _back_b = _back_a;
            _back_c = _back_b;

            _cycle_a = new TestObject("a");
            _cycle_b = new TestObject("b");
            _cycle_a._other = _cycle_b;
            _cycle_b._other = _cycle_a;

            _polymorphic = new TestObjectKid("dilbert", "dilbert@myotherdrive.com");
            _polymorphics = new TestObject[] {new TestObjectKid("dog", "dog@house.com"), new TestObjectKid("cat", "cat@house.com"), new TestObject("shortie")};
        }
    }

    static class Column
    {
        final Object value;

        Column(Object v)
        {
            value = v;
        }
    }

    static class Axis
    {
        final String name;
        final Column column;

        Axis(String n, Column c)
        {
            name = n;
            column = c;
        }
    }

    static class Delta
    {
        final Object newValue;

        Delta(Object n)
        {
            newValue = n;
        }
    }

    public static class DateWithChildren
    {
        public LocalDate date;
        public DateWithChildren[] children;
    }

    @Test
    void testReferences()
    {
        TestReferences obj = new TestReferences();
        obj.init();
        String jsonOut = TestUtil.toJson(obj);
        TestUtil.printLine(jsonOut);

        TestReferences root = (TestReferences) TestUtil.toObjects(jsonOut, null);

        assertEquals(1, root._a.length);
        assertNotNull(root._b);
        assertSame(root._a[0], root._b);
        assertEquals(1, root._c.length);
        assertSame(root._c[0], root._b);

        assertEquals(3, root._foo.length);
        assertNull(root._foo[0]);
        assertEquals(0, root._foo[1].length);
        assertEquals(2, root._foo[2].length);

        assertSame(root._back_b, root._back_a);
        assertSame(root._back_c, root._back_a);

        assertEquals("a", root._cycle_a._name);
        assertEquals("b", root._cycle_b._name);
        assertSame(root._cycle_a._other, root._cycle_b);
        assertSame(root._cycle_b._other, root._cycle_a);

        assertEquals(TestObjectKid.class, root._polymorphic.getClass());
        TestObjectKid kid = (TestObjectKid) root._polymorphic;
        assert "dilbert".equals(kid._name);
        assert "dilbert@myotherdrive.com".equals(kid._email);

        assertEquals(3, root._polymorphics.length);
        TestObjectKid kid1 = (TestObjectKid) root._polymorphics[0];
        TestObjectKid kid2 = (TestObjectKid) root._polymorphics[1];
        TestObject kid3 = root._polymorphics[2];
        assertSame(kid1.getClass(), TestObjectKid.class);
        assertSame(kid1.getClass(), kid2.getClass());
        assertSame(kid3.getClass(), TestObject.class);
        assert "dog".equals(kid1._name);
        assert "dog@house.com".equals(kid1._email);
        assert "cat".equals(kid2._name);
        assert "cat@house.com".equals(kid2._email);
        assert "shortie".equals(kid3._name);
        assert '\ufbfc' == root._big;
    }

    @Test
    void testRefResolution()
    {
        TestObject a = new TestObject("a");
        TestObject b = new TestObject("b");
        a._other = b;
        b._other = a;
        String json = TestUtil.toJson(a);
        Map<Class<?>, JsonReader.JsonClassReader> readers = new HashMap<>();
        readers.put(TestObject.class, new TestObjectReader());
        TestObject aa = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(readers).build(), null);
    }

    private static class TestObjectReader implements JsonReader.JsonClassReader {

        @Override
        public Object read(Object jsonObj, Resolver resolver) {
            JsonObject jObj = (JsonObject) jsonObj;
            TestObject x = new TestObject((String) jObj.get("name"));
            JsonObject b1 = (JsonObject) jObj.get("_other");
            JsonObject aRef = (JsonObject) b1.get("_other");
            assert aRef.isReference();
            JsonObject aTarget = resolver.getReferences().get(aRef.refId);
            assert aRef != aTarget;
            assert "a".equals(aTarget.get("_name"));
            return x;
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTypedAndUntypedReference()
    {
        Column column = new Column("foo");
        Axis axis = new Axis("state", column);
        Delta delta1 = new Delta(column);
        Delta delta2 = new Delta(axis);

        List<Delta> deltas = new ArrayList<>();
        deltas.add(delta2);
        deltas.add(delta1);

        // With forward reference
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@type\":\"com.cedarsoftware.io.RefsTest$Delta\",\"newValue\":{\"@ref\":1}}, {\"@type\":\"com.cedarsoftware.io.RefsTest$Delta\",\"newValue\":{\"@type\":\"com.cedarsoftware.io.RefsTest$Axis\",\"name\":\"state\",\"column\":{\"@id\":1,\"value\":\"foo\"}}}]}";
        List<Object> newList = TestUtil.toObjects(json, null);
        Delta d1 = (Delta) newList.get(0);
        Delta d2 = (Delta) newList.get(1);

        assert d1.newValue instanceof Column;
        assert d2.newValue instanceof Axis;
        assert ((Axis) d2.newValue).column != null;

        // Backward reference
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@type\":\"com.cedarsoftware.io.RefsTest$Delta\",\"newValue\":{\"@type\":\"com.cedarsoftware.io.RefsTest$Axis\",\"name\":\"state\",\"column\":{\"@id\":1,\"value\":\"foo\"}}},{\"@type\":\"com.cedarsoftware.io.RefsTest$Delta\",\"newValue\":{\"@ref\":1}}]}";
        newList = TestUtil.toObjects(json, null);
        d1 = (Delta) newList.get(0);
        d2 = (Delta) newList.get(1);

        assert d1.newValue instanceof Axis;
        assert ((Axis) d1.newValue).column != null;
        assert d2.newValue instanceof Column;
    }

    @Test
    public void testRefChainAsJsonObjects()
    {
        String json = ClassUtilities.loadResourceAsString("references/chainRef.json");
        JsonObject jsonObj = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        LocalDate christmas = (LocalDate) jsonObj.get("date");
        Object[] children =  (Object[])jsonObj.get("children");
        assert children.length == 6;
        assert children[0] instanceof JsonObject;
        JsonObject kid1 = (JsonObject)children[0];
        JsonObject kid5 = (JsonObject)children[4];
        assert kid1.get("date").equals(christmas);
        assert kid5 == kid1;
        assert kid1 == jsonObj;
        JsonObject kid6 = (JsonObject) children[5];
        assert kid6 != kid5;
        Object date = jsonObj.get("date");
        assert kid6.get("date").equals("2023/11/17");
    }

    @Test
    public void testRefChainAsJavaObjects()
    {
        DateWithChildren dc = new DateWithChildren();

        String json = ClassUtilities.loadResourceAsString("references/chainRef.json");
        DateWithChildren root = TestUtil.toObjects(json, null);
        DateWithChildren[] children =  root.children;
        assert children.length == 6;
        DateWithChildren kid1 = children[0];
        DateWithChildren kid5 = children[4];
        assert kid1.date.equals(root.date);
        assert kid5 == kid1;
        assert kid1 == root;
        DateWithChildren kid6 = children[5];
        assert kid6 != kid5;
        assert root.date.equals(LocalDate.of(2023, 12, 25));
        assert kid6.date.equals(LocalDate.of(2023, 11, 17));
    }

    @Test
    void testConvertableReferences() {
        String json = "[{\"@ref\":1},{\"@id\":1,\"@type\":\"ZonedDateTime\",\"value\":\"2024-04-21T14:55:53.77587-04:00[America/New_York]\"}]";
        Object[] dates = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        ZonedDateTime zdt1 = (ZonedDateTime) dates[0];
        ZonedDateTime zdt2 = (ZonedDateTime) dates[1];
        assertEquals(zdt1, zdt2);
        assertNotSame(zdt1, zdt2);
    }
}
