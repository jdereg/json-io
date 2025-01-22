package com.cedarsoftware.io.factory;

import java.nio.CharBuffer;

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
public class CharBufferFactory implements JsonReader.ClassFactory {

    public CharBufferFactory() {
    }

    @Override
    public CharBuffer newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Check for the "value" field, just like ByteBufferFactory
        if (!jObj.containsKey("value")) {
            throw new JsonIoException("Invalid CharBuffer format. Missing 'value' field.");
        }

        // Grab the value and ensure it's a String
        Object val = jObj.getValue();
        if (!(val instanceof String)) {
            throw new JsonIoException("Invalid CharBuffer format. 'value' field must be a String.");
        }

        // Since CharBufferWriter wrote out a JSON-escaped string, json-io will
        // have unescaped it into this String. We can simply wrap it now.
        String decoded = (String) val;
        return CharBuffer.wrap(decoded);
    }
}

