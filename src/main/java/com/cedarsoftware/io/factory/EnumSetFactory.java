package com.cedarsoftware.io.factory;

import java.lang.reflect.Array;
import java.util.EnumSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;

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
public class EnumSetFactory implements JsonReader.ClassFactory {
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Get enum class either from javaType or from first item in @items
        Class<?> enumClass = jObj.getJavaType();

        if (!enumClass.isEnum()) {
            Object items = jObj.getItems();
            if (items != null && Array.getLength(items) > 0) {
                Object firstItem = Array.get(items, 0);
                if (firstItem instanceof JsonObject) {
                    JsonObject jsonItem = (JsonObject) firstItem;
                    enumClass = jsonItem.getJavaType();
                }
            }
        }

        if (enumClass == null || !enumClass.isEnum()) {
            throw new JsonIoException("Unable to create EnumSet - no valid enum class found");
        }

        EnumSet enumSet = EnumSet.noneOf((Class<Enum>)enumClass);
        Object items = jObj.getItems();
        if (items == null || Array.getLength(items) == 0) {
            return enumSet;
        }

        int len = Array.getLength(items);
        for (int i = 0; i < len; i++) {
            Object item = Array.get(items, i);
            if (item instanceof JsonObject) {
                // Old format - full enum objects
                JsonObject jsonItem = (JsonObject) item;
                enumSet.add(Enum.valueOf((Class)enumClass, (String)jsonItem.get("name")));
            } else if (item instanceof String) {
                // New format - just enum names
                enumSet.add(Enum.valueOf((Class)enumClass, (String)item));
            }
        }
        return enumSet;
    }

    public boolean isObjectFinal() {
        return true;
    }
}