package com.cedarsoftware.io.factory;

import java.lang.reflect.Array;
import java.util.EnumSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.MetaUtils;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ClassUtilities;

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
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        if (!EnumSet.class.isAssignableFrom(c)) {
            throw new JsonIoException("EnumSetFactory can only create EnumSet instances");
        }

        String enumTypeName = jObj.getEnumType();
        Class<? extends Enum> enumClass = enumTypeName == null ?
                evaluateEnumSetTypeFromItems(jObj) :
                (Class<? extends Enum>) ClassUtilities.forName(enumTypeName, resolver.getReadOptions().getClassLoader());

        Object items = jObj.getItems();
        if (items == null || Array.getLength(items) == 0) {
            if (enumClass != null) {
                return EnumSet.noneOf((Class<Enum>)enumClass);
            }
            return EnumSet.noneOf(MetaUtils.Dumpty.class);
        }

        if (enumClass == null) {
            throw new JsonIoException("Could not determine enum type for non-empty EnumSet");
        }

        EnumSet enumSet = null;
        int len = Array.getLength(items);
        for (int i = 0; i < len; i++) {
            Object item = Array.get(items, i);
            Enum enumItem;
            if (item instanceof String) {
                enumItem = Enum.valueOf(enumClass, (String) item);
            } else {
                JsonObject itemObj = (JsonObject) item;
                enumItem = Enum.valueOf(enumClass, (String) itemObj.get("name"));
            }

            if (enumSet == null) {
                enumSet = EnumSet.of(enumItem);
            } else {
                enumSet.add(enumItem);
            }
        }

        return enumSet;
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Enum> evaluateEnumSetTypeFromItems(final JsonObject json) {
        final Object items = json.getItems();
        if (items != null && Array.getLength(items) != 0) {
            Object value = Array.get(items, 0);
            if (value instanceof JsonObject) {
                Class<?> type = ((JsonObject) value).getJavaType();
                if (type != null && type.isEnum()) {
                    return (Class<? extends Enum>) type;
                }
            }
        }
        return null;
    }
}