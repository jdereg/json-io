package com.cedarsoftware.io.factory;

import java.lang.reflect.Array;
import java.lang.reflect.Type;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ArrayUtilities;
import com.cedarsoftware.util.TypeUtilities;
import com.cedarsoftware.util.convert.Converter;

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
public class ArrayFactory<T> implements ClassFactory {

    private final Class<T> type;

    public ArrayFactory(Class<T> c) {
        this.type = c;
    }

    public T newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object[] items = jObj.getItems();
        Converter converter = resolver.getConverter();
        if (items == null) {
            return (T)jObj.setTarget(null);
        }
        int len = items.length;
        Class<?> arrayType = getType();
        Class<?> componentType = arrayType.getComponentType();
        Object array = Array.newInstance(componentType, len);

        // Check once if any items are JsonObjects - avoid checking every iteration
        boolean hasJsonObjects = false;
        for (Object item : items) {
            if (item instanceof JsonObject) {
                hasJsonObjects = true;
                break;
            }
        }

        if (componentType.isPrimitive()) {
            // Primitive arrays - use optimized ArrayUtilities.setPrimitiveElement()
            if (hasJsonObjects) {
                // Complex path: handle JsonObject unwrapping
                for (int i = 0; i < len; i++) {
                    Object val = items[i];
                    if (val != null) {
                        if (val instanceof JsonObject) {
                            val = unwrapJsonObject((JsonObject) val, componentType, converter);
                        } else {
                            val = converter.convert(val, componentType);
                        }
                        ArrayUtilities.setPrimitiveElement(array, i, val);
                    }
                }
            } else {
                // Fast path: no JsonObjects, just convert and assign
                for (int i = 0; i < len; i++) {
                    Object val = items[i];
                    if (val != null) {
                        val = converter.convert(val, componentType);
                        ArrayUtilities.setPrimitiveElement(array, i, val);
                    }
                }
            }
        } else {
            // Reference type arrays - direct assignment
            Object[] typedArray = (Object[]) array;
            if (hasJsonObjects) {
                // Complex path: handle JsonObject unwrapping
                for (int i = 0; i < len; i++) {
                    Object val = items[i];
                    if (val != null) {
                        if (val instanceof JsonObject) {
                            val = unwrapJsonObject((JsonObject) val, componentType, converter);
                        } else if (!componentType.isAssignableFrom(val.getClass())) {
                            // Only convert if value is not already assignable to component type
                            // This preserves subclass types (e.g., java.sql.Date in a Date[] array)
                            val = converter.convert(val, componentType);
                        }
                        typedArray[i] = val;
                    }
                }
            } else {
                // Fast path: no JsonObjects, just convert and assign
                for (int i = 0; i < len; i++) {
                    Object val = items[i];
                    if (val != null) {
                        if (!componentType.isAssignableFrom(val.getClass())) {
                            // Only convert if value is not already assignable to component type
                            // This preserves subclass types (e.g., java.sql.Date in a Date[] array)
                            val = converter.convert(val, componentType);
                        }
                        typedArray[i] = val;
                    }
                }
            }
        }

        return (T) jObj.setTarget(array);
    }

    /**
     * Unwraps a JsonObject, handling nested {@type:..., value:...} structures.
     * Returns the converted value ready for array assignment.
     */
    private Object unwrapJsonObject(JsonObject jsonObject, Class<?> componentType, Converter converter) {
        Object val = jsonObject;
        Type type = null;

        // Unwrap nested JsonObjects: {@type:long, value:{@type:int, value:3}}
        do {
            JsonObject current = (JsonObject) val;
            type = current.getType();
            if (!current.hasValue()) {
                break;
            }
            val = current.getValue();
        } while (val instanceof JsonObject);

        // Use the specified type, or fall back to component type
        Class<?> targetType = (type == null) ? componentType : TypeUtilities.getRawClass(type);
        return converter.convert(val, targetType);
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
