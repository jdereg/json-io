package com.cedarsoftware.io.factory;

import java.sql.Timestamp;

import com.cedarsoftware.io.JsonIoException;
import com.cedarsoftware.io.JsonObject;
import com.cedarsoftware.io.JsonReader;
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
public class TimestampFactory implements JsonReader.ClassFactory {

    public TimestampFactory() {
    }

    @Override
    public Timestamp newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Old way
        if (jObj.containsKey("time") && jObj.containsKey("nanos")) {
            return resolver.getConverter().convert(jObj, Timestamp.class);
        }
        
        // Ensure the JSON has the "value" field
        Object value = jObj.get("value");
        if (value instanceof String || value instanceof Number) {
            return resolver.getConverter().convert(value, Timestamp.class);
        }
        value = jObj.get("_v");
        if (value instanceof String || value instanceof Number) {
            return resolver.getConverter().convert(value, Timestamp.class);
        }

        throw new JsonIoException("Invalid Timestamp format. Missing 'value' field.");
    }
}
