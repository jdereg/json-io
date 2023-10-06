package com.cedarsoftware.util.io

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue


/**
 * @author Balazs Bessenyei (h143570@gmail.com)
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
class TestUUID
{
    static class TestUUIDFields
    {
        UUID fromString
        UUID internals
        UUID internalsObj
    }

    @Test
    void testAssignUUID()
    {
        String json = '{"@type":"' + TestUUIDFields.class.name + '","fromString":"6508db3c-52c5-42ad-91f3-621d6e1d6557", "internals": {"@type": "java.util.UUID", "mostSigBits":7280309849777586861,"leastSigBits":-7929886640328317609}}'
        TestUUIDFields tu = (TestUUIDFields) TestUtil.readJsonObject(json)
        assertEquals((Object) UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.fromString)
        assertEquals((Object) UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.internals)

        Map map = (Map) JsonReader.jsonToJava(json, [(JsonReader.USE_MAPS): true] as Map)
        json = TestUtil.getJsonString(map)
        tu = (TestUUIDFields) TestUtil.readJsonObject(json)
        assertEquals((Object) UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.fromString)
        assertEquals((Object) UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.internals)

        json = '{"@type":"' + TestUUIDFields.class.name + '","fromString":""}'
        Throwable thrown = assertThrows(JsonIoException.class, { TestUtil.readJsonObject(json) })
        assertEquals(IllegalArgumentException.class, thrown.cause.class)

        json = '{"@type":"' + TestUUIDFields.class.name + '", "internals": {"@type": "java.util.UUID", "leastSigBits":-7929886640328317609}}'
        thrown = assertThrows(JsonIoException.class, { TestUtil.readJsonObject(json) })
        assertTrue(thrown.cause.message.contains("mostSigBits"))

        json = '{"@type":"' + TestUUIDFields.class.name + '", "internals": {"@type": "java.util.UUID", "mostSigBits":7280309849777586861}}'
        thrown = assertThrows(JsonIoException.class, { TestUtil.readJsonObject(json) })
        assertTrue(thrown.cause.message.contains("leastSigBits"))
    }

    @Test
    void testUUID()
    {
        String s = "9bf37453-c576-432b-a531-2dbc2be797c2"
        UUID uuid = UUID.fromString(s)
        String json = TestUtil.getJsonString(uuid)
        TestUtil.printLine("json=" + json)
        uuid = (UUID) TestUtil.readJsonObject(json)
        assertEquals(UUID.fromString(s), uuid)
    }

    @Test
    void testUUIDInArray()
    {
        String s = "f3060936-aae9-4273-aead-2768d99cf9e7"
        UUID uuid = UUID.fromString(s)
        Object[] uuids = [uuid, uuid] as Object[]
        UUID[] typedUUIDs = [uuid, uuid] as UUID[]
        String json = TestUtil.getJsonString(uuids)
        TestUtil.printLine("json=" + json)

        uuids = (Object[]) TestUtil.readJsonObject(json)
        assertTrue(uuids.length == 2)
        assertSame(uuids[0], uuids[1])
        assertTrue(UUID.fromString(s).equals(uuids[0]))
        json = TestUtil.getJsonString(typedUUIDs)
        TestUtil.printLine("json=" + json)
        assertTrue(typedUUIDs.length == 2)
        assertTrue(typedUUIDs[0] == typedUUIDs[1])
        assertEquals(UUID.fromString(s), typedUUIDs[0])
    }

    @Test
    void testUUIDInCollection()
    {
        String s = "03a7e3c2-2d3a-4ca9-a426-ff4270015fde"
        UUID uuid = UUID.fromString(s)
        List list = new ArrayList()
        list.add(uuid)
        list.add(uuid)
        String json = TestUtil.getJsonString(list)
        TestUtil.printLine("json=" + json)
        list = (List) TestUtil.readJsonObject(json)
        assertTrue(list.size() == 2)
        assertEquals(UUID.fromString(s), list.get(0))
        assertSame(list.get(0), list.get(1))
    }
}
