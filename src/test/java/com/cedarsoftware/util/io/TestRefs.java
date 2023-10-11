package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

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
class TestRefs
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

    @Test
    void testReferences()
    {
        TestReferences obj = new TestReferences();
        obj.init();
        String jsonOut = TestUtil.getJsonString(obj);
        TestUtil.printLine(jsonOut);

        TestReferences root = (TestReferences) TestUtil.readJsonObject(jsonOut);

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

        assertEquals(root._polymorphic.getClass(), TestObjectKid.class);
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
        String json = JsonWriter.objectToJson(a);
        Map<String, Object> options = new HashMap<>();
        Map<Class, JsonReader.JsonClassReaderEx> readers = new HashMap<>();
        options.put(JsonReader.CUSTOM_READER_MAP, readers);
        readers.put(TestObject.class, (jOb, stack, args) -> {
            JsonObject jObj = (JsonObject) jOb;
            TestObject x = new TestObject((String) jObj.get("name"));
            JsonObject b1 = (JsonObject) jObj.get("_other");
            JsonObject aRef = (JsonObject) b1.get("_other");
            assert aRef.isReference();
            JsonReader reader = JsonReader.JsonClassReaderEx.Support.getReader(args);
            JsonObject aTarget = (JsonObject) reader.getRefTarget(aRef);
            assert aRef != aTarget;
            assert "a".equals(aTarget.get("_name"));
            return x;
        });
        TestObject aa = (TestObject) TestUtil.readJsonObject(json, options);
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
        String json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@type\":\"com.cedarsoftware.util.io.TestRefs$Delta\",\"newValue\":{\"@ref\":1}}, {\"@type\":\"com.cedarsoftware.util.io.TestRefs$Delta\",\"newValue\":{\"@type\":\"com.cedarsoftware.util.io.TestRefs$Axis\",\"name\":\"state\",\"column\":{\"@id\":1,\"value\":\"foo\"}}}]}";
        List<Object> newList = (List<Object>) JsonReader.jsonToJava(json);
        Delta d1 = (Delta) newList.get(0);
        Delta d2 = (Delta) newList.get(1);

        assert d1.newValue instanceof Column;
        assert d2.newValue instanceof Axis;
        assert ((Axis) d2.newValue).column != null;

        // Backward reference
        json = "{\"@type\":\"java.util.ArrayList\",\"@items\":[{\"@type\":\"com.cedarsoftware.util.io.TestRefs$Delta\",\"newValue\":{\"@type\":\"com.cedarsoftware.util.io.TestRefs$Axis\",\"name\":\"state\",\"column\":{\"@id\":1,\"value\":\"foo\"}}},{\"@type\":\"com.cedarsoftware.util.io.TestRefs$Delta\",\"newValue\":{\"@ref\":1}}]}";
        newList = (List<Object>) JsonReader.jsonToJava(json);
        d1 = (Delta) newList.get(0);
        d2 = (Delta) newList.get(1);

        assert d1.newValue instanceof Axis;
        assert ((Axis) d1.newValue).column != null;
        assert d2.newValue instanceof Column;
    }
}
