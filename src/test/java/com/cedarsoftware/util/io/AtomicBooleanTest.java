package com.cedarsoftware.util.io;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
class AtomicBooleanTest
{
    static class TestAtomicBooleanField
    {
        AtomicBoolean value;
        AtomicBoolean nullValue;
        AtomicBoolean strValue;
        AtomicBoolean emptyStrValue;
        AtomicBoolean objValue;
        AtomicBoolean[] values;
    }

    @Test
    void testAssignAtomicBoolean()
    {
        String json = "{\"@type\":\"com.cedarsoftware.util.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":true,\"nullValue\":null,\"strValue\":\"true\",\"emptyStrValue\":\"\", \"objValue\":{\"value\":false},\"values\":[false,null,true, \"true\"]}";

        TestAtomicBooleanField atom2 = TestUtil.toJava(json);

        assert atom2.value.get();
        assert atom2.nullValue == null;
        assert atom2.strValue.get();
        assert atom2.emptyStrValue == null;
        assert !atom2.objValue.get();
        assert atom2.values.length == 4;
        assert !atom2.values[0].get();
        assert atom2.values[1] == null;
        assert atom2.values[2].get();
        assert atom2.values[3].get();

        json = TestUtil.toJson(atom2);
        assert json.equals("{\"@type\":\"com.cedarsoftware.util.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":true,\"nullValue\":null,\"strValue\":true,\"emptyStrValue\":null,\"objValue\":false,\"values\":[false,null,true,true]}");

        json = "{\"@type\":\"com.cedarsoftware.util.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":16.5}";
        try
        {
            Object x = TestUtil.toJava(json);
            fail("should not make it here");
        }
        catch (Exception ignore)
        { }
    }

    @Test
    void testAssignAtomicBooleanStringToMaps()
    {
        String json = "{\"@type\":\"" + TestAtomicBooleanField.class.getName() + "\",\"strValue\":\"\"}";
        Map<String, Object> args = new HashMap<>();
        args.put(JsonReader.USE_MAPS, true);
        Map map = TestUtil.toJava(json, args);
        assertNull(map.get("fromString"));      // allowing "" to null out non-primitive fields in map-of-map mode
    }

    @Test
    void testAtomicBooleanInCollection()
    {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true);
        List<AtomicBoolean> list = new ArrayList<>();
        list.add(atomicBoolean);
        list.add(atomicBoolean);
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.toJava(json);
        assert list.size() == 2;
        atomicBoolean = list.get(0);
        assert atomicBoolean.get() == new AtomicBoolean(true).get();
        assertNotSame(list.get(0), list.get(1));
    }
}
