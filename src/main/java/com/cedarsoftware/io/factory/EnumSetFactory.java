package com.cedarsoftware.io.factory;

import java.util.EnumSet;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
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
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Attempt to get the actual enum class from the provided class 'c'
        Class<?> enumClass = ClassUtilities.getClassIfEnum(jObj.getRawType());

        // If enumClass is null or not an enum, try to get it from the first item in @items
        if (enumClass == null) {
            Object[] items = jObj.getItems();
            if (items != null && items.length > 0) {
                Object firstItem = items[0];
                if (firstItem instanceof JsonObject) {
                    JsonObject jsonItem = (JsonObject) firstItem;
                    enumClass = ClassUtilities.getClassIfEnum(jsonItem.getRawType());
                } else if (firstItem instanceof String) {
                    // If items are simple strings, we need to rely on additional information
                    // Since we cannot determine the enum class from the string, throw an exception
                    throw new JsonIoException("Unable to determine enum class from items in EnumSet");
                }
            } else {
                // in case field is serialized without values, but it's mentioned as java.util.RegularEnumSet
                return null;
            }
        }

        // If we still cannot determine the enum class, throw an exception
        if (enumClass == null || !enumClass.isEnum()) {
            throw new JsonIoException("Unable to create EnumSet - no valid enum class found");
        }

        // Create an empty EnumSet of the correct type
        EnumSet enumSet = EnumSet.noneOf((Class<Enum>) enumClass);
        jObj.setTarget(enumSet);  // Set the EnumSet as the target object for further population

        Object[] items = jObj.getItems();
        if (items == null) {
            return enumSet;  // Return empty EnumSet
        }

        // Iterate over the items and populate the EnumSet
        for (Object item : items) {
            if (item instanceof JsonObject) {
                // Object format
                JsonObject jsonItem = (JsonObject) item;
                // Resolve the enum class from the item's class
                Class<?> itemEnumClass = ClassUtilities.getClassIfEnum(jsonItem.getRawType());
                if (itemEnumClass == null) {
                    itemEnumClass = enumClass;
                }

                String enumName = (String) jsonItem.get("name");
                if (enumName == null) {
                    throw new JsonIoException("Enum constant missing 'name' field");
                }
                Enum<?> enumConstant = Enum.valueOf((Class<Enum>) itemEnumClass, enumName);
                enumSet.add(enumConstant);
            } else if (item instanceof String) {
                // Just enum names
                String enumName = (String) item;
                Enum<?> enumConstant = Enum.valueOf((Class<Enum>) enumClass, enumName);
                enumSet.add(enumConstant);
            } else {
                throw new JsonIoException("Unexpected item type in EnumSet: " + item.getClass());
            }
        }
        return enumSet;
    }

    public boolean isObjectFinal() {
        return true;
    }
}