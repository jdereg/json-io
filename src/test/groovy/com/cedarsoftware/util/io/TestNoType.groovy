package com.cedarsoftware.util.io

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
class TestNoType
{
    static class Junk
    {
        Object name
        List things = []
        Map namesToAge = [:]
        Object[] stuff
    }

    static class CollectionTest
    {
        Collection foos
        Object[] bars
    }

    @Test
    void testNoType()
    {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.clear()
        cal.set(-700, 5, 10)

        Junk j = new Junk()
        j.stuff = [(int)1, 2L, BigInteger.valueOf(3), new BigDecimal(4.0), cal.getTime(), 'Hello', Junk.class] as Object[]
        j.name = 'Zeus'
        j.things = [(int)1, 2L, BigInteger.valueOf(3), new BigDecimal(4.0), cal.getTime(), 'Hello', Junk.class] as Object[]
        j.namesToAge.Appollo = 2500L
        j.namesToAge.Hercules = 2489 as int
        j.namesToAge.Poseidon = 2502 as BigInteger
        j.namesToAge.Aphrodite = 2499.0
        j.namesToAge.Zeus = cal.getTime()

        String json = TestUtil.getJsonString(j)
        String json2 = TestUtil.getJsonString(j, [(JsonWriter.TYPE):false])
        assert json != json2
        assert json2 == '{"name":"Zeus","things":[1,2,"3","4",-84243801600000,"Hello","com.cedarsoftware.util.io.TestNoType$Junk"],"namesToAge":{"Appollo":2500,"Hercules":2489,"Poseidon":"2502","Aphrodite":"2499.0","Zeus":-84243801600000},"stuff":[1,2,"3","4",-84243801600000,"Hello","com.cedarsoftware.util.io.TestNoType$Junk"]}'
    }

    @Test
    public void testItems() 
    {
        String json = '{"groups":["one","two","three"],"search":{"datalist":[]}}'

        Map map = JsonReader.jsonToJava(json)
        assert !(map.groups instanceof Map)
        assert map.groups[0] == "one"
        assert map.groups[1] == "two"
        assert map.groups[2] == "three"
        assert map.search.datalist.length == 0

        map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        assert !(map.groups instanceof Map)
        assert map.groups[0] == "one"
        assert map.groups[1] == "two"
        assert map.groups[2] == "three"
        assert map.search.datalist.length == 0
    }

    @Test
    public void testCollections()
    {
        CollectionTest cols = new CollectionTest()
        cols.foos = new ArrayList()
        cols.foos.addAll([1,2,"4",8])
        cols.bars = [1,3,"5",7] as Object[]

        String json = JsonWriter.objectToJson(cols, [(JsonWriter.TYPE): false])
        Map map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assert map.foos[0] == 1
        assert map.foos[1] == 2
        assert map.foos[2] == "4"
        assert map.foos[3] == 8

        assert map.bars[0] == 1
        assert map.bars[1] == 3
        assert map.bars[2] == "5"
        assert map.bars[3] == 7

        json = JsonWriter.objectToJson([1, 2, 3, 4], [(JsonWriter.TYPE): false])
        assert '[1,2,3,4]' == json

        json = JsonWriter.objectToJson([1, 2, 3, 4] as Object[], [(JsonWriter.TYPE): false])
        assert '[1,2,3,4]' == json
    }

    @Test
    public void testObjectArray()
    {
        def array = [[1L,2L,3L] as Object[], ['a', 'b', 'c'] as Object[]] as Object[]
        String json = JsonWriter.objectToJson(array)
        Object[] list = (Object[]) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)

        assert list[0] instanceof Object[]
        assert list[0][0] == 1L
        assert list[0][1] == 2L
        assert list[0][2] == 3L
        assert list[1] instanceof Object[]
        assert list[1][0] == 'a'
        assert list[1][1] == 'b'
        assert list[1][2] == 'c'
    }
}

