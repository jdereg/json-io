package com.cedarsoftware.io.factory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicStampedReference;

import com.cedarsoftware.io.ClassFactory;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.Resolver;

/**
 * Reads an {@link AtomicStampedReference} from {@code "pair":{"reference":...,"stamp":...}}, as
 * {@link com.cedarsoftware.io.writers.AtomicStampedReferenceWriter} writes it and as json-io wrote it by reflection
 * on JDK 8 and 11. Its field cannot be set on JDK 17+, so it is built here: the reference is read as the field's type
 * argument when the JSON does not name its type, and a reference to an object not yet built is set once it is.
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
public class AtomicStampedReferenceFactory implements ClassFactory {

    @Override
    @SuppressWarnings("unchecked")
    public Object newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        Object pair = jObj.get("pair");
        Map<?, ?> fields = pair instanceof Map ? (Map<?, ?>) pair : null;
        int stamp = resolver.getConverter().convert(fields == null ? null : fields.get("stamp"), int.class);
        AtomicStampedReference<Object> holder = new AtomicStampedReference<>(null, stamp);
        Object reference = resolver.resolveHeldValue(jObj, fields == null ? null : fields.get("reference"),
                AtomicReferenceArrayFactory.typeArgument(jObj), (built, value) -> {
                    AtomicStampedReference<Object> h = (AtomicStampedReference<Object>) built;
                    h.set(value, h.getStamp());
                });
        holder.set(reference, stamp);
        return holder;
    }

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
