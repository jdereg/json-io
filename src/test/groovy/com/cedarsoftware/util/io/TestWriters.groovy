package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
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
class TestWriters
{
    @Test
    void testUnusedAPIs()
    {
        Writers.CalendarWriter cw = new Writers.CalendarWriter()
        cw.writePrimitiveForm("", new StringWriter())

        Writers.TimestampWriter tsw = new Writers.TimestampWriter()
        tsw.writePrimitiveForm("", new StringWriter())

        Writers.LocaleWriter lw = new Writers.LocaleWriter()
        lw.writePrimitiveForm("", new StringWriter())

        Writers.JsonStringWriter jsw = new Writers.JsonStringWriter()
        jsw.write("", false, new StringWriter())
    }

    @Test
    void testNumericTruth()
    {
        assertFalse JsonWriter.isTrue(BigInteger.valueOf(0))
        assertTrue JsonWriter.isTrue(BigInteger.valueOf(1))
        assertFalse JsonWriter.isTrue(new BigDecimal(0.0))
        assertTrue JsonWriter.isTrue(new BigDecimal(1.1))
        assertFalse JsonWriter.isTrue(0.0d)
        assertTrue JsonWriter.isTrue(1.1d)
        assertFalse JsonWriter.isTrue(0.0f)
        assertTrue JsonWriter.isTrue(1.1f)
        assertFalse JsonWriter.isTrue(0 as byte)
        assertTrue JsonWriter.isTrue(1 as byte)
        assertFalse JsonWriter.isTrue(0 as short)
        assertTrue JsonWriter.isTrue(1 as short)
        assertFalse JsonWriter.isTrue(0 as int)
        assertTrue JsonWriter.isTrue(1 as int)
        assertFalse JsonWriter.isTrue(0L)
        assertTrue JsonWriter.isTrue(1L)
    }
}
