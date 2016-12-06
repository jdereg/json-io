package com.cedarsoftware.util.io

import org.junit.Test

import static junit.framework.Assert.assertEquals
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
class TestMissingFieldHandler
{
    private static class CustomPoint
    {
        public long x
        // y is deleted
        //public long y
        // replaced by newY;
        public long newY;
    }

    private static final String OLD_CUSTOM_POINT = '{\"@type\":\"com.cedarsoftware.util.io.TestMissingFieldHandler$CustomPoint\",\"x\":5,\"y\":7}';

    @Test
    void testMissingHandler()
    {
        CustomPoint pt = new CustomPoint()
        pt.x = 5

        JsonReader.MissingFieldHandler missingHandler = new JsonReader.MissingFieldHandler() {
            void fieldMissing(Object object, String fieldName, Object value)
            {
                ((CustomPoint)object).newY = (long) value;
            }
        }

        Map<String,Object> args = [(JsonReader.MISSING_FIELD_HANDLER):missingHandler]
        CustomPoint clonePoint = JsonReader.jsonToJava(OLD_CUSTOM_POINT,args)
        assertEquals(pt.x, clonePoint.x)
        assertEquals(7, clonePoint.newY)
    }

}
