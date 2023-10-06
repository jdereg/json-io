package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotEquals
import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertThrows
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
@CompileStatic
class TestFloat
{
    private static class Simple
    {
        float x
    }

    private static class ManyFloats implements Serializable
    {
        private final Float _arrayElement
        private final Float[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Float _min
        private final Float _max
        private final Float _null

        private ManyFloats()
        {
            _arrayElement = new Float(-1)
            _polyRefTarget = new Float(71)
            _polyRef = _polyRefTarget;
            _polyNotRef = new Float(71)
            Float local = new Float(75)
            _null = null
            _typeArray = [_arrayElement, 44f, local, _null, null, new Float(44f)] as Float[]
            _objArray = [_arrayElement, 69f, local, _null, null, new Float(69f)] as Object[]
            _min = Float.MIN_VALUE
            _max = Float.MAX_VALUE
        }
    }

    @Test
    void testFloat()
    {
        ManyFloats test = new ManyFloats()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyFloats that = (ManyFloats) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(-1.0f))
        assertTrue(that._polyRefTarget.equals(71.0f))
        assertTrue(that._polyRef.equals(71.0f))
        assertTrue(that._polyNotRef.equals(71.0f))
        assertNotSame(that._polyRef, that._polyRefTarget)   // Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 6)
        assertTrue(that._typeArray[1] instanceof Float)
        assertTrue(that._objArray[1] instanceof Float)
        assertTrue(that._typeArray[1].equals(44.0f))
        assertTrue(that._objArray.length == 6)
        assertTrue(that._objArray[1].equals(69.0f))
        assertTrue(that._polyRefTarget instanceof Float)
        assertTrue(that._polyNotRef instanceof Float)

        assertTrue(that._objArray[2].equals(that._typeArray[2]))
        assertNotSame(that._typeArray[1], that._typeArray[5])
        assertEquals(that._typeArray[1], that._typeArray[5], 0.00001f)

        assertNotSame(that._objArray[1], that._objArray[5])
        assertEquals((float)that._objArray[1], (float)that._objArray[5], 0.00001f)

        assertNotSame(that._typeArray[1], that._objArray[1])
        assertNotEquals((float)that._typeArray[1], (float)that._objArray[1], 0.00001f)

        assertNotSame(that._typeArray[5], that._objArray[5])
        assertNotEquals((float)that._typeArray[5], (float)that._objArray[5], 0.00001f)

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Float.MIN_VALUE))
        assertTrue(that._max.equals(Float.MAX_VALUE))
    }

    @Test
    void parseBadFloat()
    {
        String json = '[123.45.67]'
        assertThrows(JsonIoException.class, { JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map) })
    }

    @Test
    void testNanAsRoot()
    {
        Object json = JsonWriter.objectToJson(Float.NaN, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson(Float.NEGATIVE_INFINITY, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson(Float.POSITIVE_INFINITY, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')
    }

    @Test
    void testNanMapKey()
    {
        Object json = JsonWriter.objectToJson([field:Float.NaN], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.NEGATIVE_INFINITY], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.POSITIVE_INFINITY], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')
    }

    @Test
    void testNanObjectField()
    {
        Simple holder = new Simple()
        holder.x = Float.NaN
        Object json = JsonWriter.objectToJson(holder, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        holder.x = Float.NEGATIVE_INFINITY
        json = JsonWriter.objectToJson(holder, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        holder.x = Float.POSITIVE_INFINITY
        json = JsonWriter.objectToJson(holder, [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')
    }

    @Test
    void testNanArrayElement()
    {
        Object json = JsonWriter.objectToJson([Float.NaN], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson([Float.NEGATIVE_INFINITY], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.POSITIVE_INFINITY], [(JsonWriter.TYPE):false] as Map)
        assert json.contains('null')
    }

    @Test
    void testNanAsRoot2()
    {
        Object json = JsonWriter.objectToJson(Float.NaN)
        assert json.contains('null')

        json = JsonWriter.objectToJson(Float.NEGATIVE_INFINITY)
        assert json.contains('null')

        json = JsonWriter.objectToJson(Float.POSITIVE_INFINITY)
        assert json.contains('null')
    }

    @Test
    void testNanMapKey2()
    {
        Object json = JsonWriter.objectToJson([field:Float.NaN])
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.NEGATIVE_INFINITY])
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.POSITIVE_INFINITY])
        assert json.contains('null')
    }

    @Test
    void testNanObjectField2()
    {
        Simple holder = new Simple()
        holder.x = Float.NaN
        Object json = JsonWriter.objectToJson(holder)
        assert json.contains('null')

        holder.x = Float.NEGATIVE_INFINITY
        json = JsonWriter.objectToJson(holder)
        assert json.contains('null')

        holder.x = Float.POSITIVE_INFINITY
        json = JsonWriter.objectToJson(holder)
        assert json.contains('null')
    }

    @Test
    void testNanArrayElement2()
    {
        Object json = JsonWriter.objectToJson([Float.NaN])
        assert json.contains('null')

        json = JsonWriter.objectToJson([Float.NEGATIVE_INFINITY])
        assert json.contains('null')

        json = JsonWriter.objectToJson([field:Float.POSITIVE_INFINITY])
        assert json.contains('null')
    }
}
