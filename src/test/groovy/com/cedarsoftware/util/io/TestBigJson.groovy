package com.cedarsoftware.util.io

import com.google.gson.Gson
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

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
        def map = JsonReader.jsonToMaps(json)
        assertEquals('big5D', map.ncube)
        assertEquals(0L, map.defaultCellValue)
        assertNotNull(map.axes)
        assertNotNull(map.cells)
    }

    @Test
    void testJsonIoVersusJsonSlurper()
    {
        String json = TestUtil.fetchResource('big5D.json')

        Gson gson = new Gson()
        long start = System.nanoTime()
        gson.fromJson(json, Object.class)
        long stop = System.nanoTime()
        println ((stop - start) / 1000000L)

        start = System.nanoTime()
        def map = JsonReader.jsonToMaps(json)
        stop = System.nanoTime()
        println ((stop - start) / 1000000L)
    }

    @Test
    void testReadVeryLargeJson()
    {
        String json = TestUtil.fetchResource('veryLarge.json')

        long start = System.nanoTime()
        JsonReader.jsonToMaps(json)
        long stop = System.nanoTime()
        println ((stop - start) / 1000000L)
    }
}
