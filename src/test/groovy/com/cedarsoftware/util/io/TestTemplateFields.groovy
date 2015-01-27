package com.cedarsoftware.util.io

import org.junit.Test

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class TestTemplateFields
{
    static class Test1
    {
        protected Test2<String, Object> internalMember = new Test2<>()
    }

    static class Test2<K, V>
    {
        protected Map<K, Collection<? extends V>> hsOne = new HashMap<>()
        protected Class<?> two = LinkedList.class
    }

    static class Person
    {
        String name
        int age

        Person(String n, int a) { name = n; age = a}
    }

    static class Test3
    {
        protected Test4<String> internalMember = new Test4<>()
    }

    static class Test4<V>
    {
        protected Person two = new Person('Jane', 45)
    }

    // This test was provided by Github user: reuschling
    @Test
    void testTemplateClassField() throws Exception
    {
        Test1 container = new Test1()
        Test1 container2 = new Test1()
        LinkedList<Test1> llList = new LinkedList<>()
        llList.add(container2)
        container.internalMember.hsOne.put("key", llList)
        String json = JsonWriter.objectToJson(container)

        // This would throw exception in the past
        JsonReader.jsonToJava(json)
    }

    @Test
    void testTemplateNonClassFields() throws Exception
    {
        Test3 container = new Test3()
        String json = JsonWriter.objectToJson(container)
        // This would throw exception in the past
        Test3 reanimated = JsonReader.jsonToJava(json)
        assert reanimated.internalMember.two.name == 'Jane'
        assert reanimated.internalMember.two.age == 45
    }
}
