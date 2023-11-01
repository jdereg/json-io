package com.cedarsoftware.util.io.factory;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;

import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

/**
 * Abstract class to help create temporal items.
 * <p>
 * All custom writers for json-io subclass this class.  Special writers are not needed for handling
 * user-defined classes.  However, special writers are built/supplied by json-io for many of the
 * primitive types and other JDK classes simply to allow for a more concise form.
 * <p>
 * @author Kenny Partlow (kpartlow@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public abstract class AbstractTemporalFactory<T extends TemporalAccessor> implements JsonReader.ClassFactory {
    protected final DateTimeFormatter dateTimeFormatter;

    protected AbstractTemporalFactory(DateTimeFormatter dateFormatter) {
        this.dateTimeFormatter = dateFormatter;
    }

    @Override
    public T newInstance(Class<?> c, JsonObject job) {
        Object value = job.get("value");

        if (value instanceof String) {
            return fromString((String) value);
        }

        if (value instanceof Number) {
            return fromNumber((Number) value);
        }

        return fromJsonObject(job);
    }

    protected abstract T fromString(String s);

    protected T fromNumber(Number l) {
        throw new IllegalArgumentException("Long Timestamps are not supported for this Temporal class");
    }

    protected abstract T fromJsonObject(JsonObject job);

    @Override
    public boolean isObjectFinal() {
        return true;
    }
}
