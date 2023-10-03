package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

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
class TestTypeSubstitution
{
    static class Person
    {
        String name
        Map pets = new HashMap();
    }

    @Test
    void testBasicTypeSubAtRoot()
    {
        Map<String, String> types = ['java.util.ArrayList':'al']
        Map args = [(JsonWriter.TYPE_NAME_MAP):types]
        List list = ['alpha', 'bravo', 'charlie']
        String json = JsonWriter.objectToJson(list, args)
        List test = (List) JsonReader.jsonToJava(json, args)
        assert list.equals(test)
    }

    @Test
    void testBasicTypeSubInFieldAndInnerClass()
    {
        Person p = new Person()
        p.name = 'John'
        p.pets = [Eddie:'Dog', Bella:'Dog']

        Map<String, String> types = [
                'java.util.LinkedHashMap':'lmap',
                'com.cedarsoftware.util.io.TestTypeSubstitution$Person':'person'
        ]
        Map args = [(JsonWriter.TYPE_NAME_MAP):types]
        String json = JsonWriter.objectToJson(p, args)
        args[(JsonReader.USE_MAPS)] = true
        Person clone = JsonReader.jsonToJava(json, args)
        assert clone.name == 'John'
        assert clone.pets.equals(p.pets)
    }
}
