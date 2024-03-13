package com.cedarsoftware.io;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         <a href="http://www.apache.org/licenses/LICENSE-2.0">License</a>
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
class BooleanTest
{
    private static class MyBooleanTesting
    {
        private boolean myBoolean = false;
    }

    private static class MyBoolean2Testing
    {
        private Boolean myBoolean = false;
    }

    private static class ManyBooleans implements Serializable
    {
        private final Boolean _arrayElement;
        private final Boolean[] _typeArray;
        private final Object[] _objArray;
        private final Object _polyRefTarget;
        private final Object _polyRef;
        private final Object _polyNotRef;
        private final Boolean _null;

        private ManyBooleans()
        {
            _arrayElement = Boolean.TRUE;
            _polyRefTarget = Boolean.TRUE;
            _polyRef = _polyRefTarget;
            _polyNotRef = Boolean.TRUE;
            Boolean local = Boolean.TRUE;
            _null = null;
            _typeArray = new Boolean[] {_arrayElement, true, local, _null, null, Boolean.FALSE, Boolean.FALSE};
            _objArray = new Object[] {_arrayElement, true, local, _null, null, Boolean.FALSE, Boolean.FALSE};
        }
    }

    @Test
    void testBoolean()
    {
        ManyBooleans test = new ManyBooleans();
        String json = TestUtil.toJson(test);
        TestUtil.printLine("json = " + json);
        ManyBooleans that = TestUtil.toObjects(json, null);

        assertTrue(that._arrayElement);
        assertEquals(true, that._polyRefTarget);
        assertEquals(true, that._polyRef);
        assertEquals(true, that._polyNotRef);
        assertSame(that._polyRef, that._polyRefTarget);
        assertSame(that._polyNotRef, that._polyRef); // because only Boolean.TRUE or Boolean.FALSE are used

        assertEquals(7, that._typeArray.length);
        assertSame(that._typeArray[0], that._arrayElement);
        assertTrue(that._typeArray[1] instanceof Boolean);
        assertTrue(that._typeArray[1]);
        assertEquals(7, that._objArray.length);
        assertSame(that._objArray[0], that._arrayElement);
        assertTrue(that._objArray[1] instanceof Boolean);
        assertEquals(true, that._objArray[1]);
        assertTrue(that._polyRefTarget instanceof Boolean);
        assertTrue(that._polyNotRef instanceof Boolean);

        assertSame(that._objArray[2], that._typeArray[2]);
        assertSame(that._typeArray[5], that._objArray[5]);
        assertSame(that._typeArray[6], that._objArray[6]);
        assertSame(that._typeArray[5], that._typeArray[6]);
        assertSame(that._objArray[5], that._objArray[6]);

        assertNull(that._null);
        assertNull(that._typeArray[3]);
        assertNull(that._typeArray[4]);
        assertNull(that._objArray[3]);
        assertNull(that._objArray[4]);
    }

    @Test
    void testBooleanCompatibility()
    {
        MyBooleanTesting testObject = new MyBooleanTesting();
        MyBoolean2Testing testObject2 = new MyBoolean2Testing();
        String json0 = TestUtil.toJson(testObject);
        String json1 = TestUtil.toJson(testObject2);

        TestUtil.printLine("json0=" + json0);
        TestUtil.printLine("json1=" + json1);

        assertTrue(json0.contains("\"myBoolean\":false"));
        assertTrue(json1.contains("\"myBoolean\":false"));
    }
}
