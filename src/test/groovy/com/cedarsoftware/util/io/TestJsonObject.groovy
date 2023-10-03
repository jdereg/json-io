package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

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
class TestJsonObject
{
    @Test
    void testNewPrimitiveWrapper()
    {
        assertTrue MetaUtils.isLogicalPrimitive(Byte.class)
        assertTrue MetaUtils.isLogicalPrimitive(Byte.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Short.class)
        assertTrue MetaUtils.isLogicalPrimitive(Short.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Integer.class)
        assertTrue MetaUtils.isLogicalPrimitive(Integer.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Long.class)
        assertTrue MetaUtils.isLogicalPrimitive(Long.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Float.class)
        assertTrue MetaUtils.isLogicalPrimitive(Float.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Double.class)
        assertTrue MetaUtils.isLogicalPrimitive(Double.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Boolean.class)
        assertTrue MetaUtils.isLogicalPrimitive(Boolean.TYPE)
        assertTrue MetaUtils.isLogicalPrimitive(Character.class)
        assertTrue MetaUtils.isLogicalPrimitive(Character.TYPE)
        // quasi-primitives (Date, String, BigInteger, BigDecimal) as defined by json-io (not true primitive wrappers)
        assertTrue MetaUtils.isLogicalPrimitive(Date.class)
        assertTrue MetaUtils.isLogicalPrimitive(String.class)
        assertTrue MetaUtils.isLogicalPrimitive(BigInteger.class)
        assertTrue MetaUtils.isLogicalPrimitive(BigDecimal.class)
        assertTrue MetaUtils.isLogicalPrimitive(Number.class)

        assertThrows(NullPointerException.class, { MetaUtils.isLogicalPrimitive(null) })
    }

    @Test
    void testGetId()
    {
        JsonObject jObj = new JsonObject()
        assert -1L == jObj.getId()
    }

    @Test
    void testGetPrimitiveValue()
    {
        JsonObject jObj = new JsonObject()
        jObj.setType('long')
        jObj.value = 10L
        assert 10L == jObj.getPrimitiveValue()

        jObj.setType('phoney')
        try
        {
            jObj.getPrimitiveValue()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('invalid primitive type')
        }

        try
        {
            jObj.getLength()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('called')
            assert e.message.toLowerCase().contains('non-collection')
        }

        jObj.moveCharsToMate()
    }
}
