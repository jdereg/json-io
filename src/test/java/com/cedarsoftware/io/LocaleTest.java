package com.cedarsoftware.io;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.cedarsoftware.util.DeepEquals;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
public class LocaleTest
{
    @Test
    public void testLocale()
    {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        String json = TestUtil.toJson(locale);
        TestUtil.printLine("json=" + json);
        Locale us = TestUtil.toObjects(json, null);
        assertEquals(locale, us);

        locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry(), "johnson");
        json = TestUtil.toJson(locale);
        TestUtil.printLine("json=" + json);
        us = TestUtil.toObjects(json, null);
        assertEquals(locale, us);
        
        Throwable e = assertThrows(Exception.class, () -> { TestUtil.toObjects("{\"@type\":\"java.util.Locale\"}", null); });
        assertTrue(e.getMessage().contains("Map to 'Locale' the map must include: [locale], [value], or [_v] as key with associated value"));

        json = "{\"@type\":\"java.util.Locale\",\"locale\":\"en\"}";
        locale = TestUtil.toObjects(json, null);
        assertEquals("en", locale.getLanguage());
        assertEquals("", locale.getCountry());
        assertEquals("", locale.getVariant());

        json = "{\"@type\":\"java.util.Locale\",\"locale\":\"en-US\"}";
        locale = TestUtil.toObjects(json, null);
        assertEquals("en", locale.getLanguage());
        assertEquals("US", locale.getCountry());
        assertEquals("", locale.getVariant());
    }

    @Test
    public void testLocaleArray()
    {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        String json = TestUtil.toJson(new Object[]{locale});
        TestUtil.printLine("json=" + json);
        Object[] oArray = TestUtil.toObjects(json, null);
        assertEquals(1, oArray.length);
        Locale us = (Locale) oArray[0];
        assertEquals(locale, us);

        json = TestUtil.toJson(new Locale[]{locale});
        TestUtil.printLine("json=" + json);
        Locale[] lArray = TestUtil.toObjects(json, null);
        assertEquals(1, lArray.length);
        us = lArray[0];
        assertEquals(locale, us);
    }

    @Test
    public void testLocaleInMapValue()
    {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        Map map = new HashMap<>();
        map.put("us", locale);
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json=" + json);
        map = TestUtil.toObjects(json, null);

        assertThat(map)
                .hasSize(1)
                .containsEntry("us", locale);
    }

    @Test
    public void testLocaleInMapKey()
    {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        Map map = new HashMap<>();
        map.put(locale, "us");
        String json = TestUtil.toJson(map);
        TestUtil.printLine("json=" + json);
        map = TestUtil.toObjects(json, null);

        assertThat(map)
                .containsKey(locale);

        assertEquals(1, map.size());
        Iterator i = map.keySet().iterator();
        assertEquals(i.next(), locale);
    }

    @Test
    void testLocaleInMapOfMaps() {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        String json = TestUtil.toJson(locale);
        Locale locale1 = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertEquals(locale, locale1);
    }

    @Test
    public void testLocaleRef()
    {
        Locale locale = new Locale(Locale.ENGLISH.getLanguage(), Locale.US.getCountry());
        String json = TestUtil.toJson(new Object[]{locale, locale});
        TestUtil.printLine("json=" + json);
        Object[] oArray = TestUtil.toObjects(json, null);
        assertEquals(2, oArray.length);
        Locale us = (Locale) oArray[0];
        assertEquals(locale, us);
        assertEquals(oArray[0], oArray[1]);
    }

    @Test
    public void testLocaleInMap()
    {
        Map<Locale, String> map1 = new LinkedHashMap<>(3);
        map1.put(Locale.US, "United States of America");
        map1.put(Locale.CANADA, "Canada");
        map1.put(Locale.UK, "United Kingdom");
        Map<Locale, String> map = map1;

        String json = TestUtil.toJson(map);
        Map map2 = TestUtil.toObjects(json, null);
        assertTrue(DeepEquals.deepEquals(map, map2));
    }

}
