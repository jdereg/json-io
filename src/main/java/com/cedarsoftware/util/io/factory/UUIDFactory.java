package com.cedarsoftware.util.io.factory;

import java.util.UUID;

import com.cedarsoftware.util.io.JsonIoException;
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
public class UUIDFactory implements JsonReader.ClassFactory {
    public Object newInstance(Class<?> c, JsonObject jObj, ReaderContext context) {
        Object value = jObj.getValue();
        if (value instanceof String) {
            try {
                return UUID.fromString((String) value);
            }
            catch (Exception e) {
                throw new JsonIoException("Unable to load UUID from JSON string: " + value, e);
            }
        }

        Long mostSigBits = (Long) jObj.get("mostSigBits");
        if (mostSigBits == null) {
            throw new JsonIoException("java.util.UUID must specify 'mostSigBits' field and it cannot be empty in JSON");
        }
        Long leastSigBits = (Long) jObj.get("leastSigBits");
        if (leastSigBits == null) {
            throw new JsonIoException("java.util.UUID must specify 'leastSigBits' field and it cannot be empty in JSON");
        }

        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * @return true.  UUIDs are always immutable, final.
     */
    public boolean isObjectFinal() {
        return true;
    }
}
