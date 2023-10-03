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
class TestBoolean
{
    static class MyBooleanTesting
    {
        private boolean myBoolean = false;
    }

    static class MyBoolean2Testing
    {
        private Boolean myBoolean = false;
    }

    private static class ManyBooleans implements Serializable
    {
        private final Boolean _arrayElement
        private final Boolean[] _typeArray
        private final Object[] _objArray
        private final Object _polyRefTarget
        private final Object _polyRef
        private final Object _polyNotRef
        private final Boolean _null

        private ManyBooleans()
        {
            _arrayElement = new Boolean(true)
            _polyRefTarget = new Boolean(true)
            _polyRef = _polyRefTarget
            _polyNotRef = new Boolean(true)
            Boolean local = new Boolean(true)
            _null = null
            _typeArray = [_arrayElement, true, local, _null, null, Boolean.FALSE, new Boolean(false)] as Boolean[]
            _objArray = [_arrayElement, true, local, _null, null, Boolean.FALSE, new Boolean(false)] as Object[]
        }
    }

    @Test
    void testBoolean()
    {
        ManyBooleans test = new ManyBooleans()
        String json = TestUtil.getJsonString(test)
        TestUtil.printLine("json = " + json)
        ManyBooleans that = (ManyBooleans) TestUtil.readJsonObject(json)

        assertTrue(that._arrayElement.equals(true))
        assertTrue(that._polyRefTarget.equals(true))
        assertTrue(that._polyRef.equals(true))
        assertTrue(that._polyNotRef.equals(true))
        assertSame(that._polyRef, that._polyRefTarget)
        assertSame(that._polyNotRef, that._polyRef) // because only Boolean.TRUE or Boolean.FALSE are used

        assertTrue(that._typeArray.length == 7)
        assertTrue(that._typeArray[0] == that._arrayElement)
        assertTrue(that._typeArray[1] instanceof Boolean)
        assertTrue(that._typeArray[1].equals(true))
        assertTrue(that._objArray.length == 7)
        assertTrue(that._objArray[0] == that._arrayElement)
        assertTrue(that._objArray[1] instanceof Boolean)
        assertTrue(that._objArray[1].equals(true))
        assertTrue(that._polyRefTarget instanceof Boolean)
        assertTrue(that._polyNotRef instanceof Boolean)

        assertSame(that._objArray[2], that._typeArray[2])
        assertSame(that._typeArray[5], that._objArray[5])
        assertSame(that._typeArray[6], that._objArray[6])
        assertSame(that._typeArray[5], that._typeArray[6])
        assertSame(that._objArray[5], that._objArray[6])

        assertTrue(that._null == null)
        assertTrue(that._typeArray[3] == null)
        assertTrue(that._typeArray[4] == null)
        assertTrue(that._objArray[3] == null)
        assertTrue(that._objArray[4] == null)
    }

    @Test
    void testBooleanCompatibility()
    {
        MyBooleanTesting testObject = new MyBooleanTesting()
        MyBoolean2Testing testObject2 = new MyBoolean2Testing()
        String json0 = TestUtil.getJsonString(testObject)
        String json1 = TestUtil.getJsonString(testObject2)

        TestUtil.printLine("json0=" + json0)
        TestUtil.printLine("json1=" + json1)

        assertTrue(json0.contains('"myBoolean":false'))
        assertTrue(json1.contains('"myBoolean":false'))
    }
}
