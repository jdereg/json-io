package com.cedarsoftware.util.io;


import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 * <br>
 * Copyright (c) Cedar Software LLC
 * <br><br>
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <br><br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br><br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class SunMiscTest
{
    @Test
    public void testCustomTopReaderShoe()
    {
        Dog.Shoe shoe = Dog.Shoe.construct();

        // Dirty Workaround otherwise
        Object[] array = new Object[1];
        array[0] = shoe;
        String workaroundString = TestUtil.toJson(array);

        JsonReader.assignInstantiator(Dog.Shoe.class, new JsonReader.ClassFactory() {
            public Object newInstance(Class<?> c, JsonObject<String, Object> o)
            {
                return Dog.Shoe.construct();
            }
        });

        Map<Class<Dog.Shoe>, JsonReader.JsonClassReader> customReader = new HashMap<Class<Dog.Shoe>, JsonReader.JsonClassReader>();
        customReader.put(Dog.Shoe.class, new JsonReader.JsonClassReader() {
            public Object read(Object jOb, Deque<JsonObject<String, Object>> stack)
            {
                // no need to do anything special
                return jOb;
            }
        });
        TestUtil.toJava(workaroundString, new ReadOptionsBuilder().withCustomReaders(customReader).build());
        // shoe can be accessed by
        // checking array type + length
        // and accessing [0]

        String json = TestUtil.toJson(shoe);
        //Should not fail, as we defined our own reader
        // It is expected, that this object is instantiated twice:
        // -once for analysis + Stack
        // -deserialization with Stack
        TestUtil.toJava(json, new ReadOptionsBuilder().withCustomReaders(customReader).build());
    }

    @Test
    public void testDirectCreation()
    {
        MetaUtils.setUseUnsafe(true);
        // this test will fail without directCreation
        Dog.OtherShoe shoe = Dog.OtherShoe.construct();
        Dog.OtherShoe oShoe = TestUtil.toJava(TestUtil.toJson(shoe));
        assertEquals(shoe, oShoe);
        oShoe = TestUtil.toJava(TestUtil.toJson(shoe));
        assertEquals(shoe, oShoe);

        try
        {
            MetaUtils.setUseUnsafe(false);
            shoe = Dog.OtherShoe.construct();
            TestUtil.toJava(TestUtil.toJson(shoe));
            fail();
        }
        catch (JsonIoException e)
        {
            assert e.getMessage().toLowerCase().contains("no constructor found");
        }


        MetaUtils.setUseUnsafe(true);
        // this test will fail without directCreation
        Dog.OtherShoe.construct();
        oShoe = TestUtil.toJava(TestUtil.toJson(shoe));
        assertEquals(shoe, oShoe);
    }

    @Test
    public void testImpossibleClass()
    {
        assertThrows(Exception.class, ShouldBeImpossibleToInstantiate::new);
        String json = "{\"@type\":\"" + ShouldBeImpossibleToInstantiate.class.getName() + "\", \"x\":50}";
        assertThrows(Exception.class, () -> {  TestUtil.toJava(json); });

        MetaUtils.setUseUnsafe(true);
        ShouldBeImpossibleToInstantiate s = TestUtil.toJava(json);
        assert s.x == 50;
        MetaUtils.setUseUnsafe(false);
    }

    public static class ShouldBeImpossibleToInstantiate
    {
        public ShouldBeImpossibleToInstantiate()
        {
            throw new JsonIoException("Go away");
        }

        private Integer x = 0;
    }
}
