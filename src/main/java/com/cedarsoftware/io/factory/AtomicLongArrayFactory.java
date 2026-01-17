package com.cedarsoftware.io.factory;

import java.util.concurrent.atomic.AtomicLongArray;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory to create AtomicLongArray instances during deserialization.
 * Expects JSON format: {"@type":"AtomicLongArray","value":[1,2,3]}
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
public class AtomicLongArrayFactory implements ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object value = jObj.get("value");

        if (value == null) {
            // Empty array
            return new AtomicLongArray(0);
        }

        if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            long[] longArray = new long[array.length];
            for (int i = 0; i < array.length; i++) {
                if (array[i] instanceof Number) {
                    longArray[i] = ((Number) array[i]).longValue();
                }
            }
            return new AtomicLongArray(longArray);
        }

        return new AtomicLongArray(0);
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
