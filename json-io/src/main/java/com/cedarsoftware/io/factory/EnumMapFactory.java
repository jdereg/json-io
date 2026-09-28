package com.cedarsoftware.io.factory;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ClassUtilities;
import com.cedarsoftware.util.TypeUtilities;

/**
 * Factory class to create an empty {@link EnumMap}, which the resolver then fills from the JSON.
 * <p>
 * An EnumMap cannot be made without its enum class. It comes from the declared type (a field declared
 * {@code EnumMap<Color, V>}), or else from the first key when the keys carry their type -- json-io writes an
 * EnumMap's keys in {@code @keys} as {@code {"@type":"Color","name":"RED"}}. There was no factory, so the
 * generic instantiation path was tried, and it failed on those keys.
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
public class EnumMapFactory implements ClassFactory {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Class<?> enumClass = enumClassOf(jObj.getMapKeyType());
        if (enumClass == null && jObj.getType() instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) jObj.getType()).getActualTypeArguments();
            enumClass = args.length > 0 ? enumClassOf(args[0]) : null;
        }
        if (enumClass == null) {
            Object[] keys = jObj.getKeys();
            if (keys != null && keys.length > 0 && keys[0] instanceof JsonObject) {
                enumClass = ClassUtilities.getClassIfEnum(((JsonObject) keys[0]).getRawType());
            }
        }
        if (enumClass == null) {
            throw new JsonIoException("Unable to create an EnumMap: its enum class is not known. Declare the field or "
                    + "target type as EnumMap<YourEnum, V>, or write the map's keys with their type.");
        }
        EnumMap map = new EnumMap(enumClass);
        jObj.setTarget(map);
        return map;
    }

    private static Class<?> enumClassOf(Type type) {
        Class<?> raw = type == null ? null : TypeUtilities.getRawClass(type);
        return raw == null ? null : ClassUtilities.getClassIfEnum(raw);
    }
}
