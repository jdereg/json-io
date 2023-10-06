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
class TestInteger
{
    private static class ManyIntegers implements Serializable
    {
        private final Integer _arrayElement
        private final Integer[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Integer _min
        private final Integer _max
        private final Integer _null

        private ManyIntegers()
        {
            _arrayElement = -1
            _polyRefTarget = new Integer(710)
            _polyRef = _polyRefTarget;
            _polyNotRef = new Integer(710)
            Integer local = new Integer(75)
            _null  = null
            _typeArray = [_arrayElement, 44, local, _null, null, new Integer(44), 0, new Integer(0)] as Integer[]
            _objArray = [_arrayElement, 69, local, _null, null, new Integer(69), 0, new Integer(0)] as Object[]
            _min = Integer.MIN_VALUE;
            _max = Integer.MAX_VALUE;
        }
    }

    @Test
    void testInteger()
    {
        ManyIntegers test = new ManyIntegers()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyIntegers that = (ManyIntegers) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(-1))
        assertTrue(that._polyRefTarget.equals(710))
        assertTrue(that._polyRef.equals(710))
        assertTrue(that._polyNotRef.equals(710))
        assertNotSame(that._polyRef, that._polyRefTarget) // Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 8)
        assertSame(that._typeArray[0], that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Integer)
        assertTrue(that._typeArray[1] instanceof Integer)
        assertTrue(that._typeArray[1].equals(44))
        assertTrue(that._objArray.length == 8)
        assertSame(that._objArray[0], that._arrayElement)
        assertTrue(that._objArray[1] instanceof Integer)
        assertTrue(that._objArray[1].equals(69))
        assertTrue(that._polyRefTarget instanceof Integer)
        assertTrue(that._polyNotRef instanceof Integer)
        assertTrue(that._objArray[2].equals(75))

        assertSame(that._objArray[2], that._typeArray[2])
        assertSame(that._typeArray[1], that._typeArray[5])
        assertSame(that._objArray[1], that._objArray[5])

        // an unreferenced 0 is cached
        assertSame(that._typeArray[6], that._typeArray[7])
        assertSame(that._objArray[6], that._objArray[7])

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Integer.MIN_VALUE))
        assertTrue(that._max.equals(Integer.MAX_VALUE))
    }

}
