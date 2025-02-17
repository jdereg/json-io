package com.cedarsoftware.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
public class OverlappingMemberVariableNamesTest
{
    @Test
    public void testNestedWithSameMemberName()
    {
        Outer outer = new Outer();
        outer.setName("Joe Outer");

        Outer.Inner inner = outer.new Inner();   // Java-style instantiation for non-static inner class
        outer.setFoo(inner);
        outer.getFoo().setName("Jane Inner");

        String json = TestUtil.toJson(outer);
        assert json.contains("this$");

        Outer x = TestUtil.toObjects(json, null);

        assertEquals(x.getName(), "Joe Outer");
        assertEquals(x.getFoo().getName(), "Jane Inner");
    }

    @Test
    public void testSameMemberName()
    {
        Child child = new Child();
        child.setChildName("child");
        child.setParentName("parent");

        String json = TestUtil.toJson(child);
        Child roundTrip = TestUtil.toObjects(json, null);
        assert roundTrip.name.equals("child");
        assert roundTrip.getParentName().equals("parent");

        // Bottom class has "name" as the field name
        assert json.contains("\"name\"");
        // parent class has "Parent.name" as the field name
        assert json.contains("\"Parent.name\"");
        assertEquals(child.getParentName(), roundTrip.getParentName());
        assertEquals(child.getChildName(), roundTrip.getChildName());

        JsonObject jObj = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        String json1 = TestUtil.toJson(jObj);
        assertEquals(json, json1);
    }

    public static class Outer
    {
        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public Inner getFoo()
        {
            return foo;
        }

        public void setFoo(Inner foo)
        {
            this.foo = foo;
        }

        private String name;
        private Inner foo;

        public class Inner
        {
            public String getName()
            {
                return name;
            }

            public void setName(String name)
            {
                this.name = name;
            }

            private String name;
        }
    }

    public static class Parent
    {
        public String getParentName()
        {
            return name;
        }

        public void setParentName(String name)
        {
            this.name = name;
        }

        private String name;
    }

    public static class Child extends Parent
    {
        public String getChildName()
        {
            return name;
        }

        public void setChildName(String name)
        {
            this.name = name;
        }

        private String name;
    }
}
