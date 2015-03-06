package com.cedarsoftware.util.io

import org.junit.Test

import static org.junit.Assert.assertTrue

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
class TestTimeZones
{
    public static class TestTimeZone implements Serializable
    {
        private TimeZone _zone
    }

    @Test
    void testTimeZoneAsField() throws Exception
    {
        TimeZone zone = TimeZone.default
        TestTimeZone tz = new TestTimeZone()
        tz._zone = zone
        String json = TestUtil.getJsonString(tz)
        TestUtil.printLine("json=" + json)

        tz = (TestTimeZone) TestUtil.readJsonObject(json)
        assertTrue(zone.equals(tz._zone))
    }

    @Test
    void testTimeZone() throws Exception
    {
        TimeZone est = TimeZone.getTimeZone("EST")
        String json = TestUtil.getJsonString(est)
        TestUtil.printLine("json=" + json)
        TimeZone tz = (TimeZone) TestUtil.readJsonObject(json)
        assertTrue(tz.equals(est))

        TimeZone pst = TimeZone.getTimeZone("PST")
        json = TestUtil.getJsonString(pst)
        TestUtil.printLine("json=" + json)
        tz = (TimeZone) TestUtil.readJsonObject(json)
        assertTrue(tz.equals(pst))

        try
        {
            String noZone = '{"@type":"sun.util.calendar.ZoneInfo"}'
            TestUtil.readJsonObject(noZone)
            assertTrue("Should not reach this point.", false)
        }
        catch(Exception e) {}
    }

    @Test
    void testTimeZoneInArray() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 1)
        TimeZone tz = (TimeZone)oArray[0]
        assertTrue(tz.equals(pst))

        json = TestUtil.getJsonString([pst] as TimeZone[])
        TestUtil.printLine("json=" + json)

        Object[] tzArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(tzArray.length == 1)
        tz = (TimeZone)tzArray[0]
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInCollection() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        List col = new ArrayList()
        col.add(pst)
        String json = TestUtil.getJsonString(col)
        TestUtil.printLine("json=" + json)

        col = (List) TestUtil.readJsonObject(json)
        assertTrue(col.size() == 1)
        TimeZone tz = (TimeZone) col.get(0)
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapValue() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        Map map = new HashMap()
        map.put("p", pst)
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        TimeZone tz = (TimeZone) map.get("p")
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapKey() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        Map map = new HashMap()
        map.put(pst, "p")
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)

        Iterator i = map.keySet().iterator()
        TimeZone tz = (TimeZone) i.next()
        assertTrue(tz.equals(pst))
    }

    @Test
    void testTimeZoneInMapofMaps() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst] as Object[])
        TestUtil.printLine("json=" + json)

        Map map = JsonReader.jsonToMaps(json)
        Object[] items = (Object[]) map["@items"]
        Map item = (Map) items[0]
        assertTrue(item.containsKey("zone"))
        assertTrue("PST".equals(item.zone))
    }

    @Test
    void testTimeZoneRef() throws Exception
    {
        TimeZone pst = TimeZone.getTimeZone("PST")
        String json = TestUtil.getJsonString([pst, pst] as Object[])
        TestUtil.printLine("json=" + json)

        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 2)
        TimeZone tz = (TimeZone)oArray[0]
        assertTrue(tz.equals(pst))
        assertTrue(oArray[0] == oArray[1])
    }
}
