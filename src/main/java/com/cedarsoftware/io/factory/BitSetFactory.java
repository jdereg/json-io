package com.cedarsoftware.io.factory;

import java.util.BitSet;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory to create BitSet instances during deserialization.
 * Expects JSON format: {"@type":"BitSet","value":[1,5,10]}
 * where the array contains indices of set bits.
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
public class BitSetFactory implements ClassFactory {

    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object value = jObj.get("value");
        BitSet bitSet = new BitSet();

        if (value == null) {
            // Empty BitSet
            return bitSet;
        }

        if (value instanceof Object[]) {
            // Array of bit indices
            Object[] indices = (Object[]) value;
            for (Object index : indices) {
                if (index instanceof Number) {
                    bitSet.set(((Number) index).intValue());
                }
            }
        }

        return bitSet;
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
