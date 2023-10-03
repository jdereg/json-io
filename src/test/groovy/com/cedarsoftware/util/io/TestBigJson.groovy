package com.cedarsoftware.util.io
import com.google.gson.Gson
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
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
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestBigJson
{
    @Test
    void testBigJsonToMaps()
    {
        String json = TestUtil.fetchResource('big5D.json')
        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        assertEquals('big5D', map.ncube)
        assertEquals(0L, map.defaultCellValue)
        assertNotNull(map.axes)
        assertNotNull(map.cells)
    }

    @Test
    void testJsonIoVersusGson()
    {
        String json = TestUtil.fetchResource('big5D.json')

        Gson gson = new Gson()
        long start = System.nanoTime()
        gson.fromJson(json, Object.class)
        long stop = System.nanoTime()
        println ((stop - start) / 1000000L)

        start = System.nanoTime()
        JsonReader.jsonToJava(json)
        stop = System.nanoTime()
        println ((stop - start) / 1000000L)
    }

    @Disabled
    void testGsonOnHugeFile()
    {
        String json = TestUtil.fetchResource('big.json')

        Gson gson = new Gson()
        long start = System.nanoTime()
        gson.fromJson(json, Object.class)
        long stop = System.nanoTime()
        println 'gson: ' + ((stop - start) / 1000000L)

        int i=0
        while (i++ < 50i)
        {
            gson = new Gson()
            start = System.nanoTime()
            gson.fromJson(json, Object.class)
            stop = System.nanoTime()
            println 'gson: ' + ((stop - start) / 1000000L)
        }
    }

    @Disabled
    void testJsonOnHugeFile()
    {
        String json = TestUtil.fetchResource('big.json')

        long start = System.nanoTime()
        JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        long stop = System.nanoTime()
        println ((stop - start) / 1000000L)

//        println 'num read = ' + FastPushbackReader.numread
//        println 'num push = ' + FastPushbackReader.numpush

        int i=0
        while (i++ < 50i)
        {
            start = System.nanoTime()
            JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
            stop = System.nanoTime()
            println ((stop - start) / 1000000L)
        }
    }
}
