package com.cedarsoftware.io;


import java.util.HashMap;
import java.util.Map;

import com.cedarsoftware.util.ClassUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

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

        ReadOptionsBuilder.addPermanentClassFactory(Dog.Shoe.class, new JsonReader.ClassFactory() {
            public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver)
            {
                return Dog.Shoe.construct();
            }
        });

        Map<Class<Dog.Shoe>, JsonReader.JsonClassReader> customReader = new HashMap<>();
        customReader.put(Dog.Shoe.class, new JsonReader.JsonClassReader() {
            public Object read(Object jOb, Resolver resolver)
            {
                // no need to do anything special
                return jOb;
            }
        });
        TestUtil.toJava(workaroundString, new ReadOptionsBuilder().replaceCustomReaderClasses(customReader).build()).asClass(null);
        // shoe can be accessed by
        // checking array type + length
        // and accessing [0]

        String json = TestUtil.toJson(shoe);
        //Should not fail, as we defined our own reader
        // It is expected, that this object is instantiated twice:
        // -once for analysis + Stack
        // -deserialization with Stack
        TestUtil.toJava(json, new ReadOptionsBuilder().replaceCustomReaderClasses(customReader).build()).asClass(null);
    }

    @Test
    public void testDirectCreation()
    {
        // This test verifies that inner classes can be instantiated with unsafe mode enabled
        Dog.OtherShoe shoe = Dog.OtherShoe.construct();
        
        // For this specific test, we need to enable unsafe mode directly because
        // Dog.OtherShoe has a complex nested structure that requires special handling
        ClassUtilities.setUseUnsafe(true);
        try {
            Dog.OtherShoe oShoe = TestUtil.toJava(TestUtil.toJson(shoe), null).asClass(Dog.OtherShoe.class);
            assertEquals(shoe, oShoe);
            oShoe = TestUtil.toJava(TestUtil.toJson(shoe), null).asClass(Dog.OtherShoe.class);
            assertEquals(shoe, oShoe);
            
            // Verify unsafe mode is working by instantiating again
            oShoe = TestUtil.toJava(TestUtil.toJson(shoe), null).asClass(Dog.OtherShoe.class);
            assertEquals(shoe, oShoe);
        } finally {
            ClassUtilities.setUseUnsafe(false);
        }
    }

    @Test
    public void testImpossibleClass()
    {
        // The constructor throws an exception when called normally
        assertThrows(Exception.class, ShouldBeImpossibleToInstantiate::new);
        
        // With unsafe mode enabled, objects can be instantiated even when their constructors
        // throw exceptions, because unsafe bypasses constructors
        String json = "{\"@type\":\"" + ShouldBeImpossibleToInstantiate.class.getName() + "\", \"x\":50}";
        ReadOptions readOptions = new ReadOptionsBuilder()
                .useUnsafe(true)  // Enable unsafe mode to bypass constructor exception
                .build();
        ShouldBeImpossibleToInstantiate s = TestUtil.toJava(json, readOptions).asClass(ShouldBeImpossibleToInstantiate.class);
        assert s.x == 50;
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
