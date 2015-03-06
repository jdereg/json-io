package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

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
        assertTrue JsonObject.isPrimitiveWrapper(Byte.class)
        assertFalse JsonObject.isPrimitiveWrapper(Byte.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Short.class)
        assertFalse JsonObject.isPrimitiveWrapper(Short.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Integer.class)
        assertFalse JsonObject.isPrimitiveWrapper(Integer.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Long.class)
        assertFalse JsonObject.isPrimitiveWrapper(Long.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Float.class)
        assertFalse JsonObject.isPrimitiveWrapper(Float.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Double.class)
        assertFalse JsonObject.isPrimitiveWrapper(Double.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Boolean.class)
        assertFalse JsonObject.isPrimitiveWrapper(Boolean.TYPE)
        assertTrue JsonObject.isPrimitiveWrapper(Character.class)
        assertFalse JsonObject.isPrimitiveWrapper(Character.TYPE)
        // quasi-primitives (Date, String, BigInteger, BigDecimal) as defined by json-io (not true primitive wrappers)
        assertFalse JsonObject.isPrimitiveWrapper(Date.class)
        assertFalse JsonObject.isPrimitiveWrapper(String.class)
        assertFalse JsonObject.isPrimitiveWrapper(BigInteger.class)
        assertFalse JsonObject.isPrimitiveWrapper(BigDecimal.class)
        assertFalse JsonObject.isPrimitiveWrapper(Number.class)
        try
        {
            JsonObject.isPrimitiveWrapper(null)
            fail()
        }
        catch (NullPointerException ignored)
        { }
    }
}
