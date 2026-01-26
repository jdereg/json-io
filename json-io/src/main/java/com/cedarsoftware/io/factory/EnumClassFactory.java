package com.cedarsoftware.io.factory;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;
import com.cedarsoftware.util.ClassUtilities;

/**
 * @author Kenny Partlow (kpartlow@gmail.com)
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
public class EnumClassFactory implements ClassFactory {
    @Override
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {

        String name = getEnumName(jObj);
        Class<?> clazz = ClassUtilities.getClassIfEnum(c);

        if (clazz == null) {
            throw new JsonIoException("Unable to load enum: " + c + ", class not found or is not an Enum.");
        }

        if (name != null) {
            // Use Converter for String→Enum conversion
            Enum<?> enumValue = (Enum<?>) resolver.getConverter().convert(name, clazz);
            return jObj.setFinishedTarget(enumValue, false);
        }

        Object value = jObj.getValue();

        if (value instanceof String) {
            // Use Converter for String→Enum conversion
            Enum<?> enumValue = (Enum<?>) resolver.getConverter().convert(value, c);
            return jObj.setFinishedTarget(enumValue, true);
        }

        throw new JsonIoException("Unable to instantiate enum: " + c + ", class not found or is not an Enum.");
    }

    /**
     * @deprecated Use Converter.convert() instead. This method is retained for backward compatibility
     * with any subclasses that may override it.
     */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Enum fromString(Class<?> c, String s) {
        return Enum.valueOf((Class<Enum>) c, s);
    }


    protected String getEnumName(JsonObject jObj) {
        String name = (String) jObj.get("Enum.name");
        return name != null ? name : (String) jObj.get("name");
    }
}
