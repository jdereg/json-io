package com.cedarsoftware.util.io

import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Test
import org.junit.runners.MethodSorters

import static org.junit.Assert.assertTrue

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
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TestInnerClass
{
    static public class A
    {
        public String a;

        class B
        {

            public String b;

            public B()
            {
                // No args constructor for B
            }
        }

        static B createB(A aa)
        {
            new B(aa)
        }
    }

    @Ignore
    void testChangedClass() throws Exception
    {
        Dog dog = new Dog()
        dog.x = 10
//        Dog.Leg leg = dog.new Dog.Leg()       // original Java
        Dog.Leg leg = Dog.createLeg(dog)        // Groovy requires static method on outer class to create non-static inner instance
        leg.y = 20
        String json0 = TestUtil.getJsonString(dog)
        TestUtil.printLine("json0=" + json0)
        JsonObject job = (JsonObject) JsonReader.jsonToMaps(json0)
        job.put("phantom", new TestObject("Eddie"))
        String json1 = TestUtil.getJsonString(job)
        TestUtil.printLine("json1=" + json1)
        assertTrue(json1.contains("phantom"))
        assertTrue(json1.contains("TestObject"))
        assertTrue(json1.contains("_other"))
    }

    @Ignore
    void testInner() throws Exception
    {
        A a = new A()
        a.a = "aaa"

        String json = TestUtil.getJsonString(a)
        TestUtil.printLine("json = " + json)
        A o1 = (A) TestUtil.readJsonObject(json)
        assertTrue(o1.a.equals("aaa"))

//        TestJsonReaderWriter.A.B b = a.new B()        // Original Java
        TestInnerClass.A.B b = A.createB(a)         // Groovy requires static method on outer class to create non-static inner instance
        b.b = "bbb"
        json = TestUtil.getJsonString(b)
        TestUtil.printLine("json = " + json)
        TestInnerClass.A.B o2 = (TestInnerClass.A.B) TestUtil.readJsonObject(json)
        assertTrue(o2.b.equals("bbb"))
    }

    @Test
    void testInnerInstance() throws Exception
    {
        Dog dog = new Dog()
        dog.x = 10;
//        Dog.Leg leg = dog.new Dog.Leg()
        Dog.Leg leg = Dog.createLeg(dog)
        leg.y = 20;
        String json0 = TestUtil.getJsonString(dog)
        TestUtil.printLine("json0=" + json0)

        String json1 = TestUtil.getJsonString(leg)
        TestUtil.printLine("json1=" + json1)
        Dog.Leg go = (Dog.Leg) TestUtil.readJsonObject(json1)
        assertTrue(go.y == 20)
        assertTrue(go.getParentX() == 10)
    }
}
