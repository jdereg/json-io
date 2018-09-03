package com.cedarsoftware.util.io

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

import org.junit.Test

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
        Writers.TimeZoneWriter tzw = new Writers.TimeZoneWriter()
        tzw.writePrimitiveForm("", new StringBuilder())

        Writers.CalendarWriter cw = new Writers.CalendarWriter()
        cw.writePrimitiveForm("", new StringBuilder())

        Writers.TimestampWriter tsw = new Writers.TimestampWriter()
        tsw.writePrimitiveForm("", new StringBuilder())

        Writers.LocaleWriter lw = new Writers.LocaleWriter()
        lw.writePrimitiveForm("", new StringBuilder())

        Writers.JsonStringWriter jsw = new Writers.JsonStringWriter()
        jsw.write("", false, new StringBuilder())
    }

    @Test
    void testNumericTruth()
    {
        assertFalse JsonWriter.isTrue(new BigInteger(0))
        assertTrue JsonWriter.isTrue(new BigInteger(1))
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
