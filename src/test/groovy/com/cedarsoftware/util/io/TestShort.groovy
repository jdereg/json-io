package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestShort
{
    private static class ManyShorts implements Serializable
    {
        private final Short _arrayElement
        private final Short[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Short _min
        private final Short _max
        private final Short _null

        private ManyShorts()
        {
            _arrayElement = new Short((short) -1)
            _polyRefTarget = new Short((short)710)
            _polyRef = _polyRefTarget
            _polyNotRef = new Short((short) 710)
            Short local = new Short((short)75)
            _null  = null
            _typeArray = [_arrayElement, (short) 44, local, _null, null, new Short((short)44)] as Short[]
            _objArray = [_arrayElement, (short) 69, local, _null, null, new Short((short)69)] as Object[]
            _min = Short.MIN_VALUE;
            _max = Short.MAX_VALUE;
        }
    }

    @Test
    void testShort()
    {
        ManyShorts test = new ManyShorts()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyShorts that = (ManyShorts) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals((short) -1))
        assertTrue(that._polyRefTarget.equals((short) 710))
        assertTrue(that._polyRef.equals((short) 710))
        assertTrue(that._polyNotRef.equals((short) 710))
        assertNotSame(that._polyRef, that._polyRefTarget)   // Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 6)
        assertSame(that._typeArray[0], that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Short)
        assertTrue(that._typeArray[1] instanceof Short)
        assertTrue(that._typeArray[1].equals((short) 44))
        assertTrue(that._objArray.length == 6)
        assertSame(that._objArray[0], that._arrayElement)
        assertTrue(that._objArray[1] instanceof Short)
        assertTrue(that._objArray[1].equals((short) 69))
        assertTrue(that._polyRefTarget instanceof Short)
        assertTrue(that._polyNotRef instanceof Short)

        assertSame(that._objArray[2], that._typeArray[2])
        assertTrue(that._objArray[2].equals((short) 75))

        // Because of cache in Short.valueOf(), these values between -128 and 127 will have same address.
        assertSame(that._typeArray[1], that._typeArray[5])
        assertSame(that._objArray[1], that._objArray[5])

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Short.MIN_VALUE))
        assertTrue(that._max.equals(Short.MAX_VALUE))
    }

}
