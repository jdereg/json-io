package com.cedarsoftware.io.factory;

import java.lang.reflect.Array;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.convert.Converter;

import static com.cedarsoftware.io.JsonValue.ITEMS;

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
 *         limitations under the License.
 */
public class ArrayFactory<T> implements JsonReader.ClassFactory {

    private final Class<T> type;

    public ArrayFactory(Class<T> c) {
        this.type = c;
    }

    public T newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object items = jObj.get(ITEMS);
        Converter converter = resolver.getConverter();
        if (items == null) {
            jObj.setTarget(null);
            return null;
        }
        int len = Array.getLength(items);
        Class<?> arrayType = getType();
        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, len);

        for (int i = 0; i < len; i++) {
            Object val = Array.get(items, i);
            if (val == null) {
            } else if (val instanceof JsonObject) {
                Class<?> type;
                do {
                    // Allow for {@type:long, value:{@type:int, value:3}}  (and so on...)
                    JsonObject jsonObject = (JsonObject) val;
                    type = jsonObject.getJavaType();
                    if (!jsonObject.hasValue()) {
                        break;
                    }
                    val = jsonObject.getValue();
                } while (val instanceof JsonObject);

                if (type == null) {
                    type = componentType;
                }
                val = resolver.getConverter().convert(val, type);

            } else {
                val = resolver.getConverter().convert(val, componentType);
            }
            try {
                Array.set(array, i, val);
            } catch (Exception e) {
                Array.set(array, i, converter.convert(val, componentType));
            }
        }

        jObj.setTarget(array);
        return (T) jObj.getTarget();
    }

    public Class<?> getType() {
        return type;
    }

    /**
     * @return true.  Strings are always immutable, final.
     */
    public boolean isObjectFinal() {
        return true;
    }
}
