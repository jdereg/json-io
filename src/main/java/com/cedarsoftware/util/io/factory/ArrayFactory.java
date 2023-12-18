package com.cedarsoftware.util.io.factory;

import java.lang.reflect.Array;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

import static com.cedarsoftware.util.io.MetaUtils.convert;

/**
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
 *         limitations under the License.*
 */
public abstract class ArrayFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object[] items = jObj.getArray();

        if (items == null) {
            jObj.setTarget(null);
        } else {
            int len = items.length;
            Class<?> arrayType = getType();
            Class<?> componentType = arrayType.getComponentType();
            Object array = Array.newInstance(componentType, len);

            for (int i = 0; i < len; i++) {
                Object val = items[i];
                Object value = val == null ? null : convert(componentType, val);
                Array.set(array, i, value);
            }

            jObj.setTarget(array);
        }
        return jObj.getTarget();
    }

    public abstract Class<?> getType();

    /**
     * @return true.  Strings are always immutable, final.
     */
    public boolean isObjectFinal() {
        return true;
    }
}
