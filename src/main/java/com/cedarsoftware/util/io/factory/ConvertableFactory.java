package com.cedarsoftware.util.io.factory;

import java.util.Map;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.ReaderContext;

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
public class ConvertableFactory<T> implements JsonReader.ClassFactory {

    private final Class<? extends T> type;

    public ConvertableFactory(Class<? extends T> c) {
        this.type = c;
    }

    @Override
    public T newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        if (jObj.hasValue()) {
            // TODO: surprised we are seeing any entries come through here since we check these in the resolver
            // TODO:  turns out this was factory tests and invalid conversions.
            // TODO:  probably need to leave for invalid conversios (or check for invalid and throw our own exception).
            Object converted = context.getConverter().convert(jObj.getValue(), getType());
            return (T) jObj.setFinishedTarget(converted, true);
        }

        resolveReferences(context, jObj);

        Class<?> javaType = jObj.getJavaType();

        if (javaType == null) {
            javaType = getType();
        }
        Object converted = context.getConverter().convert(jObj, javaType);
        return (T) jObj.setFinishedTarget(converted, true);
    }

    private void resolveReferences(ReaderContext context, JsonObject jObj) {
        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
            if (entry.getValue() instanceof JsonObject) {
                JsonObject child = (JsonObject) entry.getValue();
                if (child.isReference()) {
                    entry.setValue(context.getReferences().get(child));
                }
            }
        }
    }

    public Class<? extends T> getType() {
        return type;
    }

    /**
     * @return true.  Strings are always immutable, final.
     */
    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
