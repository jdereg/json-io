package com.cedarsoftware.util.io

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

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
class TestCustomReaderMap
{
    private static class CustomPoint
    {
        public long x
        public long y
    }

    private static class CustomPointReader implements JsonReader.JsonClassReaderEx
    {
        public Object read(Object obj, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            JsonObject jObj = (JsonObject) obj

            CustomPoint point = (CustomPoint) jObj.getTarget()
            if (point == null)
            {
                throw new IllegalStateException("target must be specified on JsonObject passed to CustomPointReader")
            }

            point.x = (long) jObj.x
            point.y = (long) jObj.y
            return point
        }
    }

    @Test
    public void testMapWithCustom()
    {
        CustomPoint pt = new CustomPoint()
        pt.x = 5
        pt.y = 7

        Map<String,Object> map = [pt: pt]
        Map<Class,JsonReader.JsonClassReaderBase> customReaders = [(CustomPoint.class):new CustomPointReader()]
        Map<String,Object> args = [(JsonReader.CUSTOM_READER_MAP):customReaders]

        String json = JsonWriter.objectToJson(map)
        Map clone = (Map) JsonReader.jsonToJava(json,args)

        CustomPoint clonePoint = (CustomPoint) clone.pt
        assertEquals(pt.x, clonePoint.x)
        assertEquals(pt.y, clonePoint.y)
    }

    @Test
    public void testCollectionWithCustom()
    {
        CustomPoint pt = new CustomPoint()
        pt.x = 5
        pt.y = 7

        List<Object> list = new ArrayList<>()
        list.add(pt)

        Map<Class,JsonReader.JsonClassReaderBase> customReaders = new HashMap<>()
        customReaders[CustomPoint.class] = new CustomPointReader()
        HashMap<String,Object> args = new HashMap<>()
        args[JsonReader.CUSTOM_READER_MAP] = customReaders


        String json = JsonWriter.objectToJson(list)
        List clone = (List) JsonReader.jsonToJava(json,args)

        CustomPoint clonePoint = (CustomPoint) clone.get(0)
        assertEquals(pt.x, clonePoint.x)
        assertEquals(pt.y, clonePoint.y)
    }

    @Test
    public void testArrayWithCustom()
    {
        CustomPoint pt = new CustomPoint()
        pt.x = 5
        pt.y = 7

        Object [] list = new Object [1]
        list[0] = pt

        Map<Class,JsonReader.JsonClassReaderBase> customReaders = new HashMap<>()
        customReaders.put(CustomPoint.class, new CustomPointReader())
        HashMap<String,Object> args = new HashMap<>()
        args[JsonReader.CUSTOM_READER_MAP] = customReaders

        String json = JsonWriter.objectToJson(list)
        Object [] clone = (Object []) JsonReader.jsonToJava(json,args)

        CustomPoint clonePoint = (CustomPoint) clone[0]
        assertEquals(pt.x, clonePoint.x)
        assertEquals(pt.y, clonePoint.y)
    }
}
