package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
public class CustomReaderMapTest
{
    @Test
    public void testMapWithCustom()
    {
        CustomPoint pt = new CustomPoint();
        pt.x = 5;
        pt.y = 7;

        Map<String, CustomPoint> data = new HashMap<>(1);
        data.put("pt", pt);
        Map<Class<CustomPoint>, CustomPointReader> customReaders = new HashMap<>(1);
        customReaders.put(CustomPoint.class, new CustomPointReader());
        
        String json = TestUtil.toJson(data);
        Map<String, CustomPoint> clone = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).build(), null);

        CustomPoint clonePoint = clone.get("pt");
        assertEquals(pt.x, clonePoint.x);
        assertEquals(pt.y, clonePoint.y);
    }

    @Test
    public void testCollectionWithCustom()
    {
        CustomPoint pt = new CustomPoint();
        pt.x = 5;
        pt.y = 7;

        List<CustomPoint> list = new ArrayList<>();
        list.add(pt);

        Map<Class<CustomPoint>, JsonReader.JsonClassReader> customReaders = new HashMap<>();
        customReaders.put(CustomPoint.class, new CustomPointReader());
        String json = TestUtil.toJson(list);
        List<CustomPoint> clone = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).build(), null);

        CustomPoint clonePoint = clone.get(0);
        assertEquals(pt.x, clonePoint.x);
        assertEquals(pt.y, clonePoint.y);
    }

    @Test
    public void testArrayWithCustom()
    {
        CustomPoint pt = new CustomPoint();
        pt.x = 5;
        pt.y = 7;

        Object[] list = new Object[1];
        list[0] = pt;

        Map<Class<CustomPoint>, JsonReader.JsonClassReader> customReaders = new HashMap<>();
        customReaders.put(CustomPoint.class, new CustomPointReader());

        String json = TestUtil.toJson(list);
        Object[] clone = TestUtil.toObjects(json, new ReadOptionsBuilder().replaceCustomReaderClasses(customReaders).build(), null);

        CustomPoint clonePoint = (CustomPoint) clone[0];
        assertEquals(pt.x, clonePoint.x);
        assertEquals(pt.y, clonePoint.y);
    }

    private static class CustomPoint
    {
        public long x;
        public long y;
    }

    private static class CustomPointReader implements JsonReader.JsonClassReader
    {
        public Object read(Object obj, Resolver resolver)
        {
            JsonObject jObj = (JsonObject) obj;

            CustomPoint point = (CustomPoint) jObj.getTarget();
            if (point == null)
            {
                throw new IllegalStateException("target must be specified on JsonObject passed to CustomPointReader");
            }

            point.x = (long) jObj.get("x");
            point.y = (long) jObj.get("y");
            return point;
        }

    }
}
