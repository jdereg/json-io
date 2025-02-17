package com.cedarsoftware.io;

import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
/**
 * Test cases for JsonReader / JsonWriter
 *
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
class BigJsonTest
{
    @Test
    void testBigJsonToJava()
    {
        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJavaObjects().build();
        String json = ClassUtilities.loadResourceAsString("big/big5D.json");
        
        for (int i=0; i < 1; i++) {
            Map map = TestUtil.toObjects(json, readOptions,null);
            assertEquals("big5D", map.get("ncube"));
            assertEquals(0L, map.get("defaultCellValue"));
            assertNotNull(map.get("axes"));
            assertNotNull(map.get("cells"));
        }
    }

    @Test
    void testBigJsonToMaps()
    {
        ReadOptions readOptions = new ReadOptionsBuilder().returnAsJsonObjects().build();
        String json = ClassUtilities.loadResourceAsString("big/big5D.json");

        for (int i=0; i < 1; i++) {
            Map map = TestUtil.toObjects(json, readOptions, null);
            assertEquals("big5D", map.get("ncube"));
            assertEquals(0L, map.get("defaultCellValue"));
            assertNotNull(map.get("axes"));
            assertNotNull(map.get("cells"));
        }
    }
}
