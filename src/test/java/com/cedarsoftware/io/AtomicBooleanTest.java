package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        String json = "{\"@type\":\"com.cedarsoftware.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":true,\"nullValue\":null,\"strValue\":\"true\",\"emptyStrValue\":\"\", \"objValue\":{\"value\":false},\"values\":[false,null,true, \"true\"]}";

        TestAtomicBooleanField atom2 = TestUtil.toObjects(json, null);

        assert atom2.value.get();
        assert atom2.nullValue == null;
        assert atom2.strValue.get();
        assert atom2.emptyStrValue.get() == false;
        assert !atom2.objValue.get();
        assert atom2.values.length == 4;
        assert !atom2.values[0].get();
        assert atom2.values[1] == null;
        assert atom2.values[2].get();
        assert atom2.values[3].get();

        json = TestUtil.toJson(atom2);

        String expectedJson = "{\"@type\":\"com.cedarsoftware.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":true,\"nullValue\":null,\"strValue\":true,\"emptyStrValue\":false,\"objValue\":false,\"values\":[false,null,true,true]}";

        assert JsonParser.parseString(json).equals(JsonParser.parseString(expectedJson));

        json = "{\"@type\":\"com.cedarsoftware.io.AtomicBooleanTest$TestAtomicBooleanField\",\"value\":16.5}";
        TestAtomicBooleanField abf = TestUtil.toObjects(json, null);
        assert abf.value.get();
    }

    @Test
    void testAssignAtomicBooleanStringToMaps()
    {
        String json = "{\"@type\":\"" + TestAtomicBooleanField.class.getName() + "\",\"strValue\":\"\"}";
        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
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
        list = TestUtil.toObjects(json, null);
        assert list.size() == 2;
        atomicBoolean = list.get(0);
        assert atomicBoolean.get() == new AtomicBoolean(true).get();
        assertNotSame(list.get(0), list.get(1));    // Made Atomic* logical primitives, so they are no longer written as id/refs.
    }
}
