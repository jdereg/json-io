package com.cedarsoftware.util.io

import com.google.gson.JsonIOException
import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import java.util.concurrent.atomic.AtomicBoolean

import static org.junit.jupiter.api.Assertions.assertNotSame
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows

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
@CompileStatic
class TestAtomicBoolean
{
    static class TestAtomicBooleanField
    {
        AtomicBoolean value
        AtomicBoolean nullValue
        AtomicBoolean strValue
        AtomicBoolean emptyStrValue
        AtomicBoolean objValue
        AtomicBoolean[] values
    }

    @Test
    void testAssignAtomicBoolean()
    {
        String json = '''{"@type":"com.cedarsoftware.util.io.TestAtomicBoolean$TestAtomicBooleanField","value":true,"nullValue":null,"strValue":"true","emptyStrValue":"", "objValue":{"value":false},"values":[false,null,true, "true"]}'''
        TestAtomicBooleanField atom2 = (TestAtomicBooleanField) JsonReader.jsonToJava(json)

        assert atom2.value.get()
        assert atom2.nullValue == null
        assert atom2.strValue.get()
        assert atom2.emptyStrValue == null
        assert !atom2.objValue.get()
        assert atom2.values.length == 4
        assert !atom2.values[0].get()
        assert atom2.values[1] == null
        assert atom2.values[2].get()
        assert atom2.values[3].get()

        json = JsonWriter.objectToJson(atom2)
        assert json == '''{"@type":"com.cedarsoftware.util.io.TestAtomicBoolean$TestAtomicBooleanField","value":true,"nullValue":null,"strValue":true,"emptyStrValue":null,"objValue":false,"values":[false,null,true,true]}'''

        json = '''{"@type":"com.cedarsoftware.util.io.TestAtomicBoolean$TestAtomicBooleanField","value":16.5}'''
        assertThrows(JsonIoException.class, { JsonReader.jsonToJava(json) })
    }

    @Test
    void testAssignAtomicBooleanStringToMaps()
    {
        String json = '{"@type":"' + TestAtomicBooleanField.class.name + '","strValue":""}'
        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS):true] as Map)
        assertNull(map.fromString)      // allowing "" to null out non-primitive fields in map-of-map mode
    }

    @Test
    void testAtomicBooleanInCollection()
    {
        AtomicBoolean atomicBoolean = new AtomicBoolean(true)
        List list = new ArrayList()
        list.add(atomicBoolean)
        list.add(atomicBoolean)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        list = (List) TestUtil.readJsonObject(json)
        assert list.size() == 2
        atomicBoolean = (AtomicBoolean)list.get(0)
        assert atomicBoolean.get() == new AtomicBoolean(true).get()
        assertNotSame(list.get(0), list.get(1))
    }
}
