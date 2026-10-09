package com.cedarsoftware.io.factory;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Reads an {@link AtomicReferenceArray} from {@code "array":[...]}, as
 * {@link com.cedarsoftware.io.writers.AtomicReferenceArrayWriter} writes it and as json-io wrote it by reflection on
 * JDK 8 and 11. Its field cannot be set on JDK 17+, so the array is built here: elements are read as the field's type
 * argument ({@code AtomicReferenceArray<Item>}) when the JSON does not name their type, and an element that refers to
 * an object not yet built is set once it is.
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
public class AtomicReferenceArrayFactory implements ClassFactory {

    @Override
    @SuppressWarnings("unchecked")
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object[] items = items(jObj.get("array"));
        Type elementType = typeArgument(jObj);
        AtomicReferenceArray<Object> array = new AtomicReferenceArray<>(items.length);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            array.set(i, resolver.resolveHeldValue(jObj, items[i], elementType,
                    (holder, value) -> ((AtomicReferenceArray<Object>) holder).set(index, value)));
        }
        return array;
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }

    /** The parsed JSON array: an {@code Object[]}, a list (from TOON), or an array the parser already wrapped. */
    private static Object[] items(Object array) {
        if (array instanceof Object[]) {
            return (Object[]) array;
        }
        if (array instanceof Collection) {
            return ((Collection<?>) array).toArray();
        }
        if (array instanceof JsonObject && ((JsonObject) array).isArray()) {
            return ((JsonObject) array).getItems();
        }
        return new Object[0];
    }

    /**
     * The holder's type argument where the JSON object was read for a field declared with one ({@code Item} for
     * {@code AtomicReferenceArray<Item>}), else {@code Object}: values are then read as written.
     */
    static Type typeArgument(JsonObject jObj) {
        Type type = jObj.getType();
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (args.length == 1 && (args[0] instanceof Class || args[0] instanceof ParameterizedType
                    || args[0] instanceof GenericArrayType)) {
                return args[0];
            }
        }
        return Object.class;
    }
}
