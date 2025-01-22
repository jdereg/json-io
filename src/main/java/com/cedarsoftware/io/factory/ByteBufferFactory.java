package com.cedarsoftware.io.factory;

import java.nio.ByteBuffer;
import java.util.Base64;

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
public class ByteBufferFactory implements JsonReader.ClassFactory {

    public ByteBufferFactory() {
    }

    @Override
    public ByteBuffer newInstance(Class<?> c, JsonObject jObj, Resolver resolver) {
        // Ensure the JSON has the "value" field
        if (!jObj.containsKey("value")) {
            throw new JsonIoException("Invalid ByteBuffer format. Missing 'value' field.");
        }

        // The data should be a Base64-encoded String
        Object val = jObj.getValue();  // or jObj.get("value")
        if (!(val instanceof String)) {
            throw new JsonIoException("Invalid ByteBuffer format. 'value' must be a Base64-encoded String.");
        }

        String base64 = (String) val;
        // Decode the Base64 string into a byte array
        byte[] decoded = Base64.getDecoder().decode(base64);

        // Wrap the byte array with a ByteBuffer
        return ByteBuffer.wrap(decoded);
    }
}
