package com.cedarsoftware.util.io

import com.cedarsoftware.util.DeepEquals
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows
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
class TestLocale
{
    @Test
    void testLocale()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        String json = TestUtil.getJsonString(locale)
        TestUtil.printLine("json=" + json)
        Locale us = (Locale) TestUtil.readJsonObject(json)
        assertTrue(locale.equals(us))

        locale = new Locale(Locale.ENGLISH.language, Locale.US.country, "johnson")
        json = TestUtil.getJsonString(locale)
        TestUtil.printLine("json=" + json)
        us = (Locale) TestUtil.readJsonObject(json)
        assertTrue(locale.equals(us))


        Throwable e = assertThrows(Exception.class, { TestUtil.readJsonObject('{"@type":"java.util.Locale"}') })
        assertTrue(e.message.toLowerCase().contains("must specify 'language'"))

        json = '{"@type":"java.util.Locale","language":"en"}'
        locale = (Locale) TestUtil.readJsonObject(json)
        assertTrue("en".equals(locale.language))
        assertTrue("".equals(locale.country))
        assertTrue("".equals(locale.variant))

        json = '{"@type":"java.util.Locale","language":"en","country":"US"}'
        locale = (Locale) TestUtil.readJsonObject(json)
        assertTrue("en".equals(locale.language))
        assertTrue("US".equals(locale.country))
        assertTrue("".equals(locale.variant))
    }

    @Test
    void testLocaleArray()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        String json = TestUtil.getJsonString([locale] as Object[])
        TestUtil.printLine("json=" + json)
        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 1)
        Locale us = (Locale) oArray[0];
        assertTrue(locale.equals(us))

        json = TestUtil.getJsonString([locale] as Locale[])
        TestUtil.printLine("json=" + json)
        Locale[] lArray = (Locale[]) TestUtil.readJsonObject(json)
        assertTrue(lArray.length == 1)
        us = lArray[0];
        assertTrue(locale.equals(us))
    }

    @Test
    void testLocaleInMapValue()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        Map map = new HashMap()
        map.put("us", locale)
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)
        map = (Map) TestUtil.readJsonObject(json)
        assertTrue(map.size() == 1)
        assertTrue(map.get("us").equals(locale))
    }

    @Test
    void testLocaleInMapKey()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        Map map = new HashMap()
        map.put(locale, "us")
        String json = TestUtil.getJsonString(map)
        TestUtil.printLine("json=" + json)
        map = (Map) TestUtil.readJsonObject(json)
        assertTrue(map.size() == 1)
        Iterator i = map.keySet().iterator()
        assertTrue(i.next().equals(locale))
    }

    @Test
    void testLocaleInMapOfMaps()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        String json = TestUtil.getJsonString(locale)
        TestUtil.printLine("json=" + json)
        Map map = JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertTrue("en".equals(map.get("language")))
        assertTrue("US".equals(map.get("country")))
    }

    @Test
    void testLocaleRef()
    {
        Locale locale = new Locale(Locale.ENGLISH.language, Locale.US.country)
        String json = TestUtil.getJsonString([locale, locale] as Object[])
        TestUtil.printLine("json=" + json)
        Object[] oArray = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(oArray.length == 2)
        Locale us = (Locale) oArray[0];
        assertTrue(locale.equals(us))
        assertTrue(oArray[0] == oArray[1])
    }

    @Test
    void testLocaleInMap()
    {
        def map = [
                (Locale.US):'United States of America',
                (Locale.CANADA):'Canada',
                (Locale.UK): 'United Kingdom']

        String json = TestUtil.getJsonString(map)
        Map map2 = (Map) TestUtil.readJsonObject(json)
        assertTrue(DeepEquals.deepEquals(map, map2))
    }
}
