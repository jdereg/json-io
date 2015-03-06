package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertNotSame
import static org.junit.Assert.assertTrue

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
class TestDouble
{
    private static class ManyDoubles implements Serializable
    {
        private final Double _arrayElement
        private final Double[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Double _min
        private final Double _max
        private final Double _null

        private ManyDoubles()
        {
            _arrayElement = new Double(-1)
            _polyRefTarget = new Double(71)
            _polyRef = _polyRefTarget
            _polyNotRef = new Double(71)
            Double local = new Double(75)
            _null = null
            _typeArray = [_arrayElement, 44.0d, local, _null, null, new Double(44)] as Double[]
            _objArray = [_arrayElement, 69.0d, local, _null, null, new Double(69)] as Object[]
            _min = Double.MIN_VALUE
            _max = Double.MAX_VALUE
        }
    }

    @Test
    void testDouble() throws Exception
    {
        ManyDoubles test = new ManyDoubles()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyDoubles that = (ManyDoubles) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(-1.0d))
        assertTrue(that._polyRefTarget.equals(71.0d))
        assertTrue(that._polyRef.equals(71.0d))
        assertTrue(that._polyNotRef.equals(71.0d))
        assertNotSame(that._polyRef, that._polyRefTarget)   // Primitive wrappers are treated like primitives (no ref)
        assertNotSame(that._polyNotRef, that._polyRef)

        assertTrue(that._typeArray.length == 6)
        assertTrue(that._typeArray[1] instanceof Double)
        assertTrue(that._objArray[1] instanceof Double)
        assertTrue(that._typeArray[1].equals(44.0d))
        assertTrue(that._objArray.length == 6)
        assertTrue(that._objArray[1].equals(69.0d))
        assertTrue(that._polyRefTarget instanceof Double)
        assertTrue(that._polyNotRef instanceof Double)

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)

        assertTrue(that._min.equals(Double.MIN_VALUE))
        assertTrue(that._max.equals(Double.MAX_VALUE))
    }

}
