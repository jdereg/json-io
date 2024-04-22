package com.cedarsoftware.io;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
public class TemplateFieldsTest
{
    @Test
    public void testTemplateClassField()
    {
        Test1 container = new Test1();
        Test1 container2 = new Test1();
        List<Test1> llList = new LinkedList<>();
        llList.add(container2);
        container.internalMember.hsOne.put("key", llList);
        String json = TestUtil.toJson(container);

        // This would throw exception in the past
        TestUtil.toObjects(json, null);
    }

    @Test
    public void testTemplateNonClassFields()
    {
        Test3 container = new Test3();
        String json = TestUtil.toJson(container);
        // This would throw exception in the past
        Test3 reanimated = TestUtil.toObjects(json, null);
        assert reanimated.internalMember.two.getName().equals("Jane");
        assert reanimated.internalMember.two.getAge() == 45;
    }

    @Test
    public void testSingle()
    {
        UseSingle useSingle = new UseSingle(new Single<>("Steel", "Wood"));
        String json = TestUtil.toJson(useSingle);
        UseSingle other = TestUtil.toObjects(json, null);

        assertEquals("Steel", other.single.field1);
        assertEquals("Wood", other.single.field2);
    }

    @Test
    public void testTwoParam()
    {
        UseTwoParam useTwoParam = new UseTwoParam(new TwoParam("Hello", "Goodbye", new Point(20, 40)));
        String json = TestUtil.toJson(useTwoParam);
        UseTwoParam other = TestUtil.toObjects(json, null);

        assertEquals("Hello", other.twoParam.field1);
        assertEquals("Goodbye", other.twoParam.field2);
        assertEquals(new Point(20, 40), other.twoParam.field3);

        useTwoParam = new UseTwoParam(new TwoParam(new Point(10, 30), new Point(20, 40), "Hello"));
        json = TestUtil.toJson(useTwoParam);
        other = TestUtil.toObjects(json, null);

        assertEquals(new Point(10, 30), other.twoParam.field1);
        assertEquals(new Point(20, 40), other.twoParam.field2);
        assertEquals("Hello", other.twoParam.field3);

        useTwoParam = new UseTwoParam(new TwoParam(50, 100, "Hello"));
        json = TestUtil.toJson(useTwoParam);
        other = TestUtil.toObjects(json, null);

        assertEquals(50, other.twoParam.field1);
        assertEquals(100, other.twoParam.field2);
        assertEquals("Hello", other.twoParam.field3);

        useTwoParam = new UseTwoParam(new TwoParam(new Point(10, 30), new Point(20, 40), new TestObject("Hello")));
        json = TestUtil.toJson(useTwoParam);
        other = TestUtil.toObjects(json, null);

        assertEquals(new Point(10, 30), other.twoParam.field1);
        assertEquals(new Point(20, 40), other.twoParam.field2);
        assertEquals(new TestObject("Hello"), other.twoParam.field3);
    }

    @Test
    public void testStaticSingle()
    {
        StaticUseSingle useSingle = new StaticUseSingle(new StaticSingle<>("Boonies"));

        String json = TestUtil.toJson(useSingle);
        //this will crash on ArrayIndexOutOfBoundsException
        StaticUseSingle other = TestUtil.toObjects(json, null);

        assertEquals("Boonies", other.single.field1);
    }

    @Test
    public void test3TypeGeneric()
    {
        String json = "{\"@type\":\"" + GenericHolder.class.getName() + "\",\"a\":{\"t\":{\"x\":1,\"y\":2},\"u\":\"Sochi\",\"v\":{\"x\":10,\"y\":20}}}";
        GenericHolder gen = TestUtil.toObjects(json, null);
        assertEquals(new Point(1, 2), gen.a.t);
        assertEquals("Sochi", gen.a.u);
        assertEquals(new Point(10, 20), gen.a.v);

        json = "{\"@type\":\"" + GenericHolder.class.getName() + "\",\"a\":{\"t\":null,\"u\":null,\"v\":null}}";
        gen = TestUtil.toObjects(json, null);
        assertNull(gen.a.t);
        assertNull(gen.a.u);
        assertNull(gen.a.v);
    }

    public static class Test1
    {
        protected Test2<String, Object> internalMember = new Test2<>();
    }

    public static class Test2<K, V>
    {
        protected Map<K, Collection<? extends V>> hsOne = new HashMap<>();
        protected Class<?> two = LinkedList.class;
    }

    public static class Person
    {
        public Person(String n, int a)
        {
            name = n;
            age = a;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public int getAge()
        {
            return age;
        }

        public void setAge(int age)
        {
            this.age = age;
        }

        private String name;
        private int age;
    }

    public static class Test3
    {
        protected Test4<String> internalMember = new Test4<>();
    }

    public static class Test4<V>
    {
        protected Person two = new Person("Jane", 45);
    }

    class Single<T>
    {
        private T field1;
        private T field2;

        public Single(T field1, T field2)
        {
            this.field1 = field1;
            this.field2 = field2;
        }
    }

    class UseSingle
    {
        private Single<String> single;

        public UseSingle(Single<String> single)
        {
            this.single = single;
        }
    }

    public static class StaticSingle<T>
    {
        public StaticSingle(T field1)
        {
            this.field1 = field1;
        }

        private T field1;
    }

    public static class StaticUseSingle
    {
        public StaticUseSingle(StaticSingle<String> single)
        {
            this.single = single;
        }

        private StaticSingle<String> single;
    }

    class TwoParam<T, V>
    {
        private T field1;
        private T field2;
        private V field3;

        public TwoParam(T field1, T field2, V field3)
        {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
    }

    class UseTwoParam
    {
        private TwoParam twoParam;

        public UseTwoParam(TwoParam twoParam)
        {
            this.twoParam = twoParam;
        }
    }

    static class ThreeType<T, U, V>
    {
        T t;
        U u;
        V v;

        public ThreeType(T tt, U uu, V vv)
        {
            t = tt;
            u = uu;
            v = vv;
        }
    }

    public static class GenericHolder
    {
        public ThreeType<Point, String, Point> getA()
        {
            return a;
        }

        public void setA(ThreeType<Point, String, Point> a)
        {
            this.a = a;
        }

        private ThreeType<Point, String, Point> a;
    }
}
