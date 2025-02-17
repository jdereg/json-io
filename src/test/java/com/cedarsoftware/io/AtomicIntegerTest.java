package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;

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
class AtomicIntegerTest
{
    static class TestAtomicIntegerField
    {
        AtomicInteger value;
        AtomicInteger nullValue;
        AtomicInteger strValue;
        AtomicInteger emptyStrValue;
        AtomicInteger objValue;
        AtomicInteger[] values;
    }

    @Test
    void testAssignAtomicInteger()
    {
        String json = "{\"@type\":\"com.cedarsoftware.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16,\"nullValue\":null,\"strValue\":\"50\",\"emptyStrValue\":\"\", \"objValue\":{\"value\":-9},\"values\":[-5,null,5, \"45\"]}";
        TestAtomicIntegerField atom2 = TestUtil.toObjects(json, null);

        assert atom2.value.get() == 16;
        assert atom2.nullValue == null;
        assert atom2.strValue.get() == 50;
        assert atom2.emptyStrValue.get() == 0;
        assert atom2.objValue.get() == -9;
        assert atom2.values.length == 4;
        assert atom2.values[0].get() == -5;
        assert atom2.values[1] == null;
        assert atom2.values[2].get() == 5;
        assert atom2.values[3].get() == 45;

        json = TestUtil.toJson(atom2);
        
        String expectedJson = "{\"@type\":\"com.cedarsoftware.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16,\"nullValue\":null,\"strValue\":50,\"emptyStrValue\":0,\"objValue\":-9,\"values\":[-5,null,5,45]}";

        assert JsonParser.parseString(json).equals(JsonParser.parseString(expectedJson));
        
        json = "{\"@type\":\"com.cedarsoftware.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16.5}";
        TestAtomicIntegerField aif = TestUtil.toObjects(json, null);
        assert aif.value.get() == 16;
    }

    @Test
    void testAssignAtomicIntegerStringToMaps()
    {
        String json = "{\"@type\":\"" + TestAtomicIntegerField.class.getName() + "\",\"strValue\":\"\"}";
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        assertNull(map.get("fromString"));      // allowing "" to null out non-primitive fields in map-of-map mode
    }

    @Test
    void testAtomicIntegerInCollection()
    {
        AtomicInteger atomicInt = new AtomicInteger(12345);
        List<AtomicInteger> list = new ArrayList<>();
        list.add(atomicInt);
        list.add(atomicInt);
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.toObjects(json, null);
        assert list.size() == 2;
        atomicInt = list.get(0);
        assert atomicInt.get() == new AtomicInteger(12345).get();
        assertNotSame(list.get(0), list.get(1));
    }
}
