package com.cedarsoftware.io;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
public class UUIDTest
{
    @Test
    public void testAssignUUID()
    {
        String json = "{\"@type\":\"" + TestUUIDFields.class.getName() + "\",\"fromString\":\"6508db3c-52c5-42ad-91f3-621d6e1d6557\", \"internals\": {\"@type\": \"java.util.UUID\", \"mostSigBits\":7280309849777586861,\"leastSigBits\":-7929886640328317609}}";
        TestUUIDFields tu = TestUtil.toObjects(json, null);
        assertEquals(UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.getFromString());
        assertEquals(UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.getInternals());

        Map map = TestUtil.toObjects(json, new ReadOptionsBuilder().returnAsJsonObjects().build(), null);
        json = TestUtil.toJson(map);
        tu = TestUtil.toObjects(json, null);
        assertEquals(UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.getFromString());
        assertEquals(UUID.fromString("6508db3c-52c5-42ad-91f3-621d6e1d6557"), tu.getInternals());

        String json1 = "{\"@type\":\"" + TestUUIDFields.class.getName() + "\",\"fromString\":\"\"}";
        Throwable thrown = assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json1, null); });
        assertEquals(IllegalArgumentException.class, thrown.getCause().getClass());

        String json2 = "{\"@type\":\"" + TestUUIDFields.class.getName() + "\", \"internals\": {\"@type\": \"java.util.UUID\", \"leastSigBits\":-7929886640328317609}}";
        thrown = assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json2, null); });
        assert thrown.getMessage().contains("Map to 'UUID' the map must include: [UUID], [value], [_v], or [mostSigBits, leastSigBits] as key with associated value");

        String json3 = "{\"@type\":\"" + TestUUIDFields.class.getName() + "\", \"internals\": {\"@type\": \"java.util.UUID\", \"mostSigBits\":7280309849777586861}}";
        thrown = assertThrows(JsonIoException.class, () -> { TestUtil.toObjects(json3, null); });
        assert thrown.getMessage().contains("Map to 'UUID' the map must include: [UUID], [value], [_v], or [mostSigBits, leastSigBits] as key with associated value");
    }

    @Test
    public void testUUID()
    {
        String s = "9bf37453-c576-432b-a531-2dbc2be797c2";
        UUID uuid = UUID.fromString(s);
        String json = TestUtil.toJson(uuid);
        TestUtil.printLine("json=" + json);
        uuid = TestUtil.toObjects(json, null);
        assertEquals(UUID.fromString(s), uuid);
    }

    @Test
    public void testUUIDInArray()
    {
        String s = "f3060936-aae9-4273-aead-2768d99cf9e7";
        UUID uuid = UUID.fromString(s);
        Object[] uuids = new Object[]{uuid, uuid};
        UUID[] typedUUIDs = new UUID[]{uuid, uuid};
        String json = TestUtil.toJson(uuids);
        TestUtil.printLine("json=" + json);

        uuids = TestUtil.toObjects(json, null);
        assertEquals(2, uuids.length);
        assertNotSame(uuids[0], uuids[1]);              // Proving it is a non-ref
        assertEquals(UUID.fromString(s), uuids[0]);
        json = TestUtil.toJson(typedUUIDs);
        TestUtil.printLine("json=" + json);
        assertEquals(2, typedUUIDs.length);
        assertEquals(typedUUIDs[0], typedUUIDs[1]);
        assertEquals(UUID.fromString(s), typedUUIDs[0]);
    }

    @Test
    public void testUUIDInCollection()
    {
        String s = "03a7e3c2-2d3a-4ca9-a426-ff4270015fde";
        UUID uuid = UUID.fromString(s);
        List<UUID> list = new ArrayList<>();
        list.add(uuid);
        list.add(uuid);
        String json = TestUtil.toJson(list);
        TestUtil.printLine("json=" + json);
        list = TestUtil.toObjects(json, null);
        assertEquals(2, list.size());
        assertEquals(UUID.fromString(s), list.get(0));
        assertNotSame(list.get(0), list.get(1));        // Proving it is a non-ref
    }

    @Test
    public void testUUIDasPrimitiveForm()
    {
        UUID uuid = UUID.randomUUID();
        TestUUIDFields t = new TestUUIDFields();
        t. fromString = uuid;
        String json = TestUtil.toJson(t);
        TestUUIDFields tut = TestUtil.toObjects(json, null);
        assert tut.fromString.equals(uuid);
    }

    public static class TestUUIDFields
    {
        public UUID getFromString()
        {
            return fromString;
        }

        public void setFromString(UUID fromString)
        {
            this.fromString = fromString;
        }

        public UUID getInternals()
        {
            return internals;
        }

        public void setInternals(UUID internals)
        {
            this.internals = internals;
        }

        public UUID getInternalsObj()
        {
            return internalsObj;
        }

        public void setInternalsObj(UUID internalsObj)
        {
            this.internalsObj = internalsObj;
        }

        private UUID fromString;
        private UUID internals;
        private UUID internalsObj;
    }
}
