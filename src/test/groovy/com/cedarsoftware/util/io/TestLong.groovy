package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertSame
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
class TestLong
{
    private static class ManyLongs implements Serializable
    {
        private final Long _arrayElement
        private final Long[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Long _min
        private final Long _max
        private final Long _null

        private ManyLongs()
        {
            _arrayElement = new Long(-1)
            _polyRefTarget = new Long(710)
            _polyRef = _polyRefTarget;
            _polyNotRef = new Long(710)
            Long local = new Long(75)
            _null = null
            // 44 below is between -128 and 127, values cached by     Long Long.valueOf(long l)
            _typeArray = [_arrayElement, 44L, local, _null, null, new Long(44)] as Long[]
            _objArray = [_arrayElement, 69L, local, _null, null, new Long(69)] as Object[]
            _min = Long.MIN_VALUE
            _max = Long.MAX_VALUE
        }
    }

    private static class PhysicalAttributes
    {
        Long age
        Object weight
    }

    @Test
    void testLong()
    {
        ManyLongs test = new ManyLongs()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyLongs that = (ManyLongs) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(-1L))
        assertTrue(that._polyRefTarget.equals(710L))
        assertTrue(that._polyRef.equals(710L))
        assertTrue(that._polyNotRef.equals(710L))
        assertNotSame(that._polyRef, that._polyRefTarget)   // Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 6)
        assertSame(that._typeArray[0], that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Long)
        assertTrue(that._typeArray[1] instanceof Long)
        assertTrue(that._typeArray[1].equals(44L))
        assertTrue(that._objArray.length == 6)
        assertSame(that._objArray[0], that._arrayElement)
        assertTrue(that._objArray[1] instanceof Long)
        assertTrue(that._objArray[1].equals(69L))
        assertTrue(that._polyRefTarget instanceof Long)
        assertTrue(that._polyNotRef instanceof Long)

        assertSame(that._objArray[2], that._typeArray[2])
        assertSame(that._typeArray[1], that._typeArray[5])
        assertSame(that._objArray[1], that._objArray[5])

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Long.MIN_VALUE))
        assertTrue(that._max.equals(Long.MAX_VALUE))
    }

    @Test
    void testLongAsString()
    {
        long x = 19
        String json = JsonWriter.objectToJson(x, [(JsonWriter.WRITE_LONGS_AS_STRINGS):true])
        assert json.contains('"19"')

        Object y = JsonReader.jsonToJava(json)
        assert y instanceof Long
        assertEquals(19L, y)
    }

    @Test
    void testLongArrayAsString()
    {
        long[] x = [1L, 2L, 3L]
        String json = JsonWriter.objectToJson(x, [(JsonWriter.WRITE_LONGS_AS_STRINGS):true])
        assert json.contains('"1"')
        assert json.contains('"2"')
        assert json.contains('"3"')

        Object y = JsonReader.jsonToJava(json)
        assert y instanceof long[]
        assertEquals(1L, y[0])
        assertEquals(2L, y[1])
        assertEquals(3L, y[2])
    }

    @Test
    void testObjectArrayOfLongsAsString()
    {
        Object[] x = [1L, 2L, 3L]
        String json = JsonWriter.objectToJson(x, [(JsonWriter.WRITE_LONGS_AS_STRINGS):true])
        assert json.contains('"1"')
        assert json.contains('"2"')
        assert json.contains('"3"')

        Object y = JsonReader.jsonToJava(json)
        assert y instanceof Object[]
        assertEquals(1L, y[0])
        assertEquals(2L, y[1])
        assertEquals(3L, y[2])
    }

    @Test
    void testLongCollectionAsString()
    {
        Collection x = new ArrayList()
        x.add(1L)
        x.add(2L)
        x.add(3L)
        String json = JsonWriter.objectToJson(x, [(JsonWriter.WRITE_LONGS_AS_STRINGS):true])

        assert json.contains('"1"')
        assert json.contains('"2"')
        assert json.contains('"3"')

        Object y = JsonReader.jsonToJava(json)
        assert y instanceof Collection
        assertEquals(1L, y[0])
        assertEquals(2L, y[1])
        assertEquals(3L, y[2])
    }

    @Test
    void testLongObjectFieldAsString()
    {
        PhysicalAttributes x = new PhysicalAttributes()
        x.age = 49L
        x.weight = 205L

        String json = JsonWriter.objectToJson(x, [(JsonWriter.WRITE_LONGS_AS_STRINGS):true])

        assert json.contains('"49"')
        assert json.contains('"205"')

        Object y = JsonReader.jsonToJava(json)
        assert y instanceof PhysicalAttributes
        assertEquals(49L, y.age)
        assertEquals(205L, y.weight)
    }
}
