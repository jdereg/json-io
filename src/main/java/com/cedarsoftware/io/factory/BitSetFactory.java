package com.cedarsoftware.io.factory;

import java.util.BitSet;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Factory to create BitSet instances during deserialization.
 * Supports two JSON formats:
 * <ul>
 *   <li>Binary string (preferred): {"@type":"BitSet","value":"101010"}</li>
 *   <li>Array of indices (legacy): {"@type":"BitSet","value":[1,3,5]}</li>
 * </ul>
 * In binary string format, rightmost character is bit 0 (standard binary notation).
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

        if (value instanceof String) {
            // Binary string format: "101010" where rightmost is bit 0
            String binaryStr = (String) value;
            int len = binaryStr.length();
            for (int i = 0; i < len; i++) {
                char ch = binaryStr.charAt(i);
                if (ch == '1') {
                    // Character at position i represents bit (len - 1 - i)
                    bitSet.set(len - 1 - i);
                }
            }
        } else if (value instanceof Object[]) {
            // Legacy array format: [1, 3, 5] - array of bit indices
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
