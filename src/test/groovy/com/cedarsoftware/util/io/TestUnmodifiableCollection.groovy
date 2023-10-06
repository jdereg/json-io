package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

/**
 * Test cases for JsonReader / JsonWriter
 *
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
class TestUnmodifiableCollection
{
    static class UnmodifiableMapHolder
    {
        Map map;

        UnmodifiableMapHolder()
        {
            Map directions = new LinkedHashMap()
            directions.put("North", 0)
            directions.put("South", 1)
            directions.put("East", 2)
            directions.put("West", 3)
            map = Collections.unmodifiableMap(directions)
        }
    }

    @Test
    void testUnmodifiableCollection()
    {
        Collection col = new ArrayList()
        col.add('foo')
        col.add('bar')
        col.add('baz')
        col.add('qux')
        col = Collections.unmodifiableCollection(col)
        String json = TestUtil.getJsonString(col)
        def root = JsonReader.jsonToJava(json)
        assert root instanceof List
        assert root.size() == 4
        assert root[0] == 'foo'
        assert root[1] == 'bar'
        assert root[2] == 'baz'
        assert root[3] == 'qux'

        col = new ArrayList()
        col.add('foo')
        col.add('bar')
        col.add('baz')
        col.add('qux')
        col = Collections.unmodifiableList(col)
        json = TestUtil.getJsonString(col)
        root = JsonReader.jsonToJava(json)
        assert root instanceof List
        assert root.size() == 4
        assert root[0] == 'foo'
        assert root[1] == 'bar'
        assert root[2] == 'baz'
        assert root[3] == 'qux'
    }

    @Test
    void testUnmodifiableSet()
    {
        Collection col = new LinkedHashSet()
        col.add('foo')
        col.add('bar')
        col.add('baz')
        col.add('qux')
        col = Collections.unmodifiableSet(col)
        String json = TestUtil.getJsonString(col)
        def root = JsonReader.jsonToJava(json)
        assert root instanceof Set
        assert root.size() == 4
        assert root[0] == 'foo'
        assert root[1] == 'bar'
        assert root[2] == 'baz'
        assert root[3] == 'qux'

        col = new HashSet()
        col.add('foo')
        col.add('bar')
        col.add('baz')
        col.add('qux')
        col = Collections.unmodifiableSet(col)
        json = TestUtil.getJsonString(col)
        root = JsonReader.jsonToJava(json)
        assert root instanceof Set
        assert root.size() == 4
        assert root.contains('foo')
        assert root.contains('bar')
        assert root.contains('baz')
        assert root.contains('qux')

        col = new TreeSet()
        col.add('foo')
        col.add('bar')
        col.add('baz')
        col.add('qux')
        col = Collections.unmodifiableSortedSet(col)
        json = TestUtil.getJsonString(col)
        root = JsonReader.jsonToJava(json)
        assert root instanceof SortedSet
        assert root.size() == 4
        assert root[0] == 'bar'
        assert root[1] == 'baz'
        assert root[2] == 'foo'
        assert root[3] == 'qux'
    }

    @Test
    void testUnmodifiableMap()
    {
        Map map = new LinkedHashMap()
        map.foo = 'foot'
        map.bar = 'bart'
        map.baz = 'bastard'
        map.qux = 'quixotic'
        map = Collections.unmodifiableMap(map)
        String json = TestUtil.getJsonString(map)
        def root = JsonReader.jsonToJava(json)
        assert root instanceof Map
        assert root.size() == 4
        assert root.foo == 'foot'
        assert root.bar == 'bart'
        assert root.baz == 'bastard'
        assert root.qux == 'quixotic'

        map = new TreeMap()
        map.foo = 'foot'
        map.bar = 'bart'
        map.baz = 'bastard'
        map.qux = 'quixotic'
        map = Collections.unmodifiableSortedMap(map)
        json = TestUtil.getJsonString(map)
        root = JsonReader.jsonToJava(json)
        assert root instanceof SortedMap
        assert root.size() == 4
        assert root.foo == 'foot'
        assert root.bar == 'bart'
        assert root.baz == 'bastard'
        assert root.qux == 'quixotic'
    }

    @Test
    void testUnmodifiableMapHolder()
    {
        UnmodifiableMapHolder holder = new UnmodifiableMapHolder()
        String json = JsonWriter.objectToJson(holder)
        UnmodifiableMapHolder holder1 = (UnmodifiableMapHolder) TestUtil.readJsonObject(json)
        assert holder1.map.get("North") == 0
        assert holder1.map.get("South") == 1
        assert holder1.map.get("East") == 2
        assert holder1.map.get("West") == 3
    }

}
