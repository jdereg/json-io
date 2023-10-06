package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestByte
{
    private static class ManyBytes implements Serializable
    {
        private final Byte _arrayElement
        private final Byte[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Byte _min
        private final Byte _max
        private final Byte _null

        private ManyBytes()
        {
            _arrayElement = new Byte((byte) -1)
            _polyRefTarget = new Byte((byte)71)
            _polyRef = _polyRefTarget;
            _polyNotRef = new Byte((byte) 71)
            Byte local = new Byte((byte)75)
            _null  = null
            _typeArray = [_arrayElement, (byte) 44, local, _null, null, new Byte((byte)44)] as Byte[]
            _objArray = [_arrayElement, (byte) 69, local, _null, null, new Byte((byte)69)] as Object[]
            _min = Byte.MIN_VALUE
            _max = Byte.MAX_VALUE
        }
    }

    @Test
    void testByte()
    {
        ManyBytes test = new ManyBytes()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)

        ManyBytes that = (ManyBytes) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals((byte) -1))
        assertTrue(that._polyRefTarget.equals((byte) 71))
        assertTrue(that._polyRef.equals((byte) 71))
        assertTrue(that._polyNotRef.equals((byte) 71))
        assertSame(that._polyRef, that._polyRefTarget)
        assertSame(that._polyNotRef, that._polyRef)             // byte cache is working

        assertTrue(that._typeArray.length == 6)
        assertSame(that._typeArray[0], that._arrayElement)  // byte cache is working
        assertTrue(that._typeArray[1] instanceof Byte)
        assertTrue(that._typeArray[1] instanceof Byte)
        assertTrue(that._typeArray[1].equals((byte) 44))
        assertTrue(that._objArray.length == 6)
        assertSame(that._objArray[0], that._arrayElement)   // byte cache is working
        assertTrue(that._objArray[1] instanceof Byte)
        assertTrue(that._objArray[1].equals((byte) 69))
        assertTrue(that._polyRefTarget instanceof Byte)
        assertTrue(that._polyNotRef instanceof Byte)
        assertTrue(that._objArray[2].equals((byte) 75))

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Byte.MIN_VALUE))
        assertTrue(that._max.equals(Byte.MAX_VALUE))
    }
}
