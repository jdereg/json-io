package com.cedarsoftware.io.factory;

import java.util.concurrent.atomic.AtomicIntegerArray;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory to create AtomicIntegerArray instances during deserialization.
 * Expects JSON format: {"@type":"AtomicIntegerArray","value":[1,2,3,4,5]}
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
public class AtomicIntegerArrayFactory implements ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object value = jObj.get("value");

        if (value == null) {
            // Empty array
            return new AtomicIntegerArray(0);
        }

        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            int[] intArray = new int[array.length];
            for (int i = 0; i < array.length; i++) {
                if (array[i] instanceof Number) {
                    intArray[i] = ((Number) array[i]).intValue();
                }
            }
            return new AtomicIntegerArray(intArray);
        }

        return new AtomicIntegerArray(0);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
