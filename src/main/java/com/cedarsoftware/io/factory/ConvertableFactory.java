package com.cedarsoftware.io.factory;

import java.util.Map;

import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
import com.cedarsoftware.io.JsonValue;
import com.cedarsoftware.io.ReferenceTracker;
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
public class ConvertableFactory<T> implements JsonReader.ClassFactory {

    private final Class<? extends T> type;

    public ConvertableFactory(Class<? extends T> c) {
        this.type = c;
    }

    @Override
    public T newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        if (jObj.hasValue()) {
            // "Primitive" types, essentially Converter-able types.
            Object converted = resolver.getConverter().convert(jObj.getValue(), getType());
            return (T) jObj.setFinishedTarget(converted, true);
        }

        resolveReferences(resolver, jObj);
        Class<?> javaType = jObj.getJavaType();
        Object converted = resolver.getConverter().convert(jObj, javaType);
        return (T) jObj.setFinishedTarget(converted, true);
    }

    private void resolveReferences(Resolver resolver, JsonObject jObj) {
        ReferenceTracker refTracker = resolver.getReferences();
        for (Map.Entry<Object, Object> entry : jObj.entrySet()) {
            if (entry.getValue() instanceof JsonObject) {
                JsonObject child = (JsonObject) entry.getValue();
                if (child.isReference()) {
                    Object value = refTracker.get(child.getReferenceId());
                    if (value != null) {
                        entry.setValue(value);
                    }
                }
            }
        }
    }

    public Class<? extends T> getType() {
        return type;
    }

    /**
     * It is expected that ConvertableFactory instances complete both instantiation of the class, and loadind og the
     * instance data from JSON in the factory.  If they do not, then override this and return false.  If you go that
     * route, you would also need to write a custom reader to load later. Not sure why anyone would choose to go that
     * route.
     */
    public boolean isObjectFinal() {
        return true;
    }
}
