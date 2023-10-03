package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

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
class TestOverlappingMemberVariableNames
{
    static class Outer
    {
        String name
        public class Inner
        {
            String name
        }
        Inner foo

        public static Inner createInner(Outer outer)
        {
            return new Inner(outer)
        }
    }

    public static class Parent
    {
        private String name

        public String getParentName()
        {
            return name
        }

        public void setParentName(String name)
        {
            this.name = name;
        }
    }

    public static class Child extends Parent
    {
        private String name

        public String getChildName()
        {
            return name
        }

        public void setChildName(String name)
        {
            this.name = name
        }
    }

    @Test
    void testNestedWithSameMemberName()
    {
        Outer outer = new Outer()
        outer.name = "Joe Outer"

//        Outer.Inner inner = outer.new Inner()   // Java-style instantiation
        outer.foo = outer.createInner(outer)           // Trickier with Groovy because of difficulty using nested inner class.
        outer.foo.name = "Jane Inner"

        String json = TestUtil.getJsonString(outer)
        TestUtil.printLine("json = " + json)

        Outer x = (Outer) TestUtil.readJsonObject(json)
        assertEquals(x.name, "Joe Outer")
        assertEquals(x.foo.name, "Jane Inner")
    }

    @Test
    void testSameMemberName()
    {
        Child child = new Child()
        child.childName = 'child'
        child.parentName = 'parent'

        String json = TestUtil.getJsonString(child)
        TestUtil.printLine(json)
        Child roundTrip = (Child) TestUtil.readJsonObject(json)

        assertEquals(child.parentName, roundTrip.parentName)
        assertEquals(child.childName, roundTrip.childName)

        JsonObject jObj = (JsonObject)JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        String json1 = TestUtil.getJsonString(jObj)
        TestUtil.printLine(json1)
        assertEquals(json, json1)
    }
}
