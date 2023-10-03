package com.cedarsoftware.util.io

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
class TestUsingSunMisc
{
    static class ShouldBeImpossibleToInstantiate
    {
        private x = 0;
        ShouldBeImpossibleToInstantiate()
        {
            throw new JsonIoException("Go away")
        }
    }

    @Test
    void testCustomTopReaderShoe()
    {
        Dog.Shoe shoe = Dog.Shoe.construct()

        // Dirty Workaround otherwise
        Object[] array = new Object[1]
        array[0] = shoe;
        String workaroundString = JsonWriter.objectToJson(array)
        JsonReader.assignInstantiator(Dog.Shoe.class, new JsonReader.ClassFactoryEx() {
            Object newInstance(Class c, Map args)
            {
                return Dog.Shoe.construct()
            }
        })
        TestUtil.readJsonObject(workaroundString, [(JsonReader.CUSTOM_READER_MAP):[(Dog.Shoe.class):new JsonReader.JsonClassReader() {
            public Object read(Object jOb, Deque<JsonObject<String, Object>> stack)
            {
                // no need to do anything special
                return jOb
            }
        }]])
        // shoe can be accessed by
        // checking array type + length
        // and accessing [0]

        String json = JsonWriter.objectToJson(shoe)
        //Should not fail, as we defined our own reader
        // It is expected, that this object is instantiated twice:
        // -once for analysis + Stack
        // -deserialization with Stack
        TestUtil.readJsonObject(json, [(JsonReader.CUSTOM_READER_MAP):[(Dog.Shoe.class):new JsonReader.JsonClassReader() {
            public Object read(Object jOb, Deque<JsonObject<String, Object>> stack)
            {
                return jOb
            }
        }]])
    }

    @Test
    void testDirectCreation()
    {
        MetaUtils.useUnsafe = true;
        // this test will fail without directCreation
        Dog.OtherShoe shoe = Dog.OtherShoe.construct()
        Dog.OtherShoe oShoe = (Dog.OtherShoe) JsonReader.jsonToJava(JsonWriter.objectToJson(shoe))
        assertTrue(shoe.equals(oShoe))
        oShoe = (Dog.OtherShoe) JsonReader.jsonToJava(JsonWriter.objectToJson(shoe))
        assertTrue(shoe.equals(oShoe))

        try
        {
            MetaUtils.useUnsafe = false;
            shoe = Dog.OtherShoe.construct()
            JsonReader.jsonToJava(JsonWriter.objectToJson(shoe))
            fail()
        }
        catch (JsonIoException e)
        {
            assert e.message.toLowerCase().contains('no constructor found')
        }

        MetaUtils.useUnsafe = true;
        // this test will fail without directCreation
        Dog.OtherShoe.construct()
        oShoe = (Dog.OtherShoe) JsonReader.jsonToJava(JsonWriter.objectToJson(shoe))
        assertTrue(shoe.equals(oShoe))
    }

    @Test
    void testImpossibleClass()
    {
        assertThrows(Exception.class, { new ShouldBeImpossibleToInstantiate() })

        String json = '{"@type":"' + ShouldBeImpossibleToInstantiate.class.name + '", "x":50}'
        assertThrows(Exception.class, {  JsonReader.jsonToJava(json) })

        MetaUtils.useUnsafe = true
        ShouldBeImpossibleToInstantiate s = JsonReader.jsonToJava(json)
        assert s.x == 50
        MetaUtils.useUnsafe = false
    }
}
