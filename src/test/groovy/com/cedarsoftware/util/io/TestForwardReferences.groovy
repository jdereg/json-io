package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
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
class TestForwardReferences
{
    @Test
    void testForwardRefs()
    {
        TestObject one = new TestObject("One")
        TestObject two = new TestObject("Two")
        one._other = two
        two._other = one
        String pkg = TestObject.class.getName()
        String fwdRef = '[[{"@id":2,"@type":"' + pkg + '","_name":"Two","_other":{"@ref":1}}],[{"@id":1,"@type":"' + pkg + '","_name":"One","_other":{"@ref":2}}]]'
        Object[] foo = (Object[]) TestUtil.readJsonObject(fwdRef)
        Object[] first = (Object[]) foo[0]
        Object[] second = (Object[]) foo[1]
        assertTrue(one.equals(second[0]))
        assertTrue(two.equals(first[0]))

        String json = '[{"@ref":2},{"@id":2,"@type":"int","value":5}]'
        Object[] ints = (Object[]) TestUtil.readJsonObject(json)
        assertEquals((Integer)ints[0], 5)
        assertEquals((Integer) ints[1], 5)

        json = '{"@type":"java.util.ArrayList","@items":[{"@ref":2},{"@id":2,"@type":"int","value":5}]}'
        List list = (List) JsonReader.jsonToJava(json)
        assertEquals((Integer)list.get(0), 5)
        assertEquals((Integer)list.get(1), 5)

        json = '{"@type":"java.util.TreeSet","@items":[{"@type":"int","value":9},{"@ref":16},{"@type":"int","value":4},{"@id":16,"@type":"int","value":5}]}'
        Set set = (Set) TestUtil.readJsonObject(json)
        assertEquals(set.size(), 3)
        Iterator i = set.iterator()
        assertEquals((Integer)i.next(), 4)
        assertEquals((Integer)i.next(), 5)
        assertEquals((Integer)i.next(), 9)

        json = '{"@type":"java.util.HashMap","@keys":[1,2,3,4],"@items":[{"@type":"int","value":9},{"@ref":16},{"@type":"int","value":4},{"@id":16,"@type":"int","value":5}]}'
        Map map = (Map) TestUtil.readJsonObject(json)
        assertEquals(map.size(), 4)
        assertEquals((Integer)map.get(1L), 9)
        assertEquals((Integer)map.get(2L), 5)
        assertEquals((Integer)map.get(3L), 4)
        assertEquals((Integer)map.get(4L), 5)
    }

}
