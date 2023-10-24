package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
        String json = "{\"@type\":\"com.cedarsoftware.util.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16,\"nullValue\":null,\"strValue\":\"50\",\"emptyStrValue\":\"\", \"objValue\":{\"value\":-9},\"values\":[-5,null,5, \"45\"]}";
        TestAtomicIntegerField atom2 = (TestAtomicIntegerField) JsonReader.jsonToJava(json);

        assert atom2.value.get() == 16;
        assert atom2.nullValue == null;
        assert atom2.strValue.get() == 50;
        assert atom2.emptyStrValue == null;
        assert atom2.objValue.get() == -9;
        assert atom2.values.length == 4;
        assert atom2.values[0].get() == -5;
        assert atom2.values[1] == null;
        assert atom2.values[2].get() == 5;
        assert atom2.values[3].get() == 45;

        json = JsonWriter.objectToJson(atom2);
        assert json.equals("{\"@type\":\"com.cedarsoftware.util.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16,\"nullValue\":null,\"strValue\":50,\"emptyStrValue\":null,\"objValue\":-9,\"values\":[-5,null,5,45]}");
        
        json = "{\"@type\":\"com.cedarsoftware.util.io.AtomicIntegerTest$TestAtomicIntegerField\",\"value\":16.5}";
        try
        {
            JsonReader.jsonToJava(json);
            fail("should not make it here");
        }
        catch (JsonIoException ignore)
        { }
    }

    @Test
    void testAssignAtomicIntegerStringToMaps()
    {
        String json = "{\"@type\":\"" + TestAtomicIntegerField.class.getName() + "\",\"strValue\":\"\"}";
        Map<String, Object> args = new HashMap<>();
        args.put(JsonReader.USE_MAPS, true);
        Map map = JsonReader.jsonToJava(json, args);
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
        list = TestUtil.toJava(json);
        assert list.size() == 2;
        atomicInt = list.get(0);
        assert atomicInt.get() == new AtomicInteger(12345).get();
        assertNotSame(list.get(0), list.get(1));
    }
}
